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

    @Transactional(readOnly = true)
    public AppUser findByGoogleSubject(String sub) {
        return users.findByGoogleSubject(sub).orElse(null);
    }
}
