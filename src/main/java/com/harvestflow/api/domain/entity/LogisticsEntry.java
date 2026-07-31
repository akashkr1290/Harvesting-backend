package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.LogisticsKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "logistics_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "kind"})
)
@Getter
@Setter
@NoArgsConstructor
public class LogisticsEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "case_id", nullable = false)
    private HarvestCase harvestCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogisticsKind kind;

    @Column(nullable = false)
    private String personOrContractorName;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private Double advancePayment = 0.0;

    @Column(length = 1000)
    private String remarks;

    @Column(nullable = false)
    private Instant enteredAt = Instant.now();
}
