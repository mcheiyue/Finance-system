package com.gcc_0119.finance_tracker.config;

import com.gcc_0119.finance_tracker.model.User;
import com.gcc_0119.finance_tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@finance.com");
            admin.setPassword(encoder.encode("123456")); 
            admin.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER")); 

            userRepository.save(admin);
            System.out.println(">>> 管理员账号已自动创建: admin / 123456");
        }
    }
}