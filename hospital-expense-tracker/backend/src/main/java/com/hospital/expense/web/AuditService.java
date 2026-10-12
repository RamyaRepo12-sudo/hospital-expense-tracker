package com.hospital.expense.web;

import com.hospital.expense.model.AuditLog;
import com.hospital.expense.repo.AuditRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.dao.DuplicateKeyException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Append-only audit trail with a hash chain: every entry includes the SHA-256 of the previous entry.
 * Editing or deleting an entry in the database breaks the chain, and /api/audit/verify reports where.
 */
@Service
public class AuditService {
    private final AuditRepository repo;

    public AuditService(AuditRepository repo) { this.repo = repo; }

    /**
     * Appends one entry. If another running instance wrote the same sequence number first, the unique index on
     * "seq" rejects ours and we retry on top of the newest entry, so the chain never forks.
     */
    public synchronized void log(String username, String action, String recordId) {
        String ip = currentIp();
        for (int attempt = 0; attempt < 8; attempt++) {
            AuditLog last = repo.findTopByOrderBySeqDesc().orElse(null);
            AuditLog l = new AuditLog(username, action, recordId);
            l.ip = ip;
            l.seq = last == null ? 1 : last.seq + 1;
            l.prevHash = last == null || last.hash == null ? "GENESIS" : last.hash;
            l.hash = hash(l);
            try {
                repo.insert(l);
                return;
            } catch (DuplicateKeyException raced) {
                // retry
            }
        }
        throw new IllegalStateException("Could not append to the audit log");
    }

    public Map<String, Object> verify() { return verifyChain(repo.findAll(Sort.by("seq"))); }

    static String hash(AuditLog l) {
        String text = l.prevHash + "|" + l.seq + "|" + l.timestamp.toEpochMilli() + "|" + nz(l.username) + "|" + nz(l.action) + "|" + nz(l.recordId) + "|" + nz(l.ip);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, Object> verifyChain(List<AuditLog> logs) {
        String expectedPrev = "GENESIS";
        long lastSeq = 0, checked = 0;
        Long brokenAt = null;
        for (AuditLog l : logs) {
            if (l.hash == null) continue; // written before hashing existed
            boolean ok = expectedPrev.equals(l.prevHash) && l.seq > lastSeq && l.hash.equals(hash(l));
            if (!ok) { brokenAt = l.seq; break; }
            expectedPrev = l.hash; lastSeq = l.seq; checked++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", brokenAt == null);
        out.put("checked", checked);
        out.put("brokenAtSeq", brokenAt);
        return out;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String currentIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest().getRemoteAddr() : "-";
    }
}
