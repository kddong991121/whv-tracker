package com.whv.tracker.web;

import com.whv.tracker.payslip.Payslip;
import com.whv.tracker.payslip.PayslipService;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 페이슬립 여러 장 이어서 저장 (PRD 4.10f). 화면이 새로 고쳐지지 않게 한 장씩 JSON으로 답한다.
 * 받는 값은 /payslip 폼과 같다 (사용자가 확인한 값만, 파일 없음). 저장 규칙도 같은 PayslipService를 쓴다.
 */
@RestController
public class PayslipBatchController {

    private final PayslipService payslips;
    private final AppUserService users;

    public PayslipBatchController(PayslipService payslips, AppUserService users) {
        this.payslips = payslips;
        this.users = users;
    }

    /** 답: {"ok":true,"message":...} 또는 {"ok":false,"error":...} */
    @PostMapping("/payslip/one")
    public ResponseEntity<Map<String, Object>> saveOne(Authentication authentication,
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
                                                       @RequestParam(defaultValue = "false") boolean consent) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "error", "다시 로그인해 주세요."));
        }
        try {
            Payslip saved = payslips.importPayslip(owner, new PayslipService.Import(recordId, employerName, abn,
                    periodStart, periodEnd, totalHours, employmentType, dates, checklist, cashPaid, consent));
            return ResponseEntity.ok(Map.of("ok", true, "message", "근무 " + saved.getWorkedDays() + "일을 넣었어요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "같은 날짜 근무가 이미 있어요. 건너뛰거나 날짜를 바꿔 주세요."));
        }
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> inputError(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "입력값을 확인해 주세요 (날짜, 시간, 일터)."));
    }
}
