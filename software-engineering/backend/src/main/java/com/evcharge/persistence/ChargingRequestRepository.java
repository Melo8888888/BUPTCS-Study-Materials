package com.evcharge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingRequestRepository extends JpaRepository<ChargingRequestRecord, String> {
}
