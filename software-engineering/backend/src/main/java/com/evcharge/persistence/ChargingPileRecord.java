package com.evcharge.persistence;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.PileStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "charging_piles")
public class ChargingPileRecord {
    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private ChargeMode mode;
    private int powerKw;
    @Enumerated(EnumType.STRING)
    private PileStatus status;
    private String currentRequestId;

    protected ChargingPileRecord() {
    }

    public ChargingPileRecord(String id, ChargeMode mode, int powerKw, PileStatus status, String currentRequestId) {
        this.id = id;
        this.mode = mode;
        this.powerKw = powerKw;
        this.status = status;
        this.currentRequestId = currentRequestId;
    }

    public String getId() { return id; }
    public ChargeMode getMode() { return mode; }
    public int getPowerKw() { return powerKw; }
    public PileStatus getStatus() { return status; }
    public String getCurrentRequestId() { return currentRequestId; }
}
