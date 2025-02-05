package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.payload.response.MetricaResponse;
import com.simonegenovesi.extractorfiledata.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricaService {

    private final MetricaRepository metricaRepository;
    private final ModelMapper modelMapper;

    public List<MetricaResponse> getAllMetriche() {

        return metricaRepository.findAll()
                .stream()
                .map(metrica -> modelMapper
                        .map(metrica, MetricaResponse.class))
                .toList();

    }
}
