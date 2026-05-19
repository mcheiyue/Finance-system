package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.common.SecurityUtils;
import com.gcc_0119.finance_tracker.dto.PaginatedResponse;
import com.gcc_0119.finance_tracker.dto.TransactionDTO;
import com.gcc_0119.finance_tracker.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping
    public ApiResponse<TransactionDTO> createTransaction(@RequestBody TransactionDTO transactionDTO) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(transactionService.createTransaction(userId, transactionDTO));
    }

    @PostMapping("/{id}/reverse")
    public ApiResponse<TransactionDTO> reverseTransaction(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(transactionService.reverseTransaction(userId, id));
    }

    @GetMapping
    public ApiResponse<PaginatedResponse<TransactionDTO>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String fromAccountId,
            @RequestParam(required = false) String toAccountId,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            @RequestParam(required = false) String keyword) {
        String userId = securityUtils.getCurrentUserId();
        PaginatedResponse<TransactionDTO> result = transactionService.getTransactionsPaginated(
                userId, page, size, fromAccountId, toAccountId, startDate, endDate, keyword);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionDTO> getTransactionById(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(transactionService.getTransactionById(userId, id));
    }

    @GetMapping("/stats/recent")
    public ApiResponse<List<TransactionDTO>> getRecentTransactions(@RequestParam(defaultValue = "7") int days) {
        String userId = securityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(days, ChronoUnit.DAYS);
        return ApiResponse.success(transactionService.getTransactionsByDateRange(userId, start, now));
    }
}
