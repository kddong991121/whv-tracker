package com.whv.tracker.web;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * CSRF 토큰을 화면을 그리기 전에 미리 만든다.
 * Spring Security 6은 토큰을 필요할 때 늦게 만들어 세션에 넣는데, 세션을 DB(Spring Session JDBC)에 두면
 * 화면 출력이 이미 시작된 뒤에는 세션을 만들 수 없어 오류가 난다 ("Cannot create a session after the response
 * has been committed"). 컨트롤러 단계에서 토큰 값을 한 번 읽어 두면 세션이 먼저 만들어진다.
 */
@ControllerAdvice
public class CsrfEagerAdvice {

    @ModelAttribute
    public void loadCsrfToken(CsrfToken token) {
        if (token != null) {
            token.getToken();
        }
    }
}
