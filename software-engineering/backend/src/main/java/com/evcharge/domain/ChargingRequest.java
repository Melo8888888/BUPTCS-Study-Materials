package com.evcharge.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ChargingRequest {
    private final String id;
    private final String vehicleId;
    private ChargeMode mode;
    private BigDecimal requestedKwh;
    private BigDecimal batteryCapacity = BigDecimal.ZERO;
    private String queueNo;
    private RequestStatus status;
    private String pileId;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;
    private BigDecimal chargedKwh = BigDecimal.ZERO;

    public ChargingRequest(String vehicleId, ChargeMode mode, BigDecimal requestedKwh, String queueNo, LocalDateTime createdAt) {
        this(UUID.randomUUID().toString(), vehicleId, mode, requestedKwh, queueNo, createdAt);
    }

    public ChargingRequest(String id, String vehicleId, ChargeMode mode, BigDecimal requestedKwh, String queueNo, LocalDateTime createdAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.mode = mode;
        this.requestedKwh = requestedKwh;
        this.queueNo = queueNo;
        this.createdAt = createdAt;
        this.status = RequestStatus.WAITING_AREA;
    }

    public String getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public ChargeMode getMode() { return mode; }
    public void setMode(ChargeMode mode) { this.mode = mode; }
    public BigDecimal getRequestedKwh() { return requestedKwh; }
    public void setRequestedKwh(BigDecimal requestedKwh) { this.requestedKwh = requestedKwh; }
    public String getQueueNo() { return queueNo; }
    public void setQueueNo(String queueNo) { this.queueNo = queueNo; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public String getPileId() { return pileId; }
    public void setPileId(String pileId) { this.pileId = pileId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(LocalDateTime stoppedAt) { this.stoppedAt = stoppedAt; }
    public BigDecimal getChargedKwh() { return chargedKwh; }
    public void setChargedKwh(BigDecimal chargedKwh) { this.chargedKwh = chargedKwh; }
    public BigDecimal getBatteryCapacity() { return batteryCapacity == null ? BigDecimal.ZERO : batteryCapacity; }
    public void setBatteryCapacity(BigDecimal batteryCapacity) { this.batteryCapacity = batteryCapacity == null ? BigDecimal.ZERO : batteryCapacity; }
}
