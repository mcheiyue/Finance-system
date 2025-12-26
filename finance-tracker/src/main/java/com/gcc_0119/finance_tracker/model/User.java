package com.gcc_0119.finance_tracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Document(collection = "users")
@Data
public class User {
    @Id
    private String id;

    @Indexed(unique = true) 
    private String username;

    @Indexed(unique = true) 
    private String email;

    @JsonIgnore
    private String password; 

    private Set<String> roles; 
}