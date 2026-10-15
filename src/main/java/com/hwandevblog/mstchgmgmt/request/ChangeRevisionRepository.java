package com.hwandevblog.mstchgmgmt.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ChangeRevisionRepository extends JpaRepository<ChangeRevision, Long> {
    java.util.List<ChangeRevision> findAllByRequestIdOrderByRevisionNumberDesc(long requestId);

    // 같은 리비전의 파일 교체와 검증을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChangeRevision r where r.id = :id")
    Optional<ChangeRevision> findLockedById(@Param("id") long id);
}
