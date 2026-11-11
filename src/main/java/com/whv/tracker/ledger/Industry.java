package com.whv.tracker.ledger;

/**
 * 업종 임시 목록. F2(포스트코드·업종 판정) 단계에서 시드 기준 목록으로 교체한다.
 * NATURAL_DISASTER_RECOVERY는 F1에서 인정 합계에서 제외한다 (PRD 4.9 규칙 8·9).
 */
public enum Industry {
    TOURISM_HOSPITALITY("관광·요식업"),
    REGIONAL_PRIMARY_MINING_CONSTRUCTION("농장·축산·어업·임업·광업·건설"),
    BUSHFIRE_RECOVERY("산불 복구"),
    NATURAL_DISASTER_RECOVERY("재해 복구"),
    COVID_HEALTHCARE("코로나 의료");

    private final String label;

    Industry(String label) {
        this.label = label;
    }

    /** 화면에 보여줄 한국어 이름. 영문 상수 이름을 화면에 그대로 쓰지 않는다. */
    public String label() {
        return label;
    }
}
