package com.whv.tracker.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 기준 주당 근무일 W를 기본값(5)에서 바꿀 때 남기는 이력 1건 (사유 필수, 근거 출처 선택). */
@Entity
@Table(name = "work_standard_change")
public class WorkStandardChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private WorkRecord record;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "previous_days", nullable = false)
    private int previousDays;

    @Column(name = "new_days", nullable = false)
    private int newDays;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "source_note", length = 255)
    private String sourceNote;

    protected WorkStandardChange() {
    }

    public WorkStandardChange(WorkRecord record, int previousDays, int newDays, String reason, String sourceNote) {
        this.record = record;
        this.changedAt = LocalDateTime.now();
        this.previousDays = previousDays;
        this.newDays = newDays;
        this.reason = reason;
        this.sourceNote = sourceNote;
    }

    public Long getId() {
        return id;
    }

    public int getPreviousDays() {
        return previousDays;
    }

    public int getNewDays() {
        return newDays;
    }

    public String getReason() {
        return reason;
    }

    public String getSourceNote() {
        return sourceNote;
    }
}
