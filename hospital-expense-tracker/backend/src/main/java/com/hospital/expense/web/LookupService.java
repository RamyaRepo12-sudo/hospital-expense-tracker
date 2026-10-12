package com.hospital.expense.web;

import com.hospital.expense.repo.LookupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LookupService {
    private final LookupRepository repo;

    public LookupService(LookupRepository repo) { this.repo = repo; }

    /** Expenses may only use departments, categories and vendors that an admin has added and not deactivated. */
    public void require(String type, String name, String label) {
        if (name == null || !repo.existsByTypeAndNameAndActiveTrue(type, name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " \"" + name + "\" is not in the active list. Ask an admin to add it under Settings.");
        }
    }
}
