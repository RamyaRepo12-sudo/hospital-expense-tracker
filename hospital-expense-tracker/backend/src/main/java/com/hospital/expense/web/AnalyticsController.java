package com.hospital.expense.web;

import com.hospital.expense.model.AuditLog;
import com.hospital.expense.model.Budget;
import com.hospital.expense.model.Expense;
import com.hospital.expense.repo.AuditRepository;
import com.hospital.expense.repo.BudgetRepository;
import com.hospital.expense.repo.ExpenseRepository;
import com.hospital.expense.repo.LookupRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Dashboard, budgets and audit endpoints. All are blocked for the STAFF role. */
@RestController
@RequestMapping("/api")
public class AnalyticsController {
    public record LimitRequest(@NotNull @DecimalMin("1") BigDecimal monthlyLimit) {}

    private static final String NOT_STAFF = "!hasRole('STAFF')";

    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final AuditRepository auditRepo;
    private final AuditService audit;
    private final LookupRepository lookups;

    public AnalyticsController(ExpenseRepository expenses, BudgetRepository budgets, AuditRepository auditRepo, AuditService audit, LookupRepository lookups) {
        this.expenses = expenses; this.budgets = budgets; this.auditRepo = auditRepo; this.audit = audit; this.lookups = lookups;
    }

    private static BigDecimal sum(Collection<Expense> list) {
        return list.stream().map(Expense::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean inMonth(Expense e, YearMonth ym) { return e.date != null && YearMonth.from(e.date).equals(ym); }

    private List<Map<String, Object>> budgetRows(List<Expense> spend) {
        YearMonth now = YearMonth.now();
        Set<String> active = lookups.findByType("DEPARTMENT").stream().filter(l -> l.active).map(l -> l.name).collect(Collectors.toSet());
        return budgets.findAll().stream().filter(b -> active.isEmpty() || active.contains(b.department)).sorted(Comparator.comparing((Budget b) -> b.department)).map(b -> {
            BigDecimal used = sum(spend.stream().filter(e -> b.department.equals(e.department) && inMonth(e, now)).toList());
            BigDecimal pct = b.monthlyLimit.signum() == 0 ? BigDecimal.ZERO
                    : used.divide(b.monthlyLimit, 4, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("department", b.department); m.put("monthlyLimit", b.monthlyLimit);
            m.put("spent", used); m.put("utilization", pct);
            return m;
        }).toList();
    }

    @GetMapping("/dashboard")
    @PreAuthorize(NOT_STAFF)
    public Map<String, Object> dashboard() {
        List<Expense> all = expenses.findAll();
        List<Expense> spend = all.stream().filter(Expense::isSpend).toList();
        YearMonth now = YearMonth.now();
        List<Expense> thisMonth = spend.stream().filter(e -> inMonth(e, now)).toList();
        List<Expense> pending = all.stream().filter(e -> Expense.PENDING.equals(e.status)).toList();
        List<Map<String, Object>> rows = budgetRows(spend);
        BigDecimal budgetTotal = rows.stream().map(r -> (BigDecimal) r.get("monthlyLimit")).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            trend.add(Map.of("month", ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    "amount", sum(spend.stream().filter(e -> inMonth(e, ym)).toList())));
        }
        Map<String, BigDecimal> byCat = thisMonth.stream().collect(Collectors.groupingBy(
                e -> e.category, TreeMap::new, Collectors.reducing(BigDecimal.ZERO, Expense::getTotal, BigDecimal::add)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalExpenses", sum(spend));
        out.put("thisMonth", sum(thisMonth));
        out.put("lastMonth", sum(spend.stream().filter(e -> inMonth(e, now.minusMonths(1))).toList()));
        out.put("pendingCount", pending.size());
        out.put("pendingAmount", sum(pending));
        out.put("outstanding", sum(all.stream().filter(e -> Expense.APPROVED.equals(e.status)).toList()));
        out.put("budgetTotal", budgetTotal);
        out.put("trend", trend);
        out.put("departments", rows);
        out.put("categories", byCat.entrySet().stream().map(en -> Map.of("name", en.getKey(), "amount", en.getValue())).toList());
        out.put("recent", all.stream().sorted(Comparator.comparing((Expense e) -> e.date).reversed()).limit(6).toList());
        return out;
    }

    @GetMapping("/budgets")
    @PreAuthorize(NOT_STAFF)
    public List<Map<String, Object>> budgets() {
        return budgetRows(expenses.findAll().stream().filter(Expense::isSpend).toList());
    }

    @PutMapping("/budgets/{department}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN')")
    public Budget setLimit(@PathVariable String department, @Valid @RequestBody LimitRequest r, Authentication a) {
        Budget b = budgets.findByDepartment(department)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        b.monthlyLimit = r.monthlyLimit();
        budgets.save(b);
        audit.log(a.getName(), "Budget changed to " + b.monthlyLimit, department);
        return b;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN','AUDITOR')")
    public List<AuditLog> audit() { return auditRepo.findTop200ByOrderBySeqDesc(); }

    @GetMapping("/audit/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN','AUDITOR')")
    public Map<String, Object> verifyAudit() { return audit.verify(); }
}
