package com.evcharge.domain;

import java.util.ArrayList;
import java.util.List;

public class ChargingPile {
    private final String id;
    private final ChargeMode mode;
    private final int powerKw;
    private PileStatus status = PileStatus.WORKING;
    private String currentRequestId;
    private final List<String> queue = new ArrayList<>();

    public ChargingPile(String id, ChargeMode mode) {
        this.id = id;
        this.mode = mode;
        this.powerKw = mode.powerKw();
    }

    public String getId() { return id; }
    public ChargeMode getMode() { return mode; }
    public int getPowerKw() { return powerKw; }
    public PileStatus getStatus() { return status; }
    public void setStatus(PileStatus status) { this.status = status; }
    public String getCurrentRequestId() { return currentRequestId; }
    public void setCurrentRequestId(String currentRequestId) { this.currentRequestId = currentRequestId; }
    public List<String> getQueue() { return queue; }
    public boolean isWorking() { return status == PileStatus.WORKING; }
    public int occupiedSlots() { return (currentRequestId == null ? 0 : 1) + queue.size(); }
    public boolean hasCapacity(int maxSlots) { return isWorking() && occupiedSlots() < maxSlots; }
}
