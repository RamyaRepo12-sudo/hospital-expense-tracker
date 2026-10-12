package com.hospital.expense.web;

import com.hospital.expense.model.Expense;
import com.hospital.expense.repo.ExpenseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    public record ExpenseRequest(@NotNull LocalDate date, @NotBlank @Size(max = 100) String department, @NotBlank @Size(max = 100) String category,
                                 @NotBlank @Size(max = 100) String vendor, @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") BigDecimal amount,
                                 @DecimalMin("0") @DecimalMax("1000000000") BigDecimal gst, @NotBlank @Size(max = 30) String paymentMethod,
                                 @NotBlank @Size(max = 60) String invoiceNo, @Size(max = 500) String description) {}
    public record Reason(@NotBlank @Size(max = 300) String reason) {}

    static final Set<String> PAYMENT_METHODS = Set.of("Bank Transfer", "UPI", "Card", "Cheque", "Cash");

    static final String CREATORS = "hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN','DEPARTMENT_MANAGER','ACCOUNTANT','STAFF')";
    private static final String APPROVERS = "hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN','DEPARTMENT_MANAGER')";
    private static final String PAYERS = "hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN','ACCOUNTANT')";
    private static final String ADMINS = "hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN')";

    private final ExpenseRepository repo;
    private final ExpenseQueryService visible;
    private final AuditService audit;
    private final NotificationService notes;
    private final LookupService lookups;
    private final SequenceService sequences;

    public ExpenseController(ExpenseRepository repo, ExpenseQueryService visible, AuditService audit, NotificationService notes, LookupService lookups, SequenceService sequences) {
        this.repo = repo; this.visible = visible; this.audit = audit; this.notes = notes; this.lookups = lookups; this.sequences = sequences;
    }

    @GetMapping
    public List<Expense> list(Authentication a, @RequestParam(required = false) String department,
                              @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return visible.find(a, department, status, q, null, null);
    }

    @PostMapping
    @PreAuthorize(CREATORS)
    public Expense create(@Valid @RequestBody ExpenseRequest r, @RequestParam(defaultValue = "false") boolean submit, Authentication a) {
        Expense e = new Expense();
        apply(e, r);
        e.expenseNo = "EXP-" + sequences.next("expense");
        e.createdBy = a.getName();
        e.createdAt = Instant.now();
        e.status = submit ? Expense.PENDING : Expense.DRAFT;
        repo.save(e);
        audit.log(a.getName(), "Created expense (" + e.status + ")", e.expenseNo);
        if (submit) notes.submitted(e);
        return e;
    }

    @PutMapping("/{id}")
    @PreAuthorize(CREATORS)
    public Expense update(@PathVariable String id, @Valid @RequestBody ExpenseRequest r,
                          @RequestParam(defaultValue = "false") boolean submit, Authentication a) {
        Expense e = visible.loadVisible(id, a);
        if (!List.of(Expense.DRAFT, Expense.REJECTED).contains(e.status)) throw bad("Only draft or rejected expenses can be edited");
        apply(e, r);
        e.rejectionReason = null;
        e.status = submit ? Expense.PENDING : Expense.DRAFT;
        repo.save(e);
        audit.log(a.getName(), "Updated expense (" + e.status + ")", e.expenseNo);
        if (submit) notes.submitted(e);
        return e;
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(CREATORS)
    public Expense submit(@PathVariable String id, Authentication a) {
        Expense e = visible.loadVisible(id, a);
        if (!List.of(Expense.DRAFT, Expense.REJECTED).contains(e.status)) throw bad("Only draft or rejected expenses can be submitted");
        e.status = Expense.PENDING; e.rejectionReason = null;
        saveAndLog(e, a, "Submitted for approval");
        notes.submitted(e);
        return e;
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVERS)
    public Expense approve(@PathVariable String id, Authentication a) {
        Expense e = visible.loadVisible(id, a);
        if (!Expense.PENDING.equals(e.status)) throw bad("Only pending expenses can be approved");
        if (a.getName().equals(e.createdBy)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve your own expense");
        e.status = Expense.APPROVED;
        saveAndLog(e, a, "Approved");
        notes.decided(e, "Approved", a.getName());
        return e;
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVERS)
    public Expense reject(@PathVariable String id, @Valid @RequestBody Reason r, Authentication a) {
        Expense e = visible.loadVisible(id, a);
        if (!Expense.PENDING.equals(e.status)) throw bad("Only pending expenses can be rejected");
        e.status = Expense.REJECTED; e.rejectionReason = r.reason().trim();
        saveAndLog(e, a, "Rejected: " + e.rejectionReason);
        notes.decided(e, "Rejected", a.getName());
        return e;
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize(PAYERS)
    public Expense pay(@PathVariable String id, Authentication a) {
        Expense e = visible.loadVisible(id, a);
        if (!Expense.APPROVED.equals(e.status)) throw bad("Only approved expenses can be marked as paid");
        e.status = Expense.PAID;
        saveAndLog(e, a, "Marked as paid");
        notes.decided(e, "Paid", a.getName());
        return e;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMINS)
    public void delete(@PathVariable String id, Authentication a) {
        Expense e = visible.loadVisible(id, a);
        repo.delete(e);
        audit.log(a.getName(), "Deleted expense", e.expenseNo);
    }

    private void saveAndLog(Expense e, Authentication a, String action) {
        repo.save(e);
        audit.log(a.getName(), action, e.expenseNo);
    }

    private void apply(Expense e, ExpenseRequest r) {
        lookups.require("DEPARTMENT", r.department(), "Department");
        lookups.require("CATEGORY", r.category(), "Category");
        lookups.require("VENDOR", r.vendor(), "Vendor");
        if (!PAYMENT_METHODS.contains(r.paymentMethod())) throw bad("Unknown payment method");
        e.date = r.date(); e.department = r.department(); e.category = r.category(); e.vendor = r.vendor();
        e.amount = r.amount(); e.gst = r.gst() == null ? BigDecimal.ZERO : r.gst();
        e.paymentMethod = r.paymentMethod(); e.invoiceNo = r.invoiceNo().trim(); e.description = r.description();
    }

    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
