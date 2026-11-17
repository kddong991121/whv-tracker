package com.whv.tracker.feedback;

import com.whv.tracker.user.AppUser;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앱 안 의견 받기 (PRD 4.12). 하루 5건까지, 글은 500자까지. */
@Service
public class FeedbackService {

    public static final int MAX_LENGTH = 500;
    public static final int DAILY_LIMIT = 5;
    /** 근무를 이만큼(일) 넣은 사람에게 👍/👎를 한 번 묻는다. */
    public static final int ASK_AFTER_WORK_DAYS = 5;

    public enum Result { OK, EMPTY, TOO_LONG, TOO_MANY }

    private final FeedbackRepository repo;
    private final EntityManager em;

    public FeedbackService(FeedbackRepository repo, EntityManager em) {
        this.repo = repo;
        this.em = em;
    }

    /** @param rating 1(👍), -1(👎), 그 밖의 값은 점수 없음으로 본다. */
    @Transactional
    public Result submit(AppUser owner, Integer rating, String message) {
        Integer score = rating != null && (rating == 1 || rating == -1) ? rating : null;
        String text = message == null ? "" : message.strip();
        if (text.length() > MAX_LENGTH) {
            return Result.TOO_LONG;
        }
        if (score == null && text.isEmpty()) {
            return Result.EMPTY;
        }
        LocalDateTime now = LocalDateTime.now();
        if (repo.countByOwnerAndCreatedAtGreaterThanEqual(owner, now.toLocalDate().atStartOfDay()) >= DAILY_LIMIT) {
            return Result.TOO_MANY;
        }
        repo.save(new Feedback(owner, score, text.isEmpty() ? null : text, now));
        return Result.OK;
    }

    /** 근무를 5일 이상 넣었고 아직 👍/👎를 안 눌렀으면 홈에서 한 번 묻는다. */
    @Transactional(readOnly = true)
    public boolean shouldAskRating(AppUser owner) {
        if (repo.existsByOwnerAndRatingIsNotNull(owner)) {
            return false;
        }
        long days = em.createQuery("select count(d) from WorkDay d where d.record.owner.id = :u", Long.class)
                .setParameter("u", owner.getId()).getSingleResult();
        return days >= ASK_AFTER_WORK_DAYS;
    }
}
