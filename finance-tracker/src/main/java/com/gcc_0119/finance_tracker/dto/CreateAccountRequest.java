package com.gcc_0119.finance_tracker.dto;

import com.gcc_0119.finance_tracker.model.AccountType;
import com.gcc_0119.finance_tracker.model.Necessity;
import lombok.Data;

@Data
public class CreateAccountRequest {
    private String name;
    private AccountType type;
    private boolean fixed;
    private Necessity necessity;
}
