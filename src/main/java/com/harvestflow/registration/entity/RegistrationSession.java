package com.harvestflow.registration.entity;

import com.harvestflow.registration.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registration_session")
@Getter @Setter @NoArgsConstructor
public class RegistrationSession {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, unique = true, updatable = false, length = 64) private String registrationToken;
    @Column(nullable = false, unique = true, length = 64) private String username;
    @Column(nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false) private String fullName;
    @Column(nullable = false) private String email;
    private String mobileNumber;
    @Column(nullable = false) private boolean mobileVerified;
    @Convert(converter = EncryptedStringConverter.class) @Column(name = "aadhaar_number_encrypted") private String aadhaarNumber;
    @Column(length = 4) private String aadhaarLast4;
    @Column(nullable = false) private boolean aadhaarVerified;
    @Enumerated(EnumType.STRING) private SelfRegisterableRole selectedRole;
    @Embedded private RegistrationAddress address;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private RegistrationStatus status = RegistrationStatus.REGISTRATION_STARTED;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant expiresAt = Instant.now().plusSeconds(3600);
    private Instant completedAt;
}
