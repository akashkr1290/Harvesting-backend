package com.harvestflow.registration.repository;
import com.harvestflow.registration.entity.RegistrationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession,UUID> {
 Optional<RegistrationSession> findByRegistrationToken(String token);
 boolean existsByUsername(String username);
 boolean existsByMobileNumber(String mobileNumber);
 /** Another unexpired session already holds this number (expired, abandoned sessions do not block). */
 boolean existsByMobileNumberAndIdNotAndExpiresAtAfter(String mobileNumber, UUID id, java.time.Instant now);
}
