package com.whv.tracker.eligibility;

/**
 * 포스트코드 번호대 → 주 (PRD 4.3a 표). 공식 원문이 아닌 호주 우편번호 번호대 체계 (조사일 2026-11-10).
 * 시드 CSV에 없는 포스트코드에만 쓴다. 2899 (Norfolk Island)는 NSW 번호대에 포함된다.
 */
final class StateRanges {

    private static final Object[][] RANGES = {
            {800, 999, "NT"},
            {1000, 2599, "NSW"},
            {2600, 2618, "ACT"},
            {2619, 2899, "NSW"},
            {2900, 2920, "ACT"},
            {2921, 2999, "NSW"},
            {3000, 3999, "VIC"},
            {4000, 4999, "QLD"},
            {5000, 5999, "SA"},
            {6000, 6797, "WA"},
            {6800, 6999, "WA"},
            {7000, 7999, "TAS"},
            {8000, 8999, "VIC"},
            {9000, 9999, "QLD"},
    };

    private StateRanges() {
    }

    /** 번호대에 없으면 null (예: 6798, 6799는 표에 없음 → 확인 불가). */
    static String stateOf(int postcode) {
        for (Object[] r : RANGES) {
            if (postcode >= (int) r[0] && postcode <= (int) r[1]) {
                return (String) r[2];
            }
        }
        return null;
    }
}
