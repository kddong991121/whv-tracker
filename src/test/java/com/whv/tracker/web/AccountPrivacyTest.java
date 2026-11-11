package com.whv.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.whv.tracker.ledger.WorkRecordRepository;
import com.whv.tracker.payslip.PayslipService;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 개인정보처리방침 공개, 탈퇴 시 모든 기록 삭제. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountPrivacyTest {

    @Autowired private WebApplicationContext context;
    @Autowired private LedgerService ledger;
    @Autowired private PayslipService payslips;
    @Autowired private AppUserService users;
    @Autowired private WorkRecordRepository records;

    @Test
    void privacyPage_isPublic() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/privacy")).andExpect(status().isOk())
                .andExpect(content().string(containsString("페이슬립 원본 파일")));
    }

    @Test
    void deleteAccount_requiresConfirmWord_thenRemovesEverything() throws Exception {
        AppUser owner = users.upsertGoogleUser("del-sub", "del@example.com", "탈퇴");
        AppUser other = users.upsertGoogleUser("keep-sub", "keep@example.com", "남는 사람");
        LocalDate mon = LocalDate.of(2026, 11, 2);
        WorkRecord r = ledger.addCasual(owner, "농장", "4870", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                mon, mon.plusDays(10), 5, null, null, null, new BigDecimal("8"), null, false);
        payslips.importPayslip(owner, new PayslipService.Import(r.getId(), "농장", null, mon, mon.plusDays(6),
                new BigDecimal("16"), null, List.of(mon, mon.plusDays(1)), List.of(), false, true));
        ledger.addEmployment(other, "남는 농장", "4870", Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                mon, mon.plusDays(10), 0, null, null, false);

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(oauth2Login().attributes(a -> a.put("sub", "del-sub"))).with(csrf()))
                .build();
        mvc.perform(post("/account/delete").param("confirm", "아니오")).andExpect(flash().attributeExists("error"));
        assertThat(users.findByGoogleSubject("del-sub")).isNotNull();

        mvc.perform(post("/account/delete").param("confirm", "탈퇴")).andExpect(redirectedUrl("/?deleted"));
        assertThat(users.findByGoogleSubject("del-sub")).isNull();
        assertThat(records.findAllByOwnerOrderByIdDesc(other)).hasSize(1);
    }
}
