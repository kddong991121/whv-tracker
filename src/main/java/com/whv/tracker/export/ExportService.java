package com.whv.tracker.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.whv.tracker.calc.VisaSummary;
import com.whv.tracker.calc.WorkCalculator;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkDay;
import com.whv.tracker.ledger.WorkMode;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.payslip.Payslip;
import com.whv.tracker.payslip.PayslipService;
import com.whv.tracker.user.AppUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * F4: 근무 이력 PDF (영문, 서버 생성). 페이슬립 원본은 없으므로 읽은 값과 체크리스트 점수만 적는다 (PRD F4).
 * 신청자 영문 이름은 출력할 때만 받고 저장하지 않는다.
 */
@Service
public class ExportService {

    static final String DISCLAIMER_EN = "Not legal or migration advice. Check official Home Affairs guidance for the final decision.";
    static final String DISCLAIMER_KO = "법률·이민 자문이 아니며, 최종 판단은 호주 내무부 공식 안내를 확인하세요.";
    static final String SOURCE_URL =
            "https://immi.homeaffairs.gov.au/visas/getting-a-visa/visa-listing/work-holiday-417/specified-work";

    private final LedgerService ledger;
    private final PayslipService payslips;

    public ExportService(LedgerService ledger, PayslipService payslips) {
        this.ledger = ledger;
        this.payslips = payslips;
    }

