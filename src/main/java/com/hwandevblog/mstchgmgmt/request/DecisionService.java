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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 고정 제출본을 기준으로 승인·반려·철회를 처리하고 성공 이력을 보관한다.
 * 승인 시 장비 변경과 이력·최종 상태를 하나의 트랜잭션으로 반영한다.
 */
@Service
@Transactional(readOnly=true)
public class DecisionService {
    private final ChangeRequestRepository requests;
    private final ChangeRevisionRepository revisions;
    private final SubmissionService submissions;
    private final RevisionWorkspaceService workspaces;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RevisionPolicy policy=new RevisionPolicy();

    public DecisionService(ChangeRequestRepository requests,ChangeRevisionRepository revisions,SubmissionService submissions,
                           RevisionWorkspaceService workspaces,JdbcTemplate jdbc,ObjectMapper json) {
        this.requests=requests;this.revisions=revisions;this.submissions=submissions;this.workspaces=workspaces;this.jdbc=jdbc;this.json=json;
    }
    public record Input(long revisionId,String contentHash,String reason) { }
    public record AppliedChange(String equipmentCode,String operation,RevisionWorkspaceService.Values before,
                                RevisionWorkspaceService.Values after) { }
    public record Receipt(UUID decisionId,long requestId,long revisionId,String contentHash,String action,String state,
                          long actorId,String actorUsername,String reason,Instant processedAt,List<AppliedChange> changes) { }
    public record RevisionInfo(long revisionId,int revisionNumber,String state,String reason,Instant createdAt) { }

    public List<RequestService.RequestResponse> pending(SessionUser user) {
        if(!user.active() || !user.roles().contains(RevisionPolicy.Role.APPROVER))throw new AccessDeniedException("Approver required");
        return jdbc.query("""
                SELECT q.id,q.requester_id,r.id,r.revision_number,r.state,s.snapshot_json,q.created_at
                FROM change_request q JOIN change_revision r ON r.id=q.current_revision_id
                JOIN revision_submission s ON s.revision_id=r.id
                WHERE r.state='SUBMITTED' AND q.requester_id<>? ORDER BY q.id DESC
                """,(rs,n)->new RequestService.RequestResponse(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getInt(4),rs.getString(5),
                decode(rs.getString(6),SubmissionService.Snapshot.class).reason(),rs.getTimestamp(7).toInstant()),user.id());
    }
    public List<RequestService.RequestResponse> applied(SessionUser user) {
        if(!user.active() || user.roles().isEmpty())throw new AccessDeniedException("Active role required");
        return jdbc.query("""
                SELECT q.id,q.requester_id,r.id,r.revision_number,r.state,s.snapshot_json,q.created_at
                FROM change_request q JOIN change_revision r ON r.id=q.current_revision_id
                JOIN revision_submission s ON s.revision_id=r.id WHERE r.state='APPLIED' ORDER BY q.id DESC
                """,(rs,n)->new RequestService.RequestResponse(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getInt(4),rs.getString(5),
                decode(rs.getString(6),SubmissionService.Snapshot.class).reason(),rs.getTimestamp(7).toInstant()));
    }

