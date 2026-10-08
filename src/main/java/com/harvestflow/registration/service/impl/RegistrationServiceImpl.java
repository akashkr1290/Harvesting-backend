package com.harvestflow.registration.service.impl;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.registration.dto.request.*;
import com.harvestflow.registration.dto.response.*;
import com.harvestflow.registration.entity.*;
import com.harvestflow.registration.exception.*;
import com.harvestflow.registration.repository.*;
import com.harvestflow.registration.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Self-registration for marketplace users (farmer, FPO, consumer, bulk buyer).
 *
 * <p>Flow: basic details, mobile OTP, role, address, documents (only what the
 * role needs), bank details (farmers), submit. Aadhaar OTP is optional unless
 * {@code harvestflow.registration.aadhaar-required=true}: doing Aadhaar OTP
 * needs a UIDAI-licensed partner, which is not integrated yet.
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    /** Documents each role must upload. Signature is optional for everyone (thumbprint users). */
    static final Map<SelfRegisterableRole, Set<DocumentType>> REQUIRED_DOCS = Map.of(
            SelfRegisterableRole.FARMER, EnumSet.of(DocumentType.PHOTO, DocumentType.KHET_LAND_RECEIPT),
            SelfRegisterableRole.FPO, EnumSet.of(DocumentType.PHOTO),
            SelfRegisterableRole.CONSUMER, EnumSet.noneOf(DocumentType.class),
            SelfRegisterableRole.BULK_BUYER, EnumSet.noneOf(DocumentType.class));

    private final RegistrationSessionRepository sessions;
    private final RegistrationDocumentRepository docs;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final OtpService otp;
    private final AadhaarVerificationService aadhaar;
    private final DocumentStorageService storage;
    private final boolean aadhaarRequired;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RegistrationServiceImpl(RegistrationSessionRepository s, RegistrationDocumentRepository d, UserRepository u,
                                   PasswordEncoder e, OtpService o, AadhaarVerificationService a, DocumentStorageService st,
                                   @Value("${harvestflow.registration.aadhaar-required:false}") boolean aadhaarRequired) {
        sessions = s;
        docs = d;
        users = u;
        encoder = e;
        otp = o;
        aadhaar = a;
        storage = st;
        this.aadhaarRequired = aadhaarRequired;
    }

    /** Kept for existing unit tests: Aadhaar optional. */
    public RegistrationServiceImpl(RegistrationSessionRepository s, RegistrationDocumentRepository d, UserRepository u,
                                   PasswordEncoder e, OtpService o, AadhaarVerificationService a, DocumentStorageService st) {
        this(s, d, u, e, o, a, st, false);
    }

    @Transactional
    public RegistrationSessionResponse startRegistration(BasicDetailsRequest r) {
        String username = (r.username() == null || r.username().isBlank()) ? generateUsername(r.fullName()) : r.username().trim();
        if (users.existsByUsername(username) || sessions.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException("Username already taken");
        }
        String email = (r.email() == null || r.email().isBlank())
                ? username.toLowerCase(Locale.ROOT) + "@users.harvestflow.local"
                : r.email().trim();
        if (users.existsByEmail(email)) throw new RegistrationException("Email already registered");
        RegistrationSession s = new RegistrationSession();
        s.setRegistrationToken(UUID.randomUUID().toString());
        s.setUsername(username);
        s.setPasswordHash(encoder.encode(r.password()));
        s.setFullName(r.fullName().trim());
        s.setEmail(email);
        s.setExpiresAt(Instant.now().plusSeconds(3600));
        sessions.save(s);
        return new RegistrationSessionResponse(s.getRegistrationToken(), s.getStatus().name());
    }

    /** Readable, unique username such as "sunilwagh4821"; users normally log in with their mobile number instead. */
    private String generateUsername(String fullName) {
        String base = fullName == null ? "" : fullName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.length() < 3) base = "user";
        if (base.length() > 20) base = base.substring(0, 20);
        for (int i = 0; i < 20; i++) {
            String candidate = base + (1000 + random.nextInt(9000));
            if (!users.existsByUsername(candidate) && !sessions.existsByUsername(candidate)) return candidate;
        }
        return "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private RegistrationSession get(String token) {
        RegistrationSession s = sessions.findByRegistrationToken(token)
                .orElseThrow(() -> new RegistrationException("Invalid or expired registration session"));
        if (Instant.now().isAfter(s.getExpiresAt()) && s.getStatus() != RegistrationStatus.COMPLETED) {
            s.setStatus(RegistrationStatus.EXPIRED);
            sessions.save(s);
            throw new RegistrationException("Registration session expired, please start again");
        }
        return s;
    }

    public OtpSentResponse sendMobileOtp(SendMobileOtpRequest r) {
        RegistrationSession s = get(r.registrationToken());
        if (users.existsByMobile(r.mobileNumber()) || sessions.existsByMobileNumberAndIdNotAndExpiresAtAfter(r.mobileNumber(), s.getId(), Instant.now())) {
            throw new MobileAlreadyRegisteredException("Mobile number already registered");
        }
        s.setMobileNumber(r.mobileNumber());
        s.setMobileVerified(false);
        sessions.save(s);
        otp.sendOtp(s.getId(), OtpPurpose.MOBILE_VERIFICATION, r.mobileNumber());
        return new OtpSentResponse("OTP_SENT", 300, 30);
    }

    @Transactional
    public OtpVerifiedResponse verifyMobileOtp(VerifyMobileOtpRequest r) {
        RegistrationSession s = get(r.registrationToken());
        otp.verifyOtp(s.getId(), OtpPurpose.MOBILE_VERIFICATION, r.otp());
        s.setMobileVerified(true);
        advance(s, RegistrationStatus.MOBILE_VERIFIED);
        sessions.save(s);
        return new OtpVerifiedResponse(true, s.getStatus().name());
    }

    @Transactional
    public OtpSentResponse sendAadhaarOtp(SendAadhaarOtpRequest r) {
        RegistrationSession s = get(r.registrationToken());
        require(s, RegistrationStatus.MOBILE_VERIFIED);
        s.setAadhaarNumber(r.aadhaarNumber());
        s.setAadhaarLast4(r.aadhaarNumber().substring(8));
        s.setAadhaarVerified(false);
        sessions.save(s);
        aadhaar.sendAadhaarOtp(s.getId(), r.aadhaarNumber());
        return new OtpSentResponse("OTP_SENT", 300, 30);
    }

    @Transactional
    public OtpVerifiedResponse verifyAadhaarOtp(VerifyAadhaarOtpRequest r) {
        RegistrationSession s = get(r.registrationToken());
        aadhaar.verifyAadhaarOtp(s.getId(), r.otp());
        s.setAadhaarVerified(true);
        advance(s, RegistrationStatus.AADHAAR_VERIFIED);
        sessions.save(s);
        return new OtpVerifiedResponse(true, s.getStatus().name());
    }

    @Transactional
    public RegistrationSessionResponse selectRole(RoleSelectionRequest r) {
        RegistrationSession s = get(r.registrationToken());
        require(s, aadhaarRequired ? RegistrationStatus.AADHAAR_VERIFIED : RegistrationStatus.MOBILE_VERIFIED);
        SelfRegisterableRole role;
        try {
            role = SelfRegisterableRole.valueOf(r.role().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new InvalidOrInternalRoleException("Role not allowed for self-registration");
        }
        s.setSelectedRole(role);
        advance(s, RegistrationStatus.ROLE_SELECTED);
        sessions.save(s);
        return new RegistrationSessionResponse(s.getRegistrationToken(), s.getStatus().name());
    }

    @Transactional
    public RegistrationSessionResponse submitAddress(AddressRequest r) {
        RegistrationSession s = get(r.registrationToken());
        require(s, RegistrationStatus.ROLE_SELECTED);
        RegistrationAddress a = new RegistrationAddress();
        a.setAddressLine(r.addressLine() == null ? "" : r.addressLine().trim());
        a.setVillageOrCity(r.villageOrCity().trim());
        a.setDistrict(r.district().trim());
        a.setState(r.state().trim());
        a.setPincode(r.pincode());
        s.setAddress(a);
        advance(s, RegistrationStatus.ADDRESS_COMPLETED);
        if (hasRequiredDocs(s)) advance(s, RegistrationStatus.DOCUMENTS_COMPLETED);
        sessions.save(s);
        return new RegistrationSessionResponse(s.getRegistrationToken(), s.getStatus().name());
    }

    public DocumentUploadResponse uploadKhetReceipt(String t, MultipartFile f) {
        return upload(t, DocumentType.KHET_LAND_RECEIPT, f);
    }

    public DocumentUploadResponse uploadSignature(String t, MultipartFile f) {
        return upload(t, DocumentType.SIGNATURE, f);
    }

    public DocumentUploadResponse uploadPhoto(String t, MultipartFile f) {
        return upload(t, DocumentType.PHOTO, f);
    }

    @Transactional
    public DocumentUploadResponse upload(String t, DocumentType type, MultipartFile f) {
        RegistrationSession s = get(t);
        require(s, RegistrationStatus.ADDRESS_COMPLETED);
        String path = storage.storeDocument(s.getId(), type, f);
        RegistrationDocument d = new RegistrationDocument();
        d.setRegistrationSessionId(s.getId());
        d.setDocumentType(type);
        d.setStoragePath(path);
        d.setOriginalFilename(Optional.ofNullable(f.getOriginalFilename()).orElse("document"));
        d.setContentType(f.getContentType());
        d.setFileSizeBytes(f.getSize());
        docs.deleteByRegistrationSessionIdAndDocumentType(s.getId(), type);
        docs.save(d);
        if (hasRequiredDocs(s)) {
            advance(s, RegistrationStatus.DOCUMENTS_COMPLETED);
            sessions.save(s);
        }
        return new DocumentUploadResponse(type.name(), d.getOriginalFilename(), "UPLOADED");
    }

    private Set<DocumentType> requiredDocs(RegistrationSession s) {
        if (s.getSelectedRole() == null) return EnumSet.of(DocumentType.PHOTO, DocumentType.KHET_LAND_RECEIPT);
        return REQUIRED_DOCS.getOrDefault(s.getSelectedRole(), EnumSet.noneOf(DocumentType.class));
    }

    private boolean hasRequiredDocs(RegistrationSession s) {
        Set<DocumentType> required = requiredDocs(s);
        if (required.isEmpty()) return true;
        Set<DocumentType> uploaded = docs.findDocumentTypesBySessionId(s.getId());
        return uploaded != null && uploaded.containsAll(required);
    }

    @Transactional
    public RegistrationSessionResponse submitBankDetails(BankDetailsRequest r) {
        RegistrationSession s = get(r.registrationToken());
        require(s, RegistrationStatus.ADDRESS_COMPLETED);
        if (s.getSelectedRole() != SelfRegisterableRole.FARMER) {
            throw new RegistrationException("Bank details are required only for FARMER registration");
        }
        String a = r.accountNumber().replaceAll("\\s+", "");
        if (!a.matches("\\d{9,18}")) throw new RegistrationException("Invalid bank account number");
        s.setBankAccountHolderName(r.accountHolderName().trim());
        s.setBankAccountNumber(a);
        s.setBankIfscCode(r.ifscCode().trim().toUpperCase(Locale.ROOT));
        s.setBankName(r.bankName().trim());
        if (hasRequiredDocs(s)) advance(s, RegistrationStatus.BANK_DETAILS_COMPLETED);
        sessions.save(s);
        return new RegistrationSessionResponse(s.getRegistrationToken(), s.getStatus().name());
    }

    @Transactional
    public RegistrationCompleteResponse submit(SubmitRegistrationRequest r) {
        RegistrationSession s = get(r.registrationToken());
        if (!s.isMobileVerified()) throw new RegistrationIncompleteException("Mobile number not verified");
        if (aadhaarRequired && !s.isAadhaarVerified()) throw new RegistrationIncompleteException("Aadhaar not verified");
        if (s.getSelectedRole() == null) throw new InvalidOrInternalRoleException("Role not selected");
        if (s.getAddress() == null) throw new RegistrationIncompleteException("Address incomplete");
        if (!hasRequiredDocs(s)) {
            String missing = requiredDocs(s).stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new DocumentValidationException("Please upload: " + missing);
        }
        if (s.getSelectedRole() == SelfRegisterableRole.FARMER
                && (s.getBankAccountNumber() == null || s.getBankIfscCode() == null)) {
            throw new RegistrationIncompleteException("Farmer bank details are incomplete");
        }
        if (users.existsByUsername(s.getUsername()) || users.existsByEmail(s.getEmail())
                || (s.getMobileNumber() != null && users.existsByMobile(s.getMobileNumber()))) {
            throw new RegistrationException("Account details are already registered");
        }
        User u = new User();
        u.setName(s.getFullName());
        u.setEmail(s.getEmail());
        u.setUsername(s.getUsername());
        u.setPasswordHash(s.getPasswordHash());
        u.setMobile(s.getMobileNumber());
        RegistrationAddress addr = s.getAddress();
        u.setLocation(List.of(nz(addr.getAddressLine()), nz(addr.getVillageOrCity()), nz(addr.getDistrict()),
                        nz(addr.getState()), nz(addr.getPincode())).stream()
                .filter(x -> !x.isBlank()).collect(Collectors.joining(", ")));
        u.setRole(UserRole.valueOf(s.getSelectedRole().name()));
        u.setMustChangePassword(false);
        u.setActive(true);
        if (s.getSelectedRole() == SelfRegisterableRole.FARMER) {
            u.setBankAccountHolderName(s.getBankAccountHolderName());
            u.setBankAccountNumber(s.getBankAccountNumber());
            u.setBankIfscCode(s.getBankIfscCode());
            u.setBankName(s.getBankName());
        }
        users.save(u);
        s.setStatus(RegistrationStatus.COMPLETED);
        s.setCompletedAt(Instant.now());
        sessions.save(s);
        String last4 = s.getAadhaarLast4() != null ? s.getAadhaarLast4() : "----";
        return new RegistrationCompleteResponse(s.getUsername(), maskMobile(s.getMobileNumber()), "XXXX-XXXX-" + last4,
                s.getSelectedRole().name(), "COMPLETED");
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    /** Status only moves forward: optional steps done late must not undo later progress. */
    private static void advance(RegistrationSession s, RegistrationStatus target) {
        if (s.getStatus().ordinal() < target.ordinal()) s.setStatus(target);
    }

    private void require(RegistrationSession s, RegistrationStatus min) {
        if (s.getStatus().ordinal() < min.ordinal()) {
            throw new RegistrationIncompleteException("Complete the previous registration step first");
        }
    }

    private String maskMobile(String x) {
        if (x == null || x.length() < 4) return "XXXXXXXXXX";
        return "XXXXXX" + x.substring(x.length() - 4);
    }
}