    public byte[] build(AppUser owner, String applicantName, LocalDate from, LocalDate to, Set<Long> recordIds) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("기간을 확인하세요.");
        }
        String name = applicantName == null ? "" : applicantName.trim();
        if (!name.matches("[A-Za-z][A-Za-z .'-]{0,99}")) {
            throw new IllegalArgumentException("이름은 여권과 같은 영문으로 입력하세요.");
        }
        LedgerService.Dashboard d = ledger.dashboard(owner, recordIds, from, to);
        Map<Long, Payslip> latestSlip = payslips.list(owner).stream()
                .collect(Collectors.toMap(p -> p.getRecord().getId(), Function.identity(), (a, b) -> a));

        try {
            BaseFont latin = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont korean = koreanFont();
            Font h1 = new Font(latin, 16, Font.BOLD);
            Font h2 = new Font(latin, 12, Font.BOLD);
            Font body = new Font(korean, 9); // 사용자가 넣은 일터 이름에 한글이 있을 수 있다
            Font bold = new Font(latin, 9, Font.BOLD);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 54);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            boolean empty = d.summaries().stream().allMatch(s -> s.total().signum() == 0);
            writer.setPageEvent(new Footer(latin, korean, empty));
            doc.open();

            doc.add(new Paragraph("Specified Work Record - Working Holiday visa (Subclass 417)", h1));
            doc.add(new Paragraph("Applicant: " + name + "    Period: " + from + " to " + to + "    Generated: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), body));
            doc.add(new Paragraph(" "));

            doc.add(heading("Summary", h2));
            PdfPTable summary = table(new float[]{2, 2, 2, 2, 2, 2, 2},
                    List.of("Visa", "Full-time calendar days", "Casual days (x 7 / 5)", "Total counted days",
                            "Short days (not counted)", "Hours recorded", "Employers"), bold);
            for (VisaSummary s : d.summaries()) {
                String visa = s.visaYear() == 2 ? "Second (88 days)" : "Third (179 days)";
                cells(summary, body, visa, String.valueOf(s.employmentDays()), s.casualConvertedText(),
                        s.totalText(), String.valueOf(s.shortDays()), hours(d, s.visaYear()).toPlainString(),
                        String.valueOf(d.records().stream().filter(r -> r.getVisaYear() == s.visaYear()).count()));
            }
            doc.add(summary);
            doc.add(new Paragraph(" "));

            doc.add(heading("Employers", h2));
            PdfPTable detail = table(new float[]{3, 2, 1.2f, 3, 1.5f, 3, 2.2f, 2.5f},
                    List.of("Employer", "ABN", "Postcode", "Period", "Visa", "Industry / work type",
                            "Worked", "Area check (evidence)"), bold);
            for (WorkRecord r : d.records()) {
                Payslip slip = latestSlip.get(r.getId());
                String worked = r.getMode() == WorkMode.EMPLOYMENT_PERIOD
                        ? WorkCalculator.calendarDays(max(r.getStartDate(), from), min(r.getEndDate(), to)) + " calendar days"
                        : d.daysByRecord().getOrDefault(r.getId(), List.of()).size() + " days worked";
                String evidence = (d.countedByRecord().getOrDefault(r.getId(), false) ? "Counted" : "Not counted")
                        + " / " + statusEn(d.statusByRecord().get(r.getId()).name())
                        + (slip == null ? "" : " / payslip checklist " + slip.score() + "/7"
                        + (slip.isCashPaid() ? ", cash paid" : ""));
                cells(detail, body, r.getEmployerName(), slip == null || slip.getAbn() == null ? "-" : slip.getAbn(),
                        r.getPostcode(), r.getStartDate() + " to " + r.getEndDate(),
                        r.getVisaYear() == 2 ? "Second" : "Third",
                        r.getIndustry().name() + (r.getWorkType() == null ? "" : " / " + r.getWorkType().name()),
                        worked, evidence);
            }
            doc.add(detail);
            doc.add(new Paragraph(" "));

            doc.add(heading("Rules and sources", h2));
            PdfPTable rules = table(new float[]{5, 3, 2}, List.of("Rule used", "Source section", "Evidence status"), bold);
            cells(rules, body, "88 / 179 calendar days; weekends in the employment period count", "Minimum period", "Official text");
            cells(rules, body, "One calendar day never counts as more than one day", "Normal work days or shifts", "Official text");
            cells(rules, body, "Casual days converted as days x 7 / 5 (5-day normal week assumed)", "Normal work days or shifts", "Needs confirmation");
            cells(rules, body, "Short days (all shifts below normal daily hours) are not counted", "Normal work days or shifts", "Needs confirmation");
            cells(rules, body, "Area and industry from postcode tables 1-6", "Approved industries and areas", "Official text");
            doc.add(rules);
            doc.add(new Paragraph("Source: " + SOURCE_URL + " (retrieved 2026-11-10)", body));
            doc.add(new Paragraph("Payslip files are not stored by this service; only values entered by the applicant are listed.", body));
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("PDF를 만들지 못했습니다.", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 나눔고딕(OFL)을 PDF에 넣는다. 보는 사람 PC에 한글 글꼴이 없어도 한국어 문구가 보이게 하려는 것이다. */
    private static BaseFont koreanFont() throws IOException, DocumentException {
        byte[] ttf;
        try (var in = new org.springframework.core.io.ClassPathResource("fonts/NanumGothic.ttf").getInputStream()) {
            ttf = in.readAllBytes();
        }
        return BaseFont.createFont("NanumGothic.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, ttf, null);
    }

    private static BigDecimal hours(LedgerService.Dashboard d, int visaYear) {
        BigDecimal sum = BigDecimal.ZERO;
        for (WorkRecord r : d.records()) {
            if (r.getVisaYear() != visaYear) {
                continue;
            }
            for (WorkDay w : d.daysByRecord().getOrDefault(r.getId(), List.of())) {
                if (w.getWorkHours() != null) {
                    sum = sum.add(w.getWorkHours());
                }
            }
        }
        return sum;
    }

    private static String statusEn(String status) {
        return switch (status) {
            case "ELIGIBLE" -> "listed area";
            case "CONDITIONAL" -> "conditional";
            case "CONDITION_NOT_MET" -> "lodgement condition not met";
            case "NOT_SPECIFIED_WORK" -> "work type not eligible";
            default -> "not found in tables";
        };
    }

    private static Paragraph heading(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(6);
        return p;
    }

    private static PdfPTable table(float[] widths, List<String> headers, Font font) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, font));
            c.setGrayFill(0.92f);
            c.setPadding(4);
            t.addCell(c);
        }
        t.setHeaderRows(1);
        return t;
    }

    private static void cells(PdfPTable t, Font font, String... values) {
        for (String v : values) {
            PdfPCell c = new PdfPCell(new Phrase(v, font));
            c.setPadding(4);
            t.addCell(c);
        }
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /** 모든 페이지 아래에 영문·한국어 면책 문구. 인정 일수 0이면 "기록 부족" 워터마크. */
    private static final class Footer extends PdfPageEventHelper {
        private final Font en;
        private final Font ko;
        private final Font mark;
        private final boolean empty;

        Footer(BaseFont latin, BaseFont korean, boolean empty) {
            this.en = new Font(latin, 8);
            this.ko = new Font(korean, 8);
            this.mark = new Font(korean, 60);
            this.empty = empty;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float x = document.left();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(DISCLAIMER_EN, en), x, 30, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(DISCLAIMER_KO, ko), x, 18, 0);
            if (empty) {
                PdfContentByte under = writer.getDirectContentUnder();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.15f);
                under.saveState();
                under.setGState(gs);
                ColumnText.showTextAligned(under, Element.ALIGN_CENTER, new Phrase("기록 부족 / INSUFFICIENT RECORDS", mark),
                        document.getPageSize().getWidth() / 2, document.getPageSize().getHeight() / 2, 20);
                under.restoreState();
            }
        }
    }
}
