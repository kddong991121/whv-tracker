package com.whv.tracker.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.whv.tracker.ledger.Industry;
import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.user.AppUser;
import com.whv.tracker.user.AppUserService;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** F4 필수: PDF 생성, 기간 자르기, 면책 문구, 영문 이름 검증. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExportServiceTest {

    @Autowired
    private ExportService export;
    @Autowired
    private LedgerService ledger;
    @Autowired
    private AppUserService users;

    @Test
    void pdf_containsSummaryClippedToPeriod_andDisclaimer() throws Exception {
        AppUser owner = users.upsertGoogleUser("pdf-sub", "pdf@example.com", "PDF");
        WorkRecord farm = ledger.addEmployment(owner, "Green Valley Farm", "4870",
                Industry.REGIONAL_PRIMARY_MINING_CONSTRUCTION, 2,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31), 0, null, null, false);

        // 3월만 내보내기 → 31 달력일
        byte[] pdf = export.build(owner, "KIM MINSU", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                Set.of(farm.getId()));

        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("KIM MINSU").contains("Green Valley Farm").contains("31 calendar days")
                .contains("Not legal or migration advice");
    }

    @Test
    void nonEnglishName_isRejected() {
        AppUser owner = users.upsertGoogleUser("pdf-sub2", "pdf2@example.com", "PDF");
        assertThatThrownBy(() -> export.build(owner, "김민수", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
