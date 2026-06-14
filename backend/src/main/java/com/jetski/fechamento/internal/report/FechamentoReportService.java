package com.jetski.fechamento.internal.report;

import com.jetski.fechamento.domain.FechamentoDiario;
import com.jetski.fechamento.domain.FechamentoMensal;
import com.jetski.shared.exception.BusinessException;

// OpenPDF imports for PDF generation
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// Apache POI imports for Excel generation
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service for generating PDF and Excel reports for Fechamento (Closures).
 *
 * @author Jetski Team
 * @since 0.11.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FechamentoReportService {

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(PT_BR);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // PDF Fonts
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, Color.DARK_GRAY);
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.GRAY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.DARK_GRAY);

    // ========================================
    // PDF Generation
    // ========================================

    public byte[] generateDiarioPdf(FechamentoDiario fechamento, String tenantName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header
            addPdfHeader(document, "Relatório de Fechamento Diário", tenantName);
            addPdfSubtitle(document, "Data: " + fechamento.getDtReferencia().format(DATE_FORMAT));
            addPdfStatus(document, fechamento.getStatus());

            document.add(Chunk.NEWLINE);

            // Summary Table
            addDiarioSummaryTable(document, fechamento);

            document.add(Chunk.NEWLINE);

            // Payment Methods Table
            addPaymentMethodsTable(document, fechamento);

            document.add(Chunk.NEWLINE);

            // Footer
            addPdfFooter(document, fechamento.getDtFechamento());

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for fechamento diario: {}", fechamento.getId(), e);
            throw new BusinessException("Erro ao gerar PDF: " + e.getMessage());
        }
    }

    public byte[] generateMensalPdf(FechamentoMensal fechamento, String tenantName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header
            addPdfHeader(document, "Relatório de Fechamento Mensal", tenantName);
            String mesAno = getMonthName(fechamento.getMes()) + " / " + fechamento.getAno();
            addPdfSubtitle(document, "Período: " + mesAno);
            addPdfStatus(document, fechamento.getStatus());

            document.add(Chunk.NEWLINE);

            // Summary Table
            addMensalSummaryTable(document, fechamento);

            document.add(Chunk.NEWLINE);

            // Result highlight
            addResultadoLiquido(document, fechamento.getResultadoLiquido());

            document.add(Chunk.NEWLINE);

            // Footer
            addPdfFooter(document, fechamento.getDtFechamento());

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for fechamento mensal: {}", fechamento.getId(), e);
            throw new BusinessException("Erro ao gerar PDF: " + e.getMessage());
        }
    }

    private void addPdfHeader(Document document, String title, String tenantName) throws DocumentException {
        Paragraph header = new Paragraph(title, TITLE_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        document.add(header);

        Paragraph tenant = new Paragraph(tenantName, SUBTITLE_FONT);
        tenant.setAlignment(Element.ALIGN_CENTER);
        document.add(tenant);

        document.add(Chunk.NEWLINE);
    }

    private void addPdfSubtitle(Document document, String text) throws DocumentException {
        Paragraph subtitle = new Paragraph(text, SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitle);
    }

    private void addPdfStatus(Document document, String status) throws DocumentException {
        Font statusFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        switch (status) {
            case "aberto" -> statusFont.setColor(Color.ORANGE);
            case "fechado" -> statusFont.setColor(Color.BLUE);
            case "aprovado" -> statusFont.setColor(new Color(0, 128, 0));
        }
        Paragraph statusPara = new Paragraph("Status: " + status.toUpperCase(), statusFont);
        statusPara.setAlignment(Element.ALIGN_CENTER);
        document.add(statusPara);
    }

    private void addDiarioSummaryTable(Document document, FechamentoDiario f) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(80);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        addTableHeader(table, "Resumo Financeiro", 2);

        addTableRow(table, "Total de Locações", String.valueOf(f.getTotalLocacoes()));
        addTableRow(table, "Total Faturado", formatCurrency(f.getTotalFaturado()));
        addTableRow(table, "Combustível", formatCurrency(f.getTotalCombustivel()));
        addTableRow(table, "Comissões", formatCurrency(f.getTotalComissoes()));

        document.add(table);
    }

    private void addPaymentMethodsTable(Document document, FechamentoDiario f) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(80);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        addTableHeader(table, "Por Forma de Pagamento", 2);

        addTableRow(table, "Dinheiro", formatCurrency(f.getTotalDinheiro()));
        addTableRow(table, "Cartão", formatCurrency(f.getTotalCartao()));
        addTableRow(table, "PIX", formatCurrency(f.getTotalPix()));

        BigDecimal total = f.getTotalDinheiro().add(f.getTotalCartao()).add(f.getTotalPix());
        addTableTotalRow(table, "Total", formatCurrency(total));

        document.add(table);
    }

    private void addMensalSummaryTable(Document document, FechamentoMensal f) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(80);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        addTableHeader(table, "Resumo do Mês", 2);

        addTableRow(table, "Total de Locações", String.valueOf(f.getTotalLocacoes()));
        addTableRow(table, "Total Faturado", formatCurrency(f.getTotalFaturado()));
        addTableRow(table, "(-) Custos Operacionais", formatCurrency(f.getTotalCustos()));
        addTableRow(table, "(-) Comissões", formatCurrency(f.getTotalComissoes()));
        addTableRow(table, "(-) Manutenções", formatCurrency(f.getTotalManutencoes()));

        document.add(table);
    }

    private void addResultadoLiquido(Document document, BigDecimal resultado) throws DocumentException {
        Font font = new Font(Font.HELVETICA, 16, Font.BOLD);
        font.setColor(resultado.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0, 128, 0) : Color.RED);

        Paragraph p = new Paragraph("Resultado Líquido: " + formatCurrency(resultado), font);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
    }

    private void addTableHeader(PdfPTable table, String title, int colspan) {
        PdfPCell headerCell = new PdfPCell(new Phrase(title, HEADER_FONT));
        headerCell.setColspan(colspan);
        headerCell.setBackgroundColor(new Color(52, 73, 94));
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        headerCell.setPadding(8);
        table.addCell(headerCell);
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, CELL_FONT));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(245, 245, 245));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, CELL_FONT));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addTableTotalRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, TOTAL_FONT));
        labelCell.setPadding(5);
        labelCell.setBackgroundColor(new Color(220, 220, 220));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, TOTAL_FONT));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(new Color(220, 220, 220));
        table.addCell(valueCell);
    }

    private void addPdfFooter(Document document, java.time.Instant dtFechamento) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        Font footerFont = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
        String geradoEm = "Relatório gerado em: " +
                java.time.LocalDateTime.now().format(DATETIME_FORMAT);
        Paragraph footer = new Paragraph(geradoEm, footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        if (dtFechamento != null) {
            String fechadoEm = "Fechamento realizado em: " +
                    dtFechamento.atZone(ZoneId.of("America/Sao_Paulo"))
                            .format(DATETIME_FORMAT);
            Paragraph fechamento = new Paragraph(fechadoEm, footerFont);
            fechamento.setAlignment(Element.ALIGN_CENTER);
            document.add(fechamento);
        }
    }

    // ========================================
    // Excel Generation
    // ========================================

    public byte[] generateDiarioExcel(FechamentoDiario fechamento, String tenantName) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Fechamento Diário");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            int rowNum = 0;

            // Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Fechamento Diário - " + tenantName);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            // Date
            org.apache.poi.ss.usermodel.Row dateRow = sheet.createRow(rowNum++);
            dateRow.createCell(0).setCellValue("Data:");
            dateRow.createCell(1).setCellValue(fechamento.getDtReferencia().format(DATE_FORMAT));

            // Status
            org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
            statusRow.createCell(0).setCellValue("Status:");
            statusRow.createCell(1).setCellValue(fechamento.getStatus().toUpperCase());

            rowNum++; // Empty row

            // Summary Header
            org.apache.poi.ss.usermodel.Row summaryHeader = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell summaryCell = summaryHeader.createCell(0);
            summaryCell.setCellValue("Resumo Financeiro");
            summaryCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

            // Summary Data
            addExcelRow(sheet, rowNum++, "Total Locações", fechamento.getTotalLocacoes(), null);
            addExcelCurrencyRow(sheet, rowNum++, "Total Faturado", fechamento.getTotalFaturado(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "Combustível", fechamento.getTotalCombustivel(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "Comissões", fechamento.getTotalComissoes(), currencyStyle);

            rowNum++; // Empty row

            // Payment Methods Header
            org.apache.poi.ss.usermodel.Row paymentHeader = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell paymentCell = paymentHeader.createCell(0);
            paymentCell.setCellValue("Por Forma de Pagamento");
            paymentCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

            addExcelCurrencyRow(sheet, rowNum++, "Dinheiro", fechamento.getTotalDinheiro(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "Cartão", fechamento.getTotalCartao(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "PIX", fechamento.getTotalPix(), currencyStyle);

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error generating Excel for fechamento diario: {}", fechamento.getId(), e);
            throw new BusinessException("Erro ao gerar Excel: " + e.getMessage());
        }
    }

    public byte[] generateMensalExcel(FechamentoMensal fechamento, String tenantName) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Fechamento Mensal");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle resultStyle = createResultStyle(workbook, fechamento.getResultadoLiquido());

            int rowNum = 0;

            // Title
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Fechamento Mensal - " + tenantName);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            // Period
            org.apache.poi.ss.usermodel.Row periodRow = sheet.createRow(rowNum++);
            periodRow.createCell(0).setCellValue("Período:");
            periodRow.createCell(1).setCellValue(getMonthName(fechamento.getMes()) + " / " + fechamento.getAno());

            // Status
            org.apache.poi.ss.usermodel.Row statusRow = sheet.createRow(rowNum++);
            statusRow.createCell(0).setCellValue("Status:");
            statusRow.createCell(1).setCellValue(fechamento.getStatus().toUpperCase());

            rowNum++; // Empty row

            // Summary Header
            org.apache.poi.ss.usermodel.Row summaryHeader = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell summaryCell = summaryHeader.createCell(0);
            summaryCell.setCellValue("Resumo do Mês");
            summaryCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

            // Summary Data
            addExcelRow(sheet, rowNum++, "Total Locações", fechamento.getTotalLocacoes(), null);
            addExcelCurrencyRow(sheet, rowNum++, "Total Faturado", fechamento.getTotalFaturado(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "(-) Custos Operacionais", fechamento.getTotalCustos(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "(-) Comissões", fechamento.getTotalComissoes(), currencyStyle);
            addExcelCurrencyRow(sheet, rowNum++, "(-) Manutenções", fechamento.getTotalManutencoes(), currencyStyle);

            rowNum++; // Empty row

            // Resultado Líquido
            org.apache.poi.ss.usermodel.Row resultRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell resultLabel = resultRow.createCell(0);
            resultLabel.setCellValue("RESULTADO LÍQUIDO");
            resultLabel.setCellStyle(resultStyle);

            org.apache.poi.ss.usermodel.Cell resultValue = resultRow.createCell(1);
            resultValue.setCellValue(fechamento.getResultadoLiquido().doubleValue());
            resultValue.setCellStyle(resultStyle);

            // Auto-size columns
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error generating Excel for fechamento mensal: {}", fechamento.getId(), e);
            throw new BusinessException("Erro ao gerar Excel: " + e.getMessage());
        }
    }

    private void addExcelRow(Sheet sheet, int rowNum, String label, Object value, CellStyle style) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        org.apache.poi.ss.usermodel.Cell valueCell = row.createCell(1);
        if (value instanceof Number) {
            valueCell.setCellValue(((Number) value).doubleValue());
        } else {
            valueCell.setCellValue(String.valueOf(value));
        }
        if (style != null) {
            valueCell.setCellStyle(style);
        }
    }

    private void addExcelCurrencyRow(Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        org.apache.poi.ss.usermodel.Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value.doubleValue());
        if (style != null) {
            valueCell.setCellStyle(style);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("R$ #,##0.00"));
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/MM/yyyy"));
        return style;
    }

    private CellStyle createResultStyle(Workbook workbook, BigDecimal resultado) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(resultado.compareTo(BigDecimal.ZERO) >= 0 ?
                IndexedColors.DARK_GREEN.getIndex() : IndexedColors.RED.getIndex());
        style.setFont(font);

        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("R$ #,##0.00"));
        return style;
    }

    // ========================================
    // Utilities
    // ========================================

    private String formatCurrency(BigDecimal value) {
        return CURRENCY_FORMAT.format(value);
    }

    private String getMonthName(int month) {
        String[] months = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
        return months[month - 1];
    }
}
