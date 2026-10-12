package com.hospital.expense.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Status flow: Draft -> Pending -> Approved | Rejected -> Paid. Money uses BigDecimal, never double. */
@Document("expenses")
public class Expense {
    public static final String DRAFT = "Draft", PENDING = "Pending", APPROVED = "Approved", REJECTED = "Rejected", PAID = "Paid";

    @Id public String id;
    public String expenseNo;
    public LocalDate date;
    public String department, category, vendor;
    public BigDecimal amount, gst;
    public String paymentMethod, invoiceNo, description;
    public String status, createdBy, rejectionReason;
    public Instant createdAt;
    public String attachmentId, attachmentName, attachmentType;
    /** Set on drafts made by a recurring rule (ruleId:date). The unique index stops duplicates if two instances run at once. */
    @Indexed(unique = true, sparse = true)
    public String recurringKey;

    @Transient
    public BigDecimal getTotal() {
        return (amount == null ? BigDecimal.ZERO : amount).add(gst == null ? BigDecimal.ZERO : gst);
    }

    @Transient
    public boolean isSpend() { return APPROVED.equals(status) || PAID.equals(status); }
}
