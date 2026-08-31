package com.harvestflow.api.repository;
import com.harvestflow.api.domain.entity.DeliveryOtp; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface DeliveryOtpRepository extends JpaRepository<DeliveryOtp,UUID>{Optional<DeliveryOtp> findByMarketOrderId(UUID marketOrderId);}
