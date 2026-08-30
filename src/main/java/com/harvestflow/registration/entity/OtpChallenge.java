package com.harvestflow.registration.entity;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="otp_challenge") @Getter @Setter @NoArgsConstructor
public class OtpChallenge {
 @Id @GeneratedValue private UUID id;
 @Column(nullable=false) private UUID registrationSessionId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private OtpPurpose purpose;
 @Column(nullable=false,length=100) private String otpHash;
 @Column(nullable=false) private Instant expiresAt;
 private Instant consumedAt;
 @Column(nullable=false) private int attemptCount;
 @Column(nullable=false) private int resendCount;
 @Column(nullable=false) private Instant createdAt=Instant.now();
}
