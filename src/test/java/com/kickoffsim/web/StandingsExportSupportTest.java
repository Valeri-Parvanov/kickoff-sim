package com.kickoffsim.web;

import com.kickoffsim.dto.StandingRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void writeExcel_propagatesFailure_whenOutputStreamFails() {
        List<StandingRow> standings = List.of(row("Sample FC", "Sofia", 1, 1, 0, 0, 3, 1));
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("boom");
            }
        };

        assertThatThrownBy(() -> StandingsExportSupport.writeExcel("Test League", standings, failing))
                .isInstanceOf(RuntimeException.class);
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
    void sanitizeFilename_replacesNonAlphanumericCharacters() {
        assertThat(StandingsExportSupport.sanitizeFilename("Test League #1!")).isEqualTo("Test_League_1_");
    }
}
