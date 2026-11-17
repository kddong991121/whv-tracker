package com.whv.tracker.feedback;

import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 앱 안 의견 받기 (PRD 4.12). 로그인과 CSRF 검사를 받는다. */
@Controller
public class FeedbackController {

    private final FeedbackService feedback;
    private final AppUserService users;

    public FeedbackController(FeedbackService feedback, AppUserService users) {
        this.feedback = feedback;
        this.users = users;
    }

    @GetMapping("/feedback")
    public String page(Authentication authentication, Model model) {
        if (users.findByGoogleSubject(authentication.getName()) == null) {
            return "redirect:/login";
        }
        model.addAttribute("maxLength", FeedbackService.MAX_LENGTH);
        return "feedback";
    }

    /** 글만 보내기 (/feedback 화면). */
    @PostMapping("/feedback")
    public String send(Authentication authentication, @RequestParam(required = false) String message,
                       RedirectAttributes redirect) {
        return handle(authentication, null, message, redirect, "/feedback");
    }

    /** 홈의 "쓸 만한가요?" 카드: 👍(1) 또는 👎(-1), 👎는 이유를 같이 보낼 수 있다. */
    @PostMapping("/feedback/rate")
    public String rate(Authentication authentication, @RequestParam int rating,
                       @RequestParam(required = false) String message, RedirectAttributes redirect) {
        return handle(authentication, rating, message, redirect, "/");
    }

    private String handle(Authentication authentication, Integer rating, String message, RedirectAttributes redirect,
                          String back) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        switch (feedback.submit(owner, rating, message)) {
            case OK -> redirect.addFlashAttribute("message", "고맙습니다. 꼼꼼히 읽고 고칠게요.");
            case EMPTY -> redirect.addFlashAttribute("error", "내용을 적어 주세요.");
            case TOO_LONG -> redirect.addFlashAttribute("error", "500자까지 쓸 수 있어요.");
            case TOO_MANY -> redirect.addFlashAttribute("error", "오늘은 이미 여러 번 보내 주셨어요. 내일 또 보내 주세요.");
        }
        return "redirect:" + back;
    }
}
