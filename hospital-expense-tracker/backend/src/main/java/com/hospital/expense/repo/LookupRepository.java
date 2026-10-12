package com.hospital.expense.repo;

import com.hospital.expense.model.Lookup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LookupRepository extends MongoRepository<Lookup, String> {
    List<Lookup> findByType(String type);
    Optional<Lookup> findByTypeAndName(String type, String name);
    boolean existsByTypeAndNameAndActiveTrue(String type, String name);
}
