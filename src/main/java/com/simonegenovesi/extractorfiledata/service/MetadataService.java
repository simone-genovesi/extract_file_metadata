package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.payload.request.MetadataRequest;
import com.simonegenovesi.extractorfiledata.repository.MetadatiRisorsaRepository;
import com.simonegenovesi.extractorfiledata.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

import static com.simonegenovesi.extractorfiledata.util.Codici.estraiCodici;
import static com.simonegenovesi.extractorfiledata.util.Files.getAllFilesFromFolders;
import static com.simonegenovesi.extractorfiledata.util.Files.processaFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataService {

    @Value("${path.base:/app/data}") // Valore di default per Docker
    private String pathBase;

    private final MetadatiRisorsaRepository metadatiRisorseRepository;
    private final MetricaRepository metricheRepository;

    public void estraiMetadata(MetadataRequest request) {
        var start = System.nanoTime();
        List<File> allFiles = getAllFilesFromFolders(pathBase, request.getPath());

        var codici = estraiCodici(request.getPath());
        var fileProcessati = processaFile(allFiles, codici);
        var metadati = fileProcessati.metadati();
        var metriche = fileProcessati.metrica();

        metadatiRisorseRepository.saveAll(metadati);
        metricheRepository.save(metriche);
        var end = System.nanoTime();
        if (!allFiles.isEmpty()) {
            log.info("Tempo medio di salvataggio: {} ms", ((double) (end - start) / 1_000_000) / allFiles.size());
            log.info("Tempo totale operazione: {} ms", (double) (end - start) / 1_000_000);
        }
    }

    public void deleteAllData() {
        log.info("Cancellazione di tutti i dati dal db...");
        metadatiRisorseRepository.deleteAll();
        metricheRepository.deleteAll();
        log.info("Tutti i dati sono stati cancellati");
    }

}
