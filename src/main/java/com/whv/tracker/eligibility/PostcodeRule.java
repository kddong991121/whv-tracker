package com.whv.tracker.eligibility;

/** 시드 CSV 한 행. postcode가 "ALL"이면 그 주 전체에 일치한다. */
public record PostcodeRule(String postcode, String state, String industry, String table, String condition) {
}
