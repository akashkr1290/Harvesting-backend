package com.harvestflow.api.controller;

import com.harvestflow.api.dto.user.CreateUserRequest;
import com.harvestflow.api.dto.user.CredentialResponse;
import com.harvestflow.api.dto.user.LoginHistoryResponse;
import com.harvestflow.api.dto.user.UserResponse;
import com.harvestflow.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> list() {
        return userService.listUsers();
    }

    @PostMapping
    public CredentialResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PatchMapping("/{id}/toggle-active")
    public UserResponse toggleActive(@PathVariable UUID id) {
        return userService.toggleActive(id);
    }

    @PostMapping("/{id}/reset-password")
    public CredentialResponse resetPassword(@PathVariable UUID id) {
        return userService.resetPassword(id);
    }

    @GetMapping("/{id}/login-history")
    public List<LoginHistoryResponse> loginHistory(@PathVariable UUID id) {
        return userService.getLoginHistory(id);
    }
}
