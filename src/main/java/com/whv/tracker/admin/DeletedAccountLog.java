package com.whv.tracker.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 탈퇴 시각만 남기는 기록 (누가 탈퇴했는지는 남기지 않는다). /admin의 "최근 탈퇴" 수에 쓴다. */
@Entity
@Table(name = "deleted_account_log")
public class DeletedAccountLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    protected DeletedAccountLog() {
    }

    public DeletedAccountLog(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
