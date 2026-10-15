package com.hwandevblog.mstchgmgmt.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwandevblog.mstchgmgmt.domain.CatalogRules;
import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import com.hwandevblog.mstchgmgmt.domain.SubmissionContent;
import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 화면에서 검토한 파일과 검증 결과를 대조해 불변 제출본을 생성한다.
 * 제출본 저장과 리비전 상태 변경을 하나의 트랜잭션으로 처리한다.
 */
@Service
@Transactional(readOnly = true)
public class SubmissionService {
    private final ChangeRequestRepository requests;
    private final ChangeRevisionRepository revisions;
    private final RevisionWorkspaceService workspaces;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RevisionPolicy policy = new RevisionPolicy();
    public SubmissionService(ChangeRequestRepository requests, ChangeRevisionRepository revisions,
                             RevisionWorkspaceService workspaces, JdbcTemplate jdbc, ObjectMapper json) {
        this.requests=requests; this.revisions=revisions; this.workspaces=workspaces; this.jdbc=jdbc; this.json=json;
    }
    public record Snapshot(long requestId, long revisionId, long requesterId, int revisionNumber,
                           String reason, String rulesVersion, UUID fileId, UUID validationId,
                           String filename, String originalSha256, String contentHash,
                           List<RevisionWorkspaceService.Comparison> rows, UUID submissionId, Instant submittedAt) { }
    public record SubmitInput(UUID fileId, UUID validationId, String contentHash) { }

    @Transactional
    public RequestService.RequestResponse updateReason(SessionUser user, long requestId, long revisionId, String reason) {
        var request=requests.findLockedById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision=revision(requestId,revisionId,true);
        require(user,request,revision,RevisionPolicy.Action.EDIT);
        String value=reason==null?"":reason;
        if(value.codePointCount(0,value.length())>CatalogRules.MAX_REASON_CODE_POINTS || value.indexOf(0)>=0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_REASON");
        }
        revision.changeReason(value);
        return new RequestService.RequestResponse(requestId,request.getRequesterId(),revisionId,revision.getRevisionNumber(),
                revision.getState().name(),value,request.getCreatedAt());
    }

    public Snapshot prepare(SessionUser user, long requestId, long revisionId) {
        var request=requests.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision=revision(requestId,revisionId,false);
        require(user,request,revision,RevisionPolicy.Action.SUBMIT);
        return proposal(user,request,revision);
    }

