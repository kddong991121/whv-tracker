package com.whv.tracker.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whv.tracker.user.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** /admin: 관리자 이메일로 로그인했을 때만 숫자 통계가 보이고, 다른 사람에게는 404. 개인정보는 나오지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "whv.admin-email=Admin@Example.com")
@Transactional
class AdminControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService users;

    @Test
    void onlyAdminSeesStats_withoutPersonalInfo() throws Exception {
        users.upsertGoogleUser("u1", "someone@example.com", "홍길동");
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // 로그인 안 함 → 로그인 화면으로
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        // 일반 사용자 → 404 (화면이 있다는 것도 알리지 않음)
        mvc.perform(get("/admin").with(oauth2Login().attributes(a -> {
            a.put("sub", "u1");
            a.put("email", "someone@example.com");
        }))).andExpect(status().isNotFound());
        // 관리자 (대소문자 무시) → 통계, 다른 사용자의 이메일·이름은 없음
        mvc.perform(get("/admin").with(oauth2Login().attributes(a -> {
                    a.put("sub", "admin-sub");
                    a.put("email", "admin@example.com");
                    a.put("email_verified", true);
                })))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 가입자")))
                .andExpect(content().string(containsString("다시 온 사람")))
                .andExpect(content().string(not(containsString("someone@example.com"))))
                .andExpect(content().string(not(containsString("홍길동"))));
    }
}
