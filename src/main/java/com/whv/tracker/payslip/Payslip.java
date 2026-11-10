package com.whv.tracker.payslip;

import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 페이슬립에서 읽은 값 1건 (원본 파일은 저장하지 않는다). */
@Entity
@Table(name = "payslip")
public class Payslip {

    /** 증빙 체크리스트 항목 (PRD 4.6.1). 원문에 없는 자체 점검 항목이다. */
    public static final String[] CHECKLIST = {
            "고용주 상호명, ABN이 맞아요",
            "내 이름이 여권 영문명과 같아요",
            "급여 기간이 적혀 있어요",
            "시급(또는 피스레이트 단가)과 총 근무 시간이 적혀 있어요",
            "세금(PAYG) 공제 내역이 있어요",
            "연금(Superannuation) 적립 내역이 있어요",
            "실제 입금액이 Net pay와 같아요"};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private WorkRecord record;

    @Column(name = "employer_name", nullable = false, length = 100)
    private String employerName;

    @Column(length = 11)
    private String abn;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "employment_type", length = 30)
    private String employmentType;

    @Column(name = "worked_days", nullable = false)
    private int workedDays;

    @Column(nullable = false)
    private int checklist;

    @Column(name = "cash_paid", nullable = false)
    private boolean cashPaid;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Payslip() {
    }

    public Payslip(AppUser owner, WorkRecord record, String employerName, String abn, LocalDate periodStart,
                   LocalDate periodEnd, BigDecimal totalHours, String employmentType, int workedDays,
                   int checklist, boolean cashPaid) {
        this.owner = owner;
        this.record = record;
        this.employerName = employerName;
        this.abn = abn;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalHours = totalHours;
        this.employmentType = employmentType;
        this.workedDays = workedDays;
        this.checklist = checklist;
        this.cashPaid = cashPaid;
        this.createdAt = LocalDateTime.now();
    }

    /** 체크리스트 완성도 (켜진 항목 수). */
    public int score() {
        return Integer.bitCount(checklist);
    }

    public Long getId() { return id; }
    public WorkRecord getRecord() { return record; }
    public String getEmployerName() { return employerName; }
    public String getAbn() { return abn; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getTotalHours() { return totalHours; }
    public String getEmploymentType() { return employmentType; }
    public int getWorkedDays() { return workedDays; }
    public int getChecklist() { return checklist; }
    public boolean isCashPaid() { return cashPaid; }
}
