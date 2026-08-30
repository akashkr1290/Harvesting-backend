package com.harvestflow.registration.repository;
import com.harvestflow.registration.entity.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface RegistrationDocumentRepository extends JpaRepository<RegistrationDocument,UUID> {
 @Query("select d.documentType from RegistrationDocument d where d.registrationSessionId=:sessionId") Set<DocumentType> findDocumentTypesBySessionId(@Param("sessionId") UUID sessionId);
 void deleteByRegistrationSessionIdAndDocumentType(UUID sessionId, DocumentType documentType);
}
