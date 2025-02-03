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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            return stream.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .toList();
        }
    }

    private String getMimeType(File file) {
        try {
            String mimeType = Files.probeContentType(file.toPath());

            // Se il MIME è null o generico, usa Tika per un'analisi più accurata
            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                mimeType = tika.detect(file);
            }

            // Normalizza il MIME type rimuovendo eventuali charset extra (tranne per hOCR)
            if (mimeType.contains(";") && !file.getName().endsWith(".hocr")) {
                mimeType = mimeType.split(";")[0]; // Rimuove il charset se presente
            }

            // Correzioni per formati specifici
            if (mimeType.equals("text/xml")) {
                mimeType = "application/xml";
            }

            if (file.getName().endsWith(".j")) {
                mimeType = "image/jpeg";
            }

            if (file.getName().endsWith(".hocr")) {
                mimeType = "application/xhtml+xml; charset=UTF-8";
            }

            return mimeType;
        } catch (IOException e) {
            log.error("Errore durante la lettura del file {}: {}", file.getAbsolutePath(), e.getMessage());
            return "sconosciuto";
        }
    }

    private List<String> estraiCodici(String percorso) {
        Path path = Paths.get(percorso);
        int depth = path.getNameCount();

        return List.of(
                depth > 0 ? path.getName(0).toString() : "N/A",
                depth > 1 ? path.getName(1).toString() : "N/A",
                depth > 2 ? path.getName(2).toString() : "N/A"
        );
    }

    private Pair<List<MetadatiRisorse>, Metriche> processaFile(List<File> allFiles, List<String> codici) {
        Map<String, Metriche.MetricheSummary> metricheMap = new HashMap<>();
        List<MetadatiRisorse> metadatiList = new ArrayList<>();
        long dimTotale = 0;

        for (File file : allFiles) {
            String formatoFile = getMimeType(file);
            long fileSize = file.length();

            String nomeOggetto = file.getName();

            // Se il file è image/jpeg e ha estensione .j, rinominalo in .jpg
            if (formatoFile.equals("image/jpeg") && nomeOggetto.endsWith(".j")) {
                nomeOggetto = nomeOggetto.substring(0, nomeOggetto.length() - 2) + ".jpg";
            }

            MetadatiRisorse metadato = MetadatiRisorse.builder()
                    .urlOggetto(file.getAbsolutePath())
                    .nomeOggetto(nomeOggetto)
                    .dimensioneFile(fileSize)
                    .formatoFile(formatoFile)
                    .codiceCantiere(codici.get(0))
                    .codiceLotto(codici.get(1))
                    .codicePacchetto(codici.get(2))
                    .build();

            metadatiList.add(metadato);

            // Aggiorna metriche
            if (!metricheMap.containsKey(formatoFile)) {
                metricheMap.put(formatoFile, new Metriche.MetricheSummary(0, 0L));
            }
            Metriche.MetricheSummary metriche = metricheMap.get(formatoFile);
            metriche.setDimTotale(fileSize);

            dimTotale += fileSize;
        }

        List<Metriche.DettaglioRisorsa> dettagliRisorse = new ArrayList<>();
        for (Map.Entry<String, Metriche.MetricheSummary> entry : metricheMap.entrySet()) {
            dettagliRisorse.add(Metriche.DettaglioRisorsa.builder()
                    .formatoFile(entry.getKey())
                    .metricheSummary(entry.getValue())
                    .build());
        }

        Metriche metriche = Metriche.builder()
                .codiceCantiere(codici.get(0))
                .codiceLotto(codici.get(1))
                .codicePacchetto(codici.get(2))
                .metricheSummary(new Metriche.MetricheSummary(allFiles.size(), dimTotale))
                .dettagliRisorse(dettagliRisorse)
                .build();

        return new ImmutablePair<>(metadatiList, metriche);
    }
}
