package com.hwandevblog.mstchgmgmt.request;

import com.hwandevblog.mstchgmgmt.domain.CatalogRules;
import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * 인증된 요청자의 임시 요청 생성과 조회를 처리한다.
 * URL에 전달된 요청 ID는 소유권 검사를 통과한 뒤에만 응답한다.
 */
@Service
@Transactional(readOnly = true)
public class RequestService {
    private final ChangeRequestRepository requests;
    private final ChangeRevisionRepository revisions;
    private final RevisionPolicy policy = new RevisionPolicy();

    public RequestService(ChangeRequestRepository requests, ChangeRevisionRepository revisions) {
        this.requests = requests;
        this.revisions = revisions;
    }

    public record RequestResponse(long requestId, long requesterId, long revisionId,
                                  int revisionNumber, String state, String reason, Instant createdAt) { }

    public List<RequestResponse> mine(SessionUser user) {
        requireRequester(user);
        return requests.findAllByRequesterIdOrderByIdDesc(user.id()).stream()
                .map(request -> detail(user, request.getId(), request.getCurrentRevisionId())).toList();
    }

    public RequestResponse detail(SessionUser user, long requestId, Long revisionId) {
        var request = requests.findById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long selectedId = revisionId == null ? request.getCurrentRevisionId() : revisionId;
        var revision = revisions.findById(selectedId)
                .filter(row -> row.getRequestId().equals(requestId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        policy.require(user.actor(), new RevisionPolicy.Revision(request.getRequesterId(), revision.getId(),
                request.getCurrentRevisionId(), revision.getState()), RevisionPolicy.Action.VIEW_DETAIL);
        return response(request, revision);
    }

    @Transactional
    public RequestResponse create(SessionUser user, String reason) {
        requireRequester(user);
        // 임시 저장에는 빈 사유를 허용하고, 필수 사유 검사는 제출 단계에서 수행한다.
        String draftReason = reason == null ? "" : reason;
        if (draftReason.codePointCount(0, draftReason.length()) > CatalogRules.MAX_REASON_CODE_POINTS
                || draftReason.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid draft reason");
        }
        var request = requests.saveAndFlush(new ChangeRequest(user.id()));
        var revision = revisions.saveAndFlush(new ChangeRevision(request.getId(), draftReason));
        request.selectRevision(revision.getId());
        return response(request, revision);
    }

    private void requireRequester(SessionUser user) {
        if (!user.active() || !user.roles().contains(RevisionPolicy.Role.REQUESTER)) {
            throw new AccessDeniedException("Requester role required");
        }
    }

    private RequestResponse response(ChangeRequest request, ChangeRevision revision) {
        return new RequestResponse(request.getId(), request.getRequesterId(), revision.getId(),
                revision.getRevisionNumber(), revision.getState().name(), revision.getReason(), request.getCreatedAt());
    }
}
