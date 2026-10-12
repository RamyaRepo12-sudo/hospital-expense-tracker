package com.hospital.expense.repo;

import com.hospital.expense.model.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findTop200ByOrderBySeqDesc();
    Optional<AuditLog> findTopByOrderBySeqDesc();
}
