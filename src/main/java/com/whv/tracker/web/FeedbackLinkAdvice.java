package com.whv.tracker.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면에 "의견 보내기" 링크 주소를 넣는다.
 * 주소는 서버 환경변수 WHV_FEEDBACK_URL(Google 설문지 응답용 링크)에서 받는다. 비어 있으면 링크를 숨긴다.
 * https:// 로 시작하지 않는 값은 무시한다 (잘못 넣은 값이 링크가 되지 않도록).
 */
@ControllerAdvice
public class FeedbackLinkAdvice {

    private final String feedbackUrl;

    public FeedbackLinkAdvice(@Value("${whv.feedback-url:}") String feedbackUrl) {
        String v = feedbackUrl == null ? "" : feedbackUrl.trim();
        this.feedbackUrl = v.startsWith("https://") ? v : "";
    }

    @ModelAttribute("feedbackUrl")
    public String feedbackUrl() {
        return feedbackUrl;
    }
}
