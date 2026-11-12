package com.whv.tracker.web;

import com.whv.tracker.eligibility.WorkType;
import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkDay;
import com.whv.tracker.ledger.WorkMode;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
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
 * 근무 기록 (/work): 시안(docs/design/Daily.dc.html) 기준 주간 화면. 날짜마다 여러 일터의 근무를 넣고 지운다.
 * 일한 날 기록 일터만 다룬다. 매일 출근 기간은 /ledger 상세 화면에서 입력한다.
 */
@Controller
public class WorkController {

    private static final String[] COLORS = {"#2F6FEB", "#0F8A6A", "#7C3AED", "#C2410C"};

    private final LedgerService ledger;
    private final AppUserService users;

    public WorkController(LedgerService ledger, AppUserService users) {
        this.ledger = ledger;
        this.users = users;
    }

    record Shift(long id, String employer, String hours, String color, boolean full) {
    }

    record Day(String label, String date, String badge, String badgeCss, String note, List<Shift> shifts) {
    }

    record Place(long id, String name, String detail, String color, boolean counted, String status, String normalHours) {
    }

    @GetMapping("/work")
    public String week(Authentication authentication,
                       @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate week,
                       Model model) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        LocalDate monday = (week == null ? LocalDate.now() : week).with(DayOfWeek.MONDAY);
        LedgerService.Dashboard dashboard = ledger.dashboard(owner);

