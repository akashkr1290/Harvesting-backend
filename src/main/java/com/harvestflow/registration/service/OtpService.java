package com.harvestflow.registration.service; import com.harvestflow.registration.entity.OtpPurpose; import java.util.UUID;
public interface OtpService { void sendOtp(UUID sessionId,OtpPurpose purpose,String destination); void verifyOtp(UUID sessionId,OtpPurpose purpose,String otp); }
