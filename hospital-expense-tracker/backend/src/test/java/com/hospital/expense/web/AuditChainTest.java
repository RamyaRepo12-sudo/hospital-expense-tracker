package com.hospital.expense.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hospital.expense.model.AuditLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditChainTest {
    private static List<AuditLog> chain(int n) {
        List<AuditLog> list = new ArrayList<>();
        String prev = "GENESIS";
        for (int i = 1; i <= n; i++) {
            AuditLog l = new AuditLog("user" + i, "Action " + i, "EXP-" + i);
            l.ip = "10.0.0." + i;
            l.seq = i;
            l.prevHash = prev;
            l.hash = AuditService.hash(l);
            prev = l.hash;
            list.add(l);
        }
        return list;
    }

    @Test
    void anUntouchedChainVerifies() {
        Map<String, Object> r = AuditService.verifyChain(chain(5));
        assertTrue((Boolean) r.get("ok"));
        assertEquals(5L, r.get("checked"));
    }

    @Test
    void editingAnEntryIsDetected() {
        List<AuditLog> list = chain(5);
        list.get(2).action = "Approved (edited later)";
        Map<String, Object> r = AuditService.verifyChain(list);
        assertFalse((Boolean) r.get("ok"));
        assertEquals(3L, r.get("brokenAtSeq"));
    }

    @Test
    void deletingAnEntryIsDetected() {
        List<AuditLog> list = chain(5);
        list.remove(1);
        Map<String, Object> r = AuditService.verifyChain(list);
        assertFalse((Boolean) r.get("ok"));
        assertEquals(3L, r.get("brokenAtSeq"));
    }
}
