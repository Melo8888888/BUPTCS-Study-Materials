package com.evcharge.service;

import java.util.Optional;

public interface StationStateStore {
    Optional<StationStateData> load();

    void save(StationStateData state);
}
