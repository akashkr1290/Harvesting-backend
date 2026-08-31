package com.harvestflow.api.domain.entity;

import com.harvestflow.registration.crypto.EncryptedStringConverter;

import com.harvestflow.api.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String mobile;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private String location;

    @Column(nullable = false)
    private String username;

    /** BCrypt hash — never store or return the plaintext password. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    private boolean active = true;

    private Instant lastActiveAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bank_account_number_encrypted") private String bankAccountNumber;
    private String bankAccountHolderName;
    private String bankIfscCode;
    private String bankName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
