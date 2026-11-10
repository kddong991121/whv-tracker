package com.whv.tracker.eligibility;

/** 농축산 작업 종류 (PRD 4.3). 가공품 제조와 일반 정원 관리는 원문상 인정 업무가 아니다. */
public enum WorkType {
    HARVEST_PACKING("수확·포장 (인정)"),
    MANUFACTURING("가공품 제조 (불인정)"),
    GARDENING("정원 관리 (불인정)");

    private final String label;

    WorkType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean counts() {
        return this == HARVEST_PACKING;
    }
}
