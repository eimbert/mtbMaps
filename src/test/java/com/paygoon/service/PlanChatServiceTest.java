package com.paygoon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.paygoon.model.AppUser;
import com.paygoon.model.PlanChatMessage;
import com.paygoon.model.PlanFolder;
import com.paygoon.model.PlanFolderMember;
import com.paygoon.repository.PlanChatMessageRepository;
import com.paygoon.repository.PlanChatReadStateRepository;
import com.paygoon.repository.PlanFolderMemberRepository;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PlanChatServiceTest {
    @Mock PlanFolderRepository folders;
    @Mock PlanFolderMemberRepository members;
    @Mock PlanChatMessageRepository messages;
    @Mock PlanChatReadStateRepository readStates;
    @Mock UserRepository users;
    @InjectMocks PlanChatService chat;

    @Test
    void usesNickOrInitialsWithoutExposingFullName() {
        AppUser owner = user(901L, "owner@example.test", "Organizador", "Propietario Prueba");
        AppUser withNick = user(902L, "nick@example.test", "RuedaLibre", "Ana Pérez López");
        AppUser withoutNick = user(903L, "initials@example.test", null, "Luis García Pérez");
        PlanFolder folder = folder(owner);
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(folders.findById(42L)).thenReturn(Optional.of(folder));
        when(messages.findTop100ByFolderIdOrderByIdDesc(42L)).thenReturn(List.of(message(2L, withoutNick), message(1L, withNick)));
        when(readStates.findByFolderIdAndUserId(42L, 901L)).thenReturn(Optional.empty());
        when(messages.countByFolderIdAndIdGreaterThanAndAuthorIdNot(42L, 0L, 901L)).thenReturn(2L);
        when(messages.countByFolderIdAndIdGreaterThan(42L, 0L)).thenReturn(2L);

        PlanChatService.Inbox inbox = chat.inbox(42L, owner.getEmail(), 0);
        assertEquals("RuedaLibre", inbox.messages().get(0).authorName());
        assertEquals("L. G. P.", inbox.messages().get(1).authorName());
        assertEquals(2L, inbox.unreadCount());
    }

    @Test
    void rejectsNonMemberBeforeReadingMessages() {
        AppUser owner = user(901L, "owner@example.test", "Owner", "Owner Name");
        AppUser stranger = user(999L, "stranger@example.test", "Other", "Other Name");
        when(users.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        when(folders.findById(42L)).thenReturn(Optional.of(folder(owner)));
        when(members.findByFolderIdAndUserId(42L, 999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> chat.inbox(42L, stranger.getEmail(), 0));
        verifyNoInteractions(messages, readStates);
    }

    @Test
    void presenceCountsPeopleOnlyInChatAndKeepsOtherTabsConnected() throws Exception {
        chat = spy(chat);
        AppUser owner = user(901L, "owner@example.test", "Organizador", "Nombre Completo");
        AppUser guest = user(902L, "guest@example.test", null, "Ana Pérez");
        PlanFolderMember member = new PlanFolderMember();
        member.setStatus(PlanFolderMember.Status.accepted);
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findByEmail(guest.getEmail())).thenReturn(Optional.of(guest));
        when(folders.findById(42L)).thenReturn(Optional.of(folder(owner)));
        when(members.findByFolderIdAndUserId(42L, guest.getId())).thenReturn(Optional.of(member));

        Connection overview = connect(owner.getEmail(), false);
        assertEquals(List.of(), overview.latest());
        Connection first = connect(guest.getEmail(), true);
        Connection second = connect(guest.getEmail(), true);
        assertEquals(List.of(new PlanChatService.Participant(902L, "A. P.")), overview.latest());
        first.close[0].run();
        assertEquals(1, overview.latest().size(), "Another tab keeps the user online");
        second.timeout[0].run();
        assertEquals(List.of(), overview.latest());
        second.close[0].run(); // Duplicate container callbacks must be harmless.
        assertEquals(List.of(), overview.latest());
    }

    @Test
    void presenceRemovesRevokedMembersAndBrokenConnections() throws Exception {
        chat = spy(chat);
        AppUser owner = user(901L, "owner@example.test", "Organizador", "Nombre Completo");
        AppUser guest = user(902L, "guest@example.test", "RuedaLibre", "Nombre Privado");
        PlanFolderMember member = new PlanFolderMember();
        member.setStatus(PlanFolderMember.Status.accepted);
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(users.findByEmail(guest.getEmail())).thenReturn(Optional.of(guest));
        when(folders.findById(42L)).thenReturn(Optional.of(folder(owner)));
        when(members.findByFolderIdAndUserId(42L, guest.getId())).thenReturn(Optional.of(member));
        Connection overview = connect(owner.getEmail(), false);
        Connection guestChat = connect(guest.getEmail(), true);
        member.setStatus(PlanFolderMember.Status.revoked);
        chat.keepAlive();
        assertEquals(List.of(), overview.latest());
        verify(guestChat.emitter).complete();

        Connection ownerChat = connect(owner.getEmail(), true);
        doThrow(new java.io.IOException("socket closed")).when(ownerChat.emitter).send(any(SseEmitter.SseEventBuilder.class));
        chat.keepAlive();
        assertEquals(List.of(), overview.latest());
    }

    @Test
    void rejectsNonMemberBeforeOpeningPresenceStream() {
        AppUser owner = user(901L, "owner@example.test", "Owner", "Owner Name");
        AppUser stranger = user(999L, "stranger@example.test", "Other", "Other Name");
        when(users.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        when(folders.findById(42L)).thenReturn(Optional.of(folder(owner)));
        when(members.findByFolderIdAndUserId(42L, 999L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> chat.stream(42L, stranger.getEmail(), true));
    }

    private static class Connection {
        final SseEmitter emitter = mock(SseEmitter.class);
        final Runnable[] close = new Runnable[1];
        final Runnable[] timeout = new Runnable[1];
        final List<List<?>> snapshots = new ArrayList<>();
        List<?> latest() { return snapshots.get(snapshots.size() - 1); }
    }

    private Connection connect(String email, boolean inChat) throws Exception {
        Connection connection = new Connection();
        doReturn(connection.emitter).when(chat).createEmitter();
        doAnswer(call -> { connection.close[0] = call.getArgument(0); return null; }).when(connection.emitter).onCompletion(any());
        doAnswer(call -> { connection.timeout[0] = call.getArgument(0); return null; }).when(connection.emitter).onTimeout(any());
        doAnswer(call -> {
            SseEmitter.SseEventBuilder event = call.getArgument(0);
            event.build().forEach(data -> { if (data.getData() instanceof List<?> list) connection.snapshots.add(list); });
            return null;
        }).when(connection.emitter).send(any(SseEmitter.SseEventBuilder.class));
        chat.stream(42L, email, inChat);
        return connection;
    }

    private AppUser user(Long id, String email, String nick, String name) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setNickname(nick);
        user.setName(name);
        return user;
    }

    private PlanFolder folder(AppUser owner) {
        PlanFolder folder = new PlanFolder();
        folder.setId(42L);
        folder.setOwner(owner);
        return folder;
    }

    private PlanChatMessage message(Long id, AppUser author) {
        PlanChatMessage message = new PlanChatMessage();
        message.setId(id);
        message.setAuthor(author);
        message.setBody("Hola");
        message.setCreatedAt(LocalDateTime.of(2026, 10, 27, 8, 0));
        return message;
    }
}
