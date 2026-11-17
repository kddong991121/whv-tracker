package com.whv.tracker.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 전용 통계 화면 /admin. 관리자 이메일(서버 환경변수 WHV_ADMIN_EMAIL)로 로그인했을 때만 열린다.
 * 그 밖의 사람에게는 화면이 있다는 것도 알리지 않도록 404를 돌려준다. 설정이 비어 있으면 아무도 못 연다.
 */
@Controller
public class AdminController {

    private final AdminStatsService stats;
    private final String adminEmail;

    public AdminController(AdminStatsService stats, @Value("${whv.admin-email:}") String adminEmail) {
        this.stats = stats;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
    }

    @GetMapping("/admin")
    public String admin(Authentication authentication, Model model, HttpServletResponse response) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // 가입자 이메일이 들어 있으니 브라우저·중간 서버에 저장하지 않고, 검색에도 안 나오게 한다 (PRD 4.9c)
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("stats", stats.stats(now));
        model.addAttribute("details", stats.details(now));
        return "admin";
    }

    boolean isAdmin(Authentication authentication) {
        if (adminEmail.isEmpty() || !(authentication instanceof OAuth2AuthenticationToken token)) {
            return false;
        }
        Object email = token.getPrincipal().getAttribute("email");
        Object verified = token.getPrincipal().getAttribute("email_verified");
        return email != null && adminEmail.equals(email.toString().trim().toLowerCase())
                && !Boolean.FALSE.equals(verified);
    }
}
