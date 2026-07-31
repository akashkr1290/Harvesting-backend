package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.MasterItem;
import com.harvestflow.api.domain.enums.MasterCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MasterItemRepository extends JpaRepository<MasterItem, UUID> {
    List<MasterItem> findByCategoryOrderByNameAsc(MasterCategory category);
    List<MasterItem> findByCategoryAndActiveTrueOrderByNameAsc(MasterCategory category);
    boolean existsByCategoryAndNameIgnoreCase(MasterCategory category, String name);
}
