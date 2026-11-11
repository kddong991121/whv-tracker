package com.whv.tracker.web;

import com.whv.tracker.eligibility.EligibilityService;
import com.whv.tracker.eligibility.WorkType;
import com.whv.tracker.ledger.DisasterWorkType;
import com.whv.tracker.ledger.Industry;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** F2 세컨 인정 지역 확인 (/check). 로그인 없이 누구나 쓸 수 있다. 입력값은 저장하지 않는다. */
@Controller
public class CheckController {

    static final String SOURCE_URL =
            "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-listing/work-holiday-417/specified-work";

    private final EligibilityService eligibility;

    public CheckController(EligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    @GetMapping("/check")
    public String check(@RequestParam(required = false) String postcode,
                        @RequestParam(required = false) Industry industry,
                        @RequestParam(required = false) WorkType workType,
                        @RequestParam(required = false) DisasterWorkType disasterWorkType,
                        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate lodgeDate,
                        Authentication authentication,
                        Model model) {
        // 로그인한 사용자에게만 하단 메뉴를 보여준다 (다른 탭은 로그인이 필요해서).
        model.addAttribute("loggedIn", HomeController.isLoggedIn(authentication));
        model.addAttribute("industries", Industry.selectable());
        model.addAttribute("workTypes", WorkType.values());
        model.addAttribute("disclaimer", LedgerController.DISCLAIMER);
        model.addAttribute("sourceUrl", SOURCE_URL);
        model.addAttribute("postcode", postcode);
        model.addAttribute("industry", industry);
        if (postcode != null && industry != null) {
            try {
                model.addAttribute("result",
                        eligibility.check(postcode.trim(), industry, workType, disasterWorkType, lodgeDate));
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "check";
    }
}
