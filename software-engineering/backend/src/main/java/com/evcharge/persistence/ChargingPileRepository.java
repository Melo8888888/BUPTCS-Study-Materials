package com.evcharge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingPileRepository extends JpaRepository<ChargingPileRecord, String> {
}
