package com.hwandevblog.mstchgmgmt.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static com.hwandevblog.mstchgmgmt.domain.RevisionPolicy.*;
import static org.assertj.core.api.Assertions.*;

class RevisionPolicyTest {
    private final RevisionPolicy policy = new RevisionPolicy();
    private final Actor requester = new Actor(1, true, Set.of(Role.REQUESTER));
    private final Actor approver = new Actor(2, true, Set.of(Role.APPROVER));
    private final Actor viewer = new Actor(3, true, Set.of(Role.VIEWER));

    private Revision current(State state) { return new Revision(1, 10, 10, state); }
    private void denied(Actor actor, Revision revision, Action action, Failure failure) {
        assertThatThrownBy(() -> policy.require(actor, revision, action))
                .isInstanceOf(Denied.class).extracting(e -> ((Denied) e).failure()).isEqualTo(failure);
    }

    @ParameterizedTest @EnumSource(value = Action.class, names = {"APPROVE", "REJECT"})
    void dualRoleCannotDecideOwnRequest(Action action) {
        denied(new Actor(1, true, Set.of(Role.REQUESTER, Role.APPROVER)),
                current(State.SUBMITTED), action, Failure.FORBIDDEN);
    }

    @ParameterizedTest @EnumSource(value = Action.class,
            names = {"VIEW_DETAIL", "VIEW_ORIGINAL", "EDIT", "SUBMIT", "WITHDRAW", "NEW_REVISION"})
    void differentRequesterCannotAccessOrMutateById(Action action) {
        denied(new Actor(9, true, Set.of(Role.REQUESTER)), current(State.SUBMITTED), action, Failure.FORBIDDEN);
    }

    @ParameterizedTest @EnumSource(Action.class)
    void inactiveAccountIsAlwaysDenied(Action action) {
        denied(new Actor(1, false, EnumSet.allOf(Role.class)), current(State.APPLIED), action, Failure.FORBIDDEN);
    }

    @Test void viewerReadsOnlyAppliedDetailsAndNeverOriginals() {
        assertThatCode(() -> policy.require(viewer, current(State.APPLIED), Action.VIEW_DETAIL)).doesNotThrowAnyException();
        for (State state : State.values()) {
            denied(viewer, current(state), Action.VIEW_ORIGINAL, Failure.FORBIDDEN);
            if (state != State.APPLIED) denied(viewer, current(state), Action.VIEW_DETAIL, Failure.FORBIDDEN);
        }
    }

    @Test void approverReadsSubmittedAndCompletedButNotPrivateDrafts() {
        for (Action action : Set.of(Action.VIEW_DETAIL, Action.VIEW_ORIGINAL)) {
            assertThatCode(() -> policy.require(approver, current(State.SUBMITTED), action)).doesNotThrowAnyException();
            denied(approver, current(State.DRAFT), action, Failure.FORBIDDEN);
            denied(approver, current(State.REJECTED), action, Failure.FORBIDDEN);
            denied(approver, current(State.WITHDRAWN), action, Failure.FORBIDDEN);
        }
        assertThatCode(() -> policy.require(approver, current(State.APPLIED), Action.VIEW_DETAIL)).doesNotThrowAnyException();
    }

    @Test void authorizedActorsMustStillMatchCurrentRevision() {
        for (Action action : Set.of(Action.EDIT, Action.SUBMIT, Action.WITHDRAW, Action.NEW_REVISION)) {
            denied(requester, new Revision(1, 10, 11, State.SUBMITTED), action, Failure.STALE_REVISION);
        }
        for (Action action : Set.of(Action.APPROVE, Action.REJECT)) {
            denied(approver, new Revision(1, 10, 11, State.SUBMITTED), action, Failure.STALE_REVISION);
        }
    }

    @Test void ownersCanReadTheirPreservedOldRevisions() {
        assertThatCode(() -> policy.require(requester,
                new Revision(1, 10, 11, State.WITHDRAWN), Action.VIEW_DETAIL)).doesNotThrowAnyException();
    }

    @Test void exactTransitionMatrixIsEnforced() {
        for (State state : State.values()) {
            for (Action action : Set.of(Action.EDIT, Action.SUBMIT, Action.WITHDRAW,
                    Action.APPROVE, Action.REJECT, Action.NEW_REVISION)) {
                Actor actor = action == Action.APPROVE || action == Action.REJECT ? approver : requester;
                boolean allowed = switch (state) {
                    case DRAFT -> action == Action.EDIT || action == Action.SUBMIT;
                    case SUBMITTED -> Set.of(Action.WITHDRAW, Action.APPROVE, Action.REJECT).contains(action);
                    case REJECTED, WITHDRAWN -> action == Action.NEW_REVISION;
                    case APPLIED -> false;
                };
                if (allowed) assertThatCode(() -> policy.require(actor, current(state), action)).doesNotThrowAnyException();
                else denied(actor, current(state), action, Failure.INVALID_STATE);
            }
        }
    }

    @Test void transitionResultsMatchWorkflow() {
        assertThat(policy.next(requester, current(State.DRAFT), Action.SUBMIT)).isEqualTo(State.SUBMITTED);
        assertThat(policy.next(approver, current(State.SUBMITTED), Action.APPROVE)).isEqualTo(State.APPLIED);
        assertThat(policy.next(approver, current(State.SUBMITTED), Action.REJECT)).isEqualTo(State.REJECTED);
        assertThat(policy.next(requester, current(State.SUBMITTED), Action.WITHDRAW)).isEqualTo(State.WITHDRAWN);
        assertThatThrownBy(() -> policy.next(requester, current(State.REJECTED), Action.NEW_REVISION))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
