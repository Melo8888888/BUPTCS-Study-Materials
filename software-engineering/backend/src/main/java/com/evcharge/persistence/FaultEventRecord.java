package com.evcharge.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "fault_events")
public class FaultEventRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String pileId;
    private LocalDateTime startedAt;
    private LocalDateTime recoverAt;

    protected FaultEventRecord() {
    }

    public FaultEventRecord(String pileId, LocalDateTime startedAt, LocalDateTime recoverAt) {
        this.pileId = pileId;
        this.startedAt = startedAt;
        this.recoverAt = recoverAt;
    }

    public Long getId() { return id; }
    public String getPileId() { return pileId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getRecoverAt() { return recoverAt; }
}
