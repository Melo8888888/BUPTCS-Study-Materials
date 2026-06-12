package com.evcharge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargingDetailRepository extends JpaRepository<ChargingDetailRecord, String> {
    List<ChargingDetailRecord> findAllByOrderByGeneratedAtAscDetailNoAsc();
}
