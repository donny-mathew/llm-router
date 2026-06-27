package com.llmrouter.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, String> {

    @Query("SELECT r.tierAssigned, COUNT(r), AVG(r.estimatedCostUsd), AVG(r.qualityScore) " +
           "FROM RequestLog r GROUP BY r.tierAssigned")
    List<Object[]> statsByTier();

    @Query("SELECT r.providerUsed, SUM(r.estimatedCostUsd) FROM RequestLog r GROUP BY r.providerUsed")
    List<Object[]> costByProvider();

    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.rerouted = true")
    long countRerouted();

    @Query("SELECT AVG(r.latencyMs) FROM RequestLog r")
    Double avgLatencyMs();
}
