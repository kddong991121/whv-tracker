package com.whv.tracker.ledger;

import com.whv.tracker.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRecordRepository extends JpaRepository<WorkRecord, Long> {

    List<WorkRecord> findByOwnerAndMode(AppUser owner, WorkMode mode);

    List<WorkRecord> findByOwnerAndEmployerKey(AppUser owner, String employerKey);

    List<WorkRecord> findAllByOwnerOrderByIdDesc(AppUser owner);
}
