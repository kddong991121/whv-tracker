package com.whv.tracker.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일한 날 근무 1행 (고용주 1곳의 그 날짜 근무). 행마다 저장 시점의 W를 스냅샷으로 가진다. 근무 시간은 하루 판정에 쓴다. */
@Entity
@Table(name = "work_day")
public class WorkDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private WorkRecord record;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "work_hours", precision = 4, scale = 2)
    private BigDecimal workHours;

    @Column(name = "standard_days_snapshot", nullable = false)
    private int standardDays;

    /** 근무를 적은 시각 (재방문 지표용). V11 이전 행은 null. */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected WorkDay() {
    }

    public WorkDay(WorkRecord record, LocalDate workDate, BigDecimal workHours, int standardDays) {
        this.record = record;
        this.workDate = workDate;
        this.workHours = workHours;
        this.standardDays = standardDays;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public WorkRecord getRecord() {
        return record;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public BigDecimal getWorkHours() {
        return workHours;
    }

    public int getStandardDays() {
        return standardDays;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
