package com.whv.tracker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 회원 계정. 구글 로그인의 sub로 식별한다. 비밀번호는 저장하지 않는다. */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_subject", nullable = false, unique = true, length = 255)
    private String googleSubject;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** F5: 1년차 비자 만료일 (선택). */
    @Column(name = "visa_expiry")
    private java.time.LocalDate visaExpiry;

    /** F5: 완료 목표 버퍼 일수 (선택, 0 이상). */
    @Column(name = "buffer_days")
    private Integer bufferDays;

    /** F5: 지금 준비 중인 비자. 2 = 세컨, 3 = 서드 (사용자가 직접 고름, 기본 2). */
    @Column(name = "goal_visa", nullable = false)
    private int goalVisa = 2;

    protected AppUser() {
    }

    public AppUser(String googleSubject, String email, String displayName) {
        this.googleSubject = googleSubject;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = LocalDateTime.now();
    }

    void refresh(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    void updateVisaPlan(java.time.LocalDate visaExpiry, int bufferDays) {
        this.visaExpiry = visaExpiry;
        this.bufferDays = bufferDays;
    }

    void updateGoalVisa(int goalVisa) {
        this.goalVisa = goalVisa;
    }

    public int getGoalVisa() {
        return goalVisa;
    }

    public java.time.LocalDate getVisaExpiry() {
        return visaExpiry;
    }

    public Integer getBufferDays() {
        return bufferDays;
    }

    public Long getId() {
        return id;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
