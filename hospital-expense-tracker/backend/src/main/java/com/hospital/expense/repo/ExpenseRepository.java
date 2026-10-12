package com.hospital.expense.repo;

import com.hospital.expense.model.Expense;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExpenseRepository extends MongoRepository<Expense, String> {
    List<Expense> findByCreatedBy(String createdBy);
}
