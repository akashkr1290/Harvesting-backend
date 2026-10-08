package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_otp")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryOtp {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "market_order_id", nullable = false, unique = true)
    private MarketOrder marketOrder;

    @Column(nullable = false)
    private String otpHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    /** Wrong guesses for the current OTP; locked at {@code DeliveryOtpVerifier.MAX_ATTEMPTS}. */
    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
