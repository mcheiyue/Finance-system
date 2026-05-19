package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.common.SecurityUtils;
import com.gcc_0119.finance_tracker.dto.UserDTO;
import com.gcc_0119.finance_tracker.exception.BusinessException;
import com.gcc_0119.finance_tracker.model.User;
import com.gcc_0119.finance_tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_initializesPresetAccountsAfterUserSaved() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(encoder.encode("password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user123");
            return user;
        });

        userService.registerUser("alice", "alice@example.com", "password");

        verify(accountService).initializePresetAccounts("user123");
    }

    @Test
    void registerUser_existingUsernameDoesNotInitializeAccounts() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> userService.registerUser("alice", "alice@example.com", "password"));

        verify(userRepository, never()).save(any(User.class));
        verify(accountService, never()).initializePresetAccounts(any());
    }

    @Test
    void getCurrentUserProfile_returnsUserDTO() {
        User user = new User();
        user.setId("user123");
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRoles(java.util.Set.of("ROLE_USER"));

        when(securityUtils.getCurrentUserId()).thenReturn("user123");
        when(userRepository.findById("user123")).thenReturn(java.util.Optional.of(user));

        UserDTO result = userService.getCurrentUserProfile();

        assertNotNull(result);
        assertEquals("user123", result.getId());
        assertEquals("alice", result.getUsername());
        assertEquals("alice@example.com", result.getEmail());
    }

    @Test
    void getCurrentUserProfile_userNotFound_throwsException() {
        when(securityUtils.getCurrentUserId()).thenReturn("user123");
        when(userRepository.findById("user123")).thenReturn(java.util.Optional.empty());

        assertThrows(BusinessException.class, () -> userService.getCurrentUserProfile());
    }
}
