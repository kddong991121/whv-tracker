package com.whv.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeedbackLinkAdviceTest {

    @Test
    void keepsHttpsLink_andHidesBlankOrUnsafeValues() {
        assertThat(new FeedbackLinkAdvice(" https://docs.google.com/forms/d/e/x/viewform ").feedbackUrl())
                .isEqualTo("https://docs.google.com/forms/d/e/x/viewform");
        assertThat(new FeedbackLinkAdvice("").feedbackUrl()).isEmpty();
        assertThat(new FeedbackLinkAdvice(null).feedbackUrl()).isEmpty();
        // 잘못 넣은 값(javascript:, http:)은 링크로 만들지 않는다
        assertThat(new FeedbackLinkAdvice("javascript:alert(1)").feedbackUrl()).isEmpty();
        assertThat(new FeedbackLinkAdvice("http://example.com").feedbackUrl()).isEmpty();
    }
}
