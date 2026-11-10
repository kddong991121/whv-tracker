package com.whv.tracker.ledger;

import com.whv.tracker.eligibility.EligibilityResult.Status;
import com.whv.tracker.eligibility.WorkType;
import com.whv.tracker.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** 고용 기록 1건. 모드 A는 기간 1건, 모드 B는 고용 기간 + 날짜별 행(WorkDay)을 가진다. */
@Entity
@Table(name = "work_record")
public class WorkRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기록 소유자 (구글 로그인 계정). 모든 조회·저장은 이 값으로 한정한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_mode", nullable = false, length = 20)
    private WorkMode mode;

    @Column(name = "visa_year", nullable = false)
    private int visaYear;

    @Column(name = "employer_name", nullable = false, length = 100)
    private String employerName;

    /** 고용주 비교 키 (trim, 연속 공백 1개, 소문자). 비교는 이 키로만 한다. */
    @Column(name = "employer_key", nullable = false, length = 100)
    private String employerKey;

    @Column(nullable = false, length = 4)
    private String postcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Industry industry;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 모드 A 전용: 무급 날씨 휴무 일수. */
    @Column(name = "unpaid_weather_days", nullable = false)
    private int unpaidWeatherDays;

    /** 모드 B 전용: 기준 주당 정상 근무일 W (1~7). 모드 A는 null. */
    @Column(name = "standard_days")
    private Integer standardDays;

    /** 일한 날 기록 전용: 이 고용주의 평소 하루 근무 시간. 이보다 짧은 날은 짧게 일한 날 (PRD 4.2.3). */
    @Column(name = "normal_hours", precision = 4, scale = 2)
    private BigDecimal normalHours;

    /** 농장 업종 전용: 작업 종류. 가공품 제조·정원 관리는 인정 업무가 아니다 (PRD 4.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", length = 20)
    private WorkType workType;

    /** 저장 시점의 인정 지역 판정 스냅샷 (PRD 4.2.8). null이면 조회 시 다시 판정한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", length = 30)
    private Status eligibilityStatus;

    /** "조건부" 판정을 사용자가 충족했다고 체크했는지. */
    @Column(name = "condition_confirmed", nullable = false)
    private boolean conditionConfirmed;

    /** 재해 복구 업종 전용: 유급/봉사. 미선택(null)이면 조건부로 인정 합계에서 제외. */
    @Enumerated(EnumType.STRING)
    @Column(name = "disaster_work_type", length = 20)
    private DisasterWorkType disasterWorkType;

    protected WorkRecord() {
    }

    public WorkRecord(AppUser owner, WorkMode mode, int visaYear, String employerName, String postcode, Industry industry,
                      LocalDate startDate, LocalDate endDate, int unpaidWeatherDays, Integer standardDays,
                      DisasterWorkType disasterWorkType, BigDecimal normalHours) {
        this.owner = owner;
        this.mode = mode;
        this.visaYear = visaYear;
        this.employerName = employerName;
        this.employerKey = keyOf(employerName);
        this.postcode = postcode;
        this.industry = industry;
        this.startDate = startDate;
        this.endDate = endDate;
        this.unpaidWeatherDays = unpaidWeatherDays;
        this.standardDays = standardDays;
        this.disasterWorkType = disasterWorkType;
        this.normalHours = normalHours;
    }

    /** 고용주 비교 키. 공백 차이는 다른 고용주로 본다 (공백 제거 키는 쓰지 않는다). */
    public static String keyOf(String name) {
        return name.trim().replaceAll("\s+", " ").toLowerCase(Locale.ROOT);
    }

    public AppUser getOwner() {
        return owner;
    }

    public Long getId() {
        return id;
    }

    public WorkMode getMode() {
        return mode;
    }

    public int getVisaYear() {
        return visaYear;
    }

    public String getEmployerName() {
        return employerName;
    }

    public String getEmployerKey() {
        return employerKey;
    }

    public String getPostcode() {
        return postcode;
    }

    public Industry getIndustry() {
        return industry;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getUnpaidWeatherDays() {
        return unpaidWeatherDays;
    }

    public Integer getStandardDays() {
        return standardDays;
    }

    /** 인정 지역 판정 정보를 기록한다 (저장 전에 서비스가 호출). */
    public void setEligibility(WorkType workType, Status status, boolean conditionConfirmed) {
        this.workType = workType;
        this.eligibilityStatus = status;
        this.conditionConfirmed = conditionConfirmed;
    }

    public WorkType getWorkType() {
        return workType;
    }

    public Status getEligibilityStatus() {
        return eligibilityStatus;
    }

    public boolean isConditionConfirmed() {
        return conditionConfirmed;
    }

    public BigDecimal getNormalHours() {
        return normalHours;
    }

    public DisasterWorkType getDisasterWorkType() {
        return disasterWorkType;
    }
}
