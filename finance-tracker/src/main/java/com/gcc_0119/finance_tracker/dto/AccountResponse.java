package com.gcc_0119.finance_tracker.dto;

import com.gcc_0119.finance_tracker.model.Account;
import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Necessity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountResponse {
    private String id;
    private String userId;
    private String name;
    private AccountType type;
    private BigDecimal balance;
    private int version;
    private boolean isSystem;
    private boolean fixed;
    private Necessity necessity;
    private LocalDateTime createdAt;

    public static AccountResponse from(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setUserId(account.getUserId());
        response.setName(account.getName());
        response.setType(account.getType());
        response.setBalance(account.getBalance());
        response.setVersion(account.getVersion());
        response.setSystem(account.isSystem());
        response.setFixed(account.isFixed());
        response.setNecessity(account.getNecessity());
        response.setCreatedAt(account.getCreatedAt());
        return response;
    }
}
