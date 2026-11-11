package com.whv.tracker.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 근무 기록 화면 smoke 테스트: Thymeleaf 렌더링, 면책 문구, POST 흐름, 바인딩 오류.
 * 모든 요청은 테스트 전용 로그인 사용자 (구글 sub "ctl-sub")와 CSRF 토큰을 기본으로 붙인다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LedgerService ledger;

    @Autowired
    private AppUserService users;

    private MockMvc mvc;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.upsertGoogleUser("ctl-sub", "ctl@example.com", "컨트롤러 테스트");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/")
                        .with(oauth2Login().attributes(a -> a.put("sub", "ctl-sub")))
                        .with(csrf()))
                .build();
    }

    @Test
    void indexRendersDisclaimerAndForms() throws Exception {
        // 모드 B 기록과 근무일 행을 넣어 목록 렌더링 경로도 함께 확인한다
        WorkRecord casual = ledger.addCasual(owner, "렌더 카페", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), 5, null, null, null,
                new BigDecimal("8"), null, false);
        ledger.addWorkDay(owner, casual.getId(), LocalDate.of(2027, 1, 5), new BigDecimal("6.5"));

        mvc.perform(get("/ledger"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(LedgerController.DISCLAIMER)))
                .andExpect(content().string(containsString("매일 출근한 정규 근무")))
                .andExpect(content().string(containsString("렌더 카페")))
                .andExpect(content().string(containsString("2027-01-05")));
    }

    @Test
    void visaPlan_savedAndPaceShown() throws Exception {
        mvc.perform(post("/visa-plan").param("visaExpiry", "2099-01-01").param("bufferDays", "14"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));
        mvc.perform(post("/visa-plan").param("visaExpiry", "2099-01-01").param("bufferDays", "-1"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(get("/ledger"))
                .andExpect(content().string(containsString("일주일에 필요한 인정 일수")));
    }

    @Test
    void employmentPostWithValidDatesRedirectsWithMessage() throws Exception {
        mvc.perform(post("/employment")
                        .param("employer", "웹 테스트 농장")
                        .param("postcode", "4670")
                        .param("industry", "REGIONAL_PRIMARY_MINING_CONSTRUCTION")
                        .param("visaYear", "2")
                        .param("startDate", "2027-01-01")
                        .param("endDate", "2027-03-29")
                        .param("unpaidWeatherDays", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ledger"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void invalidPostShowsErrorFlash() throws Exception {
        mvc.perform(post("/casual")
                        .param("employer", "웹 테스트 카페")
                        .param("postcode", "0000")
                        .param("industry", "TOURISM_HOSPITALITY")
                        .param("visaYear", "2")
                        .param("startDate", "2027-01-01")
                        .param("endDate", "2027-01-10")
                        .param("standardDays", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void badDateFormatRedirectsWithErrorFlash_notBadRequestPage() throws Exception {
        mvc.perform(post("/employment")
                        .param("employer", "형식 오류")
                        .param("postcode", "4670")
                        .param("industry", "TOURISM_HOSPITALITY")
                        .param("visaYear", "2")
                        .param("startDate", "2027-13-45")
                        .param("endDate", "2027-03-29"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ledger"))
                .andExpect(flash().attributeExists("error"));
    }
}
