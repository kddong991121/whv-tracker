package com.whv.tracker.web;

import com.whv.tracker.calc.PacePlan;
import com.whv.tracker.eligibility.WorkType;
import com.whv.tracker.ledger.DisasterWorkType;
import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * 근무 기록 화면 (/ledger). 로그인한 본인 계정의 기록만 조회·저장한다.
 * 모든 입력 오류는 400 페이지 대신 플래시 메시지로 돌려보낸다.
 */
@Controller
public class LedgerController {

    public static final String DISCLAIMER = "법률·이민 자문이 아니며, 최종 판단은 호주 내무부 공식 안내를 확인하세요.";

    private static final String DUPLICATE_MESSAGE = "같은 기록이 동시에 저장되어 거부되었습니다. 화면을 새로고침한 뒤 다시 확인하세요.";
    private static final String NO_ACCOUNT_MESSAGE = "계정 정보를 찾지 못했습니다. 다시 로그인하세요.";

    private final LedgerService ledger;
    private final AppUserService users;

    public LedgerController(LedgerService ledger, AppUserService users) {
        this.ledger = ledger;
        this.users = users;
    }

    @GetMapping("/ledger")
    public String index(Authentication authentication, Model model) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        var dashboard = ledger.dashboard(owner);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("owner", owner);
        if (owner.getVisaExpiry() != null && owner.getBufferDays() != null) {
            model.addAttribute("pace", PacePlan.of(owner.getVisaExpiry(), owner.getBufferDays(), LocalDate.now(),
                    dashboard.summaries().get(0)));
        }
        model.addAttribute("industries", Industry.values());
        model.addAttribute("disasterTypes", DisasterWorkType.values());
        model.addAttribute("workTypes", WorkType.values());
        model.addAttribute("disclaimer", DISCLAIMER);
        return "ledger";
    }

    @PostMapping("/employment")
    public String addEmployment(Authentication authentication,
                                @RequestParam String employer,
                                @RequestParam String postcode,
                                @RequestParam Industry industry,
                                @RequestParam int visaYear,
                                @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
                                @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
                                @RequestParam(defaultValue = "0") int unpaidWeatherDays,
                                @RequestParam(required = false) DisasterWorkType disasterWorkType,
                                @RequestParam(required = false) WorkType workType,
                                @RequestParam(defaultValue = "false") boolean conditionConfirmed,
                                RedirectAttributes redirect) {
        warnIfFuture(redirect, startDate, endDate);
        return run(redirect, authentication, owner -> ledger.addEmployment(owner, employer, postcode, industry,
                visaYear, startDate, endDate, unpaidWeatherDays, disasterWorkType, workType, conditionConfirmed),
                "고용 기간을 저장했습니다.");
    }

    @PostMapping("/casual")
    public String addCasual(Authentication authentication,
                            @RequestParam String employer,
                            @RequestParam String postcode,
                            @RequestParam Industry industry,
                            @RequestParam int visaYear,
                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
                            @RequestParam(defaultValue = "5") int standardDays,
                            @RequestParam(required = false) String standardReason,
                            @RequestParam(required = false) String standardSource,
                            @RequestParam(required = false) DisasterWorkType disasterWorkType,
                            @RequestParam BigDecimal normalHours,
                            @RequestParam(required = false) WorkType workType,
                            @RequestParam(defaultValue = "false") boolean conditionConfirmed,
                            RedirectAttributes redirect) {
        warnIfFuture(redirect, startDate, endDate);
        return run(redirect, authentication, owner -> ledger.addCasual(owner, employer, postcode, industry, visaYear,
                startDate, endDate, standardDays, standardReason, standardSource, disasterWorkType, normalHours,
                        workType, conditionConfirmed),
                "일터를 저장했습니다.");
    }

    @PostMapping("/casual/{recordId}/days")
    public String addWorkDay(Authentication authentication,
                             @PathVariable long recordId,
                             @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate workDate,
                             @RequestParam(required = false) BigDecimal workHours,
                             RedirectAttributes redirect) {
        warnIfFuture(redirect, workDate);
        return run(redirect, authentication, owner -> ledger.addWorkDay(owner, recordId, workDate, workHours),
                "근무일을 저장했습니다.");
    }

    @PostMapping("/visa-plan")
    public String saveVisaPlan(Authentication authentication,
                               @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate visaExpiry,
                               @RequestParam Integer bufferDays,
                               RedirectAttributes redirect) {
        return run(redirect, authentication, owner -> users.updateVisaPlan(owner, visaExpiry, bufferDays),
                "비자 만료일을 저장했습니다.");
    }

    /** 바인딩과 형식 오류 (날짜, enum, 숫자, 누락 파라미터)도 플래시 메시지로 돌려보낸다. */
    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public String handleInputError(Exception e, HttpServletRequest request) {
        String message;
        if (e instanceof MethodArgumentTypeMismatchException m) {
            message = "입력값 형식이 올바르지 않습니다: " + m.getName();
        } else if (e instanceof MissingServletRequestParameterException m) {
            message = "필수 입력값이 없습니다: " + m.getParameterName();
        } else {
            message = "입력값을 읽을 수 없습니다.";
        }
        RequestContextUtils.getOutputFlashMap(request).put("error", message);
        return "redirect:/ledger";
    }

    private String run(RedirectAttributes redirect, Authentication authentication, Action action,
                       String successMessage) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            redirect.addFlashAttribute("error", NO_ACCOUNT_MESSAGE);
            return "redirect:/ledger";
        }
        try {
            action.run(owner);
            redirect.addFlashAttribute("message", successMessage);
        } catch (IllegalArgumentException e) {
            redirect.getFlashAttributes().remove("warning"); // 저장되지 않았으면 미래 날짜 경고도 뺀다
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.getFlashAttributes().remove("warning");
            redirect.addFlashAttribute("error", DUPLICATE_MESSAGE);
        }
        return "redirect:/ledger";
    }

    /** 미래 날짜는 경고만 하고 저장은 허용한다 (PRD 4.10). */
    private static void warnIfFuture(RedirectAttributes redirect, LocalDate... dates) {
        LocalDate today = LocalDate.now();
        for (LocalDate d : dates) {
            if (d != null && d.isAfter(today)) {
                redirect.addFlashAttribute("warning", "미래 날짜(" + d + ")가 포함되어 있습니다. 저장은 되었습니다.");
                return;
            }
        }
    }

    @FunctionalInterface
    private interface Action {
        void run(AppUser owner);
    }
}
