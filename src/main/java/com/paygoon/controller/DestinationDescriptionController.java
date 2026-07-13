package com.paygoon.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.paygoon.dto.DestinationDescriptionRequest;
import com.paygoon.dto.DestinationDescriptionResponse;
import com.paygoon.service.DestinationDescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/destination-descriptions") @RequiredArgsConstructor
public class DestinationDescriptionController {
    private final DestinationDescriptionService service;
    @PostMapping("/resolve")
    public ResponseEntity<List<DestinationDescriptionResponse>> resolve(@Valid @RequestBody DestinationDescriptionRequest request) {
        return ResponseEntity.ok(service.resolve(request.places()));
    }
}
