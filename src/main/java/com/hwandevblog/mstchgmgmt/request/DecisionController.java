package com.hwandevblog.mstchgmgmt.request;

import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import com.hwandevblog.mstchgmgmt.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 승인 대기·반영 이력과 제출본에 대한 결정 API를 제공한다.
 * 결정 요청에는 화면에서 확인한 리비전 ID와 내용 해시만 받는다.
 */
@RestController
@RequestMapping("/api")
public class DecisionController {
    private final DecisionService service;
    public DecisionController(DecisionService service){this.service=service;}
    public record Input(@NotNull Long revisionId,@NotNull @Pattern(regexp="[0-9a-f]{64}")String contentHash,String reason) {
        DecisionService.Input value(){return new DecisionService.Input(revisionId,contentHash,reason);}
    }
    @GetMapping("/approvals/pending") public List<RequestService.RequestResponse> pending(@AuthenticationPrincipal SessionUser user){return service.pending(user);}
    @GetMapping("/history/applied") public List<RequestService.RequestResponse> applied(@AuthenticationPrincipal SessionUser user){return service.applied(user);}
    @PostMapping("/requests/{requestId}/approve") public DecisionService.Receipt approve(@AuthenticationPrincipal SessionUser user,@PathVariable("requestId") long requestId,@Valid @RequestBody Input input){return service.decide(user,requestId,input.value(),RevisionPolicy.Action.APPROVE);}
    @PostMapping("/requests/{requestId}/reject") public DecisionService.Receipt reject(@AuthenticationPrincipal SessionUser user,@PathVariable("requestId") long requestId,@Valid @RequestBody Input input){return service.decide(user,requestId,input.value(),RevisionPolicy.Action.REJECT);}
    @PostMapping("/requests/{requestId}/withdraw") public DecisionService.Receipt withdraw(@AuthenticationPrincipal SessionUser user,@PathVariable("requestId") long requestId,@Valid @RequestBody Input input){return service.decide(user,requestId,input.value(),RevisionPolicy.Action.WITHDRAW);}
    @PostMapping("/requests/{requestId}/revisions") public RequestService.RequestResponse newDraft(@AuthenticationPrincipal SessionUser user,@PathVariable("requestId") long requestId,@Valid @RequestBody Input input){return service.newDraft(user,requestId,input.value());}
    @GetMapping("/requests/{requestId}/history") public List<DecisionService.RevisionInfo> history(@AuthenticationPrincipal SessionUser user,@PathVariable("requestId") long requestId){return service.history(user,requestId);}
    @GetMapping("/requests/{requestId}/revisions/{revisionId}/decision") public DecisionService.Receipt receipt(@AuthenticationPrincipal SessionUser user,@PathVariable("requestId") long requestId,@PathVariable("revisionId") long revisionId){return service.receipt(user,requestId,revisionId);}
}
