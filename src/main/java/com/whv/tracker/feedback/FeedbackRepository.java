package com.whv.tracker.feedback;

import com.whv.tracker.user.AppUser;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    long countByOwnerAndCreatedAtGreaterThanEqual(AppUser owner, LocalDateTime since);

    boolean existsByOwnerAndRatingIsNotNull(AppUser owner);
}
