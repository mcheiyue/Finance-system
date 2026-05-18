package com.gcc_0119.finance_tracker.dto;

import com.gcc_0119.finance_tracker.model.Necessity;
import lombok.Data;

@Data
public class UpdateAccountRequest {
    private String name;
    private Boolean fixed;
    private Necessity necessity;
}
