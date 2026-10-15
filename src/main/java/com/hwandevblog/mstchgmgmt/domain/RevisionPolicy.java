package com.hwandevblog.mstchgmgmt.domain;

import java.util.Set;

/**
 * 사용자 역할과 요청 소유권, 현재 리비전과 상태에 따라 허용할 동작을 판단한다.
 * 후속 서버 서비스에서 인증된 사용자 정보를 전달해 객체별 접근을 검사한다.
 */
public final class RevisionPolicy {
    public enum Role { REQUESTER, APPROVER, VIEWER }
    public enum State { DRAFT, SUBMITTED, APPLIED, REJECTED, WITHDRAWN }
    public enum Action { VIEW_DETAIL, VIEW_ORIGINAL, EDIT, SUBMIT, WITHDRAW, APPROVE, REJECT, NEW_REVISION }
    public enum Failure { FORBIDDEN, STALE_REVISION, INVALID_STATE }
    public record Actor(long id, boolean active, Set<Role> roles) {
        public Actor { roles = Set.copyOf(roles); }
        boolean has(Role role) { return roles.contains(role); }
    }
    public record Revision(long ownerId, long revisionId, long currentRevisionId, State state) { }
    public static final class Denied extends RuntimeException {
        private final Failure failure;
        public Denied(Failure failure) { super(failure.name()); this.failure = failure; }
        public Failure failure() { return failure; }
    }

    public void require(Actor actor, Revision revision, Action action) {
        if (!actor.active()) deny(Failure.FORBIDDEN);
        boolean owner = actor.id() == revision.ownerId();
        boolean requester = owner && actor.has(Role.REQUESTER);
        boolean approver = !owner && actor.has(Role.APPROVER);
        boolean completedReader = !actor.roles().isEmpty() && revision.state() == State.APPLIED;

        boolean authorized = switch (action) {
            case VIEW_DETAIL -> requester || completedReader
                    || (approver && revision.state() == State.SUBMITTED);
            case VIEW_ORIGINAL -> requester || (approver && revision.state() == State.SUBMITTED);
            case EDIT, SUBMIT, WITHDRAW, NEW_REVISION -> requester;
            case APPROVE, REJECT -> approver;
        };
        if (!authorized) deny(Failure.FORBIDDEN);
        if (action == Action.VIEW_DETAIL || action == Action.VIEW_ORIGINAL) return;
        if (revision.revisionId() != revision.currentRevisionId()) deny(Failure.STALE_REVISION);
        boolean stateAllowed = switch (action) {
            case EDIT, SUBMIT -> revision.state() == State.DRAFT;
            case WITHDRAW, APPROVE, REJECT -> revision.state() == State.SUBMITTED;
            case NEW_REVISION -> revision.state() == State.REJECTED || revision.state() == State.WITHDRAWN;
            default -> false;
        };
        if (!stateAllowed) deny(Failure.INVALID_STATE);
    }

    public State next(Actor actor, Revision revision, Action action) {
        require(actor, revision, action);
        return switch (action) {
            case SUBMIT -> State.SUBMITTED;
            case APPROVE -> State.APPLIED;
            case REJECT -> State.REJECTED;
            case WITHDRAW -> State.WITHDRAWN;
            case EDIT -> State.DRAFT;
            // 새 리비전은 별도 행으로 생성해 기존 제출본의 상태를 보존한다.
            default -> throw new IllegalArgumentException("Action does not change this revision's state");
        };
    }

    private static void deny(Failure failure) { throw new Denied(failure); }
}
