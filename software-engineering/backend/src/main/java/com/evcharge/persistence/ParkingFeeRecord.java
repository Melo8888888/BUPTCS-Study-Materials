package com.evcharge.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parking_fees")
public class ParkingFeeRecord {
    @Id
    private String id;

    @Column(name = "vehicle_id", nullable = false, length = 64)
    private String vehicleId;

    @Column(name = "pile_id", length = 16)
    private String pileId;

    @Column(name = "bill_detail_no", nullable = false, unique = true, length = 64)
    private String billDetailNo;

    @Column(name = "over_time_minutes", nullable = false)
    private long overTimeMinutes;

    @Column(name = "rate_per_minute", nullable = false, precision = 10, scale = 4)
    private BigDecimal ratePerMinute;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    protected ParkingFeeRecord() {
    }

    public ParkingFeeRecord(String id, String vehicleId, String pileId, String billDetailNo,
                            long overTimeMinutes, BigDecimal ratePerMinute, BigDecimal amount,
                            LocalDateTime generatedAt, boolean paid, LocalDateTime paidAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.pileId = pileId;
        this.billDetailNo = billDetailNo;
        this.overTimeMinutes = overTimeMinutes;
        this.ratePerMinute = ratePerMinute;
        this.amount = amount;
        this.generatedAt = generatedAt;
        this.paid = paid;
        this.paidAt = paidAt;
    }

    public String getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public String getPileId() { return pileId; }
    public String getBillDetailNo() { return billDetailNo; }
    public long getOverTimeMinutes() { return overTimeMinutes; }
    public BigDecimal getRatePerMinute() { return ratePerMinute; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public boolean isPaid() { return paid; }
    public LocalDateTime getPaidAt() { return paidAt; }

    public void markPaid(LocalDateTime when) {
        this.paid = true;
        this.paidAt = when;
    }
}
