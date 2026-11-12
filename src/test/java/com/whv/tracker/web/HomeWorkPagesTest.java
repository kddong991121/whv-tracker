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

/** 시안 기준 화면 (홈, 근무 기록) 필수 렌더링과 근무 추가·삭제. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HomeWorkPagesTest {

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
        owner = users.upsertGoogleUser("page-sub", "page@example.com", "화면 테스트");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(oauth2Login().attributes(a -> a.put("sub", "page-sub"))).with(csrf()))
                .build();
    }

    @Test
    void home_showsProgressAndShortDays() throws Exception {
        LocalDate today = LocalDate.now();
        WorkRecord a = ledger.addCasual(owner, "오전 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                today.minusDays(10), today, 5, null, null, null, new BigDecimal("8"), null, false);
        WorkRecord b = ledger.addCasual(owner, "오후 포장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                today.minusDays(10), today, 5, null, null, null, new BigDecimal("8"), null, false);
        ledger.addWorkDay(owner, a.getId(), today.minusDays(1), new BigDecimal("8"));
        ledger.addWorkDay(owner, a.getId(), today.minusDays(2), new BigDecimal("4"));
        ledger.addWorkDay(owner, b.getId(), today.minusDays(2), new BigDecimal("4"));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("세컨 비자까지")))
                .andExpect(content().string(containsString("짧게 일한 날이 1일 있어요")))
                .andExpect(content().string(containsString("오전 농장")))
                .andExpect(content().string(containsString(HomeController.DISCLAIMER)));
    }

    @Test
    void quickShift_today_thenShowsDone() throws Exception {
        LocalDate today = LocalDate.now();
        WorkRecord r = ledger.addCasual(owner, "원탭 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                today.minusDays(20), today.plusDays(20), 5, null, null, null, new BigDecimal("7.6"), null, false);
        // 근무를 한 번도 안 넣었으면 원탭 버튼이 없다
        mvc.perform(get("/")).andExpect(content().string(org.hamcrest.Matchers.not(containsString("/work/today"))));

        ledger.addWorkDay(owner, r.getId(), today.minusDays(1), new BigDecimal("7.6"));
        mvc.perform(get("/")).andExpect(content().string(containsString("/work/today")))
                .andExpect(content().string(containsString("7.6시간")));

        mvc.perform(post("/work/today").param("recordId", String.valueOf(r.getId())))
                .andExpect(redirectedUrl("/")).andExpect(flash().attribute("message", "오늘 근무를 넣었어요 ✓"));
        mvc.perform(get("/")).andExpect(content().string(containsString("기록 완료 ✓")));
        // 같은 날 또 누르면 거부 (이미 있음)
        mvc.perform(post("/work/today").param("recordId", String.valueOf(r.getId())))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void copyPreviousWeek_copiesAndSkipsExisting() throws Exception {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        WorkRecord r = ledger.addCasual(owner, "복사 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                monday.minusDays(14), monday.plusDays(14), 5, null, null, null, new BigDecimal("8"), null, false);
        ledger.addWorkDay(owner, r.getId(), monday.minusDays(7), new BigDecimal("8")); // 지난주 월
        ledger.addWorkDay(owner, r.getId(), monday.minusDays(6), new BigDecimal("6")); // 지난주 화
        ledger.addWorkDay(owner, r.getId(), monday, new BigDecimal("8"));              // 이번 주 월은 이미 있음

        mvc.perform(post("/work/copy-week").param("week", monday.plusDays(2).toString()))
                .andExpect(redirectedUrl("/work?week=" + monday))
                .andExpect(flash().attribute("message", "1일 넣었어요 · 1일 건너뜀 (이미 있거나 넣을 수 없는 날)"));
        org.assertj.core.api.Assertions.assertThat(
                ledger.dashboard(owner).records().stream().filter(x -> x.getId().equals(r.getId())).count()).isEqualTo(1);
    }

    @Test
    void deleteShift_offersUndo_withSameValues() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 10);
        WorkRecord r = ledger.addCasual(owner, "되돌리기 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                day.minusDays(5), day.plusDays(5), 5, null, null, null, new BigDecimal("8"), null, false);
        long id = ledger.addWorkDay(owner, r.getId(), day, new BigDecimal("6.5")).getId();

        mvc.perform(post("/work/shift/" + id + "/delete").param("week", day.toString()))
                .andExpect(flash().attribute("undo", new com.whv.tracker.ledger.LedgerService.DeletedShift(
                        r.getId(), day, new BigDecimal("6.5"))));
        // 되돌리기 = 같은 값으로 다시 넣기
        mvc.perform(post("/work/shift").param("recordId", String.valueOf(r.getId()))
                        .param("workDate", day.toString()).param("workHours", "6.5"))
                .andExpect(flash().attribute("message", "근무를 추가했어요."));
    }

    @Test
    void goalVisa_third_switchesHomeAndNewPlaces() throws Exception {
        LocalDate today = LocalDate.now();
        ledger.addCasual(owner, "세컨 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                today.minusDays(10), today, 5, null, null, null, new BigDecimal("8"), null, false);

        // 잘못된 값은 거부하고 기본값(세컨)을 유지한다
        mvc.perform(post("/account/goal-visa").param("goalVisa", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        mvc.perform(post("/account/goal-visa").param("goalVisa", "3"))
                .andExpect(status().is3xxRedirection());

        // 홈은 서드 기준(179일)이고, 세컨에 넣은 일터는 보이지 않는다
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("서드 비자 준비 중")))
                .andExpect(content().string(containsString("/ 179일")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("세컨 농장"))));

        // 근무 기록 화면에서 새로 넣는 일터는 서드(3)로 저장된다
        mvc.perform(post("/work/place").param("employer", "서드 농장").param("postcode", "4670")
                        .param("industry", "REGIONAL_PRIMARY_MINING_CONSTRUCTION").param("normalHours", "8")
                        .param("startDate", today.minusDays(5).toString()).param("endDate", today.toString()))
                .andExpect(status().is3xxRedirection());
        WorkRecord added = ledger.dashboard(owner).records().stream()
                .filter(r -> r.getEmployerName().equals("서드 농장")).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(added.getVisaYear()).isEqualTo(3);
    }

    @Test
    void work_addAndDeleteShift() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 5);
        WorkRecord a = ledger.addCasual(owner, "그린밸리", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                day.minusDays(3), day.plusDays(3), 5, null, null, null, new BigDecimal("8"), null, false);

        mvc.perform(post("/work/shift").param("recordId", a.getId().toString())
                        .param("workDate", day.toString()).param("workHours", "4"))
                .andExpect(flash().attributeExists("message"));
        mvc.perform(get("/work").param("week", day.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("그린밸리")))
                .andExpect(content().string(containsString("짧게 일한 날")));

        long id = ledger.dashboard(owner).daysByRecord().get(a.getId()).get(0).getId();
        mvc.perform(post("/work/shift/" + id + "/delete").param("week", day.toString()))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void payslipPage_rendersWithoutFileUploadField() throws Exception {
        ledger.addCasual(owner, "페이슬립 농장", "4670", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 5, null, null, null, new BigDecimal("8"), null, false);

        mvc.perform(get("/payslip"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("원본 파일은 서버로 보내지 않아요")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("multipart"))));
    }
}
