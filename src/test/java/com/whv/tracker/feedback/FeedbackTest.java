package com.whv.tracker.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whv.tracker.admin.AdminStatsService;
import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AccountDeletionService;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 앱 안 의견 받기 (PRD 4.12): 저장·한도, 홈 카드 조건, /admin 표시, 탈퇴 시 삭제, 비로그인 차단. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackTest {

    @Autowired private FeedbackService feedback;
    @Autowired private FeedbackRepository repo;
    @Autowired private AppUserService users;
    @Autowired private LedgerService ledger;
    @Autowired private AdminStatsService stats;
    @Autowired private AccountDeletionService deletion;
    @Autowired private WebApplicationContext context;

    @Test
    void submit_savesAndEnforcesLimits() {
        AppUser u = users.upsertGoogleUser("fb1", "fb1@example.com", "의견");
        assertThat(feedback.submit(u, null, "  ")).isEqualTo(FeedbackService.Result.EMPTY);
        assertThat(feedback.submit(u, 7, "")).isEqualTo(FeedbackService.Result.EMPTY); // 이상한 점수는 점수 없음
        assertThat(feedback.submit(u, null, "x".repeat(501))).isEqualTo(FeedbackService.Result.TOO_LONG);
        assertThat(repo.count()).isZero();

        for (int i = 0; i < FeedbackService.DAILY_LIMIT; i++) {
            assertThat(feedback.submit(u, null, "의견 " + i)).isEqualTo(FeedbackService.Result.OK);
        }
        assertThat(feedback.submit(u, null, "여섯 번째")).isEqualTo(FeedbackService.Result.TOO_MANY);
        // 다른 사람은 영향이 없다
        AppUser other = users.upsertGoogleUser("fb2", "fb2@example.com", "다른 사람");
        assertThat(feedback.submit(other, -1, null)).isEqualTo(FeedbackService.Result.OK);
    }

    @Test
    void shouldAskRating_afterFiveWorkDays_untilAnswered() {
        AppUser u = users.upsertGoogleUser("fb3", "fb3@example.com", "질문");
        LocalDate start = LocalDate.of(2026, 10, 1);
        WorkRecord place = ledger.addCasual(u, "농장", "4350", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                start, start.plusMonths(2), 5, null, null, null, new BigDecimal("8"), null, false);
        for (int i = 0; i < 4; i++) {
            ledger.addWorkDay(u, place.getId(), start.plusDays(i), new BigDecimal("8"));
        }
        assertThat(feedback.shouldAskRating(u)).isFalse(); // 4일
        ledger.addWorkDay(u, place.getId(), start.plusDays(4), new BigDecimal("8"));
        assertThat(feedback.shouldAskRating(u)).isTrue();  // 5일
        feedback.submit(u, null, "글만 보냄");
        assertThat(feedback.shouldAskRating(u)).isTrue();  // 점수 없이 글만 보낸 건 답한 게 아니다
        feedback.submit(u, 1, null);
        assertThat(feedback.shouldAskRating(u)).isFalse(); // 답하면 다시 안 묻는다
    }

    @Test
    void adminDetails_showsCountsAndRecentMessages_andDeletionRemovesThem() {
        AppUser u = users.upsertGoogleUser("fb4", "fb4@example.com", "관리");
        feedback.submit(u, 1, null);
        feedback.submit(u, -1, "버튼이 작아요");
        AdminStatsService.Details d = stats.details(LocalDateTime.now());
        assertThat(d.feedbackUp()).isEqualTo(1);
        assertThat(d.feedbackDown()).isEqualTo(1);
        assertThat(d.feedbacks()).extracting(AdminStatsService.FeedbackRow::message).contains("버튼이 작아요");
        assertThat(d.feedbacks()).extracting(AdminStatsService.FeedbackRow::email).contains("fb4@example.com");

        deletion.deleteAccount(u.getId());
        assertThat(repo.count()).isZero();
    }

    @Test
    void web_requiresLogin_andRateRedirectsHome() throws Exception {
        MockMvc anon = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        anon.perform(get("/feedback")).andExpect(status().is3xxRedirection());
        anon.perform(post("/feedback").with(csrf()).param("message", "안녕")).andExpect(status().is3xxRedirection());
        assertThat(repo.count()).isZero();

        users.upsertGoogleUser("fb5", "fb5@example.com", "웹");
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(oauth2Login().attributes(a -> a.put("sub", "fb5"))).with(csrf())).build();
        mvc.perform(get("/feedback")).andExpect(status().isOk())
                .andExpect(content().string(containsString("의견 보내기")));
        mvc.perform(post("/feedback").param("message", "있으면 좋겠어요")).andExpect(redirectedUrl("/feedback"))
                .andExpect(flash().attributeExists("message"));
        mvc.perform(post("/feedback").param("message", " ")).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/feedback/rate").param("rating", "-1").param("message", "불편"))
                .andExpect(redirectedUrl("/")).andExpect(flash().attributeExists("message"));
        assertThat(repo.count()).isEqualTo(2);
        // 구글 폼 링크는 없다
        mvc.perform(get("/")).andExpect(content().string(not(containsString("docs.google.com/forms"))));
    }
}
