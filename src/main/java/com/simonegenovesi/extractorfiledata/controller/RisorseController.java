package com.simonegenovesi.extractorfiledata.controller;

import com.simonegenovesi.extractorfiledata.payload.response.MetadatiRisorsaResponse;
import com.simonegenovesi.extractorfiledata.service.RisorseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/risorse")
@RequiredArgsConstructor
public class RisorseController {

    private final RisorseService risorseService;

    @GetMapping("/")
    public ResponseEntity<List<MetadatiRisorsaResponse>> getAllRisorse() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(risorseService.getAllRisorse());
    }

}
