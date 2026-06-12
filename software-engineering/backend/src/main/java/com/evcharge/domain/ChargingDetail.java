package com.evcharge.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ChargingDetail {
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
    private String priceBreakdown;
    private RequestStatus status;

    public String getDetailNo() { return detailNo; }
    public void setDetailNo(String detailNo) { this.detailNo = detailNo; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getPileId() { return pileId; }
    public void setPileId(String pileId) { this.pileId = pileId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public BigDecimal getChargedKwh() { return chargedKwh; }
    public void setChargedKwh(BigDecimal chargedKwh) { this.chargedKwh = chargedKwh; }
    public long getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(long durationMinutes) { this.durationMinutes = durationMinutes; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(LocalDateTime stoppedAt) { this.stoppedAt = stoppedAt; }
    public BigDecimal getChargeFee() { return chargeFee; }
    public void setChargeFee(BigDecimal chargeFee) { this.chargeFee = chargeFee; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
    public BigDecimal getTotalFee() { return totalFee; }
    public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }
    public String getPriceBreakdown() { return priceBreakdown; }
    public void setPriceBreakdown(String priceBreakdown) { this.priceBreakdown = priceBreakdown; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
}
