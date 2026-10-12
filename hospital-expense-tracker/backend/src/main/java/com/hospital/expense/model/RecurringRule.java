package com.hospital.expense.model;

import com.hospital.expense.web.Recurrence;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

/** A repeating expense (rent, service contract, subscription). Each due date creates a Draft expense for the creator to review. */
@Document("recurring_rules")
public class RecurringRule {
    @Id public String id;
    public String department, category, vendor, paymentMethod, description, frequency, createdBy;
    public BigDecimal amount, gst;
    public LocalDate startDate, endDate;
    /** How many dates have already been generated. The next date is always computed from startDate, so months never drift. */
    public int occurrences;
    public boolean active = true;
    public Instant createdAt;

    @Transient
    public LocalDate getNextDate() { return Recurrence.nth(startDate, frequency, occurrences); }
}
