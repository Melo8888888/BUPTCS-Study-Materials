package com.evcharge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserRecord, String> {
    Optional<UserRecord> findByUsername(String username);

    boolean existsByUsername(String username);
}
