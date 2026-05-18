package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.common.SecurityUtils;
import com.gcc_0119.finance_tracker.dto.AccountResponse;
import com.gcc_0119.finance_tracker.dto.CreateAccountRequest;
import com.gcc_0119.finance_tracker.dto.UpdateAccountRequest;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<AccountResponse>> listAccounts(
            @RequestParam(required = false) AccountType type) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.listAccounts(userId, type));
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountResponse> getAccountById(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.getAccountById(userId, id));
    }

    @PostMapping
    public ApiResponse<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.createAccount(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountResponse> updateAccount(
            @PathVariable String id,
            @RequestBody UpdateAccountRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(accountService.updateAccount(userId, id, request));
    }
}
