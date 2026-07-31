package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.CaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one record that moves across every team (SOW section 2, objective 3).
 * Plot Selection fields live directly on this entity since they're always
 * present from creation; every later stage is a separate child entity that
 * stays null until that stage's screen saves it — which doubles as "has
 * this stage happened yet" (e.g. Purchase Invoice auto-pulling completion
 * and logistics data).
 */
@Entity
@Table(name = "harvest_case")
@Getter
@Setter
@NoArgsConstructor
public class HarvestCase {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.SUBMITTED_FOR_PLANNING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private String createdByUsername;

    // ---- Plot Selection fields (SOW 5.3 A) ----
    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private LocalDate visitDate;

    private String commissionAgentName;

    @Column(nullable = false)
    private String farmerName;

    private String farmerPhone;

    @Column(nullable = false)
    private String village;

    private Integer numberOfPlants;

    @Column(nullable = false)
    private LocalDate harvestingDate;

    @Column(length = 1000)
    private String remark;

    private Double volume;
    private Double recovery;
    private Double pulp;

    @Column(nullable = false)
    private String selectedCompany;

    private String plotCode;
    private String gpsNote;
    private String priority = "medium";
    private String photoPath;

    // ---- Progressive stage data ----
    @OneToOne(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private PlanningDetails planning;

    private Double ratePerUnit;

    @OneToMany(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackingMaterialRecord> packingRecords = new ArrayList<>();

    @OneToOne(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private HarvestCompletion completion;

    @OneToMany(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LogisticsEntry> logisticsEntries = new ArrayList<>();

    @OneToOne(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private PurchaseInvoice purchaseInvoice;

    @OneToOne(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private SalesInvoice salesInvoice;

    @OneToMany(mappedBy = "harvestCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimelineEntry> timeline = new ArrayList<>();

    public void addTimelineEntry(String actor, String action) {
        TimelineEntry entry = new TimelineEntry();
        entry.setHarvestCase(this);
        entry.setActor(actor);
        entry.setAction(action);
        this.timeline.add(entry);
    }
}
