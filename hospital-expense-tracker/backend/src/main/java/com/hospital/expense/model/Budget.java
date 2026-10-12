package com.hospital.expense.model;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("budgets")
public class Budget {
    @Id public String id;
    @Indexed(unique = true) public String department;
    public BigDecimal monthlyLimit;

    public Budget() {}
    public Budget(String department, BigDecimal monthlyLimit) { this.department = department; this.monthlyLimit = monthlyLimit; }
}
