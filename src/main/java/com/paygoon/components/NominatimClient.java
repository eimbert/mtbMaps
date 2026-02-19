package com.paygoon.components;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.paygoon.dto.TrackLocationDetails;

import java.util.List;
import java.util.Map;

@Component
public class NominatimClient {

  private final WebClient webClient;

  public NominatimClient(WebClient.Builder builder) {
    this.webClient = builder
      .baseUrl("https://nominatim.openstreetmap.org")
      .defaultHeader(HttpHeaders.USER_AGENT, "tracketeo/1.0 (contacto@tracketeo.bike)")
      .defaultHeader(HttpHeaders.ACCEPT, "application/json")
      .build();
  }

  @SuppressWarnings("unchecked")
  public TrackLocationDetails reverse(double lat, double lon) {

    Map<String, Object> json = webClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/reverse")
        .queryParam("format", "geocodejson")
        .queryParam("lat", lat)
        .queryParam("lon", lon)
        .queryParam("zoom", 15)
        .queryParam("addressdetails", 1)
        .queryParam("layer", "address")
        .build())
      .retrieve()
      .bodyToMono(Map.class)
      .block();

    if (json == null) return new TrackLocationDetails(null, null, null, null);

    List<Object> features = (List<Object>) json.get("features");
    if (features == null || features.isEmpty()) return new TrackLocationDetails(null, null, null, null);

    Map<String, Object> firstFeature = (Map<String, Object>) features.get(0);
    Map<String, Object> props = (Map<String, Object>) firstFeature.get("properties");
    Map<String, Object> geocoding = props == null ? null : (Map<String, Object>) props.get("geocoding");
    if (geocoding == null) return new TrackLocationDetails(null, null, null, null);

    Map<String, Object> admin = (Map<String, Object>) geocoding.get("admin");

    String city = admin == null ? null : (String) admin.get("level8");
    if (city == null) city = (String) geocoding.get("city");
    if (city == null) city = (String) geocoding.get("town");
    if (city == null) city = (String) geocoding.get("village");

    String state = admin == null ? null : (String) admin.get("level4");
    if (state == null) state = (String) geocoding.get("state");

    String county = admin == null ? null : (String) admin.get("level7");
    if (county == null) county = (String) geocoding.get("county");

    String province = admin == null ? null : (String) admin.get("level6");
    if (province == null) province = county != null ? county : state;

    return new TrackLocationDetails(
      city,
      state,
      county,
      province
    );
  }
}
