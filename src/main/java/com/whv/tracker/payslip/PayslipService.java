package com.whv.tracker.payslip;

import com.whv.tracker.ledger.LedgerService;
import com.whv.tracker.ledger.WorkMode;
import com.whv.tracker.ledger.WorkRecord;
import com.whv.tracker.ledger.WorkRecordRepository;
import com.whv.tracker.user.AppUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F3: 브라우저가 페이슬립에서 읽은 값만 받아 저장한다 (PRD 4.6). 파일은 받지 않는다.
 * 고른 날짜마다 일한 날 근무 1행을 만들고, 총 근무 시간을 날짜 수로 똑같이 나눠 넣는다.
 */
@Service
public class PayslipService {

    private static final int[] ABN_WEIGHTS = {10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19};

    private final PayslipRepository payslips;
    private final WorkRecordRepository records;
    private final LedgerService ledger;

    public PayslipService(PayslipRepository payslips, WorkRecordRepository records, LedgerService ledger) {
        this.payslips = payslips;
        this.records = records;
        this.ledger = ledger;
    }

    /** 입력값. dates는 사용자가 고른 일한 날. checklist는 항목 번호(0~6) 목록. */
    public record Import(long recordId, String employerName, String abn, LocalDate periodStart, LocalDate periodEnd,
                         BigDecimal totalHours, String employmentType, List<LocalDate> dates,
                         List<Integer> checklist, boolean cashPaid, boolean consent) {
    }

    /** 하나라도 실패하면 근무 행까지 모두 되돌린다 (트랜잭션). */
    @Transactional
    public Payslip importPayslip(AppUser owner, Import in) {
        if (!in.consent()) {
            throw new IllegalArgumentException("날짜·시간·일터만 저장하는 데 동의해야 저장할 수 있어요.");
        }
        WorkRecord record = records.findById(in.recordId())
                .filter(r -> r.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new IllegalArgumentException("일터를 찾을 수 없습니다."));
        if (record.getMode() != WorkMode.CASUAL_DAYS) {
            throw new IllegalArgumentException("일한 날 기록 일터만 고를 수 있어요.");
        }
        String name = in.employerName() == null ? "" : in.employerName().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("고용주 이름은 1~100자로 입력하세요.");
        }
        String abn = normalizeAbn(in.abn());
        if (in.periodStart() == null || in.periodEnd() == null || in.periodEnd().isBefore(in.periodStart())) {
            throw new IllegalArgumentException("급여 기간을 확인하세요.");
        }
        TreeSet<LocalDate> dates = new TreeSet<>(in.dates() == null ? List.of() : in.dates());
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("일한 날을 하루 이상 고르세요.");
        }
        for (LocalDate d : dates) {
            if (d.isBefore(in.periodStart()) || d.isAfter(in.periodEnd())) {
                throw new IllegalArgumentException(d + "은(는) 급여 기간 밖이에요.");
            }
        }
        BigDecimal total = in.totalHours();
        if (total == null || total.signum() <= 0 || total.scale() > 2) {
            throw new IllegalArgumentException("총 근무 시간을 확인하세요 (0 초과, 소수 둘째 자리까지).");
        }
        BigDecimal perDay = total.divide(BigDecimal.valueOf(dates.size()), 2, RoundingMode.HALF_UP);
        for (LocalDate d : dates) {
            ledger.addWorkDay(owner, record.getId(), d, perDay); // 24시간 초과, 같은 날짜 중복은 여기서 거부
        }
        int bits = 0;
        for (Integer i : in.checklist() == null ? List.<Integer>of() : in.checklist()) {
            if (i != null && i >= 0 && i < Payslip.CHECKLIST.length) {
                bits |= 1 << i;
            }
        }
        String type = in.employmentType() == null || in.employmentType().isBlank() ? null
                : in.employmentType().trim().substring(0, Math.min(30, in.employmentType().trim().length()));
        return payslips.save(new Payslip(owner, record, name, abn, in.periodStart(), in.periodEnd(), total, type,
                dates.size(), bits, in.cashPaid()));
    }

    @Transactional(readOnly = true)
    public List<Payslip> list(AppUser owner) {
        return payslips.findByOwnerOrderByPeriodStartDesc(owner);
    }

    /** ABN: 비우면 null. 넣으면 숫자 11자리와 체크섬을 검사한다 (입력 검증이며 규정 요건이 아님). */
    static String normalizeAbn(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\s", "");
        if (!digits.matches("\\d{11}")) {
            throw new IllegalArgumentException("ABN은 숫자 11자리예요.");
        }
        int sum = 0;
        for (int i = 0; i < 11; i++) {
            int d = digits.charAt(i) - '0';
            if (i == 0) {
                d -= 1;
            }
            sum += d * ABN_WEIGHTS[i];
        }
        if (sum % 89 != 0) {
            throw new IllegalArgumentException("ABN 번호가 맞지 않아요. 다시 확인해 주세요.");
        }
        return digits;
    }
}
