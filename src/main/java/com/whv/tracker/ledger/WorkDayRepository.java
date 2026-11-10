package com.whv.tracker.ledger;

import com.whv.tracker.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkDayRepository extends JpaRepository<WorkDay, Long> {

    /** 같은 소유자의 같은 날짜 근무 행 (날짜당 1행 검사). */
    List<WorkDay> findByRecordOwnerAndWorkDate(AppUser owner, LocalDate workDate);

    /** 같은 소유자의 같은 고용주 키 근무 행 (모드 A 기간과의 겹침 검사). */
    List<WorkDay> findByRecordOwnerAndRecordEmployerKey(AppUser owner, String employerKey);

    /** 소유자의 모든 근무 행 (화면용). */
    List<WorkDay> findByRecordOwner(AppUser owner);
}
