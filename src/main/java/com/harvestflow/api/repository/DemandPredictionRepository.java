package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.DemandPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandPredictionRepository extends JpaRepository<DemandPrediction, UUID> {

    Optional<DemandPrediction> findFirstByCropTypeIgnoreCaseAndLocationIgnoreCaseOrderByGeneratedAtDesc(
            String cropType, String location);

    List<DemandPrediction> findByCropTypeIgnoreCaseAndLocationIgnoreCaseOrderByGeneratedAtDesc(
            String cropType, String location);

    List<DemandPrediction> findAllByOrderByGeneratedAtDesc();
}
