package com.hospital.expense.report;

import com.hospital.expense.model.Expense;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PdfExporter {
    private PdfExporter() {}

    private static String money(BigDecimal v) { return String.format("%,.2f", v == null ? BigDecimal.ZERO : v); }

    public static byte[] build(List<Expense> rows, String user) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE);
        doc.add(new Paragraph("Hospital expense report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        doc.add(new Paragraph("Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) + " by " + user
                + " | " + rows.size() + " expenses | amounts in INR", small));
        doc.add(Chunk.NEWLINE);

        PdfPTable t = new PdfPTable(new float[]{1.2f, 1.1f, 1.6f, 1.8f, 2f, 1.2f, 1.4f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : new String[]{"ID", "Date", "Department", "Category", "Vendor", "Status", "Total"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, headFont));
            c.setBackgroundColor(new Color(15, 118, 110));
            c.setPadding(5);
            if ("Total".equals(h)) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(c);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Expense e : rows) {
            for (String v : new String[]{e.expenseNo, String.valueOf(e.date), e.department, e.category, e.vendor, e.status}) {
                PdfPCell c = new PdfPCell(new Phrase(v == null ? "" : v, small));
                c.setPadding(4);
                t.addCell(c);
            }
            PdfPCell tot = new PdfPCell(new Phrase(money(e.getTotal()), small));
            tot.setPadding(4);
            tot.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(tot);
            sum = sum.add(e.getTotal());
        }
        doc.add(t);
        Paragraph total = new Paragraph("Total: INR " + money(sum), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
        total.setAlignment(Element.ALIGN_RIGHT);
        total.setSpacingBefore(8);
        doc.add(total);
        doc.close();
        return out.toByteArray();
    }
}
