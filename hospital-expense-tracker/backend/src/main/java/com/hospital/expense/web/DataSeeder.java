package com.hospital.expense.web;

import com.hospital.expense.model.Budget;
import com.hospital.expense.model.Expense;
import com.hospital.expense.model.Lookup;
import com.hospital.expense.model.User;
import com.hospital.expense.repo.BudgetRepository;
import com.hospital.expense.repo.ExpenseRepository;
import com.hospital.expense.repo.LookupRepository;
import com.hospital.expense.repo.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * First-start data, only when the collections are empty.
 * SEED_MODE=demo: six demo users, sample expenses and budgets.
 * SEED_MODE=bootstrap: a single Super Admin (must change password at first sign-in), no expenses, default budgets.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    static final List<String> DEPTS = List.of("Emergency", "ICU", "Pharmacy", "Laboratory", "Radiology", "Operation Theatre", "Maintenance", "IT");
    static final List<String> CATS = List.of("Medicines", "Medical Equipment", "Surgical Supplies", "Laboratory Supplies", "Utilities", "Housekeeping", "IT & Software", "Food & Catering", "Maintenance");
    static final List<String> VENDORS = List.of("MedLife Distributors", "SurgiCore Supplies", "Apex Diagnostics", "BrightGrid Power", "CleanWorks", "TechNova Systems", "FreshBite Catering", "Sterling Equipment");
    static final List<String> PAY = List.of("Bank Transfer", "UPI", "Card", "Cheque");
    static final BigDecimal DEFAULT_LIMIT = BigDecimal.valueOf(100000);

    private final UserRepository users;
    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final LookupRepository lookups;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String mode, seedPassword, adminUsername, adminName, adminEmail, adminPassword;
    private final Random rnd = new Random(42);
    private int counter = 1001;

    public DataSeeder(UserRepository users, ExpenseRepository expenses, BudgetRepository budgets, LookupRepository lookups, PasswordEncoder encoder,
                      @Value("${app.seed.enabled}") boolean enabled, @Value("${app.seed.mode}") String mode,
                      @Value("${app.seed.password}") String seedPassword, @Value("${app.admin.username}") String adminUsername,
                      @Value("${app.admin.name}") String adminName, @Value("${app.admin.email}") String adminEmail,
                      @Value("${app.admin.password}") String adminPassword) {
        this.users = users; this.expenses = expenses; this.budgets = budgets; this.lookups = lookups; this.encoder = encoder;
        this.enabled = enabled; this.mode = mode; this.seedPassword = seedPassword;
        this.adminUsername = adminUsername; this.adminName = adminName; this.adminEmail = adminEmail; this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        boolean bootstrap = "bootstrap".equalsIgnoreCase(mode);
        boolean demo = enabled && !bootstrap;
        if (enabled) {
            if (users.count() == 0) { if (bootstrap) seedAdmin(); else seedDemoUsers(); }
            if (demo && expenses.count() == 0) seedExpenses();
        }
        // The app needs departments and categories to be usable, so these are created even when SEED_DATA=false.
        if (lookups.count() == 0) seedLookups(demo);
        if (budgets.count() == 0) seedBudgets(!demo); // parameter: use the default limit for every department?
    }

    private void seedLookups(boolean demo) {
        List<Lookup> all = new ArrayList<>();
        DEPTS.forEach(d -> all.add(new Lookup("DEPARTMENT", d, null)));
        CATS.forEach(c -> all.add(new Lookup("CATEGORY", c, null)));
        if (demo) VENDORS.forEach(v -> all.add(new Lookup("VENDOR", v, null))); // real vendors are added by the admin under Settings
        lookups.saveAll(all);
    }

    private void seedAdmin() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("SEED_MODE=bootstrap requires ADMIN_PASSWORD (8+ characters, with a letter and a number).");
        }
        UserController.checkPassword(adminPassword, adminUsername);
        User a = new User(adminUsername.trim().toLowerCase(), encoder.encode(adminPassword), adminName.trim(), "SUPER_ADMIN");
        a.email = adminEmail == null || adminEmail.isBlank() ? null : adminEmail.trim();
        a.mustChangePassword = true;
        users.save(a);
    }

    private void seedDemoUsers() {
        String h = encoder.encode(seedPassword);
        users.saveAll(List.of(
                new User("admin", h, "System Admin", "SUPER_ADMIN"),
                new User("finance", h, "Meera Nair", "FINANCE_ADMIN"),
                new User("manager", h, "Dr. Rao", "DEPARTMENT_MANAGER"),
                new User("accountant", h, "Imran Khan", "ACCOUNTANT"),
                new User("staff", h, "Priya Sharma", "STAFF"),
                new User("auditor", h, "Arun Iyer", "AUDITOR")));
    }

    private void seedExpenses() {
        List<Expense> list = new ArrayList<>();
        for (String d : DEPTS) {
            list.add(make(d, 0, Expense.APPROVED));
            list.add(make(d, 0, Expense.PAID));
            for (int back = 1; back <= 5; back++) { list.add(make(d, back, Expense.PAID)); list.add(make(d, back, Expense.PAID)); }
        }
        for (int i = 0; i < 5; i++) list.add(make(DEPTS.get(i), 0, Expense.PENDING));
        list.add(make(DEPTS.get(5), 0, Expense.DRAFT));
        list.add(make(DEPTS.get(6), 0, Expense.REJECTED));
        expenses.saveAll(list);
    }

    private void seedBudgets(boolean bootstrap) {
        double[] factor = {.55, .70, .84, .93, 1.10, .62, .78, .88};
        LocalDate now = LocalDate.now();
        for (int i = 0; i < DEPTS.size(); i++) {
            String d = DEPTS.get(i);
            BigDecimal limit = DEFAULT_LIMIT;
            if (!bootstrap) {
                BigDecimal spent = expenses.findAll().stream()
                        .filter(e -> e.isSpend() && d.equals(e.department) && e.date.getMonth() == now.getMonth() && e.date.getYear() == now.getYear())
                        .map(Expense::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal computed = spent.divide(BigDecimal.valueOf(factor[i]), 0, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(10000), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(10000));
                if (computed.signum() > 0) limit = computed;
            }
            budgets.save(new Budget(d, limit));
        }
    }

    private Expense make(String dept, int monthsBack, String status) {
        LocalDate today = LocalDate.now();
        LocalDate first = today.withDayOfMonth(1).minusMonths(monthsBack);
        int maxDay = monthsBack == 0 ? Math.max(1, today.getDayOfMonth()) : first.lengthOfMonth();
        Expense e = new Expense();
        e.expenseNo = "EXP-" + (counter++);
        e.date = first.withDayOfMonth(1 + rnd.nextInt(maxDay));
        e.department = dept;
        e.category = CATS.get(rnd.nextInt(CATS.size()));
        e.vendor = VENDORS.get(rnd.nextInt(VENDORS.size()));
        e.amount = BigDecimal.valueOf(8000 + rnd.nextInt(90000)).setScale(0, RoundingMode.HALF_UP);
        e.gst = e.amount.multiply(new BigDecimal("0.12")).setScale(0, RoundingMode.HALF_UP);
        e.paymentMethod = PAY.get(rnd.nextInt(PAY.size()));
        e.invoiceNo = "INV-" + (1000 + rnd.nextInt(9000));
        e.description = "Routine purchase (demo data)";
        e.status = status;
        e.createdBy = "staff";
        e.createdAt = Instant.now();
        if (Expense.REJECTED.equals(status)) e.rejectionReason = "Invoice number does not match the vendor record";
        return e;
    }
}
