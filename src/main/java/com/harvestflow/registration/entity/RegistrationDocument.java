package com.harvestflow.registration.entity;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="registration_document", uniqueConstraints=@UniqueConstraint(columnNames={"registration_session_id","document_type"}))
@Getter @Setter @NoArgsConstructor
public class RegistrationDocument {
 @Id @GeneratedValue private UUID id;
 @Column(nullable=false) private UUID registrationSessionId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private DocumentType documentType;
 @Column(nullable=false) private String storagePath;
 @Column(nullable=false) private String originalFilename;
 @Column(nullable=false) private String contentType;
 @Column(nullable=false) private long fileSizeBytes;
 @Column(nullable=false) private Instant uploadedAt=Instant.now();
}
