package com.hospital.expense.repo;

import com.hospital.expense.model.Budget;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BudgetRepository extends MongoRepository<Budget, String> {
    Optional<Budget> findByDepartment(String department);
}
