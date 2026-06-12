package com.evcharge.service;

import com.evcharge.dto.SystemSnapshot;

public interface StationEventPublisher {
    void publish(String type, SystemSnapshot snapshot);
}
