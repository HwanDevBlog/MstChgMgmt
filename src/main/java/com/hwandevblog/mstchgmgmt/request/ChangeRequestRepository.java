package com.hwandevblog.mstchgmgmt.request;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ChangeRequest r where r.id = :id")
    java.util.Optional<ChangeRequest> findLockedById(@org.springframework.data.repository.query.Param("id") long id);

    List<ChangeRequest> findAllByRequesterIdOrderByIdDesc(long requesterId);
}
