package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse; 
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
@CrossOrigin(origins = "*") 
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping
    public ApiResponse<TransactionDTO> createTransaction(@RequestBody TransactionDTO transactionDTO) {
        TransactionDTO result = transactionService.save(transactionDTO);
        return ApiResponse.success(result);
    }

    @GetMapping
    public ApiResponse<List<TransactionDTO>> getAllTransactions() {
        return ApiResponse.success(transactionService.getAllTransactions());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTransaction(@PathVariable String id) {
        transactionService.deleteById(id);
        return ApiResponse.success("删除成功", null);
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionDTO> updateTransaction(@PathVariable String id, @RequestBody TransactionDTO transactionDTO) {
        TransactionDTO result = transactionService.update(id, transactionDTO);
        return ApiResponse.success("更新成功", result);
    }

    @GetMapping("/stats/type")
    public ApiResponse<Map<String, BigDecimal>> getStatsByType(@RequestParam(defaultValue = "30") int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTotalByType(start, end));
    }

    @GetMapping("/stats/category")
    public ApiResponse<Map<String, BigDecimal>> getStatsByCategory(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTotalByCategory(type, start, end));
    }

    @GetMapping("/stats/recent")
    public ApiResponse<List<TransactionDTO>> getRecentTransactions(@RequestParam(defaultValue = "7") int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTransactionsByDateRange(start, now));
    }
}