package com.hwandevblog.mstchgmgmt.request;

import com.hwandevblog.mstchgmgmt.security.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * 제출 전 확인 내용과 제출 후 고정된 내용을 조회하는 API를 제공한다.
 * 클라이언트는 확인한 식별자와 내용 해시만 전송한다.
 */
@RestController
@RequestMapping("/api/requests/{requestId}/revisions/{revisionId}")
public class SubmissionController {
    private final SubmissionService service;
    public SubmissionController(SubmissionService service){this.service=service;}
    public record Input(@NotNull UUID fileId,@NotNull UUID validationId,@NotNull @Pattern(regexp="[0-9a-f]{64}") String contentHash) { }
    public record ReasonInput(String reason) { }
    @PostMapping("/reason")
    public RequestService.RequestResponse reason(@AuthenticationPrincipal SessionUser user,@PathVariable long requestId,
                                                 @PathVariable long revisionId,@RequestBody ReasonInput input) {
        return service.updateReason(user,requestId,revisionId,input.reason());
    }
    @GetMapping("/submission-preview")
    public SubmissionService.Snapshot prepare(@AuthenticationPrincipal SessionUser user,@PathVariable long requestId,@PathVariable long revisionId) {
        return service.prepare(user,requestId,revisionId);
    }
    @PostMapping("/submission")
    public SubmissionService.Snapshot submit(@AuthenticationPrincipal SessionUser user,@PathVariable long requestId,@PathVariable long revisionId,@Valid @RequestBody Input input) {
        return service.submit(user,requestId,revisionId,new SubmissionService.SubmitInput(input.fileId(),input.validationId(),input.contentHash()));
    }
    @GetMapping("/submission")
    public SubmissionService.Snapshot get(@AuthenticationPrincipal SessionUser user,@PathVariable long requestId,@PathVariable long revisionId) {
        return service.get(user,requestId,revisionId);
    }
}
