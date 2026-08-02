package com.kickoffsim.web;

import com.kickoffsim.dto.StandingRow;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public final class StandingsExportSupport {

    private static final String[] HEADERS = {
            "#", "Team", "P", "W", "D", "L", "GF", "GA", "GD", "Pts"
    };

    private static final Color BG_DEEP = new Color(0x06, 0x14, 0x0D);
    private static final Color SURFACE = new Color(0x10, 0x1F, 0x17);
    private static final Color SURFACE_RAISED = new Color(0x17, 0x2C, 0x20);
    private static final Color HEADER_BG = new Color(0x0B, 0x1C, 0x12);
    private static final Color BORDER_SOFT = new Color(0x2B, 0x47, 0x35);
    private static final Color TEXT = new Color(0xDD, 0xE8, 0xE1);
    private static final Color HEADING = new Color(0xEE, 0xF7, 0xF1);
    private static final Color MUTED = new Color(0x93, 0xAA, 0x9C);
    private static final Color GREEN = new Color(0x4A, 0xDE, 0x80);
    private static final Color RED = new Color(0xF8, 0x71, 0x71);

    private static final int TITLE_ROW = 0;
    private static final int HEADER_ROW = 2;
    private static final int FIRST_DATA_ROW = 3;
    private static final int SHADED_COLUMNS = 26;

    private StandingsExportSupport() {
    }

    public static byte[] toExcel(String leagueName, List<StandingRow> standings) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeExcel(leagueName, standings, out);
        return out.toByteArray();
    }

    static void writeExcel(String leagueName, List<StandingRow> standings, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Standings");
            sheet.setDisplayGridlines(false);
            sheet.setTabColor(color(GREEN));

            XSSFCellStyle pageStyle = fill(workbook, BG_DEEP);
            for (int i = 0; i < SHADED_COLUMNS; i++) {
                sheet.setDefaultColumnStyle(i, pageStyle);
            }

            XSSFCellStyle titleStyle = fill(workbook, BG_DEEP);
            titleStyle.setFont(font(workbook, 16, true, HEADING));
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle headerStyle = fill(workbook, HEADER_BG);
            headerStyle.setFont(font(workbook, 10, true, MUTED));
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderColor(BorderSide.BOTTOM, color(BORDER_SOFT));

            XSSFCellStyle teamHeaderStyle = workbook.createCellStyle();
            teamHeaderStyle.cloneStyleFrom(headerStyle);
            teamHeaderStyle.setAlignment(HorizontalAlignment.LEFT);

            XSSFCellStyle[] rank = new XSSFCellStyle[2];
            XSSFCellStyle[] team = new XSSFCellStyle[2];
            XSSFCellStyle[] value = new XSSFCellStyle[2];
            XSSFCellStyle[] diffUp = new XSSFCellStyle[2];
            XSSFCellStyle[] diffDown = new XSSFCellStyle[2];
            XSSFCellStyle[] points = new XSSFCellStyle[2];
            for (int band = 0; band < 2; band++) {
                Color background = band == 0 ? SURFACE_RAISED : SURFACE;
                rank[band] = dataStyle(workbook, background, 11, true, HEADING, HorizontalAlignment.CENTER);
                team[band] = dataStyle(workbook, background, 11, true, GREEN, HorizontalAlignment.LEFT);
                value[band] = dataStyle(workbook, background, 11, false, TEXT, HorizontalAlignment.CENTER);
                diffUp[band] = dataStyle(workbook, background, 11, true, GREEN, HorizontalAlignment.CENTER);
                diffDown[band] = dataStyle(workbook, background, 11, true, RED, HorizontalAlignment.CENTER);
                points[band] = dataStyle(workbook, background, 11, true, HEADING, HorizontalAlignment.CENTER);
            }

            Row title = sheet.createRow(TITLE_ROW);
            title.setHeightInPoints(34);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue(leagueName + " — Standings");
            titleCell.setCellStyle(titleStyle);
            for (int i = 1; i < HEADERS.length; i++) {
                title.createCell(i).setCellStyle(titleStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW, TITLE_ROW, 0, HEADERS.length - 1));

            Row spacer = sheet.createRow(TITLE_ROW + 1);
            spacer.setHeightInPoints(6);
            for (int i = 0; i < HEADERS.length; i++) {
                spacer.createCell(i).setCellStyle(pageStyle);
            }

            Row headerRow = sheet.createRow(HEADER_ROW);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(i == 1 ? teamHeaderStyle : headerStyle);
            }

            int rowIndex = FIRST_DATA_ROW;
            int position = 1;
            for (StandingRow row : standings) {
                int band = position % 2 == 1 ? 0 : 1;
                Row dataRow = sheet.createRow(rowIndex++);
                dataRow.setHeightInPoints(20);
                styled(dataRow, 0, position++, rank[band]);
                Cell teamCell = dataRow.createCell(1);
                teamCell.setCellValue(row.getTeamName()
                        + (row.getTeamCity() != null ? " (" + row.getTeamCity() + ")" : ""));
                teamCell.setCellStyle(team[band]);
                styled(dataRow, 2, row.getPlayed(), value[band]);
                styled(dataRow, 3, row.getWins(), value[band]);
                styled(dataRow, 4, row.getDraws(), value[band]);
                styled(dataRow, 5, row.getLosses(), value[band]);
                styled(dataRow, 6, row.getGoalsFor(), value[band]);
                styled(dataRow, 7, row.getGoalsAgainst(), value[band]);
                styled(dataRow, 8, row.getGoalDiff(),
                        row.getGoalDiff() < 0 ? diffDown[band] : diffUp[band]);
                styled(dataRow, 9, row.getPoints(), points[band]);
            }

            sheet.setColumnWidth(0, 6 * 256);
            sheet.setColumnWidth(1, 34 * 256);
            for (int i = 2; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, 9 * 256);
            }
            sheet.createFreezePane(0, FIRST_DATA_ROW);

            workbook.write(out);
        }
    }

    private static void styled(Row row, int column, int value, XSSFCellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static XSSFCellStyle dataStyle(XSSFWorkbook workbook, Color background, int size, boolean bold,
                                           Color textColor, HorizontalAlignment alignment) {
        XSSFCellStyle style = fill(workbook, background);
        style.setFont(font(workbook, size, bold, textColor));
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderColor(BorderSide.BOTTOM, color(BG_DEEP));
        return style;
    }

    private static XSSFCellStyle fill(XSSFWorkbook workbook, Color background) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color(background));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static XSSFFont font(XSSFWorkbook workbook, int size, boolean bold, Color textColor) {
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        font.setColor(color(textColor));
        return font;
    }

    private static XSSFColor color(Color source) {
        return new XSSFColor(new byte[]{
                (byte) source.getRed(), (byte) source.getGreen(), (byte) source.getBlue()
        }, null);
    }

    public static byte[] toPdf(String leagueName, List<StandingRow> standings) {
        Document document = new Document(PageSize.A4, 28, 28, 32, 32);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new DarkBackground());
            document.open();

            Paragraph title = new Paragraph(leagueName + " — Standings",
                    new Font(Font.HELVETICA, 18, Font.BOLD, HEADING));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            document.add(title);

            Paragraph subtitle = new Paragraph("Kickoff Sim", new Font(Font.HELVETICA, 9, Font.NORMAL, GREEN));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(18);
            document.add(subtitle);

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{6, 30, 8, 8, 8, 8, 9, 9, 9, 9});

            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, MUTED);
            for (int i = 0; i < HEADERS.length; i++) {
                PdfPCell cell = new PdfPCell(new Paragraph(HEADERS[i], headerFont));
                cell.setHorizontalAlignment(i == 1 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                cell.setBackgroundColor(HEADER_BG);
                cell.setBorderColor(BG_DEEP);
                cell.setBorderWidth(2);
                cell.setPadding(7);
                table.addCell(cell);
            }

            int position = 1;
            for (StandingRow row : standings) {
                Color background = position % 2 == 1 ? SURFACE_RAISED : SURFACE;
                addCell(table, String.valueOf(position++), font(11, true, HEADING), background, Element.ALIGN_CENTER);
                addCell(table, row.getTeamName()
                                + (row.getTeamCity() != null ? " (" + row.getTeamCity() + ")" : ""),
                        font(11, true, GREEN), background, Element.ALIGN_LEFT);
                Font bodyFont = font(11, false, TEXT);
                addCell(table, String.valueOf(row.getPlayed()), bodyFont, background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getWins()), bodyFont, background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getDraws()), bodyFont, background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getLosses()), bodyFont, background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getGoalsFor()), bodyFont, background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getGoalsAgainst()), bodyFont, background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getGoalDiff()),
                        font(11, true, row.getGoalDiff() < 0 ? RED : GREEN), background, Element.ALIGN_CENTER);
                addCell(table, String.valueOf(row.getPoints()), font(11, true, HEADING), background,
                        Element.ALIGN_CENTER);
            }
            document.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static Font font(int size, boolean bold, Color textColor) {
        return new Font(Font.HELVETICA, size, bold ? Font.BOLD : Font.NORMAL, textColor);
    }

    private static void addCell(PdfPTable table, String value, Font font, Color background, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(background);
        cell.setBorderColor(BG_DEEP);
        cell.setBorderWidth(2);
        cell.setPadding(7);
        table.addCell(cell);
    }

    private static final class DarkBackground extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.saveState();
            canvas.setColorFill(BG_DEEP);
            canvas.rectangle(0, 0, page.getWidth(), page.getHeight());
            canvas.fill();
            canvas.restoreState();
        }
    }

    public static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9-]+", "_");
    }
}
