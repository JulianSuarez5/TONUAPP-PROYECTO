package com.tonuapp.reportes.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tonuapp.reportes.TablaReporte;
import com.tonuapp.shared.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Genera los archivos de los reportes (RF-014, D-21): XLSX con Apache POI y PDF con
 * OpenPDF, a partir de una {@link TablaReporte} comun, de modo que cualquier tipo de
 * reporte se exporta sin logica duplicada.
 */
@Component
public class ReporteExporter {

    private static final String NOTA_SOLO_LECTURA = "Documento generado por TONUAPP: solo lectura.";

    public byte[] xlsx(TablaReporte tabla) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Reporte");
            sheet.setDefaultColumnWidth(16);

            CellStyle encabezadoStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            encabezadoStyle.setFont(bold);

            Row t = sheet.createRow(0);
            crearCelda(t, 0, tabla.titulo(), encabezadoStyle);
            Row s = sheet.createRow(1);
            if (tabla.subtitulo() != null && !tabla.subtitulo().isBlank()) {
                crearCelda(s, 0, tabla.subtitulo(), null);
            }

            int filaHeader = 3;
            Row header = sheet.createRow(filaHeader);
            for (int c = 0; c < tabla.encabezados().length; c++) {
                crearCelda(header, c, tabla.encabezados()[c], encabezadoStyle);
            }
            int fila = filaHeader + 1;
            for (String[] datos : tabla.filas()) {
                Row r = sheet.createRow(fila++);
                for (int c = 0; c < datos.length; c++) {
                    crearCelda(r, c, datos[c], null);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el archivo XLSX.");
        }
    }

    public byte[] pdf(TablaReporte tabla) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.addTitle(tabla.titulo());
            doc.addCreator("TONUAPP");
            doc.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            doc.add(new Paragraph(tabla.titulo(), tituloFont));
            if (tabla.subtitulo() != null && !tabla.subtitulo().isBlank()) {
                doc.add(new Paragraph(tabla.subtitulo(), FontFactory.getFont(FontFactory.HELVETICA, 11)));
            }

            PdfPTable table = new PdfPTable(Math.max(1, tabla.encabezados().length));
            table.setWidthPercentage(100);
            for (String encabezado : tabla.encabezados()) {
                PdfPCell cell = new PdfPCell(new Phrase(encabezado, headerFont));
                cell.setGrayFill(0.9f);
                table.addCell(cell);
            }
            for (String[] datos : tabla.filas()) {
                for (String valor : datos) {
                    table.addCell(new PdfPCell(new Phrase(valor == null ? "" : valor, normalFont)));
                }
            }
            doc.add(table);

            StringBuilder nota = new StringBuilder();
            if (tabla.notaPie() != null && !tabla.notaPie().isBlank()) {
                nota.append(tabla.notaPie()).append(" ");
            }
            nota.append(NOTA_SOLO_LECTURA);
            doc.add(new Paragraph(nota.toString(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8)));

            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el archivo PDF.");
        }
    }

    private void crearCelda(Row fila, int columna, String valor, CellStyle style) {
        Cell c = fila.createCell(columna);
        c.setCellValue(valor == null ? "" : valor);
        if (style != null) {
            c.setCellStyle(style);
        }
    }
}