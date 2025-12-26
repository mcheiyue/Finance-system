package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.dto.UserDTO;
import com.gcc_0119.finance_tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserDTO> getMyProfile() {
        return ApiResponse.success(userService.getCurrentUserProfile());
    }

    @PostMapping("/password")
    public ApiResponse<String> changePassword(@RequestBody Map<String, String> request) {
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        userService.changePassword(oldPassword, newPassword);

        return ApiResponse.success("密码修改成功");
    }
}