package com.hospital.expense.repo;

import com.hospital.expense.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    List<User> findAllByEmailIgnoreCase(String email);
}
