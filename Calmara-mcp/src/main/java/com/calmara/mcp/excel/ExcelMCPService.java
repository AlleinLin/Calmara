package com.calmara.mcp.excel;

import com.calmara.common.BusinessException;
import com.calmara.common.ErrorCode;
import com.calmara.model.dto.PsychRecord;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class ExcelMCPService {

    @Value("${calmara.mcp.excel.output-dir:/data/reports}")
    private String outputDir;

    private static final String[] HEADERS = {
            "用户ID", "对话内容", "情绪标签", "情绪分数", "风险等级", "记录时间"
    };

    public synchronized String writeRecord(PsychRecord record) {
        String fileName = "mental-record-" + LocalDate.now() + ".xlsx";
        Path filePath = Paths.get(outputDir, fileName);

        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            log.error("创建目录失败", e);
            throw new BusinessException(ErrorCode.EXCEL_WRITE_ERROR, "创建目录失败: " + e.getMessage());
        }

        Workbook workbook = null;
        try {
            Sheet sheet;
            int rowNum;

            if (Files.exists(filePath)) {
                try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                    workbook = new XSSFWorkbook(fis);
                    sheet = workbook.getSheetAt(0);
                    rowNum = sheet.getLastRowNum() + 1;
                }
            } else {
                workbook = new XSSFWorkbook();
                sheet = workbook.createSheet("心理记录");

                Row header = sheet.createRow(0);
                for (int i = 0; i < HEADERS.length; i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(HEADERS[i]);
                    CellStyle style = workbook.createCellStyle();
                    Font font = workbook.createFont();
                    font.setBold(true);
                    style.setFont(font);
                    cell.setCellStyle(style);
                }
                rowNum = 1;
            }

            Row row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue(record.getUserId());
            row.createCell(1).setCellValue(record.getContent());
            row.createCell(2).setCellValue(record.getEmotion());
            row.createCell(3).setCellValue(record.getEmotionScore() != null ? record.getEmotionScore() : 0.0);
            row.createCell(4).setCellValue(record.getRiskLevel());
            row.createCell(5).setCellValue(
                    record.getTimestamp() != null
                            ? record.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }

            log.info("Excel记录写入成功: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("Excel写入失败", e);
            throw new BusinessException(ErrorCode.EXCEL_WRITE_ERROR, "Excel写入失败: " + e.getMessage());
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    log.warn("关闭workbook失败", e);
                }
            }
        }
    }

    public synchronized void markRecordAsRevoked(String userId, LocalDateTime timestamp) {
        String fileName = "mental-record-" + LocalDate.now() + ".xlsx";
        Path filePath = Paths.get(outputDir, fileName);

        if (!Files.exists(filePath)) {
            log.warn("Excel文件不存在，无法撤销: {}", filePath);
            return;
        }

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            String targetTime = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String rowUserId = row.getCell(0) != null ? row.getCell(0).getStringCellValue() : "";
                String rowTime = row.getCell(5) != null ? row.getCell(5).getStringCellValue() : "";

                if (userId != null && userId.equals(rowUserId) && targetTime.equals(rowTime)) {
                    Cell statusCell = row.createCell(6);
                    statusCell.setCellValue("已撤销");

                    CellStyle style = workbook.createCellStyle();
                    Font font = workbook.createFont();
                    font.setColor(IndexedColors.RED.getIndex());
                    style.setFont(font);
                    statusCell.setCellStyle(style);

                    log.info("记录已标记撤销: userId={}, time={}", userId, targetTime);
                    break;
                }
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }

        } catch (IOException e) {
            log.error("标记撤销失败", e);
            throw new BusinessException(ErrorCode.EXCEL_WRITE_ERROR, "标记撤销失败: " + e.getMessage());
        }
    }

    @Data
    public static class ExcelConfig {
        private String outputDir = "/data/reports";
        private String templatePath;
    }
}
