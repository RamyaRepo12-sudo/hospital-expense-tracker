package com.hospital.expense.web;

import com.hospital.expense.repo.AuditRepository;
import com.hospital.expense.repo.BudgetRepository;
import com.hospital.expense.repo.ExpenseRepository;
import com.hospital.expense.repo.LookupRepository;
import com.hospital.expense.repo.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON snapshot for archiving (no password hashes, no invoice files). For a full copy use mongodump (see the guide). */
@RestController
@RequestMapping("/api/admin")
public class BackupController {
    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final AuditRepository auditRepo;
    private final UserRepository users;
    private final AuditService audit;
    private final LookupRepository lookups;

    public BackupController(ExpenseRepository expenses, BudgetRepository budgets, AuditRepository auditRepo, UserRepository users, AuditService audit, LookupRepository lookups) {
        this.expenses = expenses; this.budgets = budgets; this.auditRepo = auditRepo; this.users = users; this.audit = audit; this.lookups = lookups;
    }

    @GetMapping("/backup")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> backup(Authentication a) {
        List<Map<String, Object>> people = users.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.username); m.put("name", u.name); m.put("role", u.role); m.put("active", u.active); m.put("email", u.email);
            return m;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now().toString());
        out.put("expenses", expenses.findAll());
        out.put("budgets", budgets.findAll());
        out.put("lookups", lookups.findAll());
        out.put("auditLogs", auditRepo.findAll());
        out.put("users", people);
        audit.log(a.getName(), "Downloaded JSON backup", "-");
        return out;
    }
}
