package com.evcharge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParkingFeeRepository extends JpaRepository<ParkingFeeRecord, String> {
    List<ParkingFeeRecord> findAllByOrderByGeneratedAtAscIdAsc();

    List<ParkingFeeRecord> findByVehicleIdOrderByGeneratedAtAscIdAsc(String vehicleId);

    Optional<ParkingFeeRecord> findByBillDetailNo(String billDetailNo);
}
