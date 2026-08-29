package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.LoginHistory;
import com.harvestflow.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {
    List<LoginHistory> findTop50ByUserOrderByLoggedInAtDesc(User user);
}
