package com.whv.tracker.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 방문 통계 (GoatCounter) 주소를 모든 화면에 넣는다. 쿠키를 쓰지 않고 개인 식별 정보를 모으지 않는다.
 * 주소는 서버 환경변수 WHV_GOATCOUNTER_URL (예: https://kddong99.goatcounter.com/count)에서 받는다.
 * 비어 있거나 GoatCounter 주소 형식이 아니면 스크립트를 넣지 않는다 (로컬·테스트·데모는 집계 안 함).
 */
@ControllerAdvice
public class AnalyticsAdvice {

    private static final String PATTERN = "https://[a-z0-9-]+\\.goatcounter\\.com/count";

    private final String goatcounterUrl;

    public AnalyticsAdvice(@Value("${whv.goatcounter-url:}") String goatcounterUrl) {
        String v = goatcounterUrl == null ? "" : goatcounterUrl.trim();
        this.goatcounterUrl = v.matches(PATTERN) ? v : "";
    }

    @ModelAttribute("goatcounterUrl")
    public String goatcounterUrl() {
        return goatcounterUrl;
    }
}
