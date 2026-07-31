package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.dto.user.CreateUserRequest;
import com.harvestflow.api.dto.user.CredentialResponse;
import com.harvestflow.api.dto.user.UserResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.api.util.CredentialGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialGenerator credentialGenerator;

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public CredentialResponse createUser(CreateUserRequest request) {
        User user = new User();
        user.setName(request.name());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setLocation(request.location());

        String username = credentialGenerator.generateUsername(request.name());
        String tempPassword = credentialGenerator.generateTempPassword();

        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setActive(true);

        User saved = userRepository.save(user);
        return new CredentialResponse(UserResponse.from(saved), tempPassword);
    }

    @Transactional
    public UserResponse toggleActive(UUID userId) {
        User user = findUser(userId);
        user.setActive(!user.isActive());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public CredentialResponse resetPassword(UUID userId) {
        User user = findUser(userId);
        String tempPassword = credentialGenerator.generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        return new CredentialResponse(UserResponse.from(saved), tempPassword);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
