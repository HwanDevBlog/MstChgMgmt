package com.hwandevblog.mstchgmgmt.request;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 변경 요청의 소유자와 현재 리비전 포인터를 보관한다.
 * 요청과 첫 리비전은 한 트랜잭션에서 생성한다.
 */
@Entity
@Table(name = "change_request")
public class ChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;
    @Column(name = "current_revision_id")
    private Long currentRevisionId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChangeRequest() { }
    public ChangeRequest(long requesterId) {
        this.requesterId = requesterId;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getRequesterId() { return requesterId; }
    public Long getCurrentRevisionId() { return currentRevisionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void selectRevision(long revisionId) { this.currentRevisionId = revisionId; }
}
