package com.simonegenovesi.extractorfiledata.controller;

import com.simonegenovesi.extractorfiledata.payload.response.MetricaResponse;
import com.simonegenovesi.extractorfiledata.service.MetricaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/metrica")
@RequiredArgsConstructor
public class MetricaController {

    private final MetricaService metricaService;

    @GetMapping("/")
    public ResponseEntity<List<MetricaResponse>> getAllMetriche() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(metricaService.getAllMetriche());
    }
}
