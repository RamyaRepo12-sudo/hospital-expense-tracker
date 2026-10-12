package com.hospital.expense.web;

import com.hospital.expense.model.RecurringRule;
import com.hospital.expense.repo.RecurringRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recurring")
public class RecurringController {
    public record RuleRequest(@NotNull LocalDate startDate, LocalDate endDate, @NotBlank String frequency,
                              @NotBlank @Size(max = 100) String department, @NotBlank @Size(max = 100) String category, @NotBlank @Size(max = 100) String vendor,
                              @NotNull @DecimalMin("0.01") @DecimalMax("1000000000") BigDecimal amount, @DecimalMin("0") @DecimalMax("1000000000") BigDecimal gst,
                              @NotBlank @Size(max = 30) String paymentMethod, @Size(max = 500) String description) {}
    public record Active(boolean active) {}

    private final RecurringRepository rules;
    private final RecurringService service;
    private final LookupService lookups;
    private final AuditService audit;

    public RecurringController(RecurringRepository rules, RecurringService service, LookupService lookups, AuditService audit) {
        this.rules = rules; this.service = service; this.lookups = lookups; this.audit = audit;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    @GetMapping
    public List<RecurringRule> list(Authentication a) {
        List<RecurringRule> base = ExpenseQueryService.isStaff(a) ? rules.findByCreatedBy(a.getName()) : rules.findAll();
        return base.stream().sorted(Comparator.comparing((RecurringRule r) -> !r.active).thenComparing(RecurringRule::getNextDate)).toList();
    }

    @PostMapping
    @PreAuthorize(ExpenseController.CREATORS)
    public RecurringRule create(@Valid @RequestBody RuleRequest r, Authentication a) {
        if (!Recurrence.FREQUENCIES.contains(r.frequency())) throw bad("Frequency must be weekly, monthly, quarterly or yearly");
        if (r.endDate() != null && r.endDate().isBefore(r.startDate())) throw bad("The end date is before the start date");
        if (r.startDate().isBefore(LocalDate.now().minusDays(31))) throw bad("The start date cannot be more than 31 days in the past");
        if (!ExpenseController.PAYMENT_METHODS.contains(r.paymentMethod())) throw bad("Unknown payment method");
        if (rules.countByCreatedBy(a.getName()) >= 100) throw bad("You have reached the limit of 100 recurring rules");
        lookups.require("DEPARTMENT", r.department(), "Department");
        lookups.require("CATEGORY", r.category(), "Category");
        lookups.require("VENDOR", r.vendor(), "Vendor");
        RecurringRule x = new RecurringRule();
        x.startDate = r.startDate(); x.endDate = r.endDate(); x.frequency = r.frequency();
        x.department = r.department(); x.category = r.category(); x.vendor = r.vendor();
        x.amount = r.amount(); x.gst = r.gst() == null ? BigDecimal.ZERO : r.gst();
        x.paymentMethod = r.paymentMethod(); x.description = r.description();
        x.createdBy = a.getName(); x.createdAt = Instant.now();
        rules.save(x);
        audit.log(a.getName(), "Created recurring rule (" + x.frequency + ")", x.vendor);
        service.generateDue(); // a rule that starts today (or a few days ago) produces its first draft immediately
        return rules.findById(x.id).orElse(x);
    }

    @PutMapping("/{id}/active")
    @PreAuthorize(ExpenseController.CREATORS)
    public RecurringRule setActive(@PathVariable String id, @RequestBody Active r, Authentication a) {
        RecurringRule x = owned(id, a);
        x.active = r.active();
        rules.save(x);
        audit.log(a.getName(), x.active ? "Resumed recurring rule" : "Paused recurring rule", x.vendor);
        return x;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ExpenseController.CREATORS)
    public void delete(@PathVariable String id, Authentication a) {
        RecurringRule x = owned(id, a);
        rules.delete(x);
        audit.log(a.getName(), "Deleted recurring rule (drafts already created are kept)", x.vendor);
    }

    private RecurringRule owned(String id, Authentication a) {
        RecurringRule x = rules.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        String role = ExpenseQueryService.role(a);
        boolean admin = role.equals("SUPER_ADMIN") || role.equals("FINANCE_ADMIN");
        if (!admin && !a.getName().equals(x.createdBy)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        return x;
    }
}
