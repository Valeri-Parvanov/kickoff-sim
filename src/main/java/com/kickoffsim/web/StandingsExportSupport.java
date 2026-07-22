package com.kickoffsim.web;

import com.kickoffsim.dto.StandingRow;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public final class StandingsExportSupport {

    private static final String[] HEADERS = {
            "#", "Team", "P", "W", "D", "L", "GF", "GA", "GD", "Pts"
    };

    private StandingsExportSupport() {
    }

    public static byte[] toExcel(String leagueName, List<StandingRow> standings) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeExcel(leagueName, standings, out);
        return out.toByteArray();
    }

    static void writeExcel(String leagueName, List<StandingRow> standings, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Standings");

            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue(leagueName + " — Standings");

            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 3;
            int position = 1;
            for (StandingRow row : standings) {
                Row dataRow = sheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(position++);
                dataRow.createCell(1).setCellValue(row.getTeamName()
                        + (row.getTeamCity() != null ? " (" + row.getTeamCity() + ")" : ""));
                dataRow.createCell(2).setCellValue(row.getPlayed());
                dataRow.createCell(3).setCellValue(row.getWins());
                dataRow.createCell(4).setCellValue(row.getDraws());
                dataRow.createCell(5).setCellValue(row.getLosses());
                dataRow.createCell(6).setCellValue(row.getGoalsFor());
                dataRow.createCell(7).setCellValue(row.getGoalsAgainst());
                dataRow.createCell(8).setCellValue(row.getGoalDiff());
                dataRow.createCell(9).setCellValue(row.getPoints());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
        }
    }

    public static byte[] toPdf(String leagueName, List<StandingRow> standings) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph(leagueName + " — Standings", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(16);
            document.add(title);

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            for (String header : HEADERS) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font bodyFont = new Font(Font.HELVETICA, 10);
            int position = 1;
            for (StandingRow row : standings) {
                addCell(table, String.valueOf(position++), bodyFont);
                addCell(table, row.getTeamName()
                        + (row.getTeamCity() != null ? " (" + row.getTeamCity() + ")" : ""), bodyFont);
                addCell(table, String.valueOf(row.getPlayed()), bodyFont);
                addCell(table, String.valueOf(row.getWins()), bodyFont);
                addCell(table, String.valueOf(row.getDraws()), bodyFont);
                addCell(table, String.valueOf(row.getLosses()), bodyFont);
                addCell(table, String.valueOf(row.getGoalsFor()), bodyFont);
                addCell(table, String.valueOf(row.getGoalsAgainst()), bodyFont);
                addCell(table, String.valueOf(row.getGoalDiff()), bodyFont);
                addCell(table, String.valueOf(row.getPoints()), bodyFont);
            }
            document.add(table);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static void addCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(value, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    public static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9-]+", "_");
    }
}
