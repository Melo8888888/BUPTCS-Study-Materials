package com.evcharge.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "charging_details")
public class ChargingDetailRecord {
    @Id
    private String detailNo;
    private LocalDateTime generatedAt;
    private String pileId;
    private String vehicleId;
    private String requestId;
    private BigDecimal chargedKwh;
    private long durationMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;
    private BigDecimal chargeFee;
    private BigDecimal serviceFee;
    private BigDecimal totalFee;
    @Column(length = 1000)
    private String priceBreakdown;

    protected ChargingDetailRecord() {
    }

    public ChargingDetailRecord(String detailNo, LocalDateTime generatedAt, String pileId, String vehicleId,
                                String requestId, BigDecimal chargedKwh, long durationMinutes,
                                LocalDateTime startedAt, LocalDateTime stoppedAt, BigDecimal chargeFee,
                                BigDecimal serviceFee, BigDecimal totalFee, String priceBreakdown) {
        this.detailNo = detailNo;
        this.generatedAt = generatedAt;
        this.pileId = pileId;
        this.vehicleId = vehicleId;
        this.requestId = requestId;
        this.chargedKwh = chargedKwh;
        this.durationMinutes = durationMinutes;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
        this.chargeFee = chargeFee;
        this.serviceFee = serviceFee;
        this.totalFee = totalFee;
        this.priceBreakdown = priceBreakdown;
    }

    public String getDetailNo() { return detailNo; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getPileId() { return pileId; }
    public String getVehicleId() { return vehicleId; }
    public String getRequestId() { return requestId; }
    public BigDecimal getChargedKwh() { return chargedKwh; }
    public long getDurationMinutes() { return durationMinutes; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public BigDecimal getChargeFee() { return chargeFee; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public BigDecimal getTotalFee() { return totalFee; }
    public String getPriceBreakdown() { return priceBreakdown; }
}
