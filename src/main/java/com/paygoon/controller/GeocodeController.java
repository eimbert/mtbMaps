package com.paygoon.controller;

import org.springframework.web.bind.annotation.*;

import com.paygoon.dto.TrackLocationDetails;
import com.paygoon.service.NominatimReverseService;

@RestController
@RequestMapping("/geocode")
public class GeocodeController {

  private final NominatimReverseService nominatimReverseService;

  public GeocodeController(NominatimReverseService nominatimReverseService) {
    this.nominatimReverseService = nominatimReverseService;
  }

  @GetMapping("/reverse")
  public TrackLocationDetails reverse(@RequestParam double lat, @RequestParam double lon) {
    return nominatimReverseService.reverse(lat, lon);
  }
}


