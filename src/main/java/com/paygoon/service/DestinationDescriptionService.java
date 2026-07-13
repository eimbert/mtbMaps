package com.paygoon.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paygoon.dto.DestinationDescriptionRequest.Place;
import com.paygoon.dto.DestinationDescriptionResponse;
import com.paygoon.model.DestinationDescription;
import com.paygoon.repository.DestinationDescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j @Service @RequiredArgsConstructor
public class DestinationDescriptionService {
    private final DestinationDescriptionRepository repository;
    private final ObjectMapper objectMapper;
    @Value("${spring.ai.openai.api-key:}") private String apiKey;
    @Value("${route-analysis.openai.model:gpt-5-mini}") private String model;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Transactional
    public List<DestinationDescriptionResponse> resolve(List<Place> input) {
        List<Place> places = Optional.ofNullable(input).orElse(List.of()).stream()
                .filter(Objects::nonNull).distinct().limit(100).toList();
        Map<String, DestinationDescription> stored = repository.findAllByPlaceKeyIn(places.stream().map(this::key).toList())
                .stream().collect(Collectors.toMap(DestinationDescription::getPlaceKey, Function.identity()));
        List<Place> missing = places.stream().filter(place -> !stored.containsKey(key(place))).toList();
        if (!missing.isEmpty() && apiKey != null && !apiKey.isBlank()) {
            generate(missing).forEach((placeKey, description) -> {
                Place place = missing.stream().filter(item -> key(item).equals(placeKey)).findFirst().orElse(null);
                if (place == null || description.isBlank()) return;
                DestinationDescription entity = new DestinationDescription();
                entity.setPlaceKey(placeKey); entity.setPlaceType(place.type()); entity.setPlaceName(place.name());
                entity.setContextName(place.context()); entity.setDescription(description.substring(0, Math.min(600, description.length()))); entity.setModel(model);
                stored.put(placeKey, repository.save(entity));
            });
        }
        return places.stream().map(place -> {
            DestinationDescription entity = stored.get(key(place));
            return new DestinationDescriptionResponse(key(place), place.type(), place.name(), place.context(), entity == null ? "" : entity.getDescription());
        }).toList();
    }

    private Map<String, String> generate(List<Place> places) {
        try {
            ObjectNode body = objectMapper.createObjectNode(); body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", "Eres redactor geográfico. Responde solo JSON válido, sin markdown.");
            String requested = objectMapper.writeValueAsString(places.stream().map(p -> Map.of("key", key(p), "type", p.type(), "name", p.name(), "context", Objects.toString(p.context(), ""))).toList());
            messages.addObject().put("role", "user").put("content", "Para cada lugar devuelve una descripción factual y atractiva en español de 2 frases y máximo 280 caracteres. Incluye situación geográfica, paisaje o un rasgo conocido. No hables de tracks, rutas disponibles ni inventes datos. Devuelve un objeto JSON donde cada clave recibida tenga su descripción. Lugares: " + requested);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) throw new IllegalStateException("OpenAI HTTP " + response.statusCode());
            String content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText();
            JsonNode json = objectMapper.readTree(content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
            Map<String, String> result = new HashMap<>(); json.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText().trim()));
            return result;
        } catch (Exception ex) {
            log.warn("destination descriptions unavailable: {}", ex.getMessage()); return Map.of();
        }
    }

    private String key(Place place) {
        return normalize(place.type()) + ":" + normalize(place.name()) + ":" + normalize(place.context());
    }
    private String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
