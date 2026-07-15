package com.paygoon.service;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paygoon.dto.RouteAnalysisRequest;
import com.paygoon.dto.RouteAnalysisResponse;
import com.paygoon.dto.PublicRouteAnalysisResponse;
import com.paygoon.dto.RouteAnalysisResponse.RouteAnalysisApproaches;
import com.paygoon.dto.RouteAnalysisResponse.RouteAnalysisHighlight;
import com.paygoon.dto.RouteAnalysisResponse.RouteAnalysisReport;
import com.paygoon.dto.RouteAnalysisResponse.RouteAnalysisSector;
import com.paygoon.model.AppUser;
import com.paygoon.model.RouteAnalysis;
import com.paygoon.repository.RouteAnalysisRepository;
import com.paygoon.repository.TrackRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteAnalysisService {

    private static final String ANALYSIS_VERSION = "v3";
    private static final int MAX_PROMPT_POINTS = 140;

    private final RouteAnalysisRepository routeAnalysisRepository;
    private final TrackRepository trackRepository;
    private final ObjectMapper objectMapper;
    private final EntitlementService entitlementService;
    private final Map<String, Double> elevationCache = new ConcurrentHashMap<>();

    @Value("${route-analysis.openai.enabled:true}")
    private boolean openAiEnabled;

    @Value("${route-analysis.openai.model:gpt-5-mini}")
    private String analysisModel;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${route-analysis.elevation.enabled:true}")
    private boolean elevationLookupEnabled;

    @Value("${route-analysis.elevation.base-url:https://api.opentopodata.org/v1/eudem25m}")
    private String elevationBaseUrl;

    @Value("${route-analysis.elevation.sample-step-meters:250}")
    private double elevationSampleStepMeters;

    private final HttpClient openAiHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Transactional
    public RouteAnalysisResponse analyze(RouteAnalysisRequest request, AppUser user) {
        String routeXml = request.routeXml();
        String source = normalizeSource(request.source());
        String gpxHash = sha256(routeXml);
        String userInstructions = normalizeInstructions(request.userInstructions());
        String instructionsHash = sha256(userInstructions);
        boolean forceRefresh = Boolean.TRUE.equals(request.forceRefresh());

        if (!forceRefresh) {
            Optional<RouteAnalysis> cached = findCachedAnalysis(gpxHash, instructionsHash);
            if (cached.isPresent()) {
                return mapToResponse(cached.get());
            }
        }

        entitlementService.assertCanUseRouteAnalysis(user);

        RouteStats stats = parseStats(routeXml);
        GeneratedReport generatedReport = generateReport(stats, userInstructions);
        if (isAiReport(generatedReport)) {
            entitlementService.recordRouteAnalysis(user);
        }

        RouteAnalysis analysis = routeAnalysisRepository.findFirstByGpxHashOrderByUpdatedAtDesc(gpxHash).orElseGet(RouteAnalysis::new);
        analysis.setUserId(user.getId());
        analysis.setSource(source);
        analysis.setSourceTrackId(request.trackId());
        analysis.setFileName(request.fileName());
        analysis.setRouteTitle(normalizeRouteTitle(request.title()));
        analysis.setGpxHash(gpxHash);
        analysis.setAnalysisVersion(isAiReport(generatedReport) ? ANALYSIS_VERSION : ANALYSIS_VERSION + "-fallback");
        analysis.setUserInstructions(userInstructions);
        analysis.setInstructionsHash(instructionsHash);
        analysis.setModel(generatedReport.model());
        analysis.setFallbackReason(generatedReport.fallbackReason());
        analysis.setElevationSource(stats.elevationSource());
        analysis.setRouteXml(routeXml);
        analysis.setReportJson(writeReport(generatedReport.report()));

        RouteAnalysis saved = routeAnalysisRepository.save(analysis);
        removeDuplicateAnalyses(gpxHash, saved.getId());
        return mapToResponse(saved, isAiReport(generatedReport), false);
    }

    @Transactional(readOnly = true)
    public Optional<RouteAnalysisResponse> findByTrack(Long trackId, AppUser user) {
        return routeAnalysisRepository
                .findFirstBySourceAndSourceTrackIdAndUserIdOrderByUpdatedAtDesc("tracks", trackId, user.getId())
                .or(() -> routeAnalysisRepository.findFirstBySourceAndSourceTrackIdAndUserIdOrderByUpdatedAtDesc("plan", trackId, user.getId()))
                .filter(this::isReusableAnalysis)
                .map(this::mapToResponse);
    }

    @Transactional
    public List<PublicRouteAnalysisResponse> findPublicAnalyses() {
        Set<String> seenRoutes = new HashSet<>();
        List<RouteAnalysis> ordered = routeAnalysisRepository.findAllByOrderByUpdatedAtDesc();
        List<RouteAnalysis> duplicates = ordered.stream()
                .filter(analysis -> !seenRoutes.add(analysis.getGpxHash()))
                .toList();
        if (!duplicates.isEmpty()) {
            routeAnalysisRepository.deleteAllInBatch(duplicates);
            log.info("route-analysis removed {} historical duplicate rows", duplicates.size());
        }
        return ordered.stream()
                .filter(analysis -> !duplicates.contains(analysis))
                .limit(100)
                .map(this::mapToPublicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicRouteAnalysisResponse> findPublicAnalysis(Long analysisId) {
        return routeAnalysisRepository.findById(analysisId).map(this::mapToPublicResponse);
    }

    @Transactional
    public Optional<RouteAnalysisResponse> refresh(Long analysisId, AppUser user) {
        return routeAnalysisRepository.findByIdAndUserId(analysisId, user.getId())
                .map(existing -> {
                    RouteAnalysisRequest request = new RouteAnalysisRequest(
                            existing.getSourceTrackId(),
                            existing.getSource(),
                            existing.getFileName(),
                            existing.getRouteTitle(),
                            existing.getRouteXml(),
                            existing.getUserInstructions(),
                            true
                    );
                    return analyze(request, user);
                });
    }

    private Optional<RouteAnalysis> findCachedAnalysis(String gpxHash, String instructionsHash) {
        return routeAnalysisRepository.findFirstByGpxHashOrderByUpdatedAtDesc(gpxHash)
                .filter(analysis -> instructionsHash.equals(nullToEmptyHash(analysis.getInstructionsHash())) && isReusableAnalysis(analysis));
    }

    @Transactional(readOnly = true)
    public Optional<RouteAnalysisResponse> findExisting(String routeXml) {
        return routeAnalysisRepository.findFirstByGpxHashOrderByUpdatedAtDesc(sha256(routeXml))
                .filter(this::isReusableAnalysis)
                .map(this::mapToResponse);
    }

    private void removeDuplicateAnalyses(String gpxHash, Long keepId) {
        List<RouteAnalysis> duplicates = routeAnalysisRepository.findAllByGpxHashOrderByUpdatedAtDesc(gpxHash).stream()
                .filter(item -> !item.getId().equals(keepId))
                .toList();
        if (!duplicates.isEmpty()) {
            routeAnalysisRepository.deleteAllInBatch(duplicates);
            log.info("route-analysis removed {} duplicate rows for hash={}", duplicates.size(), gpxHash);
        }
    }

    private GeneratedReport generateReport(RouteStats stats, String userInstructions) {
        RouteAnalysisReport localReport = buildLocalReport(stats);
        if (!openAiEnabled) {
            return new GeneratedReport(localReport, "local", "OpenAI desactivado por configuracion");
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return new GeneratedReport(localReport, "local", "Falta configurar OPENAI_API_KEY");
        }

        try {
            String content = callOpenAi(buildPrompt(stats, localReport, userInstructions));
            RouteAnalysisReport aiReport = objectMapper.readValue(extractJson(content), RouteAnalysisReport.class);
            return new GeneratedReport(sanitizeReport(aiReport, localReport), analysisModel, null);
        } catch (Exception ex) {
            log.warn("route-analysis OpenAI fallback reason={}", ex.getMessage());
            return new GeneratedReport(localReport, "local-fallback", truncateFallbackReason(ex.getMessage()));
        }
    }

    private String callOpenAi(String prompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", analysisModel);
        ArrayNode messages = body.putArray("messages");
        messages.add(messageNode("system", "Eres un analizador experto de rutas GPX para ciclismo. Responde solo JSON valido, sin markdown."));
        messages.add(messageNode("user", prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = openAiHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " - " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("OpenAI no devolvio contenido de analisis");
        }
        return content.asText();
    }

    private ObjectNode messageNode(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
    private String buildPrompt(RouteStats stats, RouteAnalysisReport localReport, String userInstructions) throws Exception {
        return "Analiza esta ruta GPX como si el usuario pagara por una lectura realmente util. "
                + "No repitas obviedades: interpreta como afrontar la ruta. Clasifica routeType como uno de: rodadora, tecnica, subida_dura, mixta, suave, exigente. "
                + "Devuelve solo JSON valido con esta forma exacta: {\"summary\":string,\"routeType\":string,\"difficultyExplanation\":string,"
                + "\"highlights\":[{\"title\":string,\"description\":string,\"kmStart\":number,\"kmEnd\":number|null,\"lat\":number|null,\"lon\":number|null,\"type\":string,\"severity\":string}],"
                + "\"warnings\":[string],\"recommendations\":[string],"
                + "\"sectors\":[{\"name\":string,\"kmStart\":number,\"kmEnd\":number,\"focus\":string,\"effort\":\"suave\"|\"medio\"|\"alto\"|\"critico\"}],"
                + "\"approaches\":{\"recreational\":[string],\"training\":[string],\"competition\":[string]}}. "
                + "Incluye 3 o 4 highlights numerables, 3 sectores y consejos distintos para salida de recreo, entreno y competicion. "
                + "Usa kilometros concretos, pendientes/desnivel cuando puedas inferirlos, y lenguaje claro de ciclista. "
                + instructionPrompt(userInstructions)
                + "Datos base: "
                + objectMapper.writeValueAsString(new PromptPayload(stats.distanceKm(), stats.elevationGainM(), stats.elevationLossM(), stats.minEleM(), stats.maxEleM(), stats.points().size(), stats.elevationSource(), samplePoints(stats.points()), localReport));
    }
    private RouteAnalysisReport buildLocalReport(RouteStats stats) {
        double distanceKm = stats.distanceKm();
        double gain = stats.elevationGainM();
        String type = classifyRoute(distanceKm, gain);
        List<RouteAnalysisHighlight> highlights = findClimbHighlights(stats.points());
        List<String> warnings = new ArrayList<>();
        if (gain > 900) {
            warnings.add("Ruta con desnivel acumulado alto: conviene dosificar desde el inicio.");
        }
        if (hasHardInitialClimb(stats.points())) {
            warnings.add("Hay subida relevante en los primeros kilometros.");
        }
        if (distanceKm > 60 && highlights.isEmpty()) {
            warnings.add("Ruta larga: revisa puntos de agua y avituallamiento antes de salir.");
        }
        if (!"gpx".equals(stats.elevationSource())) {
            warnings.add("Altimetria recalculada o marcada como no fiable: fuente " + stats.elevationSource() + ".");
        }

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Usa los puntos numerados como referencias para regular, reagrupar o cambiar el ritmo sin llegar tarde al tramo clave.");
        recommendations.add("Planifica comida y agua antes del bloque mas exigente: cuando aparece el desnivel, conviene entrar ya alimentado.");
        if (gain > 700) {
            recommendations.add("Reserva energia para la segunda mitad si el desnivel aparece concentrado.");
        }

        return new RouteAnalysisReport(
                String.format(Locale.US, "Ruta de %.1f km con %.0f m de desnivel positivo.", distanceKm, gain),
                type,
                String.format(Locale.US, "La dureza viene marcada por %.0f m positivos repartidos en %.1f km, con %d puntos de track analizados.", gain, distanceKm, stats.points().size()),
                highlights,
                warnings,
                recommendations,
                buildLocalSectors(stats),
                buildLocalApproaches(gain)
        );
    }

    private RouteStats parseStats(String gpx) {
        List<RoutePoint> points = parsePoints(gpx);
        points = calculateDistances(points);
        double distance = points.isEmpty() ? 0 : points.get(points.size() - 1).distanceKm();
        String elevationSource = "gpx";

        if (!hasUsableElevation(points) && elevationLookupEnabled) {
            List<RoutePoint> enrichedPoints = enrichElevations(points);
            if (hasUsableElevation(enrichedPoints)) {
                points = enrichedPoints;
                elevationSource = "opentopodata-eudem25m";
            } else {
                elevationSource = "gpx-unreliable";
            }
        } else if (!hasUsableElevation(points)) {
            elevationSource = "gpx-unreliable";
        }

        double minEle = points.stream().mapToDouble(RoutePoint::ele).min().orElse(0);
        double maxEle = points.stream().mapToDouble(RoutePoint::ele).max().orElse(0);
        ElevationTotals elevationTotals = calculateElevationTotals(points, 25.0, 100.0, 1.5, 40.0);
        double gain = elevationTotals.gain();
        double loss = elevationTotals.loss();
        if (gain < 1 && Double.isFinite(maxEle - minEle) && maxEle - minEle > 8) {
            gain = maxEle - minEle;
        }

        return new RouteStats(points, distance, gain, loss, minEle, maxEle, elevationSource);
    }

    private List<RoutePoint> calculateDistances(List<RoutePoint> rawPoints) {
        List<RoutePoint> points = new ArrayList<>(rawPoints);
        double distance = 0;
        for (int i = 0; i < points.size(); i++) {
            RoutePoint current = points.get(i);
            if (i > 0) {
                RoutePoint previous = points.get(i - 1);
                distance += distanceMeters(previous, current) / 1000.0;
            }
            points.set(i, new RoutePoint(current.lat(), current.lon(), current.ele(), distance));
        }
        return points;
    }

    private boolean hasUsableElevation(List<RoutePoint> points) {
        if (points.size() < 2) {
            return false;
        }
        double min = points.stream().mapToDouble(RoutePoint::ele).min().orElse(0);
        double max = points.stream().mapToDouble(RoutePoint::ele).max().orElse(0);
        long nonZero = points.stream().filter(point -> Math.abs(point.ele()) > 0.1).count();
        return Double.isFinite(min) && Double.isFinite(max) && nonZero > Math.max(2, points.size() / 20) && max - min >= 10;
    }

    private List<RoutePoint> enrichElevations(List<RoutePoint> points) {
        if (points.size() < 2) {
            return points;
        }
        try {
            List<RoutePoint> samples = sampleElevationLookupPoints(points);
            List<RoutePoint> missing = samples.stream()
                    .filter(point -> !elevationCache.containsKey(elevationCacheKey(point)))
                    .toList();
            for (int start = 0; start < missing.size(); start += 100) {
                List<RoutePoint> batch = missing.subList(start, Math.min(start + 100, missing.size()));
                List<Double> elevations = fetchElevationBatch(batch);
                for (int i = 0; i < batch.size() && i < elevations.size(); i++) {
                    Double elevation = elevations.get(i);
                    if (elevation != null && Double.isFinite(elevation)) {
                        elevationCache.put(elevationCacheKey(batch.get(i)), elevation);
                    }
                }
            }
            List<RoutePoint> elevatedSamples = samples.stream()
                    .filter(point -> elevationCache.containsKey(elevationCacheKey(point)))
                    .map(point -> new RoutePoint(point.lat(), point.lon(), elevationCache.get(elevationCacheKey(point)), point.distanceKm()))
                    .toList();
            if (elevatedSamples.size() < 2) {
                return points;
            }
            return interpolateElevations(points, elevatedSamples);
        } catch (Exception ex) {
            log.warn("route-analysis elevation fallback unavailable reason={}", ex.getMessage());
            return points;
        }
    }

    private List<RoutePoint> sampleElevationLookupPoints(List<RoutePoint> points) {
        double stepMeters = Math.max(50, elevationSampleStepMeters);
        List<RoutePoint> samples = new ArrayList<>();
        samples.add(points.get(0));
        double nextMeters = stepMeters;
        for (RoutePoint point : points) {
            double pointMeters = point.distanceKm() * 1000.0;
            if (pointMeters >= nextMeters) {
                samples.add(point);
                nextMeters += stepMeters;
            }
        }
        RoutePoint last = points.get(points.size() - 1);
        if (samples.get(samples.size() - 1) != last) {
            samples.add(last);
        }
        return samples;
    }

    private List<Double> fetchElevationBatch(List<RoutePoint> points) throws Exception {
        if (points.isEmpty()) {
            return List.of();
        }
        String locations = points.stream()
                .map(point -> String.format(Locale.US, "%.6f,%.6f", point.lat(), point.lon()))
                .collect(Collectors.joining("|"));
        String separator = elevationBaseUrl.contains("?") ? "&" : "?";
        URI uri = URI.create(elevationBaseUrl + separator + "locations=" + URLEncoder.encode(locations, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = openAiHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " - " + response.body());
        }
        JsonNode results = objectMapper.readTree(response.body()).path("results");
        List<Double> elevations = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JsonNode elevation = results.get(i).path("elevation");
            elevations.add(elevation.isNumber() ? elevation.asDouble() : null);
        }
        return elevations;
    }

    private List<RoutePoint> interpolateElevations(List<RoutePoint> points, List<RoutePoint> samples) {
        List<RoutePoint> enriched = new ArrayList<>();
        int segment = 0;
        for (RoutePoint point : points) {
            while (segment < samples.size() - 2 && samples.get(segment + 1).distanceKm() < point.distanceKm()) {
                segment++;
            }
            RoutePoint start = samples.get(segment);
            RoutePoint end = samples.get(Math.min(segment + 1, samples.size() - 1));
            double length = Math.max(1e-9, end.distanceKm() - start.distanceKm());
            double ratio = Math.max(0, Math.min(1, (point.distanceKm() - start.distanceKm()) / length));
            double ele = start.ele() + (end.ele() - start.ele()) * ratio;
            enriched.add(new RoutePoint(point.lat(), point.lon(), ele, point.distanceKm()));
        }
        return enriched;
    }

    private String elevationCacheKey(RoutePoint point) {
        return String.format(Locale.US, "%.5f,%.5f", point.lat(), point.lon());
    }

    private ElevationTotals calculateElevationTotals(List<RoutePoint> points, double stepMeters, double smoothWindowMeters, double minStepMeters, double maxJumpMeters) {
        if (points.size() < 2) {
            return new ElevationTotals(0, 0);
        }
        double[] elevations = resampleElevationsByDistance(points, stepMeters);
        int windowPoints = Math.max(3, (int) Math.round(smoothWindowMeters / stepMeters));
        double[] smooth = movingAverageCentered(elevations, windowPoints);
        double gain = 0;
        double loss = 0;
        double previous = smooth[0];
        for (int i = 1; i < smooth.length; i++) {
            double diff = smooth[i] - previous;
            if (Math.abs(diff) > maxJumpMeters) {
                diff = 0;
            }
            if (diff > minStepMeters) {
                gain += diff;
            } else if (diff < -minStepMeters) {
                loss += Math.abs(diff);
            }
            previous = smooth[i];
        }
        return new ElevationTotals(gain, loss);
    }

    private double[] resampleElevationsByDistance(List<RoutePoint> points, double stepMeters) {
        double totalMeters = points.get(points.size() - 1).distanceKm() * 1000.0;
        if (totalMeters <= 0) {
            return new double[] { points.get(0).ele() };
        }
        List<Double> elevations = new ArrayList<>();
        int segment = 0;
        for (double target = 0; target <= totalMeters; target += stepMeters) {
            while (segment < points.size() - 2 && points.get(segment + 1).distanceKm() * 1000.0 < target) {
                segment++;
            }
            RoutePoint start = points.get(segment);
            RoutePoint end = points.get(Math.min(segment + 1, points.size() - 1));
            double startMeters = start.distanceKm() * 1000.0;
            double endMeters = end.distanceKm() * 1000.0;
            double length = Math.max(1e-9, endMeters - startMeters);
            double ratio = (target - startMeters) / length;
            elevations.add(start.ele() + (end.ele() - start.ele()) * ratio);
        }
        if ((elevations.size() - 1) * stepMeters < totalMeters) {
            elevations.add(points.get(points.size() - 1).ele());
        }
        double[] result = new double[elevations.size()];
        for (int i = 0; i < elevations.size(); i++) {
            result[i] = elevations.get(i);
        }
        return result;
    }

    private double[] movingAverageCentered(double[] values, int windowPoints) {
        if (values.length == 0) {
            return values;
        }
        int size = Math.max(3, windowPoints);
        if (size % 2 == 0) {
            size++;
        }
        int half = size / 2;
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double total = 0;
            for (int offset = -half; offset <= half; offset++) {
                int index = Math.max(0, Math.min(values.length - 1, i + offset));
                total += values[index];
            }
            result[i] = total / size;
        }
        return result;
    }
    private List<RoutePoint> parsePoints(String gpx) {
        List<RoutePoint> points = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(gpx)));
            NodeList nodes = document.getElementsByTagName("trkpt");
            if (nodes.getLength() == 0) {
                nodes = document.getElementsByTagNameNS("*", "trkpt");
            }
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                double lat = parseDecimal(element.getAttribute("lat"));
                double lon = parseDecimal(element.getAttribute("lon"));
                double ele = 0;
                NodeList eleNodes = element.getElementsByTagName("ele");
                if (eleNodes.getLength() == 0) {
                    eleNodes = element.getElementsByTagNameNS("*", "ele");
                }
                if (eleNodes.getLength() > 0) {
                    ele = parseDecimal(eleNodes.item(0).getTextContent());
                }
                points.add(new RoutePoint(lat, lon, ele, 0));
            }
        } catch (Exception ex) {
            log.warn("route-analysis GPX parse failed reason={}", ex.getMessage());
        }
        return points;
    }

    private double parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    private List<RouteAnalysisHighlight> findClimbHighlights(List<RoutePoint> points) {
        List<RouteAnalysisHighlight> highlights = new ArrayList<>();
        if (points.size() < 2) {
            return highlights;
        }
        double windowKm = 1.5;
        for (int start = 0; start < points.size(); start += 25) {
            RoutePoint startPoint = points.get(start);
            int end = -1;
            for (int i = start + 1; i < points.size(); i++) {
                if (points.get(i).distanceKm() >= startPoint.distanceKm() + windowKm) {
                    end = i;
                    break;
                }
            }
            if (end < 0) {
                continue;
            }
            RoutePoint endPoint = points.get(end);
            double gain = endPoint.ele() - startPoint.ele();
            double slope = gain / (windowKm * 1000.0) * 100.0;
            if (gain > 90 && slope > 5) {
                highlights.add(new RouteAnalysisHighlight(
                        "Subida destacada",
                        String.format(Locale.US, "Tramo de %.1f km con pendiente media aproximada del %.1f%%.", windowKm, slope),
                        round1(startPoint.distanceKm()),
                        round1(endPoint.distanceKm()),
                        startPoint.lat(),
                        startPoint.lon(),
                        "climb",
                        slope > 8 ? "high" : "medium"
                ));
            }
            if (highlights.size() >= 4) {
                break;
            }
        }
        return highlights;
    }


    private List<RouteAnalysisSector> buildLocalSectors(RouteStats stats) {
        double distance = Math.max(0.1, stats.distanceKm());
        double firstEnd = round1(distance / 3.0);
        double secondEnd = round1(distance * 2.0 / 3.0);
        String centralEffort = stats.elevationGainM() > 800 ? "alto" : "medio";
        return List.of(
                new RouteAnalysisSector("Entrada en ruta", 0.0, firstEnd, "Regular salida, leer terreno y evitar gastar antes de los bloques decisivos.", "medio"),
                new RouteAnalysisSector("Bloque central", firstEnd, secondEnd, "Gestionar repechos y aprovechar transiciones para recuperar sin perder continuidad.", centralEffort),
                new RouteAnalysisSector("Cierre", secondEnd, round1(distance), "Llegar con margen para responder al cansancio acumulado y mantener tecnica.", "medio")
        );
    }

    private RouteAnalysisApproaches buildLocalApproaches(double gain) {
        return new RouteAnalysisApproaches(
                List.of(
                        "Sal con ritmo conversacional y usa los puntos destacados como referencias para parar o reagrupar.",
                        "En las subidas, prioriza cadencia comoda y evita convertir el primer bloque en una persecucion."
                ),
                List.of(
                        "Trabaja las subidas destacadas como bloques de tempo controlado y recupera en los enlaces.",
                        gain > 700 ? "No pases de umbral antes del bloque central: la ruta premia la progresion." : "Usa la ruta para sostener ritmo constante y cerrar con ligera progresion."
                ),
                List.of(
                        "Reserva cambios de ritmo para los repechos numerados, donde el esfuerzo tiene mas retorno.",
                        "Reconoce antes los kilometros criticos para decidir si atacar, conservar rueda o regular."
                )
        );
    }
    private boolean hasHardInitialClimb(List<RoutePoint> points) {
        if (points.size() < 2) {
            return false;
        }
        RoutePoint first = points.get(0);
        for (RoutePoint point : points) {
            if (point.distanceKm() > 3.0) {
                break;
            }
            if (point.ele() - first.ele() > 160) {
                return true;
            }
        }
        return false;
    }

    private List<RoutePoint> samplePoints(List<RoutePoint> points) {
        if (points.size() <= MAX_PROMPT_POINTS) {
            return points;
        }
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) MAX_PROMPT_POINTS));
        List<RoutePoint> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }

    private RouteAnalysisReport sanitizeReport(RouteAnalysisReport report, RouteAnalysisReport fallback) {
        if (report == null || isBlank(report.summary()) || isBlank(report.difficultyExplanation())) {
            return fallback;
        }
        return new RouteAnalysisReport(
                report.summary(),
                isBlank(report.routeType()) ? fallback.routeType() : report.routeType(),
                report.difficultyExplanation(),
                report.highlights() != null ? report.highlights() : fallback.highlights(),
                report.warnings() != null ? report.warnings() : fallback.warnings(),
                report.recommendations() != null ? report.recommendations() : fallback.recommendations(),
                report.sectors() != null ? report.sectors() : fallback.sectors(),
                report.approaches() != null ? report.approaches() : fallback.approaches()
        );
    }

    private boolean isReusableAnalysis(RouteAnalysis analysis) {
        return ANALYSIS_VERSION.equals(analysis.getAnalysisVersion())
                && analysis.getModel() != null
                && !analysis.getModel().startsWith("local");
    }

    private boolean isAiReport(GeneratedReport generatedReport) {
        return generatedReport.model() != null && !generatedReport.model().startsWith("local");
    }

    private String truncateFallbackReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Error desconocido al llamar a OpenAI";
        }
        String normalized = reason.replaceAll("\\s+", " ").trim();
        return normalized.length() > 580 ? normalized.substring(0, 580) : normalized;
    }
    private RouteAnalysisResponse mapToResponse(RouteAnalysis analysis) {
        return mapToResponse(analysis, false, true);
    }

    private RouteAnalysisResponse mapToResponse(RouteAnalysis analysis, boolean usageCharged, boolean reusedExisting) {
        return new RouteAnalysisResponse(
                analysis.getId(),
                analysis.getSourceTrackId(),
                analysis.getUserId(),
                analysis.getSource(),
                analysis.getFileName(),
                analysis.getGpxHash(),
                analysis.getAnalysisVersion(),
                analysis.getModel(),
                analysis.getFallbackReason(),
                analysis.getUserInstructions(),
                analysis.getElevationSource(),
                calculateElevation(analysis.getRouteXml()),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt(),
                readReport(analysis.getReportJson()),
                usageCharged,
                reusedExisting
        );
    }

    private PublicRouteAnalysisResponse mapToPublicResponse(RouteAnalysis analysis) {
        return new PublicRouteAnalysisResponse(
                analysis.getId(),
                publicTitle(analysis),
                analysis.getFileName(),
                analysis.getSource(),
                calculateElevation(analysis.getRouteXml()),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt(),
                readReport(analysis.getReportJson())
        );
    }

    private String publicTitle(RouteAnalysis analysis) {
        String routeTitle = analysis.getRouteTitle();
        if (routeTitle != null && !routeTitle.isBlank()) {
            return routeTitle.trim();
        }
        if (analysis.getSourceTrackId() != null) {
            String population = trackRepository.findById(analysis.getSourceTrackId())
                    .map(track -> track.getPopulation())
                    .orElse(null);
            if (population != null && !population.isBlank()) {
                return population.trim();
            }
        }
        String fileName = analysis.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return "Ruta analizada";
        }
        String title = fileName.replaceFirst("(?i)\\.gpx$", "").trim();
        if (title.toLowerCase().startsWith("activity_")) {
            return "Ruta analizada";
        }
        return title.isBlank() ? "Ruta analizada" : title;
    }

    private String normalizeRouteTitle(String value) {
        if (value == null || value.isBlank() || value.trim().toLowerCase().startsWith("activity_")) {
            return "Ruta analizada";
        }
        return value.trim();
    }

    public RouteAnalysisResponse.RouteAnalysisStats calculateElevation(String routeXml) {
        RouteStats stats = parseStats(routeXml);
        List<RouteAnalysisResponse.RouteAnalysisElevationPoint> profile = sampleElevationProfile(stats.points()).stream()
                .map(point -> new RouteAnalysisResponse.RouteAnalysisElevationPoint(point.distanceKm(), point.ele()))
                .toList();
        return new RouteAnalysisResponse.RouteAnalysisStats(
                stats.distanceKm(), stats.elevationGainM(), stats.elevationLossM(),
                stats.minEleM(), stats.maxEleM(), profile);
    }

    private List<RoutePoint> sampleElevationProfile(List<RoutePoint> points) {
        if (points.size() <= 1000) {
            return points;
        }
        int stride = (int) Math.ceil(points.size() / 999.0);
        List<RoutePoint> sampled = new ArrayList<>();
        for (int index = 0; index < points.size(); index += stride) {
            sampled.add(points.get(index));
        }
        RoutePoint last = points.get(points.size() - 1);
        if (sampled.get(sampled.size() - 1) != last) {
            sampled.add(last);
        }
        return sampled;
    }

    private RouteAnalysisReport readReport(String reportJson) {
        try {
            return objectMapper.readValue(reportJson, RouteAnalysisReport.class);
        } catch (Exception ex) {
            return new RouteAnalysisReport("No se pudo leer el analisis guardado.", "mixta", "", List.of(), List.of(), List.of(), List.of(), new RouteAnalysisApproaches(List.of(), List.of(), List.of()));
        }
    }

    private String writeReport(RouteAnalysisReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el analisis", ex);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "upload";
        }
        String normalized = source.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tracks", "plan", "upload" -> normalized;
            default -> "upload";
        };
    }

    private String normalizeInstructions(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 1200 ? normalized.substring(0, 1200) : normalized;
    }

    private String instructionPrompt(String userInstructions) {
        if (isBlank(userInstructions)) {
            return "";
        }
        return "Instrucciones especificas del usuario: " + userInstructions
                + ". Respetalas si no contradicen los datos objetivos del track. ";
    }

    private String nullToEmptyHash(String value) {
        return value == null ? sha256("") : value;
    }

    private String classifyRoute(double distanceKm, double gain) {
        if (gain > 1200 || (distanceKm > 0 && gain / distanceKm > 35)) {
            return "subida_dura";
        }
        if (gain > 800 || distanceKm > 70) {
            return "exigente";
        }
        if (gain < 300 && distanceKm > 20) {
            return "rodadora";
        }
        if (distanceKm < 18 && gain < 250) {
            return "suave";
        }
        return "mixta";
    }

    private double distanceMeters(RoutePoint a, RoutePoint b) {
        double radius = 6371000;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double value = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * radius * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular hash GPX", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record GeneratedReport(RouteAnalysisReport report, String model, String fallbackReason) {}

    private record PromptPayload(
            double distanceKm,
            double elevationGainM,
            double elevationLossM,
            double minEleM,
            double maxEleM,
            int points,
            String elevationSource,
            List<RoutePoint> sampledPoints,
            RouteAnalysisReport localReport
    ) {}

    private record RouteStats(
            List<RoutePoint> points,
            double distanceKm,
            double elevationGainM,
            double elevationLossM,
            double minEleM,
            double maxEleM,
            String elevationSource
    ) {}

    private record ElevationTotals(double gain, double loss) {}

    private record RoutePoint(double lat, double lon, double ele, double distanceKm) {}
}























