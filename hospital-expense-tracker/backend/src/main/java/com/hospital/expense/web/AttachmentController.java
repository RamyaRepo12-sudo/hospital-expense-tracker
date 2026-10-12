package com.hospital.expense.web;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.hospital.expense.model.Expense;
import com.hospital.expense.repo.ExpenseRepository;
import com.mongodb.client.gridfs.model.GridFSFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Invoice / receipt files (PDF, JPG, PNG, max 5 MB) stored in MongoDB GridFS. The type is checked from the file bytes. */
@RestController
@RequestMapping("/api/expenses/{id}/attachment")
public class AttachmentController {
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ExpenseRepository repo;
    private final ExpenseQueryService visible;
    private final GridFsTemplate gridFs;
    private final AuditService audit;

    public AttachmentController(ExpenseRepository repo, ExpenseQueryService visible, GridFsTemplate gridFs, AuditService audit) {
        this.repo = repo; this.visible = visible; this.gridFs = gridFs; this.audit = audit;
    }

    static String detectType(byte[] d) {
        if (d.length >= 4 && d[0] == 0x25 && d[1] == 0x50 && d[2] == 0x44 && d[3] == 0x46) return "application/pdf";
        if (d.length >= 4 && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47) return "image/png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    static String safeName(String original) {
        String n = original == null ? "invoice" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.length() > 80 ? n.substring(n.length() - 80) : n;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ExpenseController.CREATORS)
    public Expense upload(@PathVariable String id, @RequestParam("file") MultipartFile file, Authentication a) throws IOException {
        Expense e = visible.loadVisible(id, a);
        if (!List.of(Expense.DRAFT, Expense.REJECTED).contains(e.status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoices can only be attached to draft or rejected expenses");
        }
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is larger than 5 MB");
        byte[] data = file.getBytes();
        String type = detectType(data);
        if (type == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPG or PNG files are allowed");
        String name = safeName(file.getOriginalFilename());
        ObjectId fileId = gridFs.store(new ByteArrayInputStream(data), name, type);
        if (e.attachmentId != null) gridFs.delete(query(where("_id").is(new ObjectId(e.attachmentId))));
        e.attachmentId = fileId.toHexString(); e.attachmentName = name; e.attachmentType = type;
        repo.save(e);
        audit.log(a.getName(), "Attached invoice " + name, e.expenseNo);
        return e;
    }

    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable String id, Authentication a) throws IOException {
        Expense e = visible.loadVisible(id, a);
        if (e.attachmentId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No invoice attached");
        GridFSFile f = gridFs.findOne(query(where("_id").is(new ObjectId(e.attachmentId))));
        if (f == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice file not found");
        byte[] bytes;
        try (InputStream in = gridFs.getResource(f).getInputStream()) { bytes = in.readAllBytes(); }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, e.attachmentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + e.attachmentName + "\"")
                .body(bytes);
    }
}
