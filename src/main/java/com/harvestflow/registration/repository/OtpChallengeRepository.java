package com.harvestflow.registration.repository;
import com.harvestflow.registration.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge,UUID> { Optional<OtpChallenge> findTopByRegistrationSessionIdAndPurposeOrderByCreatedAtDesc(UUID id,OtpPurpose purpose); }
