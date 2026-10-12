package com.hospital.expense.repo;

import com.hospital.expense.model.RecurringRule;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecurringRepository extends MongoRepository<RecurringRule, String> {
    List<RecurringRule> findByActiveTrue();
    List<RecurringRule> findByCreatedBy(String createdBy);
    long countByCreatedBy(String createdBy);
}
