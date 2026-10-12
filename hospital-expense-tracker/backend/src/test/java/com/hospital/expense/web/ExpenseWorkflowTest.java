package com.hospital.expense.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospital.expense.model.Expense;
import com.hospital.expense.repo.ExpenseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class ExpenseWorkflowTest {
    private ExpenseRepository repo;
    private NotificationService notes;
    private ExpenseController controller;

    private static Authentication as(String user, String role) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Expense expense(String status, String createdBy) {
        Expense e = new Expense();
        e.id = "1"; e.expenseNo = "EXP-1001"; e.status = status; e.createdBy = createdBy;
        e.amount = new BigDecimal("1000"); e.gst = BigDecimal.ZERO;
        when(repo.findById("1")).thenReturn(Optional.of(e));
        return e;
    }

    @BeforeEach
    void setUp() {
        repo = mock(ExpenseRepository.class);
        notes = mock(NotificationService.class);
        controller = new ExpenseController(repo, new ExpenseQueryService(repo), mock(AuditService.class), notes, mock(LookupService.class), mock(SequenceService.class));
    }

    @Test
    void managerCanApprovePendingExpenseOfSomeoneElse() {
        Expense e = expense(Expense.PENDING, "staff");
        controller.approve("1", as("manager", "DEPARTMENT_MANAGER"));
        assertEquals(Expense.APPROVED, e.status);
        verify(notes).decided(any(Expense.class), any(String.class), any(String.class));
    }

    @Test
    void cannotApproveYourOwnExpense() {
        Expense e = expense(Expense.PENDING, "manager");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.approve("1", as("manager", "DEPARTMENT_MANAGER")));
        assertEquals(403, ex.getStatusCode().value());
        assertEquals(Expense.PENDING, e.status);
    }

    @Test
    void cannotPayBeforeApproval() {
        expense(Expense.PENDING, "staff");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.pay("1", as("accountant", "ACCOUNTANT")));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void approvedExpenseCanBePaid() {
        Expense e = expense(Expense.APPROVED, "staff");
        controller.pay("1", as("accountant", "ACCOUNTANT"));
        assertEquals(Expense.PAID, e.status);
    }

    @Test
    void rejectionRecordsTheReason() {
        Expense e = expense(Expense.PENDING, "staff");
        controller.reject("1", new ExpenseController.Reason("  Wrong invoice  "), as("manager", "DEPARTMENT_MANAGER"));
        assertEquals(Expense.REJECTED, e.status);
        assertEquals("Wrong invoice", e.rejectionReason);
    }

    @Test
    void staffCannotOpenSomeoneElsesExpense() {
        expense(Expense.DRAFT, "staff");
        ExpenseQueryService q = new ExpenseQueryService(repo);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> q.loadVisible("1", as("other.staff", "STAFF")));
        assertEquals(404, ex.getStatusCode().value());
    }
}
