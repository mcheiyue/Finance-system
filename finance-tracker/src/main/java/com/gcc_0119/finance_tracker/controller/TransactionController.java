package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.common.SecurityUtils; // 引入新工具
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private SecurityUtils securityUtils; // 注入工具类

    @PostMapping
    public ApiResponse<TransactionDTO> createTransaction(@RequestBody TransactionDTO transactionDTO) {
        String userId = securityUtils.getCurrentUserId(); // 获取 ID
        return ApiResponse.success(transactionService.save(userId, transactionDTO)); // 传给 Service
    }

    @GetMapping
    public ApiResponse<List<TransactionDTO>> getAllTransactions() {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(transactionService.getAllTransactions(userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTransaction(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        transactionService.deleteById(userId, id);
        return ApiResponse.success("删除成功", null);
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionDTO> updateTransaction(@PathVariable String id,
            @RequestBody TransactionDTO transactionDTO) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success("更新成功", transactionService.update(userId, id, transactionDTO));
    }

    @GetMapping("/stats/type")
    public ApiResponse<Map<String, BigDecimal>> getStatsByType(@RequestParam(defaultValue = "30") int days) {
        String userId = securityUtils.getCurrentUserId();
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTotalByType(userId, start, end));
    }

    @GetMapping("/stats/category")
    public ApiResponse<Map<String, BigDecimal>> getStatsByCategory(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "30") int days) {
        String userId = securityUtils.getCurrentUserId();
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTotalByCategory(userId, type, start, end));
    }

    @GetMapping("/stats/recent")
    public ApiResponse<List<TransactionDTO>> getRecentTransactions(@RequestParam(defaultValue = "7") int days) {
        String userId = securityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTransactionsByDateRange(userId, start, now));
    }
}