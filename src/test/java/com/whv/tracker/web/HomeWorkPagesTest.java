package com.whv.tracker.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
}
