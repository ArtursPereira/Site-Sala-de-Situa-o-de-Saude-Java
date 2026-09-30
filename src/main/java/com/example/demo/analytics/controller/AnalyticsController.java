package com.example.demo.analytics.controller;

import com.example.demo.analytics.dto.CasoResumo;
import com.example.demo.analytics.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;

    @GetMapping("casos")
    public List<CasoResumo> buscarCasos(
            @RequestParam Integer ano,
            @RequestParam Integer mes
    ){
        return  analyticsRepository.buscarCasos(ano,mes);
    }
}
