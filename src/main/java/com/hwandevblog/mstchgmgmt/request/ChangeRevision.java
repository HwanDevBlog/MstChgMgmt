package com.hwandevblog.mstchgmgmt.request;

import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy.State;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * 리비전의 상태와 임시 작성 사유를 보관한다.
 * 불변 제출 내용은 후속 단계에서 상태와 분리해 저장한다.
 */
@Entity
@Table(name = "change_revision")
public class ChangeRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false)
    private Long requestId;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;
    @Column(nullable = false)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name="based_on_revision_id")
    private Long basedOnRevisionId;

    protected ChangeRevision() { }
    public ChangeRevision(long requestId, String reason) {
        this.requestId = requestId;
        this.revisionNumber = 1;
        this.state = State.DRAFT;
        this.reason = reason;
        this.createdAt = Instant.now();
    }
    public ChangeRevision(long requestId,String reason,int number,long basedOn) {
        this(requestId,reason);this.revisionNumber=number;this.basedOnRevisionId=basedOn;
    }
    public Long getBasedOnRevisionId(){return basedOnRevisionId;}
    public void finish(State next) {
        if(state!=State.SUBMITTED || (next!=State.APPLIED && next!=State.REJECTED && next!=State.WITHDRAWN))throw new IllegalStateException("Submitted decision required");
        state=next;
    }
    public void changeReason(String reason) { this.reason = reason; }
    public void submit() {
        if (state != State.DRAFT) throw new IllegalStateException("Draft required");
        state = State.SUBMITTED;
    }
    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public int getRevisionNumber() { return revisionNumber; }
    public State getState() { return state; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
