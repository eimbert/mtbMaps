package com.paygoon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.MensajeCreateRequest;
import com.paygoon.dto.MensajeEstadoUpdateRequest;
import com.paygoon.model.AppUser;
import com.paygoon.model.Mensaje;
import com.paygoon.repository.MensajeRepository;
import com.paygoon.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mensajes")
@RequiredArgsConstructor
@Validated
public class MensajeController {

    private final MensajeRepository mensajeRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<Mensaje> createMensaje(@Valid @RequestBody MensajeCreateRequest request) {
        AppUser user = userRepository.findById(request.userId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        AppUser userMsg = null;
        if (request.userMsgId() != null) {
            userMsg = userRepository.findById(request.userMsgId()).orElse(null);
            if (userMsg == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }

        Mensaje mensaje = new Mensaje();
        mensaje.setUser(user);
        mensaje.setUserMsg(userMsg);
        mensaje.setMensaje(request.mensaje());
        mensaje.setTipoMsg(request.tipoMsg());
        mensaje.setEstado(request.estado());

        Mensaje savedMensaje = mensajeRepository.save(mensaje);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMensaje);
    }

    @GetMapping("/usuario/{userId}/pendientes")
    public ResponseEntity<List<Mensaje>> getMensajesPendientes(@PathVariable Long userId) {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<Mensaje> mensajes = mensajeRepository.findByUser_IdAndEstadoIsNull(userId);
        return ResponseEntity.ok(mensajes);
    }

    @PutMapping("/{mensajeId}/estado")
    public ResponseEntity<Mensaje> updateEstado(
            @PathVariable Long mensajeId,
            @Valid @RequestBody MensajeEstadoUpdateRequest request) {

        Mensaje mensaje = mensajeRepository.findById(mensajeId).orElse(null);
        if (mensaje == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        mensaje.setEstado(request.estado());
        Mensaje updatedMensaje = mensajeRepository.save(mensaje);
        return ResponseEntity.ok(updatedMensaje);
    }

    @DeleteMapping("/{mensajeId}")
    public ResponseEntity<Void> deleteMensaje(@PathVariable Long mensajeId) {
        if (!mensajeRepository.existsById(mensajeId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        mensajeRepository.deleteById(mensajeId);
        return ResponseEntity.noContent().build();
    }
}
