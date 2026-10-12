package com.hospital.expense.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExpenseTest {
    @Test
    void totalIsAmountPlusGstWithoutFloatingPointError() {
        Expense e = new Expense();
        e.amount = new BigDecimal("0.10");
        e.gst = new BigDecimal("0.20");
        assertEquals(new BigDecimal("0.30"), e.getTotal());
    }

    @Test
    void missingGstCountsAsZero() {
        Expense e = new Expense();
        e.amount = new BigDecimal("100");
        assertEquals(new BigDecimal("100"), e.getTotal());
    }

    @Test
    void onlyApprovedAndPaidCountAsSpend() {
        Expense e = new Expense();
        e.status = Expense.APPROVED; assertTrue(e.isSpend());
        e.status = Expense.PAID; assertTrue(e.isSpend());
        e.status = Expense.PENDING; assertFalse(e.isSpend());
        e.status = Expense.DRAFT; assertFalse(e.isSpend());
        e.status = Expense.REJECTED; assertFalse(e.isSpend());
    }
}
