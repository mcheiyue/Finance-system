package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.UserDTO;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.User;
import com.gcc_0119.finance_tracker.repository.UserRepository;
import com.gcc_0119.finance_tracker.common.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SecurityUtils securityUtils;

    public void registerUser(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(400, "错误: 用户名已存在!");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(400, "错误: 邮箱已存在!");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setRoles(Set.of("ROLE_USER"));

        userRepository.save(user);
    }

    public UserDTO getCurrentUserProfile() {
        String userId = securityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户未找到"));

        return convertToDTO(user);
    }

    public void changePassword(String oldPassword, String newPassword) {
        String userId = securityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户未找到"));

        if (!encoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "旧密码错误！");
        }

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}