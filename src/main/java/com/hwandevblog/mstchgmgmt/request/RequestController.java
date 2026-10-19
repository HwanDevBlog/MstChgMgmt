package com.hwandevblog.mstchgmgmt.request;

import com.hwandevblog.mstchgmgmt.security.SessionUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private final RequestService service;
    public RequestController(RequestService service) { this.service = service; }
    public record CreateRequest(String reason) { }

    @GetMapping("/mine")
    public List<RequestService.RequestResponse> mine(@AuthenticationPrincipal SessionUser user) {
        return service.mine(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestService.RequestResponse create(@AuthenticationPrincipal SessionUser user,
                                                @RequestBody CreateRequest input) {
        return service.create(user, input.reason());
    }

    @GetMapping("/{requestId}")
    public RequestService.RequestResponse detail(@AuthenticationPrincipal SessionUser user, @PathVariable("requestId") long requestId) {
        return service.detail(user, requestId, null);
    }

    @GetMapping("/{requestId}/revisions/{revisionId}")
    public RequestService.RequestResponse revision(@AuthenticationPrincipal SessionUser user,
                                                  @PathVariable("requestId") long requestId, @PathVariable("revisionId") long revisionId) {
        return service.detail(user, requestId, revisionId);
    }
}
