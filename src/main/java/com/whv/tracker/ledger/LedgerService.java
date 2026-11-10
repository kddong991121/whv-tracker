package com.whv.tracker.ledger;

import com.whv.tracker.calc.CasualRecord;
import com.whv.tracker.calc.EmploymentPeriod;
import com.whv.tracker.calc.VisaSummary;
import com.whv.tracker.calc.WorkCalculator;
import com.whv.tracker.calc.WorkDayRow;
import com.whv.tracker.user.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 입력 검증과 저장. 계산은 calc 패키지의 WorkCalculator에 맡긴다. 검증 실패는 IllegalArgumentException으로 알린다. */
@Service
public class LedgerService {

    private static final BigDecimal MAX_HOURS = new BigDecimal("24");
    private static final Pattern POSTCODE = Pattern.compile("\\d{4}");
    private static final int MAX_NOTE_LENGTH = 255;

    private final WorkRecordRepository records;
    private final WorkDayRepository days;
    private final WorkStandardChangeRepository changes;

    public LedgerService(WorkRecordRepository records, WorkDayRepository days,
                         WorkStandardChangeRepository changes) {
        this.records = records;
        this.days = days;
        this.changes = changes;
    }

    /**
     * 모드 A: 풀타임 연속 고용 기간 1건.
     * 같은 고용주 키의 다른 모드 A 기간과 겹치거나, 모드 B 근무일이 기간 안에 있으면 거부한다 (PRD 4.9 규칙 2).
     */
    @Transactional
    public WorkRecord addEmployment(AppUser owner, String employer, String postcode, Industry industry, int visaYear,
                                    LocalDate start, LocalDate end, int unpaidDays, DisasterWorkType disasterType) {
        String name = validateCommon(employer, postcode, industry, visaYear, start, end, disasterType);
        long calendarDays = WorkCalculator.calendarDays(start, end);
        if (unpaidDays < 0 || unpaidDays > calendarDays) {
            throw new IllegalArgumentException("무급 날씨 휴무 일수는 0 이상이고 기간 달력일 이하여야 합니다.");
        }
        String key = WorkRecord.keyOf(name);
        for (WorkRecord r : records.findByOwnerAndEmployerKey(owner, key)) {
            if (r.getMode() == WorkMode.EMPLOYMENT_PERIOD && overlaps(r.getStartDate(), r.getEndDate(), start, end)) {
                throw new IllegalArgumentException("같은 고용주의 고용 기간이 겹칩니다 (기록 #" + r.getId() + ").");
            }
        }
        for (WorkDay d : days.findByRecordOwnerAndRecordEmployerKey(owner, key)) {
            if (!d.getWorkDate().isBefore(start) && !d.getWorkDate().isAfter(end)) {
                throw new IllegalArgumentException("같은 고용주의 모드 B 근무일(" + d.getWorkDate() + ")이 이 기간에 있습니다.");
            }
        }
        return records.save(new WorkRecord(owner, WorkMode.EMPLOYMENT_PERIOD, visaYear, name, postcode, industry,
                start, end, unpaidDays, null, disasterType, null));
    }

