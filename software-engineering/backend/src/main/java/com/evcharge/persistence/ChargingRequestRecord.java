package com.evcharge.persistence;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.RequestStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "charging_requests")
public class ChargingRequestRecord {
    @Id
    private String id;
    private String vehicleId;
    @Enumerated(EnumType.STRING)
    private ChargeMode mode;
    private BigDecimal requestedKwh;
    private String queueNo;
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    private String pileId;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;
    private BigDecimal chargedKwh;
    @Enumerated(EnumType.STRING)
    private QueueKind queueKind;
    private int queuePosition;

    protected ChargingRequestRecord() {
    }

    public ChargingRequestRecord(String id, String vehicleId, ChargeMode mode, BigDecimal requestedKwh, String queueNo,
                                 RequestStatus status, String pileId, LocalDateTime createdAt,
                                 LocalDateTime startedAt, LocalDateTime stoppedAt, BigDecimal chargedKwh,
                                 QueueKind queueKind, int queuePosition) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.mode = mode;
        this.requestedKwh = requestedKwh;
        this.queueNo = queueNo;
        this.status = status;
        this.pileId = pileId;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
        this.chargedKwh = chargedKwh;
        this.queueKind = queueKind;
        this.queuePosition = queuePosition;
    }

    public String getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public ChargeMode getMode() { return mode; }
    public BigDecimal getRequestedKwh() { return requestedKwh; }
    public String getQueueNo() { return queueNo; }
    public RequestStatus getStatus() { return status; }
    public String getPileId() { return pileId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public BigDecimal getChargedKwh() { return chargedKwh; }
    public QueueKind getQueueKind() { return queueKind; }
    public int getQueuePosition() { return queuePosition; }
}
