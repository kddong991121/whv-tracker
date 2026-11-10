package com.whv.tracker.web;

import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkMode;
import com.whv.tracker.payslip.Payslip;
import com.whv.tracker.payslip.PayslipService;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * 페이슬립 (/payslip, F3). PDF는 브라우저(pdf.js)에서만 읽고, 서버는 사용자가 확인한 값만 받는다.
 * 이 컨트롤러에는 파일 업로드 파라미터가 없다.
 */
@Controller
public class PayslipController {

    private final PayslipService payslips;
    private final LedgerService ledger;
    private final AppUserService users;

    public PayslipController(PayslipService payslips, LedgerService ledger, AppUserService users) {
        this.payslips = payslips;
        this.ledger = ledger;
        this.users = users;
    }

    @GetMapping("/payslip")
    public String page(Authentication authentication, Model model) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        model.addAttribute("places", ledger.dashboard(owner).records().stream()
                .filter(r -> r.getMode() == WorkMode.CASUAL_DAYS).toList());
        model.addAttribute("payslips", payslips.list(owner));
        model.addAttribute("checklist", Payslip.CHECKLIST);
        model.addAttribute("disclaimer", HomeController.DISCLAIMER);
        return "payslip";
    }

    @PostMapping("/payslip")
    public String save(Authentication authentication,
                       @RequestParam long recordId,
                       @RequestParam String employerName,
                       @RequestParam(required = false) String abn,
                       @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodStart,
                       @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodEnd,
                       @RequestParam BigDecimal totalHours,
                       @RequestParam(required = false) String employmentType,
                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) List<LocalDate> dates,
                       @RequestParam(required = false) List<Integer> checklist,
                       @RequestParam(defaultValue = "false") boolean cashPaid,
                       @RequestParam(defaultValue = "false") boolean consent,
                       RedirectAttributes redirect) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            Payslip saved = payslips.importPayslip(owner, new PayslipService.Import(recordId, employerName, abn,
                    periodStart, periodEnd, totalHours, employmentType, dates, checklist, cashPaid, consent));
            redirect.addFlashAttribute("message", "페이슬립 내용을 저장했어요. 근무 " + saved.getWorkedDays() + "일을 넣었어요.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", "같은 날짜 근무가 이미 있어요. 화면을 새로고침해 주세요.");
        }
        return "redirect:/payslip";
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public String handleInputError(Exception e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", "입력값을 확인해 주세요 (날짜, 시간, 일터).");
        return "redirect:/payslip";
    }
}
