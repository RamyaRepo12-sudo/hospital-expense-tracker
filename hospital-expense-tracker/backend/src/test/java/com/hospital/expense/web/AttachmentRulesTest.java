package com.hospital.expense.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AttachmentRulesTest {
    @Test
    void detectsAllowedTypesFromBytes() {
        assertEquals("application/pdf", AttachmentController.detectType(new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}));
        assertEquals("image/png", AttachmentController.detectType(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D}));
        assertEquals("image/jpeg", AttachmentController.detectType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
    }

    @Test
    void rejectsOtherFilesEvenWithAFriendlyExtension() {
        assertNull(AttachmentController.detectType("MZ-this-is-an-exe".getBytes()));
        assertNull(AttachmentController.detectType(new byte[0]));
    }

    @Test
    void filenamesAreSanitised() {
        assertEquals("my_invoice__1_.pdf", AttachmentController.safeName("my invoice (1).pdf"));
        assertEquals("_.._etc_passwd", AttachmentController.safeName("/../etc/passwd"));
    }
}
