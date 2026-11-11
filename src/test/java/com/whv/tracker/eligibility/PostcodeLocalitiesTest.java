package com.whv.tracker.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostcodeLocalitiesTest {

    private final PostcodeLocalities localities = new PostcodeLocalities();

    @Test
    void findsMainTownAndShortSummary() {
        PostcodeLocalities.Locality l = localities.find("4350").orElseThrow();
        assertThat(l.main()).isEqualTo("Toowoomba");
        assertThat(l.state()).isEqualTo("QLD");
        assertThat(l.shown()).hasSize(3).allMatch(n -> n.contains("Toowoomba"));
        assertThat(l.summary()).endsWith("곳");

        PostcodeLocalities.Locality single = localities.find("3500").orElseThrow();
        assertThat(single.summary()).isEqualTo("Mildura"); // 하나뿐이면 "외 N곳"이 없다
    }

    @Test
    void emptyForBadOrUnknownPostcode() {
        assertThat(localities.find("43")).isEmpty();
        assertThat(localities.find("abcd")).isEmpty();
        assertThat(localities.find(null)).isEmpty();
        assertThat(localities.find("0001")).isEmpty();
    }
}
