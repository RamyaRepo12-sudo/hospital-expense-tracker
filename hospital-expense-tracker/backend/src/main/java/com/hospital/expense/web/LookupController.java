package com.hospital.expense.web;

import com.hospital.expense.model.Budget;
import com.hospital.expense.model.Lookup;
import com.hospital.expense.repo.BudgetRepository;
import com.hospital.expense.repo.LookupRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Departments, categories and vendors. Everyone signed in can read them; only finance admins can change them. */
@RestController
@RequestMapping("/api/lookups")
public class LookupController {
    public record Create(@NotBlank String type,
                         @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "[\\p{L}0-9 &()./,'-]+", message = "contains characters that are not allowed") String name,
                         @Size(max = 120) String extra) {}
    public record Edit(@Size(max = 120) String extra) {}
    public record Active(boolean active) {}

    private static final Set<String> TYPES = Set.of("DEPARTMENT", "CATEGORY", "VENDOR");
    private static final String ADMINS = "hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN')";

    private final LookupRepository repo;
    private final BudgetRepository budgets;
    private final AuditService audit;

    public LookupController(LookupRepository repo, BudgetRepository budgets, AuditService audit) {
        this.repo = repo; this.budgets = budgets; this.audit = audit;
    }

    @GetMapping
    public List<Lookup> list() {
        return repo.findAll().stream().sorted(Comparator.comparing((Lookup l) -> l.type).thenComparing(l -> l.name.toLowerCase())).toList();
    }

    @PostMapping
    @PreAuthorize(ADMINS)
    public Lookup create(@Valid @RequestBody Create r, Authentication a) {
        if (!TYPES.contains(r.type())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown type");
        String name = r.name().trim();
        if (repo.findByTypeAndName(r.type(), name).isPresent()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That name already exists");
        Lookup l = repo.save(new Lookup(r.type(), name, r.extra() == null || r.extra().isBlank() ? null : r.extra().trim()));
        if ("DEPARTMENT".equals(l.type) && budgets.findByDepartment(name).isEmpty()) budgets.save(new Budget(name, BigDecimal.valueOf(100000)));
        audit.log(a.getName(), "Added " + l.type.toLowerCase(), name);
        return l;
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMINS)
    public Lookup edit(@PathVariable String id, @Valid @RequestBody Edit r, Authentication a) {
        Lookup l = find(id);
        l.extra = r.extra() == null || r.extra().isBlank() ? null : r.extra().trim();
        repo.save(l);
        audit.log(a.getName(), "Edited " + l.type.toLowerCase() + " details", l.name);
        return l;
    }

    @PutMapping("/{id}/active")
    @PreAuthorize(ADMINS)
    public Lookup setActive(@PathVariable String id, @RequestBody Active r, Authentication a) {
        Lookup l = find(id);
        l.active = r.active();
        repo.save(l);
        audit.log(a.getName(), (l.active ? "Activated " : "Deactivated ") + l.type.toLowerCase(), l.name);
        return l;
    }

    private Lookup find(String id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }
}
