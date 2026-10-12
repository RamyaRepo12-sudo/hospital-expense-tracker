package com.hospital.expense.web;

import com.hospital.expense.model.Expense;
import com.hospital.expense.model.RecurringRule;
import com.hospital.expense.repo.ExpenseRepository;
import com.hospital.expense.repo.RecurringRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Creates the Draft expenses that recurring rules call for. It runs nightly, at start-up and after each sign-in,
 * so a free server that was asleep catches up as soon as it wakes. A unique key per rule and date makes it safe
 * if two instances run it at the same time.
 */
@Service
public class RecurringService {
    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);

    private final RecurringRepository rules;
    private final ExpenseRepository expenses;
    private final SequenceService sequences;
    private final AuditService audit;
    private final NotificationService notes;

    public RecurringService(RecurringRepository rules, ExpenseRepository expenses, SequenceService sequences, AuditService audit, NotificationService notes) {
        this.rules = rules; this.expenses = expenses; this.sequences = sequences; this.audit = audit; this.notes = notes;
    }

    @Async
    public void generateDueAsync() { safely(); }

    @Scheduled(cron = "0 30 1 * * *")
    public void nightly() { safely(); }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() { safely(); }

    private void safely() {
        try {
            generateDue();
        } catch (RuntimeException e) {
            log.warn("Recurring expense generation failed: {}", e.getMessage());
        }
    }

    public synchronized int generateDue() {
        LocalDate today = LocalDate.now();
        int total = 0;
        for (RecurringRule r : rules.findByActiveTrue()) {
            List<LocalDate> due = Recurrence.due(r, today);
            if (due.isEmpty()) continue;
            int made = 0;
            for (LocalDate d : due) if (createDraft(r, d)) made++;
            r.occurrences += due.size();
            if (r.endDate != null && Recurrence.nth(r.startDate, r.frequency, r.occurrences).isAfter(r.endDate)) r.active = false;
            rules.save(r);
            if (made > 0) notes.recurringReady(r.createdBy, made, r);
            total += made;
        }
        return total;
    }

    private boolean createDraft(RecurringRule r, LocalDate date) {
        Expense e = new Expense();
        e.expenseNo = "EXP-" + sequences.next("expense");
        e.date = date;
        e.department = r.department; e.category = r.category; e.vendor = r.vendor;
        e.amount = r.amount; e.gst = r.gst == null ? java.math.BigDecimal.ZERO : r.gst;
        e.paymentMethod = r.paymentMethod;
        e.invoiceNo = "RECURRING-" + date;
        e.description = (r.description == null || r.description.isBlank() ? "Recurring expense" : r.description) + " (recurring, please check the invoice details)";
        e.status = Expense.DRAFT;
        e.createdBy = r.createdBy;
        e.createdAt = Instant.now();
        e.recurringKey = r.id + ":" + date;
        try {
            expenses.insert(e);
            audit.log("system", "Recurring draft created", e.expenseNo);
            return true;
        } catch (DuplicateKeyException alreadyThere) {
            return false;
        }
    }
}
