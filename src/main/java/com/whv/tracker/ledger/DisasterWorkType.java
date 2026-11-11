package com.whv.tracker.ledger;

/** 재해 복구 업종의 신청 유형 (PRD 4.9 규칙 8). 미선택이면 조건부로 인정 합계에서 제외한다. */
public enum DisasterWorkType {
    PAID("유급"),
    VOLUNTEER("봉사");

    private final String label;

    DisasterWorkType(String label) {
        this.label = label;
    }

    /** 화면에 보여줄 한국어 이름. */
    public String label() {
        return label;
    }
}
