package com.whv.tracker.security;

import com.whv.tracker.user.AppUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** 구글 로그인 성공 시 계정을 만들거나 갱신하고 홈으로 보낸다. 계정 정보가 부족하면 로그인 페이지로 되돌린다. */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserService users;

    public OAuth2LoginSuccessHandler(AppUserService users) {
        this.users = users;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            OAuth2User principal = token.getPrincipal();
            try {
                users.upsertGoogleUser(principal.getAttribute("sub"), principal.getAttribute("email"),
                        principal.getAttribute("name"));
            } catch (IllegalArgumentException e) {
                response.sendRedirect(request.getContextPath() + "/login?error=account");
                return;
            }
        }
        response.sendRedirect(request.getContextPath() + "/");
    }
}
