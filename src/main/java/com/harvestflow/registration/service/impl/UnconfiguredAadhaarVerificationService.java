package com.harvestflow.registration.service.impl; import com.harvestflow.registration.service.AadhaarVerificationService; import org.springframework.stereotype.Service; import java.util.UUID;
@Service public class UnconfiguredAadhaarVerificationService implements AadhaarVerificationService { public void sendAadhaarOtp(UUID id,String aadhaar){} public void verifyAadhaarOtp(UUID id,String otp){} }
