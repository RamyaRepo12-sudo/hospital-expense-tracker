package com.hospital.expense.web;

import com.hospital.expense.model.Expense;
import com.hospital.expense.report.PdfExporter;
import com.hospital.expense.report.XlsxExporter;
import com.lowagie.text.DocumentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Overall expense reporting. status: "all" or blank = every status, "spend" = Approved and Paid, or one exact status.
 * Exports are capped at 5,000 rows. Staff only ever receive their own expenses.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private static final int MAX_ROWS = 5000;
    private final ExpenseQueryService visible;

    public ReportController(ExpenseQueryService visible) { this.visible = visible; }

    private static String scope(String status, String defaultValue) {
        String s = status == null ? defaultValue : status;
        return "all".equals(s) ? "" : s;
    }

    private List<Expense> rows(Authentication a, String department, String category, String vendor, String status, String q, LocalDate from, LocalDate to, String def) {
        return visible.findScoped(a, department, category, vendor, scope(status, def), q, from, to).stream().limit(MAX_ROWS).toList();
    }

    @GetMapping("/expenses.xlsx")
    public ResponseEntity<byte[]> xlsx(Authentication a, @RequestParam(required = false) String department, @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String vendor, @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws IOException {
        byte[] body = XlsxExporter.build(rows(a, department, category, vendor, status, q, from, to, ""));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expenses.xlsx\"").body(body);
    }

    @GetMapping("/expenses.pdf")
    public ResponseEntity<byte[]> pdf(Authentication a, @RequestParam(required = false) String department, @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String vendor, @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws DocumentException {
        byte[] body = PdfExporter.build(rows(a, department, category, vendor, status, q, from, to, ""), a.getName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expenses.pdf\"").body(body);
    }

    @GetMapping("/summary")
    @PreAuthorize("!hasRole('STAFF')")
    public Map<String, Object> summary(Authentication a, @RequestParam(required = false) String department, @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String vendor, @RequestParam(required = false) String status,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Expense> list = rows(a, department, category, vendor, status, null, from, to, "spend");
        BigDecimal total = BigDecimal.ZERO, amount = BigDecimal.ZERO, gst = BigDecimal.ZERO;
        for (Expense e : list) {
            total = total.add(e.getTotal());
            amount = amount.add(e.amount == null ? BigDecimal.ZERO : e.amount);
            gst = gst.add(e.gst == null ? BigDecimal.ZERO : e.gst);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", list.size());
        out.put("total", total);
        out.put("amount", amount);
        out.put("gst", gst);
        out.put("average", list.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP));
        out.put("byMonth", group(list, e -> e.date.toString().substring(0, 7)).stream()
                .sorted(Comparator.comparing((Map<String, Object> m) -> (String) m.get("name"))).toList());
        out.put("byDepartment", group(list, e -> e.department));
        out.put("byCategory", group(list, e -> e.category));
        out.put("byVendor", group(list, e -> e.vendor).stream().limit(10).toList());
        out.put("byStatus", group(list, e -> e.status));
        out.put("byPayment", group(list, e -> e.paymentMethod));
        return out;
    }

    private static List<Map<String, Object>> group(List<Expense> rows, Function<Expense, String> key) {
        Map<String, BigDecimal> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Expense e : rows) {
            String k = key.apply(e);
            sums.merge(k, e.getTotal(), BigDecimal::add);
            counts.merge(k, 1, Integer::sum);
        }
        return sums.entrySet().stream().sorted((x, y) -> y.getValue().compareTo(x.getValue())).map(en -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", en.getKey()); m.put("amount", en.getValue()); m.put("count", counts.get(en.getKey()));
            return m;
        }).toList();
    }
}
