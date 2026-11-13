package com.whv.tracker.push;

import com.whv.tracker.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByOwnerOrderByCreatedAtAsc(AppUser owner);

    long deleteByOwnerAndEndpoint(AppUser owner, String endpoint);

    @Query("select s from PushSubscription s join fetch s.owner order by s.owner.id")
    List<PushSubscription> findAllWithOwner();

    @Query("select count(distinct s.owner.id) from PushSubscription s")
    long countUsers();
}
