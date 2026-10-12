package com.hospital.expense.report;

import com.hospital.expense.model.Expense;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class XlsxExporter {
    private XlsxExporter() {}

    private static final String[] COLS = {"ID", "Date", "Department", "Category", "Vendor", "Invoice", "Status", "Amount", "GST", "Total"};

    public static byte[] build(List<Expense> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Expenses");
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Row h = sh.createRow(0);
            for (int i = 0; i < COLS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLS[i]);
                c.setCellStyle(head);
                sh.setColumnWidth(i, (i == 4 ? 28 : 16) * 256);
            }
            BigDecimal sum = BigDecimal.ZERO;
            int r = 1;
            for (Expense e : rows) {
                Row row = sh.createRow(r++);
                String[] text = {e.expenseNo, String.valueOf(e.date), e.department, e.category, e.vendor, e.invoiceNo, e.status};
                for (int i = 0; i < text.length; i++) row.createCell(i).setCellValue(text[i]);
                BigDecimal[] nums = {e.amount, e.gst, e.getTotal()};
                for (int i = 0; i < nums.length; i++) {
                    Cell c = row.createCell(7 + i);
                    c.setCellValue(nums[i] == null ? 0 : nums[i].doubleValue());
                    c.setCellStyle(money);
                }
                sum = sum.add(e.getTotal());
            }
            Row t = sh.createRow(r + 1);
            Cell label = t.createCell(8);
            label.setCellValue("Total");
            label.setCellStyle(head);
            Cell total = t.createCell(9);
            total.setCellValue(sum.doubleValue());
            total.setCellStyle(money);
            sh.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
