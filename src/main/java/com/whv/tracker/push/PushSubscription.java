package com.whv.tracker.push;

import com.whv.tracker.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 알림을 켠 브라우저 하나 (PRD 4.10e). 한 사용자가 휴대폰·PC 등 여러 개를 가질 수 있다. */
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @Column(nullable = false, length = 600)
    private String endpoint;

    @Column(nullable = false, length = 120)
    private String p256dh;

    @Column(nullable = false, length = 40)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(AppUser owner, String endpoint, String p256dh, String auth, LocalDateTime createdAt) {
        this.owner = owner;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = createdAt;
    }

    void update(AppUser owner, String p256dh, String auth) {
        this.owner = owner;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
