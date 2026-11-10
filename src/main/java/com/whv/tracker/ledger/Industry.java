package com.whv.tracker.ledger;

/**
 * 업종 임시 목록. F2(포스트코드·업종 판정) 단계에서 시드 기준 목록으로 교체한다.
 * NATURAL_DISASTER_RECOVERY는 F1에서 인정 합계에서 제외한다 (PRD 4.9 규칙 8·9).
 */
public enum Industry {
    TOURISM_HOSPITALITY,
    REGIONAL_PRIMARY_MINING_CONSTRUCTION,
    BUSHFIRE_RECOVERY,
    NATURAL_DISASTER_RECOVERY,
    COVID_HEALTHCARE
}
