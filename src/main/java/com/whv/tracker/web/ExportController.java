package com.whv.tracker.web;

import com.whv.tracker.export.ExportService;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** F4 근무 이력 PDF: 선택 화면 (/export)과 PDF 받기 (/export/pdf). */
@Controller
public class ExportController {

    private final ExportService export;
    private final LedgerService ledger;
    private final AppUserService users;

    public ExportController(ExportService export, LedgerService ledger, AppUserService users) {
        this.export = export;
        this.ledger = ledger;
        this.users = users;
    }

    @GetMapping("/export")
    public String page(Authentication authentication, @RequestParam(required = false) String error, Model model) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        List<WorkRecord> records = ledger.dashboard(owner).records();
        LocalDate from = records.stream().map(WorkRecord::getStartDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        model.addAttribute("records", records);
        model.addAttribute("from", from);
        model.addAttribute("to", LocalDate.now());
        model.addAttribute("error", error);
        model.addAttribute("disclaimer", HomeController.DISCLAIMER);
        return "export";
    }

    @GetMapping("/export/pdf")
    public Object pdf(Authentication authentication, @RequestParam String applicantName,
                      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                      @RequestParam(required = false) List<Long> recordIds) {
        AppUser owner = users.findByGoogleSubject(authentication.getName());
        if (owner == null) {
            return "redirect:/login";
        }
        if (recordIds == null || recordIds.isEmpty()) {
            return "redirect:/export?error=records";
        }
        byte[] pdf;
        try {
            pdf = export.build(owner, applicantName, from, to, new HashSet<>(recordIds));
        } catch (IllegalArgumentException e) {
            return "redirect:/export?error=input";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("specified-work-record-" + to + ".pdf").build().toString())
                .body(pdf);
    }
}