    /**
     * 모드 B: 고용 기간 1건 (근무일은 addWorkDay로 날짜마다 추가).
     * normalHours는 이 고용주의 평소 하루 근무 시간 (0 초과 24 이하, 소수 둘째 자리까지).
     * W가 기본값 5가 아니면 사유가 필수이며 변경 이력을 남긴다. 사유와 근거 출처는 각각 255자 이내.
     */
    @Transactional
    public WorkRecord addCasual(AppUser owner, String employer, String postcode, Industry industry, int visaYear,
                                LocalDate start, LocalDate end, int standardDays, String reason,
                                String sourceNote, DisasterWorkType disasterType, BigDecimal normalHours) {
        String name = validateCommon(employer, postcode, industry, visaYear, start, end, disasterType);
        validateHours(normalHours, "평소 하루 근무 시간");
        if (standardDays < 1 || standardDays > 7) {
            throw new IllegalArgumentException("기준 주당 근무일 W는 1~7 정수여야 합니다.");
        }
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("변경 사유는 255자 이내여야 합니다.");
        }
        String trimmedSource = sourceNote == null ? "" : sourceNote.trim();
        if (trimmedSource.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("근거 출처는 255자 이내여야 합니다.");
        }
        if (standardDays != WorkCalculator.DEFAULT_STANDARD_DAYS && trimmedReason.isEmpty()) {
            throw new IllegalArgumentException("기준 주당 근무일이 5가 아니면 변경 사유를 입력해야 합니다.");
        }
        WorkRecord saved = records.save(new WorkRecord(owner, WorkMode.CASUAL_DAYS, visaYear, name, postcode, industry,
                start, end, 0, standardDays, disasterType, normalHours));
        if (standardDays != WorkCalculator.DEFAULT_STANDARD_DAYS) {
            changes.save(new WorkStandardChange(saved, WorkCalculator.DEFAULT_STANDARD_DAYS, standardDays,
                    trimmedReason, trimmedSource.isEmpty() ? null : trimmedSource));
        }
        return saved;
    }

    /**
     * 일한 날 근무 1행 추가 (고용주 1곳의 그 날짜 근무).
     * 같은 날짜에 다른 고용주의 행은 추가할 수 있다 (하루 여러 근무). 같은 고용주는 날짜당 1행이다.
     * 같은 날짜 근무 시간 합계가 24시간을 넘으면 거부한다. 근무 시간은 필수이며, 재해 복구 봉사만 생략할 수 있다.
     * 같은 고용주의 정규 근무 기간에 든 날짜는 거부한다.
     */
    @Transactional
    public WorkDay addWorkDay(AppUser owner, long recordId, LocalDate date, BigDecimal hours) {
        WorkRecord record = records.findById(recordId).filter(r -> r.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));
        if (record.getMode() != WorkMode.CASUAL_DAYS) {
            throw new IllegalArgumentException("정규 근무 기록에는 근무일을 추가할 수 없습니다.");
        }
        if (date == null || date.isBefore(record.getStartDate()) || date.isAfter(record.getEndDate())) {
            throw new IllegalArgumentException("근무 날짜가 고용 기간 밖입니다.");
        }
        boolean volunteer = record.getDisasterWorkType() == DisasterWorkType.VOLUNTEER;
        if (hours != null || !volunteer) {
            validateHours(hours, "근무 시간");
        }
        BigDecimal dayTotal = hours == null ? BigDecimal.ZERO : hours;
        for (WorkDay d : days.findByRecordOwnerAndWorkDate(owner, date)) {
            if (d.getRecord().getId().equals(record.getId())) {
                throw new IllegalArgumentException("이 고용주의 " + date + " 근무가 이미 있습니다.");
            }
            if (d.getWorkHours() != null) {
                dayTotal = dayTotal.add(d.getWorkHours());
            }
        }
        if (dayTotal.compareTo(MAX_HOURS) > 0) {
            throw new IllegalArgumentException(date + " 근무 시간 합계가 24시간을 넘습니다.");
        }
        for (WorkRecord r : records.findByOwnerAndEmployerKey(owner, record.getEmployerKey())) {
            if (r.getMode() == WorkMode.EMPLOYMENT_PERIOD && overlaps(r.getStartDate(), r.getEndDate(), date, date)) {
                throw new IllegalArgumentException("같은 고용주의 모드 A 기간(기록 #" + r.getId() + ")에 든 날짜입니다.");
            }
        }
        return days.save(new WorkDay(record, date, hours, record.getStandardDays()));
    }

    /** 화면용 묶음: 기록 목록, 기록별 근무일 행, 차수별 누적 결과 (2차, 3차). */
    @Transactional(readOnly = true)
    public Dashboard dashboard(AppUser owner) {
        List<WorkRecord> all = records.findAllByOwnerOrderByIdDesc(owner);
        Map<Long, List<WorkDay>> daysByRecord = days.findByRecordOwner(owner).stream()
                .collect(Collectors.groupingBy(d -> d.getRecord().getId()));

        List<EmploymentPeriod> employment = records.findByOwnerAndMode(owner, WorkMode.EMPLOYMENT_PERIOD).stream()
                .map(r -> new EmploymentPeriod(r.getId(), r.getVisaYear(), r.getStartDate(), r.getEndDate(),
                        r.getUnpaidWeatherDays(), countable(r)))
                .toList();
        List<CasualRecord> casual = records.findByOwnerAndMode(owner, WorkMode.CASUAL_DAYS).stream()
                .map(r -> new CasualRecord(r.getId(), r.getVisaYear(), r.getStartDate(), r.getEndDate(),
                        countable(r), r.getDisasterWorkType() == DisasterWorkType.VOLUNTEER,
                        daysByRecord.getOrDefault(r.getId(), List.of()).stream()
                                .map(d -> new WorkDayRow(d.getWorkDate(), d.getStandardDays(), isFull(r, d)))
                                .toList()))
                .toList();

        List<VisaSummary> summaries = List.of(
                WorkCalculator.summarize(2, employment, casual),
                WorkCalculator.summarize(3, employment, casual));
        return new Dashboard(all, daysByRecord, summaries);
    }

    /**
     * 재해 복구 업종은 유급/봉사 유형을 골랐을 때만 인정한다. 미선택이면 조건부로 인정 합계에서 제외한다.
     * 그 외 업종은 항상 인정 대상이다.
     */
    private static boolean countable(WorkRecord r) {
        return r.getIndustry() != Industry.NATURAL_DISASTER_RECOVERY || r.getDisasterWorkType() != null;
    }

    /** 하루 판정: 근무 시간이 그 고용주의 평소 하루 시간 이상이면 true. 봉사는 시간과 무관하게 true. */
    private static boolean isFull(WorkRecord r, WorkDay d) {
        if (r.getDisasterWorkType() == DisasterWorkType.VOLUNTEER) {
            return true;
        }
        return d.getWorkHours() != null && r.getNormalHours() != null
                && d.getWorkHours().compareTo(r.getNormalHours()) >= 0;
    }

    private static void validateHours(BigDecimal hours, String label) {
        if (hours == null || hours.signum() <= 0 || hours.compareTo(MAX_HOURS) > 0) {
            throw new IllegalArgumentException(label + "은(는) 0 초과 24 이하여야 합니다.");
        }
        if (hours.scale() > 2) {
            throw new IllegalArgumentException(label + "은(는) 소수 둘째 자리까지 입력할 수 있습니다.");
        }
    }

    private static boolean overlaps(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !e1.isBefore(s2) && !s1.isAfter(e2);
    }

    /** 공통 검증. 고용주명은 trim과 연속 공백 축약을 거친 값을 돌려준다 (비교는 WorkRecord.keyOf). */
    private static String validateCommon(String employer, String postcode, Industry industry, int visaYear,
                                         LocalDate start, LocalDate end, DisasterWorkType disasterType) {
        String name = employer == null ? "" : employer.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("고용주명은 1~100자로 입력하세요.");
        }
        if (postcode == null || !POSTCODE.matcher(postcode).matches() || "0000".equals(postcode)) {
            throw new IllegalArgumentException("포스트코드는 0이 아닌 숫자 4자리여야 합니다.");
        }
        if (industry == null) {
            throw new IllegalArgumentException("업종을 선택하세요.");
        }
        if (disasterType != null && industry != Industry.NATURAL_DISASTER_RECOVERY) {
            throw new IllegalArgumentException("유급/봉사 구분은 재해 복구 업종에서만 선택할 수 있습니다.");
        }
        if (visaYear != 2 && visaYear != 3) {
            throw new IllegalArgumentException("귀속 차수는 2차 또는 3차여야 합니다.");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("시작일과 종료일을 입력하세요.");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다.");
        }
        return name;
    }

    public record Dashboard(List<WorkRecord> records, Map<Long, List<WorkDay>> daysByRecord,
                            List<VisaSummary> summaries) {
    }
}
