package com.example.nss.analytics.web.controller;

import com.example.nss.analytics.api.response.CasoResumoResponse;
import com.example.nss.analytics.application.AnalyticsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  @GetMapping("casos")
  public List<CasoResumoResponse> buscarCasos(
      @RequestParam Integer ano, @RequestParam Integer mes) {
    return analyticsService.buscarCasos(ano, mes);
  }
}
