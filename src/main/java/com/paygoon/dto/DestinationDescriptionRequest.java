package com.paygoon.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DestinationDescriptionRequest(
        @Valid @Size(max = 100) List<Place> places
) {
    public record Place(
            @NotBlank @Size(max = 40) String type,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 180) String context
    ) {}
}
