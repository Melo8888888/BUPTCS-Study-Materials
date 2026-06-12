package com.evcharge.persistence;

import com.evcharge.domain.StationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "station_state")
public class StationStateRecord {
    @Id
    private String id;
    @Column(name = "business_date")
    private LocalDate currentDate;
    private LocalDateTime nowTime;
    private int fastSeq;
    private int slowSeq;
    private int detailSeq;
    private int waitingCapacity;
    private int pileSlotCapacity;
    @Column(name = "fast_pile_count")
    private int fastPileCount;
    @Column(name = "slow_pile_count")
    private int slowPileCount;
    @Column(name = "parking_rate", precision = 10, scale = 4)
    private BigDecimal parkingRate;
    @Column(name = "parking_grace_period_minutes")
    private int parkingGracePeriodMinutes;
    @Enumerated(EnumType.STRING)
    @Column(name = "station_state", length = 16)
    private StationState stationState;

    protected StationStateRecord() {
    }

    public StationStateRecord(String id, LocalDate currentDate, LocalDateTime nowTime,
                              int fastSeq, int slowSeq, int detailSeq,
                              int waitingCapacity, int pileSlotCapacity,
                              int fastPileCount, int slowPileCount,
                              BigDecimal parkingRate, int parkingGracePeriodMinutes,
                              StationState stationState) {
        this.id = id;
        this.currentDate = currentDate;
        this.nowTime = nowTime;
        this.fastSeq = fastSeq;
        this.slowSeq = slowSeq;
        this.detailSeq = detailSeq;
        this.waitingCapacity = waitingCapacity;
        this.pileSlotCapacity = pileSlotCapacity;
        this.fastPileCount = fastPileCount;
        this.slowPileCount = slowPileCount;
        this.parkingRate = parkingRate;
        this.parkingGracePeriodMinutes = parkingGracePeriodMinutes;
        this.stationState = stationState;
    }

    public String getId() { return id; }
    public LocalDate getCurrentDate() { return currentDate; }
    public LocalDateTime getNowTime() { return nowTime; }
    public int getFastSeq() { return fastSeq; }
    public int getSlowSeq() { return slowSeq; }
    public int getDetailSeq() { return detailSeq; }
    public int getWaitingCapacity() { return waitingCapacity; }
    public int getPileSlotCapacity() { return pileSlotCapacity; }
    public int getFastPileCount() { return fastPileCount; }
    public int getSlowPileCount() { return slowPileCount; }
    public BigDecimal getParkingRate() { return parkingRate; }
    public int getParkingGracePeriodMinutes() { return parkingGracePeriodMinutes; }
    public StationState getStationState() { return stationState; }
}
