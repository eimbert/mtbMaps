package com.paygoon.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.paygoon.service.PlanChatService;
import com.paygoon.service.PlanChatSummaryService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/plan-folders/{folderId}/chat")
@RequiredArgsConstructor
public class PlanChatController {
    private final PlanChatService chat;
    private final PlanChatSummaryService summaries;

    public record SendRequest(@NotBlank String body) {}
    public record ReadRequest(Long lastReadId) {}
    public record ReadResponse(Long lastReadId) {}
    public record SummaryRequest(String language) {}

    @GetMapping
    public PlanChatService.Inbox inbox(@PathVariable Long folderId,
            @RequestParam(defaultValue = "0") long afterId, Authentication authentication) {
        return chat.inbox(folderId, authentication.getName(), afterId);
    }

    @PostMapping
    public PlanChatService.Message send(@PathVariable Long folderId, @RequestBody SendRequest request,
            Authentication authentication) {
        return chat.send(folderId, authentication.getName(), request.body());
    }

    @PutMapping("/read")
    public ReadResponse markRead(@PathVariable Long folderId, @RequestBody ReadRequest request,
            Authentication authentication) {
        return new ReadResponse(chat.markRead(folderId, authentication.getName(),
                request.lastReadId() == null ? 0L : request.lastReadId()));
    }

    @PostMapping("/summary")
    public PlanChatSummaryService.Summary summarize(@PathVariable Long folderId,
            @RequestBody(required = false) SummaryRequest request, Authentication authentication) {
        return summaries.summarize(folderId, authentication.getName(), request == null ? "es" : request.language());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long folderId,
            @RequestParam(defaultValue = "true") boolean inChat, Authentication authentication) {
        return chat.stream(folderId, authentication.getName(), inChat);
    }
}
