package com.simonegenovesi.extractorfiledata.controller;

import com.simonegenovesi.extractorfiledata.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    @PostMapping
    public ResponseEntity<Void> extractMetadata(
            @RequestParam(defaultValue = "\\CA01CN01\\LDIG002\\PK0000004\\contenuto") String folderPath
    ) {
        metadataService.extractMetadata(folderPath);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }
}
