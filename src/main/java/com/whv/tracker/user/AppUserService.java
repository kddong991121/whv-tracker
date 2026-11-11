package com.whv.tracker.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구글 로그인 결과를 계정으로 연결한다. 같은 sub는 한 계정이고, 이메일과 표시 이름은 로그인할 때마다 갱신한다. */
@Service
public class AppUserService {

    private static final int MAX_NAME_LENGTH = 100;

    private final AppUserRepository users;

    public AppUserService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public AppUser upsertGoogleUser(String sub, String email, String name) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("구글 계정 식별자(sub)가 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("구글 계정에 이메일이 없습니다. 이메일 권한을 허용하고 다시 시도하세요.");
        }
        String base = (name == null || name.isBlank()) ? email : name.trim();
        String displayName = base.length() > MAX_NAME_LENGTH ? base.substring(0, MAX_NAME_LENGTH) : base;
        return users.findByGoogleSubject(sub)
                .map(u -> {
                    u.refresh(email, displayName);
                    return u;
                })
                .orElseGet(() -> users.save(new AppUser(sub, email, displayName)));
    }

    /** F5 입력 저장. 만료일 필수, 버퍼 일수는 0 이상 정수 (PRD F5). */
    @Transactional
    public void updateVisaPlan(AppUser owner, java.time.LocalDate visaExpiry, Integer bufferDays) {
        if (visaExpiry == null) {
            throw new IllegalArgumentException("비자 만료일을 입력하세요.");
        }
        if (bufferDays == null || bufferDays < 0) {
            throw new IllegalArgumentException("버퍼 일수는 0 이상 정수로 입력하세요.");
        }
        AppUser u = users.findById(owner.getId()).orElseThrow(() -> new IllegalArgumentException("계정을 찾지 못했습니다."));
        u.updateVisaPlan(visaExpiry, bufferDays);
    }

    /** F5: 지금 준비 중인 비자 저장. 2(세컨) 또는 3(서드)만 받는다. */
    @Transactional
    public void updateGoalVisa(AppUser owner, int goalVisa) {
        if (goalVisa != 2 && goalVisa != 3) {
            throw new IllegalArgumentException("세컨 또는 서드 중에서 골라 주세요.");
        }
        AppUser u = users.findById(owner.getId()).orElseThrow(() -> new IllegalArgumentException("계정을 찾지 못했습니다."));
        u.updateGoalVisa(goalVisa);
    }

    @Transactional(readOnly = true)
    public AppUser findByGoogleSubject(String sub) {
        return users.findByGoogleSubject(sub).orElse(null);
    }
}
