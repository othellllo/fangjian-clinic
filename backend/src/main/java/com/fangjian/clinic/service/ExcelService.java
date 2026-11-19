package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Excel 读写：统一按「首行表头 + 数据行」解析为 Map<表头, 文本>。 */
@Service
public class ExcelService {

    private static final DataFormatter FORMATTER = new DataFormatter();

    /** 解析上传文件，跳过空行与以「填写说明」开头的说明行。 */
    public List<Map<String, String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BusinessException("仅支持 .xlsx / .xls 文件，请使用系统提供的模板");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("文件不能超过 5MB");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException("Excel 为空，请使用系统模板");
            }
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(cellText(headerRow.getCell(i)).replace("*", "").trim());
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean blank = true;
                for (int c = 0; c < headers.size(); c++) {
                    String text = cellText(row.getCell(c));
                    values.put(headers.get(c), text);
                    if (!text.isBlank()) {
                        blank = false;
                    }
                }
                if (blank) {
                    continue;
                }
                String first = values.values().iterator().next();
                if (first != null && first.startsWith("填写说明")) {
                    continue;
                }
                if (rows.size() >= 2000) {
                    throw new BusinessException("单次最多导入 2000 行，请分批导入");
                }
                rows.add(values);
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析 Excel 失败：" + ex.getMessage());
        }
    }

    /** 生成模板：表头（必填带 *）+ 填写说明行 + 示例行。 */
    public byte[] template(String sheetName, List<String> headers, String instructions, List<String> example) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            Row noteRow = sheet.createRow(1);
            noteRow.createCell(0).setCellValue("填写说明：" + instructions);

            if (example != null) {
                Row exampleRow = sheet.createRow(2);
                for (int i = 0; i < example.size() && i < headers.size(); i++) {
                    exampleRow.createCell(i).setCellValue(example.get(i));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("生成模板失败：" + ex.getMessage());
        }
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        String text = FORMATTER.formatCellValue(cell);
        return text == null ? "" : text.trim();
    }
}