    @Transactional
    public Receipt decide(SessionUser user,long requestId,Input input,RevisionPolicy.Action action) {
        var request=requests.findLockedById(requestId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision=revision(requestId,input.revisionId(),true);
        // 기존 영수증을 반환하기 전에도 활성·역할·자기 결정 금지를 다시 검사한다.
        basicPermission(user,request,action);
        if(!revision.getId().equals(request.getCurrentRevisionId()))throw conflict("STALE_REVISION");
        String reason=reason(action,input.reason());
        var existing=stored(input.revisionId());
        if(existing!=null) {
            if(existing.actorId()==user.id() && existing.action().equals(action.name())
                    && existing.contentHash().equals(input.contentHash()) && existing.reason().equals(reason))return existing;
            throw conflict("DECISION_ALREADY_TAKEN");
        }
        var next=policy.next(user.actor(),view(request,revision),action);
        var snapshot=submissions.get(user,requestId,revision.getId());
        if(!snapshot.contentHash().equals(input.contentHash()) || !canonicalHash(snapshot).equals(snapshot.contentHash()))throw conflict("SUBMISSION_MISMATCH");
        UUID id=UUID.randomUUID();
        var changes=action==RevisionPolicy.Action.APPROVE?apply(snapshot,id):List.<AppliedChange>of();
        var receipt=new Receipt(id,requestId,revision.getId(),snapshot.contentHash(),action.name(),next.name(),user.id(),user.username(),reason,Instant.now(),changes);
        jdbc.update("INSERT INTO revision_decision(decision_id,revision_id,actor_id,action,content_hash,reason,processed_at,result_json) VALUES(?,?,?,?,?,?,?,?)",
                id,revision.getId(),user.id(),action.name(),snapshot.contentHash(),reason,java.sql.Timestamp.from(receipt.processedAt()),encode(receipt));
        revision.finish(next);
        return receipt;
    }

    private List<AppliedChange> apply(SubmissionService.Snapshot snapshot,UUID decisionId) {
        var rows=snapshot.rows().stream().sorted(Comparator.comparing(RevisionWorkspaceService.Comparison::equipmentCode)).toList();
        var current=new LinkedHashMap<String,RevisionWorkspaceService.Values>();
        // 기존 행을 코드순으로 모두 잠그고 변경 없음 행도 충돌 검사에 포함한다.
        for(var row:rows) {
            var values=jdbc.query("SELECT name,category,daily_fee_krw,active,version FROM equipment WHERE code=? FOR UPDATE",
                    (rs,n)->new RevisionWorkspaceService.Values(rs.getString(1),rs.getString(2),Long.toString(rs.getLong(3)),rs.getBoolean(4),Long.toString(rs.getLong(5))),row.equipmentCode());
            var actual=values.isEmpty()?null:values.getFirst();
            if(row.operation().equals("CREATE")) {
                if(actual!=null)throw conflict("CREATE_EXISTS");
            } else {
                if(actual==null || !actual.equals(row.before()) || !actual.version().equals(row.expectedVersion()))throw conflict("CATALOG_CONFLICT");
                if(!sameValues(actual,row.after()) && Long.parseLong(actual.version())==Long.MAX_VALUE)throw conflict("VERSION_EXHAUSTED");
            }
            current.put(row.equipmentCode(),actual);
        }
        var changes=new ArrayList<AppliedChange>();
        for(var row:rows) {
            var before=current.get(row.equipmentCode());
            if(before!=null && sameValues(before,row.after()))continue;
            String nextVersion=before==null?"0":Long.toString(Long.parseLong(before.version())+1);
            var after=new RevisionWorkspaceService.Values(row.after().name(),row.after().category(),row.after().dailyFeeKrw(),row.after().active(),nextVersion);
            if(before==null) {
                // 신규 코드는 잠글 행이 없으므로 UNIQUE 제약으로 동시 생성 충돌을 최종 차단한다.
                jdbc.update("INSERT INTO equipment(code,name,category,daily_fee_krw,active,version) VALUES(?,?,?,?,?,0)",
                        row.equipmentCode(),after.name(),after.category(),Long.parseLong(after.dailyFeeKrw()),after.active());
            } else {
                int changed=jdbc.update("UPDATE equipment SET name=?,category=?,daily_fee_krw=?,active=?,version=version+1 WHERE code=? AND version=?",
                        after.name(),after.category(),Long.parseLong(after.dailyFeeKrw()),after.active(),row.equipmentCode(),Long.parseLong(before.version()));
                if(changed!=1)throw conflict("CATALOG_CONFLICT");
            }
            var change=new AppliedChange(row.equipmentCode(),row.operation(),before,after);changes.add(change);
            jdbc.update("INSERT INTO equipment_change(id,decision_id,equipment_code,operation,before_json,after_json,before_version,after_version) VALUES(?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),decisionId,row.equipmentCode(),row.operation(),before==null?null:encode(before),encode(after),
                    before==null?null:Long.valueOf(before.version()),Long.valueOf(nextVersion));
        }
        return List.copyOf(changes);
    }

    public Receipt receipt(SessionUser user,long requestId,long revisionId) {
        var request=requests.findById(requestId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision=revision(requestId,revisionId,false);
        policy.require(user.actor(),view(request,revision),RevisionPolicy.Action.VIEW_DETAIL);
        var receipt=stored(revisionId);
        if(receipt==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return receipt;
    }
    public List<RevisionInfo> history(SessionUser user,long requestId) {
        var request=requests.findById(requestId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var current=revision(requestId,request.getCurrentRevisionId(),false);
        policy.require(user.actor(),view(request,current),RevisionPolicy.Action.VIEW_DETAIL);
        return revisions.findAllByRequestIdOrderByRevisionNumberDesc(requestId).stream().filter(row->{
            try {policy.require(user.actor(),view(request,row),RevisionPolicy.Action.VIEW_DETAIL);return true;}
            catch(RevisionPolicy.Denied denied){return false;}
        }).map(row->new RevisionInfo(row.getId(),row.getRevisionNumber(),row.getState().name(),row.getReason(),row.getCreatedAt())).toList();
    }

    @Transactional
    public RequestService.RequestResponse newDraft(SessionUser user,long requestId,Input input) {
        var request=requests.findLockedById(requestId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        basicPermission(user,request,RevisionPolicy.Action.WITHDRAW);
        var base=revision(requestId,input.revisionId(),true);
        var snapshot=submissions.get(user,requestId,base.getId());
        if(!snapshot.contentHash().equals(input.contentHash()))throw conflict("SUBMISSION_MISMATCH");
        if(!base.getId().equals(request.getCurrentRevisionId())) {
            var current=revision(requestId,request.getCurrentRevisionId(),true);
            if(current.getState()==RevisionPolicy.State.DRAFT && base.getId().equals(current.getBasedOnRevisionId()))return response(request,current);
            throw conflict("STALE_REVISION");
        }
        policy.require(user.actor(),view(request,base),RevisionPolicy.Action.NEW_REVISION);
        var next=revisions.saveAndFlush(new ChangeRevision(requestId,snapshot.reason(),base.getRevisionNumber()+1,base.getId()));
        var original=workspaces.original(user,requestId,base.getId());
        jdbc.update("INSERT INTO revision_workspace(revision_id,file_id,filename,original,original_sha256,uploaded_at) VALUES(?,?,?,?,?,now())",
                next.getId(),UUID.randomUUID(),snapshot.filename(),original.bytes(),snapshot.originalSha256());
        request.selectRevision(next.getId());
        return response(request,next);
    }

    private void basicPermission(SessionUser user,ChangeRequest request,RevisionPolicy.Action action) {
        boolean owner=user.id()==request.getRequesterId();
        boolean allowed=action==RevisionPolicy.Action.WITHDRAW?owner&&user.roles().contains(RevisionPolicy.Role.REQUESTER)
                :!owner&&user.roles().contains(RevisionPolicy.Role.APPROVER);
        if(!user.active() || !allowed)throw new AccessDeniedException("Decision role or ownership denied");
    }
    private String reason(RevisionPolicy.Action action,String input) {
        String value=input==null?"":input;
        if(action==RevisionPolicy.Action.REJECT && !CatalogRules.validReason(value))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REJECTION_REASON_REQUIRED");
        if(value.codePointCount(0,value.length())>500 || value.indexOf(0)>=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_REASON");
        if(action==RevisionPolicy.Action.APPROVE && !value.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_REASON");
        return value;
    }
    private String canonicalHash(SubmissionService.Snapshot snapshot) {
        return new SubmissionContent(snapshot.requestId(),snapshot.revisionId(),snapshot.requesterId(),snapshot.rulesVersion(),snapshot.reason(),
                snapshot.rows().stream().map(row->new SubmissionContent.Item(row.operation(),row.equipmentCode(),row.before()==null?null:value(row.before()),
                        row.expectedVersion()==null?null:Long.valueOf(row.expectedVersion()),value(row.after()))).toList()).hash();
    }
    private SubmissionContent.Value value(RevisionWorkspaceService.Values row){return new SubmissionContent.Value(row.name(),row.category(),Long.parseLong(row.dailyFeeKrw()),row.active());}
    private boolean sameValues(RevisionWorkspaceService.Values a,RevisionWorkspaceService.Values b) {
        return a.name().equals(b.name())&&a.category().equals(b.category())&&a.dailyFeeKrw().equals(b.dailyFeeKrw())&&a.active()==b.active();
    }
    private Receipt stored(long revisionId) {
        var rows=jdbc.query("SELECT result_json FROM revision_decision WHERE revision_id=?",(rs,n)->decode(rs.getString(1),Receipt.class),revisionId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private ChangeRevision revision(long requestId,long revisionId,boolean lock) {
        return (lock?revisions.findLockedById(revisionId):revisions.findById(revisionId)).filter(row->row.getRequestId().equals(requestId))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private RevisionPolicy.Revision view(ChangeRequest request,ChangeRevision revision) {
        return new RevisionPolicy.Revision(request.getRequesterId(),revision.getId(),request.getCurrentRevisionId(),revision.getState());
    }
    private RequestService.RequestResponse response(ChangeRequest q,ChangeRevision r) {
        return new RequestService.RequestResponse(q.getId(),q.getRequesterId(),r.getId(),r.getRevisionNumber(),r.getState().name(),r.getReason(),q.getCreatedAt());
    }
    private ResponseStatusException conflict(String code){return new ResponseStatusException(HttpStatus.CONFLICT,code);}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException exception){throw new IllegalStateException(exception);}}
    private <T>T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(JsonProcessingException exception){throw new IllegalStateException(exception);}}
}
