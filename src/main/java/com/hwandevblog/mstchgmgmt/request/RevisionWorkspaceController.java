package com.hwandevblog.mstchgmgmt.request;

import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 인증된 사용자의 리비전 파일 업로드와 검증 결과 조회를 연결한다.
 * 원본 파일은 브라우저가 실행하지 않는 첨부 파일로만 제공한다.
 */
@RestController
@RequestMapping("/api/requests/{requestId}/revisions/{revisionId}")
public class RevisionWorkspaceController {
    private final RevisionWorkspaceService service;
    public RevisionWorkspaceController(RevisionWorkspaceService service) { this.service = service; }
    public record ValidateInput(@jakarta.validation.constraints.NotNull UUID fileId) { }

    @GetMapping("/workspace")
    public RevisionWorkspaceService.Workspace workspace(@AuthenticationPrincipal SessionUser user,
                                                        @PathVariable long requestId, @PathVariable long revisionId) {
        return service.get(user, requestId, revisionId);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RevisionWorkspaceService.Workspace upload(@AuthenticationPrincipal SessionUser user,
                                                     @PathVariable long requestId, @PathVariable long revisionId,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
        return service.upload(user, requestId, revisionId, file);
    }

    @PostMapping("/validation")
    public RevisionWorkspaceService.Preview validate(@AuthenticationPrincipal SessionUser user,
                                                     @PathVariable long requestId, @PathVariable long revisionId,
                                                     @jakarta.validation.Valid @RequestBody ValidateInput input) {
        return service.validate(user, requestId, revisionId, input.fileId());
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> original(@AuthenticationPrincipal SessionUser user,
                                           @PathVariable long requestId, @PathVariable long revisionId) {
        var file = service.original(user, requestId, revisionId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }
}