    @Transactional
    public Snapshot submit(SessionUser user, long requestId, long revisionId, SubmitInput input) {
        // 요청과 리비전을 같은 순서로 잠가 동시 제출과 파일 교체를 직렬화한다.
        var request=requests.findLockedById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision=revision(requestId,revisionId,true);
        if (!user.active() || !user.roles().contains(RevisionPolicy.Role.REQUESTER) || user.id()!=request.getRequesterId()) {
            throw new AccessDeniedException("Owner requester required");
        }
        if (!revision.getId().equals(request.getCurrentRevisionId())) throw conflict("STALE_REVISION");
        var existing=stored(revisionId);
        if (existing!=null) {
            // 응답 유실 후 동일한 제출 요청만 재사용한다. 다른 묶음으로 덮어쓰지 않는다.
            if (revision.getState()!=RevisionPolicy.State.SUBMITTED || !matches(existing,input)) throw conflict("SUBMISSION_MISMATCH");
            return existing;
        }
        require(user,request,revision,RevisionPolicy.Action.SUBMIT);
        var proposed=proposal(user,request,revision);
        if (!matches(proposed,input)) throw conflict("SUBMISSION_MISMATCH");
        var frozen=new Snapshot(proposed.requestId(),proposed.revisionId(),proposed.requesterId(),proposed.revisionNumber(),
                proposed.reason(),proposed.rulesVersion(),proposed.fileId(),proposed.validationId(),proposed.filename(),
                proposed.originalSha256(),proposed.contentHash(),proposed.rows(),UUID.randomUUID(),Instant.now());
        var original=workspaces.original(user,requestId,revisionId);
        jdbc.update("""
                INSERT INTO revision_submission(submission_id,request_id,revision_id,requester_id,file_id,validation_id,
                content_hash,filename,original,original_sha256,snapshot_json,submitted_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, frozen.submissionId(),requestId,revisionId,user.id(),frozen.fileId(),frozen.validationId(),
                frozen.contentHash(),frozen.filename(),original.bytes(),frozen.originalSha256(),encode(frozen),
                java.sql.Timestamp.from(frozen.submittedAt()));
        revision.submit();
        return frozen;
    }

    public Snapshot get(SessionUser user,long requestId,long revisionId) {
        var request=requests.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision=revision(requestId,revisionId,false);
        require(user,request,revision,RevisionPolicy.Action.VIEW_DETAIL);
        var value=stored(revisionId);
        if(value==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return value;
    }

    private Snapshot proposal(SessionUser user,ChangeRequest request,ChangeRevision revision) {
        if(!CatalogRules.validReason(revision.getReason())) throw conflict("REASON_REQUIRED");
        var workspace=workspaces.get(user,request.getId(),revision.getId());
        var preview=workspace.preview();
        if(preview==null || !preview.valid()) throw conflict("VALIDATION_REQUIRED");
        if(preview.stale()) throw conflict("STALE_CATALOG");
        if(preview.created()+preview.changed()==0) throw conflict("NO_CHANGES");
        var items=preview.rows().stream().map(row -> new SubmissionContent.Item(row.operation(),row.equipmentCode(),
                row.before()==null?null:value(row.before()),row.expectedVersion()==null?null:Long.valueOf(row.expectedVersion()),value(row.after()))).toList();
        var content=new SubmissionContent(request.getId(),revision.getId(),request.getRequesterId(),preview.rulesVersion(),revision.getReason(),items);
        return new Snapshot(request.getId(),revision.getId(),request.getRequesterId(),revision.getRevisionNumber(),revision.getReason(),
                preview.rulesVersion(),workspace.fileId(),preview.validationId(),workspace.filename(),workspace.originalSha256(),content.hash(),
                List.copyOf(preview.rows()),null,null);
    }
    private SubmissionContent.Value value(RevisionWorkspaceService.Values row) {
        return new SubmissionContent.Value(row.name(),row.category(),Long.parseLong(row.dailyFeeKrw()),row.active());
    }
    private boolean matches(Snapshot snapshot,SubmitInput input) {
        return snapshot.fileId().equals(input.fileId()) && snapshot.validationId().equals(input.validationId())
                && snapshot.contentHash().equals(input.contentHash());
    }
    private ChangeRevision revision(long requestId,long revisionId,boolean lock) {
        return (lock?revisions.findLockedById(revisionId):revisions.findById(revisionId))
                .filter(row->row.getRequestId().equals(requestId)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private void require(SessionUser user,ChangeRequest request,ChangeRevision revision,RevisionPolicy.Action action) {
        policy.require(user.actor(),new RevisionPolicy.Revision(request.getRequesterId(),revision.getId(),request.getCurrentRevisionId(),revision.getState()),action);
    }
    private Snapshot stored(long revisionId) {
        var values=jdbc.query("SELECT snapshot_json FROM revision_submission WHERE revision_id=?",(rs,n)->decode(rs.getString(1)),revisionId);
        return values.isEmpty()?null:values.getFirst();
    }
    private ResponseStatusException conflict(String code) { return new ResponseStatusException(HttpStatus.CONFLICT,code); }
    private String encode(Snapshot value) {
        try{return json.writeValueAsString(value);}catch(JsonProcessingException exception){throw new IllegalStateException(exception);}
    }
    private Snapshot decode(String value) {
        try{return json.readValue(value,Snapshot.class);}catch(JsonProcessingException exception){throw new IllegalStateException(exception);}
    }
}
