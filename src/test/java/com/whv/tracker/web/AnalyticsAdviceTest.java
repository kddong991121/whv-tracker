package com.whv.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalyticsAdviceTest {

    @Test
    void acceptsOnlyGoatCounterCountUrl() {
        assertThat(new AnalyticsAdvice(" https://kddong99.goatcounter.com/count ").goatcounterUrl())
                .isEqualTo("https://kddong99.goatcounter.com/count");
        assertThat(new AnalyticsAdvice("").goatcounterUrl()).isEmpty();
        assertThat(new AnalyticsAdvice(null).goatcounterUrl()).isEmpty();
        assertThat(new AnalyticsAdvice("https://evil.example.com/count").goatcounterUrl()).isEmpty();
        assertThat(new AnalyticsAdvice("http://kddong99.goatcounter.com/count").goatcounterUrl()).isEmpty();
    }
}
