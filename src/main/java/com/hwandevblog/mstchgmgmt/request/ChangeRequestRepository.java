package com.hwandevblog.mstchgmgmt.request;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {
    List<ChangeRequest> findAllByRequesterIdOrderByIdDesc(long requesterId);
}
