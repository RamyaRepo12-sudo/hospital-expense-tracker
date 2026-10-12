package com.hospital.expense.web;

import com.hospital.expense.model.Expense;
import com.hospital.expense.repo.ExpenseRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** One place that decides which expenses a user may see: STAFF only their own, everyone else all. */
@Service
public class ExpenseQueryService {
    private final ExpenseRepository repo;

    public ExpenseQueryService(ExpenseRepository repo) { this.repo = repo; }

    public static String role(Authentication a) { return a.getAuthorities().iterator().next().getAuthority().substring(5); }
    public static boolean isStaff(Authentication a) { return "STAFF".equals(role(a)); }

    public List<Expense> find(Authentication a, String department, String status, String q, LocalDate from, LocalDate to) {
        return findScoped(a, department, null, null, status, q, from, to);
    }

    /** status: blank = all, "spend" = Approved and Paid, otherwise an exact status. */
    public List<Expense> findScoped(Authentication a, String department, String category, String vendor, String status, String q, LocalDate from, LocalDate to) {
        List<Expense> base = isStaff(a) ? repo.findByCreatedBy(a.getName()) : repo.findAll();
        String needle = q == null ? "" : q.toLowerCase();
        return base.stream()
                .filter(e -> department == null || department.isBlank() || department.equals(e.department))
                .filter(e -> category == null || category.isBlank() || category.equals(e.category))
                .filter(e -> vendor == null || vendor.isBlank() || vendor.equals(e.vendor))
                .filter(e -> status == null || status.isBlank() || ("spend".equals(status) ? e.isSpend() : status.equals(e.status)))
                .filter(e -> from == null || !e.date.isBefore(from))
                .filter(e -> to == null || !e.date.isAfter(to))
                .filter(e -> needle.isEmpty() || (e.expenseNo + " " + e.invoiceNo + " " + e.vendor).toLowerCase().contains(needle))
                .sorted(Comparator.comparing((Expense e) -> e.date).reversed()).toList();
    }

    public Expense loadVisible(String id, Authentication a) {
        Expense e = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (isStaff(a) && !a.getName().equals(e.createdBy)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
        return e;
    }
}
