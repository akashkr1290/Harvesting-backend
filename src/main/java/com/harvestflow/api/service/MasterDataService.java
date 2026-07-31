package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.MasterItem;
import com.harvestflow.api.domain.enums.MasterCategory;
import com.harvestflow.api.dto.master.MasterItemRequest;
import com.harvestflow.api.dto.master.MasterItemResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.MasterItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final MasterItemRepository masterItemRepository;

    public List<MasterItemResponse> list(MasterCategory category, boolean activeOnly) {
        List<MasterItem> items = activeOnly
                ? masterItemRepository.findByCategoryAndActiveTrueOrderByNameAsc(category)
                : masterItemRepository.findByCategoryOrderByNameAsc(category);
        return items.stream().map(MasterItemResponse::from).toList();
    }

    @Transactional
    public MasterItemResponse add(MasterCategory category, MasterItemRequest request) {
        if (masterItemRepository.existsByCategoryAndNameIgnoreCase(category, request.name())) {
            throw new IllegalArgumentException("\"" + request.name() + "\" already exists in " + category);
        }
        MasterItem saved = masterItemRepository.save(new MasterItem(category, request.name().trim()));
        return MasterItemResponse.from(saved);
    }

    @Transactional
    public MasterItemResponse toggleActive(UUID id) {
        MasterItem item = masterItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Master item not found: " + id));
        item.setActive(!item.isActive());
        return MasterItemResponse.from(masterItemRepository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        if (!masterItemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Master item not found: " + id);
        }
        masterItemRepository.deleteById(id);
    }
}
