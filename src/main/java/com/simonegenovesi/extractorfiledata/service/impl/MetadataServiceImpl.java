package com.simonegenovesi.extractorfiledata.service.impl;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorse;
import com.simonegenovesi.extractorfiledata.entity.Metriche;
import com.simonegenovesi.extractorfiledata.repository.MetadatiRisorseRepository;
import com.simonegenovesi.extractorfiledata.repository.MetricheRepository;
import com.simonegenovesi.extractorfiledata.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataServiceImpl implements MetadataService {

    @Value("${path.base}")
    private String pathBase;

    private final MetadatiRisorseRepository metadatiRisorseRepository;
    private final MetricheRepository metricheRepository;
    private final Tika tika;

    @Override
    public void extractMetadata(String folderPath) {
        var start = Instant.now();
        List<File> allFiles;
        try {
            allFiles = getAllFilesFromFolders(folderPath);
        } catch (IOException e) {
            throw new RuntimeException("Errore nel recupero dei file", e);
        }

        var codici = estraiCodici(folderPath);
        var fileProcessati = processaFile(allFiles, codici);
        var metadati = fileProcessati.getLeft();
        var metriche = fileProcessati.getRight();

        metadatiRisorseRepository.saveAll(metadati);
        metricheRepository.save(metriche);
        var end = Instant.now();
        var tempoTotaleSalvataggio = Duration.between(start, end).toMillis();
        if (!allFiles.isEmpty()) {
            log.info("Tempo medio di salvataggio: {} ms", (double) tempoTotaleSalvataggio / allFiles.size());
        }
    }

    private List<File> getAllFilesFromFolders(String folderPath) throws IOException {
        try (Stream<Path> stream = Files.walk(Path.of(pathBase + folderPath))) {
            return stream.parallel() // Parallelizza la scansione della cartella
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .toList();
        }
    }

    private String getMimeType(File file) {
        try {
            var mimeType = Files.probeContentType(file.toPath());
            return (mimeType != null && !mimeType.equals("application/octet-stream")) ? mimeType : tika.detect(file);
        } catch (IOException e) {
            log.error("Errore durante la lettura del file {}: {}", file.getAbsolutePath(), e.getMessage());
            return "unknown";
        }
    }

    private List<String> estraiCodici(String percorso) {
        var path = Paths.get(percorso);
        return List.of(
                path.getName(0).toString(),
                path.getName(1).toString(),
                path.getName(2).toString()
        );
    }

    private Pair<List<MetadatiRisorse>, Metriche> processaFile(List<File> allFiles, List<String> codici) {
        var metadatiList = allFiles.parallelStream()
                .map(file -> {
                    var formatoFile = getMimeType(file);
                    return MetadatiRisorse.builder()
                            .urlOggetto(file.getAbsolutePath())
                            .nomeOggetto(file.getName())
                            .dimensioneFile(file.length())
                            .formatoFile(formatoFile)
                            .codiceCantiere(codici.get(0))
                            .codiceLotto(codici.get(1))
                            .codicePacchetto(codici.get(2))
                            .build();
                })
                .toList();

        var dettagliRisorse = allFiles.parallelStream()
                .collect(Collectors.groupingBy(this::getMimeType, Collectors.summingLong(File::length)))
                .entrySet().stream()
                .map(entry -> Metriche.DettaglioRisorsa.builder()
                        .formatoFile(entry.getKey())
                        .metricheSummary(Metriche.MetricheSummary.builder()
                                .numRisorse((int) allFiles.stream()
                                        .filter(f -> getMimeType(f).equals(entry.getKey())).count())
                                .dimTotale(entry.getValue())
                                .build())
                        .build())
                .toList();

        long dimTotale = allFiles.stream().mapToLong(File::length).sum();

        var metriche = Metriche.builder()
                .codiceCantiere(codici.get(0))
                .codiceLotto(codici.get(1))
                .codicePacchetto(codici.get(2))
                .metricheSummary(Metriche.MetricheSummary.builder()
                        .numRisorse(allFiles.size())
                        .dimTotale(dimTotale)
                        .build())
                .dettagliRisorse(dettagliRisorse)
                .build();

        return new ImmutablePair<>(metadatiList, metriche);
    }
}
