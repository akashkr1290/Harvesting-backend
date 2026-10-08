package com.harvestflow.registration.service;

import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.repository.UserRepository;
import com.harvestflow.registration.dto.request.BasicDetailsRequest;
import com.harvestflow.registration.dto.request.SubmitRegistrationRequest;
import com.harvestflow.registration.entity.*;
import com.harvestflow.registration.repository.RegistrationDocumentRepository;
import com.harvestflow.registration.repository.RegistrationSessionRepository;
import com.harvestflow.registration.service.impl.RegistrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Farmer-friendly registration: no email, no username, mobile-only verification, role-based documents. */
class RegistrationSimplificationTest {

    @Mock RegistrationSessionRepository sessions;
    @Mock RegistrationDocumentRepository docs;
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock OtpService otp;
    @Mock AadhaarVerificationService aadhaar;
    @Mock DocumentStorageService storage;
    RegistrationServiceImpl service;

    @BeforeEach
    void set() {
        MockitoAnnotations.openMocks(this);
        service = new RegistrationServiceImpl(sessions, docs, users, encoder, otp, aadhaar, storage, false);
        when(encoder.encode(any())).thenReturn("hash");
    }

    @Test
    void startWithoutUsernameOrEmail_generatesBoth() {
        service.startRegistration(new BasicDetailsRequest("", "123456", "Sunil Wagh", ""));
        ArgumentCaptor<RegistrationSession> saved = ArgumentCaptor.forClass(RegistrationSession.class);
        verify(sessions).save(saved.capture());
        assertTrue(saved.getValue().getUsername().startsWith("sunilwagh"));
        assertTrue(saved.getValue().getEmail().endsWith("@users.harvestflow.local"));
    }

    @Test
    void consumerCanFinishWithoutAadhaarOrDocuments() {
        RegistrationSession s = session(SelfRegisterableRole.CONSUMER);
        when(sessions.findByRegistrationToken("t")).thenReturn(Optional.of(s));
        when(docs.findDocumentTypesBySessionId(s.getId())).thenReturn(EnumSet.noneOf(DocumentType.class));

        service.submit(new SubmitRegistrationRequest("t"));

        verify(users).save(any(User.class));
        assertEquals(RegistrationStatus.COMPLETED, s.getStatus());
    }

    @Test
    void farmerStillNeedsPhotoLandRecordAndBank() {
        RegistrationSession s = session(SelfRegisterableRole.FARMER);
        when(sessions.findByRegistrationToken("t")).thenReturn(Optional.of(s));
        when(docs.findDocumentTypesBySessionId(s.getId())).thenReturn(EnumSet.of(DocumentType.PHOTO, DocumentType.KHET_LAND_RECEIPT));

        assertThrows(com.harvestflow.registration.exception.RegistrationIncompleteException.class,
                () -> service.submit(new SubmitRegistrationRequest("t")));
        verify(users, never()).save(any(User.class));
    }

    private RegistrationSession session(SelfRegisterableRole role) {
        RegistrationSession s = new RegistrationSession();
        s.setId(UUID.randomUUID());
        s.setRegistrationToken("t");
        s.setUsername("sunil1234");
        s.setPasswordHash("hash");
        s.setFullName("Sunil Wagh");
        s.setEmail("sunil1234@users.harvestflow.local");
        s.setMobileNumber("9876543210");
        s.setMobileVerified(true);
        s.setSelectedRole(role);
        RegistrationAddress a = new RegistrationAddress();
        a.setVillageOrCity("Chandori");
        a.setDistrict("Nashik");
        a.setState("Maharashtra");
        a.setPincode("422201");
        s.setAddress(a);
        s.setStatus(RegistrationStatus.ADDRESS_COMPLETED);
        s.setExpiresAt(Instant.now().plusSeconds(300));
        return s;
    }
}
