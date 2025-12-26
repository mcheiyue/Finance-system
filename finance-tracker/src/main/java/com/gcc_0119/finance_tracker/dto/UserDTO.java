package com.gcc_0119.finance_tracker.dto;

import lombok.Data;
import java.util.Set;

@Data
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private Set<String> roles;

}