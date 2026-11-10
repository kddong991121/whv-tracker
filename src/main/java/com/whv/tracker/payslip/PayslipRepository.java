package com.whv.tracker.payslip;

import com.whv.tracker.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    List<Payslip> findByOwnerOrderByPeriodStartDesc(AppUser owner);
}
