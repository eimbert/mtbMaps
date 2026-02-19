package com.paygoon.service;

import org.springframework.stereotype.Service;

import com.paygoon.components.NominatimClient;
import com.paygoon.dto.TrackLocationDetails;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class NominatimReverseService {

  private final NominatimClient nominatimClient;

  // cache simple en memoria
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final Duration ttl = Duration.ofHours(12);

  // rate limit global 1 req / ~1.1s
  private final AtomicLong nextAllowedMs = new AtomicLong(0);

  public NominatimReverseService(NominatimClient nominatimClient) {
    this.nominatimClient = nominatimClient;
  }

  public TrackLocationDetails reverse(double lat, double lon) {
    String key = cacheKey(lat, lon);
    CacheEntry cached = cache.get(key);
    if (cached != null && !cached.isExpired(ttl)) {
      return cached.value();
    }

    TrackLocationDetails details = callWithRetry(lat, lon);

    // guarda cache aunque venga vacío (evita martillear si Nominatim no resuelve bien)
    cache.put(key, new CacheEntry(details, System.currentTimeMillis()));
    return details;
  }

  private TrackLocationDetails callWithRetry(double lat, double lon) {
    try {
      throttle();
      return nominatimClient.reverse(lat, lon);
    } catch (RuntimeException e) {
      if (!isRetryable(e)) {
        return empty();
      }
      // retry 1 vez
      sleep(600);
      try {
        throttle();
        return nominatimClient.reverse(lat, lon);
      } catch (RuntimeException ignored) {
        return empty();
      }
    }
  }

  private boolean isRetryable(RuntimeException e) {
    // Si usas WebClient.retrieve(), los errores vienen como WebClientResponseException
    Throwable t = e;
    while (t != null) {
      String name = t.getClass().getName();
      if (name.endsWith("WebClientResponseException")) {
        try {
          int status = (int) t.getClass().getMethod("getRawStatusCode").invoke(t);
          return status == 425 || status == 429 || status == 503;
        } catch (Exception ignored) {
          return false;
        }
      }
      t = t.getCause();
    }
    return false;
  }

  private void throttle() {
    long now = System.currentTimeMillis();
    while (true) {
      long allowed = nextAllowedMs.get();
      long wait = allowed - now;
      if (wait <= 0) {
        if (nextAllowedMs.compareAndSet(allowed, now + 1100)) return;
      } else {
        sleep(wait);
        now = System.currentTimeMillis();
      }
    }
  }

  private TrackLocationDetails empty() {
    return new TrackLocationDetails(null, null, null, null);
  }

  private String cacheKey(double lat, double lon) {
    // 5 decimales ~ 1m (lat) / ~0.8m (lon a 43º)
    return round(lat, 5) + "," + round(lon, 5);
  }

  private static String round(double v, int decimals) {
    return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
  }

  private static void sleep(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
  }

  private record CacheEntry(TrackLocationDetails value, long tsMs) {
    boolean isExpired(Duration ttl) {
      return System.currentTimeMillis() - tsMs > ttl.toMillis();
    }
  }
}
