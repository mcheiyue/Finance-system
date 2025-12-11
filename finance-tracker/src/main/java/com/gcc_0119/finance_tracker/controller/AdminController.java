package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.model.Transaction;
import com.gcc_0119.finance_tracker.model.User;
import com.gcc_0119.finance_tracker.repository.TransactionRepository;
import com.gcc_0119.finance_tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired UserRepository userRepository;
    @Autowired TransactionRepository transactionRepository;
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll().stream()
                .peek(u -> u.setPassword(null)) 
                .collect(Collectors.toList());
    }
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getUsername().equals("admin")) {
            return ResponseEntity.badRequest().body("无法删除超级管理员!");
        }
        List<Transaction> userTrans = transactionRepository.findByUserId(id);
        transactionRepository.deleteAll(userTrans);
        userRepository.deleteById(id);

        return ResponseEntity.ok("用户及其 " + userTrans.size() + " 条账单已删除");
    }
    @GetMapping("/transactions/all")
    public List<Transaction> getAllTransactionsInSystem() {
        return transactionRepository.findAll().stream()
                .sorted((t1, t2) -> t2.getTimestamp().compareTo(t1.getTimestamp()))
                .collect(Collectors.toList());
    }
}