package com.whv.tracker.web;

import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 공개 화면 (홈, 로그인) 과 로그인 필요 화면 (계정). */
@Controller
public class HomeController {

    public static final String DISCLAIMER = "법률·이민 자문이 아니며, 최종 판단은 호주 내무부 공식 안내를 확인하세요.";

    private final AppUserService users;

    public HomeController(AppUserService users) {
        this.users = users;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("loggedIn", isLoggedIn(authentication));
        model.addAttribute("disclaimer", DISCLAIMER);
        return "index";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error);
        return "login";
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        AppUser user = users.findByGoogleSubject(authentication.getName());
        model.addAttribute("user", user);
        model.addAttribute("disclaimer", DISCLAIMER);
        return "account";
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }
}
