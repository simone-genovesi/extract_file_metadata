package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.payload.response.MetadatiRisorsaResponse;
import com.simonegenovesi.extractorfiledata.repository.MetadatiRisorsaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RisorseService {

    private final MetadatiRisorsaRepository metadatiRisorsaRepository;
    private final ModelMapper modelMapper;

    public List<MetadatiRisorsaResponse> getAllRisorse() {
        return metadatiRisorsaRepository.findAll()
                .stream()
                .map(risorsa -> modelMapper
                        .map(risorsa, MetadatiRisorsaResponse.class))
                .toList();
    }
}
