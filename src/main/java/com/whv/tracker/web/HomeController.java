package com.whv.tracker.web;

import com.whv.tracker.calc.PacePlan;
import com.whv.tracker.calc.VisaSummary;
import com.whv.tracker.calc.WorkCalculator;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.LedgerService.DayKind;
import com.whv.tracker.ledger.WorkDay;
import com.whv.tracker.ledger.WorkMode;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 홈 (/): 로그아웃 상태는 소개 화면, 로그인 상태는 시안(docs/design/Main.dc.html) 기준 진행 화면.
 * 로그인 (/login), 계정 (/account).
 */
@Controller
public class HomeController {

    public static final String DISCLAIMER = "법률·이민 자문이 아니며, 최종 판단은 호주 내무부 공식 안내를 확인하세요.";

    private final AppUserService users;
    private final LedgerService ledger;
    private final com.whv.tracker.user.AccountDeletionService deletion;
    private final String contactEmail;

    public HomeController(AppUserService users, LedgerService ledger,
                          com.whv.tracker.user.AccountDeletionService deletion,
                          @org.springframework.beans.factory.annotation.Value("${whv.contact-email:}") String contactEmail) {
        this.users = users;
        this.ledger = ledger;
        this.deletion = deletion;
        this.contactEmail = contactEmail;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("disclaimer", DISCLAIMER);
        AppUser owner = isLoggedIn(authentication) ? users.findByGoogleSubject(authentication.getName()) : null;
        model.addAttribute("loggedIn", isLoggedIn(authentication));
        if (owner == null) {
            return "index";
        }
        LocalDate today = LocalDate.now();
        LedgerService.Dashboard dashboard = ledger.dashboard(owner);
        // 지금 준비 중인 비자(세컨 2 / 서드 3)의 누적만 홈에 보여준다. summaries는 [세컨, 서드] 순서.
        int goal = owner.getGoalVisa();
        VisaSummary second = dashboard.summaries().get(goal == 3 ? 1 : 0);
        model.addAttribute("s", second);
        model.addAttribute("today", today);

        // 진행 막대: 인정 비율과 짧게 일한 날 비율 (짧은 날은 하루 1.4일로 가정해 표시만 한다)
        BigDecimal target = BigDecimal.valueOf(second.target());
        BigDecimal surePct = second.total().multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100));
        BigDecimal shortPct = WorkCalculator.convert(BigDecimal.valueOf(second.shortDays()), 5)
                .multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100).subtract(surePct));
        model.addAttribute("surePct", surePct.toPlainString());
        model.addAttribute("shortPct", shortPct.toPlainString());

        if (second.firstDate() != null) {
            model.addAttribute("startedDays", ChronoUnit.DAYS.between(second.firstDate(), today) + 1);
        }
        if (owner.getVisaExpiry() != null && owner.getBufferDays() != null) {
            model.addAttribute("pace", PacePlan.of(owner.getVisaExpiry(), owner.getBufferDays(), today, second));
            model.addAttribute("expiryDays", ChronoUnit.DAYS.between(today, owner.getVisaExpiry()));
            model.addAttribute("expiry", owner.getVisaExpiry());
        }

        YearMonth month = YearMonth.from(today);
        model.addAttribute("month", month);
        model.addAttribute("cells", calendarCells(month, today, ledger.calendar(owner, month, goal)));
        model.addAttribute("places", places(dashboard, goal));
        return "home";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("error", error);
        return "login";
    }

    /** F5: 지금 준비 중인 비자(2 세컨 / 3 서드) 저장. */
    @org.springframework.web.bind.annotation.PostMapping("/account/goal-visa")
    public String saveGoalVisa(Authentication authentication, @RequestParam(defaultValue = "0") int goalVisa,
                               org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        AppUser user = users.findByGoogleSubject(authentication.getName());
        if (user == null) {
            return "redirect:/login";
        }
        try {
            users.updateGoalVisa(user, goalVisa);
            redirect.addFlashAttribute("message", (goalVisa == 3 ? "서드" : "세컨") + " 비자 준비로 바꿨어요.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/account";
        }
        return "redirect:/";
    }

    @GetMapping("/account")
    public String account(Authentication authentication, Model model) {
        AppUser user = users.findByGoogleSubject(authentication.getName());
        model.addAttribute("user", user);
        model.addAttribute("disclaimer", DISCLAIMER);
        return "account";
    }

    /** 개인정보처리방침 (로그인 없이 공개). 문의 이메일은 서버 환경변수 WHV_CONTACT_EMAIL. */
    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("contactEmail", contactEmail);
        return "privacy";
    }

    /** 회원 탈퇴: "탈퇴" 글자를 직접 입력해야 진행한다. 지운 뒤 로그아웃 처리한다. */
    @org.springframework.web.bind.annotation.PostMapping("/account/delete")
    public String deleteAccount(Authentication authentication, @RequestParam(defaultValue = "") String confirm,
                                jakarta.servlet.http.HttpServletRequest request,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirect)
            throws jakarta.servlet.ServletException {
        AppUser user = users.findByGoogleSubject(authentication.getName());
        if (user == null) {
            return "redirect:/login";
        }
        if (!"탈퇴".equals(confirm.trim())) {
            redirect.addFlashAttribute("error", "확인 칸에 \"탈퇴\"를 입력해야 지울 수 있어요.");
            return "redirect:/account";
        }
        deletion.deleteAccount(user.getId());
        request.logout();
        return "redirect:/?deleted";
    }

    /** 달력 칸 (앞쪽 빈칸 포함). css: full / worked / short / future / blank, today는 별도 표시. */
    record Cell(String label, String css) {
    }

    static List<Cell> calendarCells(YearMonth month, LocalDate today, Map<LocalDate, DayKind> kinds) {
        List<Cell> cells = new ArrayList<>();
        int blanks = month.atDay(1).getDayOfWeek().getValue() % 7; // 일요일 시작
        for (int i = 0; i < blanks; i++) {
            cells.add(new Cell("", "blank"));
        }
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            DayKind k = kinds.get(date);
            String css = k == DayKind.FULLTIME ? "full" : k == DayKind.WORKED ? "worked"
                    : k == DayKind.SHORT ? "short" : date.isAfter(today) ? "future" : "";
            if (date.equals(today)) {
                css += " today";
            }
            cells.add(new Cell(String.valueOf(d), css));
        }
        return cells;
    }

    /** "일한 곳" 목록 한 줄. */
    record Place(String name, String detail, String value, boolean counted) {
    }

    static List<Place> places(LedgerService.Dashboard dashboard, int visaYear) {
        List<Place> out = new ArrayList<>();
        for (WorkRecord r : dashboard.records()) {
            if (r.getVisaYear() != visaYear) {
                continue;
            }
            if (r.getVisaYear() != 2) {
                continue;
            }
            boolean counted = dashboard.countedByRecord().getOrDefault(r.getId(), false);
            if (r.getMode() == WorkMode.EMPLOYMENT_PERIOD) {
                out.add(new Place(r.getEmployerName(),
                        "매일 출근 · " + md(r.getStartDate()) + " – " + md(r.getEndDate()),
                        WorkCalculator.calendarDays(r.getStartDate(), r.getEndDate()) + "일", counted));
            } else {
                List<WorkDay> rows = dashboard.daysByRecord().getOrDefault(r.getId(), List.of());
                out.add(new Place(r.getEmployerName(), "일한 날 기록", rows.size() + "일 일함", counted));
            }
        }
        return out;
    }

    private static String md(LocalDate d) {
        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
    }

    /** 구글 로그인을 한 사용자인지. 익명 사용자는 false. CheckController도 하단 메뉴 표시에 쓴다. */
    static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }
}
