package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.model.Transaction;
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
    public Transaction createTransaction(@RequestBody Transaction transaction) {
        return transactionService.save(transaction);
    }

    @GetMapping
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @DeleteMapping("/{id}")
    public void deleteTransaction(@PathVariable String id) {
        transactionService.deleteById(id);
    }
    @GetMapping("/stats/type")
    public Map<String, BigDecimal> getStatsByType() {
        return transactionService.getTotalByType();
    }
    @GetMapping("/stats/category")
    public Map<String, BigDecimal> getStatsByCategory(@RequestParam(required = false) String type) {
        return transactionService.getTotalByCategory(type);
    }
    @GetMapping("/stats/recent")
    public List<Transaction> getRecentTransactions(@RequestParam(defaultValue = "7") int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(days, ChronoUnit.DAYS);
        return transactionService.getTransactionsByDateRange(start, now);
    }
}