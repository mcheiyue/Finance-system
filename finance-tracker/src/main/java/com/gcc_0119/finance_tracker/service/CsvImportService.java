package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.CsvImportPreview;
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvImportService {

    @Autowired
    private AccountRepository accountRepository;

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 预览 CSV 文件。
     * CSV 格式：description,amount,fromAccountName,toAccountName,timestamp
     */
    public CsvImportPreview preview(InputStream inputStream, String userId) {
        List<TransactionDTO> transactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalRows = 0;
        int validRows = 0;
        int invalidRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                totalRows++;

                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                try {
                    TransactionDTO dto = parseLine(line, userId);
                    transactions.add(dto);
                    validRows++;
                } catch (Exception e) {
                    errors.add("行 " + totalRows + ": " + e.getMessage());
                    invalidRows++;
                }
            }
        } catch (IOException e) {
            throw new BusinessException("读取 CSV 文件失败: " + e.getMessage());
        }

        CsvImportPreview preview = new CsvImportPreview();
        preview.setTransactions(transactions);
        preview.setTotalRows(totalRows - 1); // 减去表头
        preview.setValidRows(validRows);
        preview.setInvalidRows(invalidRows);
        preview.setErrors(errors);

        return preview;
    }

    TransactionDTO parseLine(String line, String userId) {
        String[] parts = line.split(",");
        if (parts.length < 5) {
            throw new RuntimeException("列数不足，需要 5 列");
        }

        String description = parts[0].trim();
        String amountStr = parts[1].trim();
        String fromAccountName = parts[2].trim();
        String toAccountName = parts[3].trim();
        String timestampStr = parts[4].trim();

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("金额必须为正数");
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("金额格式错误: " + amountStr);
        }

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(timestampStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("时间格式错误，应为 yyyy-MM-dd HH:mm:ss: " + timestampStr);
        }

        Account fromAccount = accountRepository.findByUserIdAndName(userId, fromAccountName)
                .orElseThrow(() -> new RuntimeException("来源账户不存在: " + fromAccountName));
        Account toAccount = accountRepository.findByUserIdAndName(userId, toAccountName)
                .orElseThrow(() -> new RuntimeException("目标账户不存在: " + toAccountName));

        TransactionDTO dto = new TransactionDTO();
        dto.setDescription(description);
        dto.setAmount(amount);
        dto.setFromAccountId(fromAccount.getId());
        dto.setToAccountId(toAccount.getId());
        dto.setTimestamp(timestamp);

        return dto;
    }
}
