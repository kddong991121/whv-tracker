package com.whv.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.whv.tracker.user.AppUserRepository;
import com.whv.tracker.user.AppUserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 1단계 인증 기반: 공개·보호 경로, CSRF, 계정 연결, users 테이블 Flyway 적용. 구글 실제 호출은 하지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserService users;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publicHome_isOk_andShowsLoginLink() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("구글로 로그인")));
    }

    @Test
    void checkPage_isPublic_andShowsResult() throws Exception {
        mvc.perform(get("/check").param("postcode", "4870").param("industry", "REGIONAL_PRIMARY_MINING_CONSTRUCTION"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("세컨 인정 지역이에요")));
    }

    @Test
    void protectedPage_redirectsAnonymousToLogin() throws Exception {
        mvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void unknownPath_isProtectedToo() throws Exception {
        mvc.perform(get("/no-such-page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPage_isPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")));
    }

    @Test
    void postWithoutCsrfToken_isRejected_evenWhenLoggedIn() throws Exception {
        mvc.perform(post("/logout").with(oauth2Login().attributes(a -> a.put("sub", "csrf-sub"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrfToken_isAccepted() throws Exception {
        mvc.perform(post("/logout").with(oauth2Login().attributes(a -> a.put("sub", "csrf-sub")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void homeForm_containsCsrfToken_whenLoggedIn() throws Exception {
        mvc.perform(get("/").with(oauth2Login().attributes(a -> a.put("sub", "form-sub"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void accountPage_showsGoogleAccountWhenLoggedIn() throws Exception {
        users.upsertGoogleUser("acc-sub", "acc@example.com", "홍길동");

        mvc.perform(get("/account").with(oauth2Login().attributes(a -> {
                    a.put("sub", "acc-sub");
                    a.put("email", "acc@example.com");
                    a.put("name", "홍길동");
                })))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("홍길동")))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void upsert_createsOnce_andRefreshesOnRelogin() {
        users.upsertGoogleUser("same-sub", "old@example.com", "옛 이름");
        users.upsertGoogleUser("same-sub", "new@example.com", "새 이름");

        Integer count = jdbc.queryForObject("select count(*) from users where google_subject = 'same-sub'", Integer.class);
        assertThat(count).isEqualTo(1);
        assertThat(userRepository.findByGoogleSubject("same-sub").orElseThrow().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void missingEmail_isRejectedWithoutCreatingAccount() {
        assertThatThrownBy(() -> users.upsertGoogleUser("no-mail-sub", null, "이름"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일");
        assertThat(userRepository.findByGoogleSubject("no-mail-sub")).isEmpty();
    }

    @Test
    void usersTable_isCreatedByFlyway_withoutPasswordColumn() {
        var columns = jdbc.queryForList(
                "select lower(column_name) as c from information_schema.columns where lower(table_name) = 'users'");
        var names = columns.stream().map(m -> (String) m.get("c")).toList();

        assertThat(names).contains("google_subject", "email", "display_name", "created_at");
        assertThat(names).doesNotContain("password", "password_hash");
    }
}
