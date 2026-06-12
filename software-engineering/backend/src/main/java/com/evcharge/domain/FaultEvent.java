package com.evcharge.domain;

import java.time.LocalDateTime;

public class FaultEvent {
    private final String pileId;
    private final LocalDateTime startedAt;
    private final LocalDateTime recoverAt;

    public FaultEvent(String pileId, LocalDateTime startedAt, LocalDateTime recoverAt) {
        this.pileId = pileId;
        this.startedAt = startedAt;
        this.recoverAt = recoverAt;
    }

    public String getPileId() { return pileId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getRecoverAt() { return recoverAt; }
}
