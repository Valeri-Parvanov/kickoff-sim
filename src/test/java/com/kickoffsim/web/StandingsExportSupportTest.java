package com.kickoffsim.web;

import com.kickoffsim.dto.StandingRow;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class StandingsExportSupportTest {

    private StandingRow row(String name, String city, int played, int wins, int draws, int losses,
                             int goalsFor, int goalsAgainst) {
        StandingRow row = new StandingRow();
        row.setTeamName(name);
        row.setTeamCity(city);
        row.setPlayed(played);
        row.setWins(wins);
        row.setDraws(draws);
        row.setLosses(losses);
        row.setGoalsFor(goalsFor);
        row.setGoalsAgainst(goalsAgainst);
        return row;
    }

    @Test
    void toExcel_writesHeaderAndRowsWithCity() throws IOException {
        List<StandingRow> standings = List.of(
                row("Sample FC", "Sofia", 2, 1, 1, 0, 5, 3));

        byte[] data = StandingsExportSupport.toExcel("Test League", standings);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Test League — Standings");
            Row header = sheet.getRow(2);
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Team");
            Row dataRow = sheet.getRow(3);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Sample FC (Sofia)");
            assertThat(dataRow.getCell(9).getNumericCellValue()).isEqualTo(4.0);
        }
    }

    @Test
    void toExcel_teamWithoutCity_omitsCitySuffix() throws IOException {
        List<StandingRow> standings = List.of(
                row("NoCity FC", null, 1, 0, 0, 1, 0, 2));

        byte[] data = StandingsExportSupport.toExcel("Test League", standings);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(3);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("NoCity FC");
        }
    }

    @Test
    void writeExcel_propagatesFailure_whenOutputStreamFails() throws IOException {
        List<StandingRow> standings = List.of(row("Sample FC", "Sofia", 1, 1, 0, 0, 3, 1));
        try (OutputStream failing = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    throw new IOException("boom");
                }
        }) {
            assertThatThrownBy(() -> StandingsExportSupport.writeExcel("Test League", standings, failing))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void toPdf_producesNonEmptyPdfDocument() {
        List<StandingRow> standings = List.of(
                row("Sample FC", "Sofia", 2, 1, 1, 0, 5, 3),
                row("NoCity FC", null, 1, 0, 0, 1, 0, 2));

        byte[] data = StandingsExportSupport.toPdf("Test League", standings);

        assertThat(data).isNotEmpty();
        assertThat(new String(data, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void toExcel_appliesDarkThemeStyling() throws IOException {
        List<StandingRow> standings = List.of(
                row("Sample FC", "Sofia", 2, 2, 0, 0, 6, 1),
                row("Second FC", "Varna", 2, 0, 0, 2, 1, 6));

        byte[] data = StandingsExportSupport.toExcel("Test League", standings);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(0);
            assertThat(sheet.isDisplayGridlines()).isFalse();
            assertThat(sheet.getMergedRegions()).hasSize(1);
            assertThat(sheet.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) 3);

            XSSFCellStyle titleStyle = (XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle();
            assertThat(hex(titleStyle.getFillForegroundColorColor())).isEqualTo("FF06140D");

            XSSFCellStyle headerStyle = (XSSFCellStyle) sheet.getRow(2).getCell(0).getCellStyle();
            assertThat(hex(headerStyle.getFillForegroundColorColor())).isEqualTo("FF0B1C12");

            XSSFCellStyle firstBand = (XSSFCellStyle) sheet.getRow(3).getCell(0).getCellStyle();
            XSSFCellStyle secondBand = (XSSFCellStyle) sheet.getRow(4).getCell(0).getCellStyle();
            assertThat(hex(firstBand.getFillForegroundColorColor())).isEqualTo("FF172C20");
            assertThat(hex(secondBand.getFillForegroundColorColor())).isEqualTo("FF101F17");

            XSSFCellStyle positiveDiff = (XSSFCellStyle) sheet.getRow(3).getCell(8).getCellStyle();
            XSSFCellStyle negativeDiff = (XSSFCellStyle) sheet.getRow(4).getCell(8).getCellStyle();
            assertThat(hex(positiveDiff.getFont().getXSSFColor())).isEqualTo("FF4ADE80");
            assertThat(hex(negativeDiff.getFont().getXSSFColor())).isEqualTo("FFF87171");

            XSSFCellStyle teamStyle = (XSSFCellStyle) sheet.getRow(3).getCell(1).getCellStyle();
            assertThat(teamStyle.getAlignment()).isEqualTo(HorizontalAlignment.LEFT);
            assertThat(hex(teamStyle.getFont().getXSSFColor())).isEqualTo("FF4ADE80");
        }
    }

    private String hex(org.apache.poi.ss.usermodel.Color color) {
        return ((XSSFColor) color).getARGBHex();
    }

    @Test
    void sanitizeFilename_replacesNonAlphanumericCharacters() {
        assertThat(StandingsExportSupport.sanitizeFilename("Test League #1!")).isEqualTo("Test_League_1_");
    }

    @Test
    void toPdf_wrapsDocumentExceptionInIllegalStateException() {
        try (MockedStatic<PdfWriter> writers = Mockito.mockStatic(PdfWriter.class)) {
            writers.when(() -> PdfWriter.getInstance(any(Document.class), any(OutputStream.class)))
                    .thenThrow(new DocumentException("boom"));

            assertThatThrownBy(() -> StandingsExportSupport.toPdf("Test League", List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(DocumentException.class);
        }
    }
}
