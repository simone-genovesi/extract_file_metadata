package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.entity.Log;
import com.simonegenovesi.extractorfiledata.payload.request.MetadataRequest;
import com.simonegenovesi.extractorfiledata.repository.LogRepository;
import com.simonegenovesi.extractorfiledata.repository.MetadatiRisorsaRepository;
import com.simonegenovesi.extractorfiledata.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.simonegenovesi.extractorfiledata.util.Codici.estraiCodici;
import static com.simonegenovesi.extractorfiledata.util.Elementi.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataService {

    @Value("${path.base::#{null}}") // Valore di default per Docker
    private String pathBase;

    private final MetadatiRisorsaRepository metadatiRisorseRepository;
    private final MetricaRepository metricheRepository;
    private final Thumbnail thumbnail;
    private final LogRepository logRepository;

    public void estraiMetadata(MetadataRequest request) {
        var start = System.nanoTime();

        var relativePath = request.getPath();

        if(!isWindows()){
            relativePath = normalizePath(relativePath); // Normalize the path
        }

        var allFiles = getAllFilesFromFolders(pathBase, relativePath);

        var codici = estraiCodici(relativePath);
        var fileProcessati = processaFile(allFiles, codici);
        var metadati = fileProcessati.metadati();
        var metriche = fileProcessati.metrica();
        var listaTiffImages = fileProcessati.listaTiffImages();

        metadatiRisorseRepository.saveAll(metadati);
        logRepository.save(Log.builder().messagio("Salvataggio dei metadati andato a buon fine.").build());
        metricheRepository.save(metriche);
        logRepository.save(Log.builder().messagio("Salvataggio delle metriche andato a buon fine.").build());
        var end = System.nanoTime();
        if (!allFiles.isEmpty()) {
            log.info("Tempo medio di salvataggio: {} ms", ((double) (end - start) / 1_000_000) / allFiles.size());
            log.info("Tempo totale operazione: {} ms", (double) (end - start) / 1_000_000);
            thumbnail.doThumbnail(listaTiffImages);
        }

    }

    public void deleteAllData() {
        log.info("Cancellazione di tutti i dati dal db...");
        metadatiRisorseRepository.deleteAll();
        metricheRepository.deleteAll();
        logRepository.deleteAll();
        log.info("Tutti i dati sono stati cancellati");
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private String normalizePath(String input) {
        return input.replace("\\", "/");
    }

}
