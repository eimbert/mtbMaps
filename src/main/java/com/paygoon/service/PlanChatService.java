package com.paygoon.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.paygoon.model.AppUser;
import com.paygoon.model.PlanChatMessage;
import com.paygoon.model.PlanChatReadState;
import com.paygoon.model.PlanFolder;
import com.paygoon.model.PlanFolderMember;
import com.paygoon.repository.PlanChatMessageRepository;
import com.paygoon.repository.PlanChatReadStateRepository;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanChatService {
    private final PlanFolderRepository folders;
    private final PlanFolderMemberRepository members;
    private final PlanChatMessageRepository messages;
    private final PlanChatReadStateRepository readStates;
    private final UserRepository users;
    private final Map<Long, CopyOnWriteArrayList<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public record Message(Long id, Long authorId, String authorName, String body, LocalDateTime createdAt) {}
    public record Inbox(List<Message> messages, Long lastReadId, long unreadCount, long totalNewCount) {}
    public record Participant(Long userId, String name) {}
    private record Subscriber(Long userId, String name, boolean inChat, long expiresAt, SseEmitter emitter) {}

    public Inbox inbox(Long folderId, String email, long afterId) {
        AppUser user = user(email);
        requireAccess(folderId, user);
        long cursor = Math.max(0L, afterId);
        List<PlanChatMessage> found;
        if (cursor == 0L) {
            found = new ArrayList<>(messages.findTop100ByFolderIdOrderByIdDesc(folderId));
            found.sort(Comparator.comparing(PlanChatMessage::getId));
        } else {
            found = messages.findTop100ByFolderIdAndIdGreaterThanOrderByIdAsc(folderId, cursor);
        }
        long lastReadId = readStates.findByFolderIdAndUserId(folderId, user.getId())
                .map(PlanChatReadState::getLastReadMessageId).orElse(0L);
        long unread = messages.countByFolderIdAndIdGreaterThanAndAuthorIdNot(folderId, lastReadId, user.getId());
        long totalNew = messages.countByFolderIdAndIdGreaterThan(folderId, lastReadId);
        return new Inbox(found.stream().map(this::toMessage).toList(), lastReadId, unread, totalNew);
    }

    public Message send(Long folderId, String email, String body) {
        AppUser user = user(email);
        PlanFolder folder = requireAccess(folderId, user);
        String cleaned = body == null ? "" : body.trim();
        if (cleaned.isEmpty() || cleaned.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje debe tener entre 1 y 2000 caracteres");
        }
        PlanChatMessage message = new PlanChatMessage();
        message.setFolder(folder);
        message.setAuthor(user);
        message.setBody(cleaned);
        Message saved = toMessage(messages.saveAndFlush(message));
        broadcast(folderId, saved);
        return saved;
    }

    public long markRead(Long folderId, String email, long requestedId) {
        AppUser user = user(email);
        PlanFolder folder = requireAccess(folderId, user);
        long latest = messages.findTop100ByFolderIdOrderByIdDesc(folderId).stream()
                .findFirst().map(PlanChatMessage::getId).orElse(0L);
        long next = Math.min(Math.max(0L, requestedId), latest);
        PlanChatReadState state = readStates.findByFolderIdAndUserId(folderId, user.getId()).orElseGet(() -> {
            PlanChatReadState created = new PlanChatReadState();
            created.setFolder(folder);
            created.setUser(user);
            return created;
        });
        state.setLastReadMessageId(Math.max(state.getLastReadMessageId(), next));
        return readStates.save(state).getLastReadMessageId();
    }

    public SseEmitter stream(Long folderId, String email, boolean inChat) {
        AppUser user = user(email);
        requireAccess(folderId, user);
        SseEmitter emitter = createEmitter();
        Subscriber subscriber = new Subscriber(user.getId(), displayName(user), inChat,
                System.currentTimeMillis() + 60_000L, emitter);
        subscribers.compute(folderId, (ignored, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(subscriber);
            return list;
        });
        Runnable cleanup = () -> {
            if (removeSubscriber(folderId, subscriber)) broadcastPresence(folderId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("connected"));
            broadcastPresence(folderId);
        } catch (IOException | IllegalStateException ex) {
            cleanup.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    SseEmitter createEmitter() { return new SseEmitter(60_000L); }

    private boolean removeSubscriber(Long folderId, Subscriber subscriber) {
        AtomicBoolean removed = new AtomicBoolean();
        subscribers.computeIfPresent(folderId, (ignored, list) -> {
            removed.set(list.remove(subscriber));
            return list.isEmpty() ? null : list;
        });
        return removed.get();
    }

    private boolean allowed(Long folderId, Subscriber subscriber) {
        return folders.findById(folderId)
                .map(folder -> folder.getOwner().getId().equals(subscriber.userId())
                        || members.findByFolderIdAndUserId(folderId, subscriber.userId())
                                .map(member -> member.getStatus() == PlanFolderMember.Status.accepted).orElse(false))
                .orElse(false);
    }

    // Writes detect closed sockets; bounded streams also expire suspended clients.
    @Scheduled(fixedDelay = 15_000L)
    void keepAlive() {
        for (Long folderId : List.copyOf(subscribers.keySet())) broadcastPresence(folderId);
    }

    private void broadcastPresence(Long folderId) {
        List<Subscriber> listeners = subscribers.get(folderId);
        if (listeners == null) return;
        for (Subscriber subscriber : listeners) {
            if (subscriber.expiresAt() <= System.currentTimeMillis() || !allowed(folderId, subscriber)) {
                removeSubscriber(folderId, subscriber);
                subscriber.emitter().complete();
            }
        }
        Map<Long, Participant> online = new LinkedHashMap<>();
        for (Subscriber subscriber : listeners) {
            if (subscriber.inChat()) online.putIfAbsent(subscriber.userId(), new Participant(subscriber.userId(), subscriber.name()));
        }
        List<Participant> people = List.copyOf(online.values());
        boolean changed = false;
        for (Subscriber subscriber : listeners) {
            try {
                subscriber.emitter().send(SseEmitter.event().name("presence").data(people));
            } catch (IOException | IllegalStateException ex) {
                changed |= removeSubscriber(folderId, subscriber);
                subscriber.emitter().complete();
            }
        }
        if (changed) broadcastPresence(folderId);
    }

    private void broadcast(Long folderId, Message message) {
        CopyOnWriteArrayList<Subscriber> listeners = subscribers.get(folderId);
        if (listeners == null) return;
        for (Subscriber subscriber : listeners) {
            if (!allowed(folderId, subscriber)) {
                removeSubscriber(folderId, subscriber);
                subscriber.emitter().complete();
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event().name("message").id(message.id().toString()).data(message));
            } catch (IOException | IllegalStateException ex) {
                removeSubscriber(folderId, subscriber);
                subscriber.emitter().complete();
            }
        }
        broadcastPresence(folderId);
    }

    private AppUser user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private PlanFolder requireAccess(Long folderId, AppUser user) {
        PlanFolder folder = folders.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (folder.getOwner().getId().equals(user.getId())) return folder;
        boolean accepted = members.findByFolderIdAndUserId(folderId, user.getId())
                .map(member -> member.getStatus() == PlanFolderMember.Status.accepted).orElse(false);
        if (!accepted) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return folder;
    }

    private Message toMessage(PlanChatMessage message) {
        AppUser author = message.getAuthor();
        return new Message(message.getId(), author.getId(), displayName(author), message.getBody(), message.getCreatedAt());
    }

    private String displayName(AppUser author) {
        String name = author.getNickname();
        if (name == null || name.isBlank()) {
            String fullName = author.getName();
            if (fullName != null && !fullName.isBlank()) {
                StringBuilder initials = new StringBuilder();
                for (String part : fullName.trim().split("\\s+")) {
                    if (part.isBlank()) continue;
                    if (initials.length() > 0) initials.append(' ');
                    initials.append(part.substring(0, part.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT)).append('.');
                }
                name = initials.toString();
            }
        }
        if (name == null || name.isBlank()) name = "Participante";
        return name;
    }
}
