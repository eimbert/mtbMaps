package com.paygoon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.PlanFolderCreateRequest;
import com.paygoon.dto.PlanFolderCreateResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.PlanFolder;
import com.paygoon.repository.PlanFolderRepository;
import com.paygoon.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/plan-folders")
@RequiredArgsConstructor
@Validated
public class PlanFolderController {

    private final PlanFolderRepository planFolderRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<PlanFolderCreateResponse> createPlanFolder(
            @Valid @RequestBody PlanFolderCreateRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new PlanFolderCreateResponse(null, "No autenticado", -1));
        }

        AppUser owner = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        try {
            PlanFolder planFolder = new PlanFolder();
            planFolder.setOwner(owner);
            planFolder.setName(request.name());
            planFolder.setPlannedDate(request.plannedDate());
            planFolder.setObservations(request.observations());

            PlanFolder savedPlanFolder = planFolderRepository.save(planFolder);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PlanFolderCreateResponse(
                            savedPlanFolder.getId(),
                            "Carpeta de plan creada correctamente",
                            0
                    ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PlanFolderCreateResponse(null, "No se pudo crear la carpeta del plan", -99));
        }
    }
}
