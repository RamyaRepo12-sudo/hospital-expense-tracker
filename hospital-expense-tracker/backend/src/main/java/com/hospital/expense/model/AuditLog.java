package com.hospital.expense.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("audit_logs")
public class AuditLog {
    @Id public String id;
    public Instant timestamp;
    public String username, action, recordId;
    /** Tamper-evidence: each entry stores the hash of the previous one (see AuditService). */
    @Indexed(unique = true, partialFilter = "{ 'seq': { $gt: 0 } }")
    public long seq;
    public String ip, prevHash, hash;

    public AuditLog() {}
    public AuditLog(String username, String action, String recordId) {
        this.timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS); this.username = username; this.action = action; this.recordId = recordId;
    }
}
