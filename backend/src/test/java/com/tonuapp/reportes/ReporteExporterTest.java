package com.tonuapp.reportes;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonuapp.reportes.export.ReporteExporter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

class ReporteExporterTest {

    private ReporteExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ReporteExporter();
    }

    private TablaReporte tablaPrueba() {
        return new TablaReporte(
                "Reporte de inventario",
                "Agregados el Tonusco S.A.S. (TONUAPP) - Generado: 2026-09-05 10:00",
                new String[]{"Codigo", "Material", "Stock"},
                List.of(
                        new String[]{"1", "Cemento", "10.00"},
                        new String[]{"2", "Arena", "5.00"}),
                "");
    }

    @Test
    @DisplayName("xlsx: genera un archivo XLSX valido con titulo, encabezados y filas")
    void generaXlsxValido() throws Exception {
        byte[] bytes = exporter.xlsx(tablaPrueba());

        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 0x50); // PK (ZIP) - cabecera de un xlsx
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Reporte");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(3);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Codigo");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Material");
            Row fila = sheet.getRow(4);
            assertThat(fila.getCell(1).getStringCellValue()).isEqualTo("Cemento");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Arena");
        }
    }

    @Test
    @DisplayName("xlsx: reporte sin filas se genera igual (solo encabezados)")
    void generaXlsxSinFilas() {
        TablaReporte tabla = new TablaReporte("Reporte", "sub",
                new String[]{"A", "B"}, List.of(), "");

        byte[] bytes = exporter.xlsx(tabla);

        assertThat(bytes).isNotEmpty();
    }

    @Test
    @DisplayName("pdf: genera un archivo PDF valido")
    void generaPdfValido() {
        byte[] bytes = exporter.pdf(tablaPrueba());

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }
}