package com.simonegenovesi.extractorfiledata.controller;

import com.simonegenovesi.extractorfiledata.payload.request.MetadataRequest;
import com.simonegenovesi.extractorfiledata.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    //http://localhost:8081/api/swagger-ui/index.html#/metadata-controller/extractMetadata
    // "\CA01CN01\LDIG002\PK0000004\contenuto"
    // "\CA01CN01\LDIG002\PK0000028\contenuto"
    @PostMapping("/")
    public ResponseEntity<Void> extractMetadata(
            @RequestBody MetadataRequest request
    ) {
        metadataService.extractMetadata(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    @DeleteMapping("/")
    public ResponseEntity<Void> deleteAllData() {
        metadataService.deleteAllData();
        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }
}
