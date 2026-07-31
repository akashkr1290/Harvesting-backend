package com.harvestflow.api.controller;

import com.harvestflow.api.dto.auth.ChangePasswordRequest;
import com.harvestflow.api.dto.auth.LoginRequest;
import com.harvestflow.api.dto.auth.LoginResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/change-password")
    public void changePassword(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.getUser(), request);
    }
}