        // 지금 준비 중인 비자(세컨/서드)에 넣은 일터만 보여준다
        int goal = owner.getGoalVisa();
        List<WorkRecord> casual = dashboard.records().stream()
                .filter(r -> r.getMode() == WorkMode.CASUAL_DAYS && r.getVisaYear() == goal).toList();
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < casual.size(); i++) {
            WorkRecord r = casual.get(i);
            boolean counted = dashboard.countedByRecord().getOrDefault(r.getId(), false);
            places.add(new Place(r.getId(), r.getEmployerName(),
                    (r.getNormalHours() == null ? "평소 시간 미입력" : "하루 " + r.getNormalHours().stripTrailingZeros().toPlainString() + "시간") + " · " + r.getPostcode(),
                    COLORS[i % COLORS.length], counted, dashboard.statusByRecord().get(r.getId()).message(),
                    r.getNormalHours() == null ? "" : r.getNormalHours().stripTrailingZeros().toPlainString()));
        }

        List<Day> days = new ArrayList<>();
        int worked = 0, sure = 0, shortDays = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            List<Shift> shifts = new ArrayList<>();
            boolean anyCounted = false, anyFull = false;
            for (int j = 0; j < casual.size(); j++) {
                WorkRecord r = casual.get(j);
                for (WorkDay d : dashboard.daysByRecord().getOrDefault(r.getId(), List.of())) {
                    if (!d.getWorkDate().equals(date)) {
                        continue;
                    }
                    boolean full = ledger.isFullShift(d);
                    String hours = d.getWorkHours() == null ? "-" : d.getWorkHours().stripTrailingZeros().toPlainString() + "h";
                    shifts.add(new Shift(d.getId(), r.getEmployerName(), hours, COLORS[j % COLORS.length], full));
                    if (dashboard.countedByRecord().getOrDefault(r.getId(), false)) {
                        anyCounted = true;
                        anyFull |= full;
                    }
                }
            }
            String label = date.getMonthValue() + "." + date.getDayOfMonth() + " ("
                    + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
            String badge, css, note = "";
            if (shifts.isEmpty()) {
                badge = "쉬는 날";
                css = "off";
            } else if (!anyCounted) {
                badge = "합계 제외";
                css = "off";
                note = "인정 지역·업무로 확인되지 않은 일터예요.";
                worked++;
            } else if (anyFull) {
                badge = "인정";
                css = "sure";
                worked++;
                sure++;
            } else {
                badge = "짧게 일한 날";
                css = "short";
                note = shifts.size() > 1 ? "여러 곳을 합쳐 일한 날이에요. 인정 안 될 수도 있어요."
                        : "평소보다 짧게 일했어요. 인정 안 될 수도 있어요.";
                worked++;
                shortDays++;
            }
            days.add(new Day(label, date.toString(), badge, css, note, shifts));
        }

        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("prevWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("places", places);
        model.addAttribute("days", days);
        model.addAttribute("worked", worked);
        model.addAttribute("sure", sure);
        model.addAttribute("shortDays", shortDays);
        model.addAttribute("workTypes", WorkType.values());
        model.addAttribute("goalName", goal == 3 ? "서드" : "세컨");
        model.addAttribute("disclaimer", HomeController.DISCLAIMER);
        return "work";
    }

    @PostMapping("/work/shift")
    public String addShift(Authentication authentication, @RequestParam long recordId,
                           @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate workDate,
                           @RequestParam BigDecimal workHours, @RequestParam(required = false) String next,
                           RedirectAttributes redirect) {
        run(authentication, redirect, owner -> ledger.addWorkDay(owner, recordId, workDate, workHours), "근무를 추가했어요.");
        return backHome(next) ? "redirect:/" : "redirect:/work?week=" + workDate;
    }

    /** 지우고, 화면 아래에 "되돌리기"를 띄울 값(일터·날짜·시간)을 넘긴다. 확인창은 띄우지 않는다. */
    @PostMapping("/work/shift/{id}/delete")
    public String deleteShift(Authentication authentication, @PathVariable long id,
                              @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate week, RedirectAttributes redirect) {
        run(authentication, redirect, owner -> {
            LedgerService.DeletedShift d = ledger.deleteWorkDay(owner, id);
            redirect.addFlashAttribute("undo", d);
        }, "근무를 지웠어요.");
        return "redirect:/work?week=" + week;
    }

    /** 홈 원탭 기록: 마지막 일터에 오늘, 평소 근무 시간으로 넣는다. 서버 날짜는 Australia/Sydney 기준. */
    @PostMapping("/work/today")
    public String addToday(Authentication authentication, @RequestParam long recordId, RedirectAttributes redirect) {
        LocalDate today = LocalDate.now();
        run(authentication, redirect, owner -> {
            LedgerService.QuickShift q = ledger.quickShift(owner, owner.getGoalVisa(), today)
                    .filter(x -> x.recordId() == recordId)
                    .orElseThrow(() -> new IllegalArgumentException("오늘 바로 넣을 수 있는 일터가 아니에요. 근무 기록에서 넣어 주세요."));
            ledger.addWorkDay(owner, q.recordId(), today, q.hours());
        }, "오늘 근무를 넣었어요 ✓");
        return "redirect:/";
    }

    /** 지난주 근무를 이번 주에 그대로 넣는다. week = 이번 주 월요일. */
    @PostMapping("/work/copy-week")
    public String copyWeek(Authentication authentication,
                           @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate week, RedirectAttributes redirect) {
        LocalDate monday = week.with(java.time.DayOfWeek.MONDAY);
        int[] r = new int[2];
        run(authentication, redirect, owner -> {
            int[] x = ledger.copyPreviousWeek(owner, monday);
            r[0] = x[0];
            r[1] = x[1];
        }, "");
        if (!redirect.getFlashAttributes().containsKey("error")) {
            redirect.addFlashAttribute("message", r[0] == 0 && r[1] == 0 ? "지난주에 넣은 근무가 없어요."
                    : r[0] + "일 넣었어요" + (r[1] > 0 ? " · " + r[1] + "일 건너뜀 (이미 있거나 넣을 수 없는 날)" : ""));
        }
        return "redirect:/work?week=" + monday;
    }

    /** 일한 날 기록 일터 추가. 차수는 지금 준비 중인 비자(세컨 2 / 서드 3), 기준 주당 근무일은 5로 저장한다. */
    @PostMapping("/work/place")
    public String addPlace(Authentication authentication, @RequestParam String employer, @RequestParam String postcode,
                           @RequestParam Industry industry, @RequestParam(required = false) WorkType workType,
                           @RequestParam BigDecimal normalHours,
                           @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
                           @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate endDate,
                           @RequestParam(required = false) String next,
                           RedirectAttributes redirect) {
        run(authentication, redirect, owner -> ledger.addCasual(owner, employer, postcode, industry, owner.getGoalVisa(), startDate,
                endDate, 5, null, null, null, normalHours, workType, false), "일터를 추가했어요.");
        return backHome(next) ? "redirect:/" : "redirect:/work?week=" + startDate;
    }

    /** 처음 온 사람 안내(홈)에서 보낸 요청이면 홈으로 돌려보낸다. 정해진 값만 받는다 (다른 주소로 보내는 데 쓰이지 않게). */
    private static boolean backHome(String next) {
        return "home".equals(next);
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public String handleInputError(Exception e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", "입력값을 확인해 주세요.");
        return "redirect:/work";
    }

    private void run(Authentication authentication, RedirectAttributes redirect, Consumer<AppUser> action, String ok) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            redirect.addFlashAttribute("error", "계정 정보를 찾지 못했어요. 다시 로그인해 주세요.");
            return;
        }
        try {
            action.accept(owner);
            redirect.addFlashAttribute("message", ok);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", "같은 근무가 이미 있어요. 화면을 새로고침해 주세요.");
        }
    }
}
