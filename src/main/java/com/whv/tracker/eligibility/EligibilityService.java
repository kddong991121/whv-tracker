package com.whv.tracker.eligibility;

import com.whv.tracker.eligibility.EligibilityResult.Status;
import com.whv.tracker.ledger.DisasterWorkType;
import com.whv.tracker.ledger.Industry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * F2: 포스트코드 + 업종으로 세컨 인정 지역 여부를 판정한다 (PRD 4.4). 근무 날짜 조건은 문장으로만 보여준다.
 * 목록에 없으면 항상 "확인 불가"다. 인정이라고 단정하지 않는다.
 */
@Service
public class EligibilityService {

    private static final Pattern POSTCODE = Pattern.compile("\\d{4}");
    /** 원문 표 조건의 신청일 기준 (예: "lodged from 2022-03-05", "lodged on or after 2025-04-05"). */
    private static final Pattern LODGED = Pattern.compile("lodged (?:from|on or after) (\\d{4}-\\d{2}-\\d{2})");
    static final String COVID_CONDITION = "Critical COVID-19 work in the healthcare and medical sectors; work from 2020-01-31";

    private final PostcodeRegistry registry;

    public EligibilityService(PostcodeRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param workType     농축산일 때 작업 종류 (다른 업종은 무시, null 허용)
     * @param disasterType 재해 복구일 때 유급/봉사 (미선택이면 조건부)
     * @param lodgeDate    신청 예정일 (null이면 오늘)
     */
    public EligibilityResult check(String postcode, Industry industry, WorkType workType,
                                   DisasterWorkType disasterType, LocalDate lodgeDate) {
        if (postcode == null || !POSTCODE.matcher(postcode).matches()) {
            throw new IllegalArgumentException("포스트코드는 숫자 4자리여야 합니다.");
        }
        if (industry == null) {
            throw new IllegalArgumentException("업종을 선택하세요.");
        }
        Set<String> states = registry.statesFor(postcode);
        if (industry == Industry.COVID_HEALTHCARE) {
            return new EligibilityResult(Status.ELIGIBLE, states, List.of(COVID_CONDITION));
        }
        if (industry == Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION && workType != null && !workType.counts()) {
            return new EligibilityResult(Status.NOT_SPECIFIED_WORK, states, List.of(workType.label()));
        }

        List<PostcodeRule> matches = new ArrayList<>();
        for (PostcodeRule r : registry.rulesFor(postcode)) {
            if (r.industry().equals(industry.name())) {
                matches.add(r);
            }
        }
        for (String state : states) {
            for (PostcodeRule r : registry.allRulesFor(state)) {
                if (r.industry().equals(industry.name())) {
                    matches.add(r);
                }
            }
        }
        if (matches.isEmpty()) {
            return new EligibilityResult(Status.NOT_LISTED, states, List.of());
        }

        LocalDate lodge = lodgeDate == null ? LocalDate.now() : lodgeDate;
        Status best = Status.CONDITION_NOT_MET;
        Set<String> conditions = new LinkedHashSet<>();
        for (PostcodeRule r : matches) {
            conditions.add(r.condition());
            Status s = judge(r, disasterType, lodge);
            if (s.ordinal() < best.ordinal()) {
                best = s;
            }
        }
        return new EligibilityResult(best, states, List.copyOf(conditions));
    }

    /** 행 하나의 판정. Status 순서가 앞일수록 사용자에게 유리한 결과다. */
    private static Status judge(PostcodeRule r, DisasterWorkType disasterType, LocalDate lodge) {
        Matcher m = LODGED.matcher(r.condition());
        if (m.find() && lodge.isBefore(LocalDate.parse(m.group(1)))) {
            return Status.CONDITION_NOT_MET;
        }
        if ("T6".equals(r.table()) && disasterType == null) {
            return Status.CONDITIONAL;
        }
        return Status.ELIGIBLE;
    }
}
