package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.common.SecurityUtils;
import com.gcc_0119.finance_tracker.dto.CsvImportPreview;
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.service.CsvImportService;
import com.gcc_0119.finance_tracker.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/csv")
public class CsvImportController {

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private SecurityUtils securityUtils;

    @Value("${app.csv.confirm.max-transactions:500}")
    private int maxTransactions;

    @PostMapping("/preview")
    public ApiResponse<CsvImportPreview> preview(@RequestParam("file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        try {
            CsvImportPreview previewResult = csvImportService.preview(file.getInputStream(), userId);
            return ApiResponse.success(previewResult);
        } catch (Exception e) {
            return ApiResponse.error(400, "CSV 解析失败: " + e.getMessage());
        }
    }

    @Transactional
    @PostMapping("/confirm")
    public ApiResponse<List<TransactionDTO>> confirm(@RequestBody List<TransactionDTO> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return ApiResponse.error(400, "导入列表不能为空");
        }
        if (transactions.size() > maxTransactions) {
            return ApiResponse.error(400, "单次导入事务数超过上限 " + maxTransactions + " 条");
        }

        String userId = securityUtils.getCurrentUserId();
        List<TransactionDTO> results = new ArrayList<>();

        for (TransactionDTO dto : transactions) {
            TransactionDTO created = transactionService.createTransaction(userId, dto);
            results.add(created);
        }

        return ApiResponse.success(results);
    }
}
