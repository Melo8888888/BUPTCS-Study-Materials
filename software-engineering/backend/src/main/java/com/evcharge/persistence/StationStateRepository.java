package com.evcharge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StationStateRepository extends JpaRepository<StationStateRecord, String> {
}
