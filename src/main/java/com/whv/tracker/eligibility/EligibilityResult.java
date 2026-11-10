package com.whv.tracker.eligibility;

import java.util.List;
import java.util.Set;

/**
 * F2 판정 결과.
 *
 * @param states     포스트코드의 주 후보 (여러 개면 주에 따라 결과가 다를 수 있음)
 * @param conditions 일치한 원문 표의 조건 문장 (중복 제거)
 */
public record EligibilityResult(Status status, Set<String> states, List<String> conditions) {

    public enum Status {
        ELIGIBLE("세컨 인정 지역이에요 (목록 기준)"),
        CONDITIONAL("조건을 확인해야 해요"),
        CONDITION_NOT_MET("신청 날짜 조건이 맞지 않아요"),
        NOT_SPECIFIED_WORK("인정 업무가 아니에요 (작업 종류)"),
        NOT_LISTED("목록에서 찾지 못했어요 (확인 불가)");

        private final String message;

        Status(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public boolean multipleStates() {
        return states.size() > 1;
    }
}
