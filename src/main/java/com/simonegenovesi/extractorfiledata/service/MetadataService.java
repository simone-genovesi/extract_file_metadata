package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorsa;
import com.simonegenovesi.extractorfiledata.entity.Metrica;
import com.simonegenovesi.extractorfiledata.payload.request.MetadataRequest;
import com.simonegenovesi.extractorfiledata.util.FileProcessati;
import com.simonegenovesi.extractorfiledata.repository.MetadatiRisorsaRepository;
import com.simonegenovesi.extractorfiledata.repository.MetricaRepository;
import com.simonegenovesi.extractorfiledata.util.MimeTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataService {

    @Value("${path.base}")
    private String pathBase;

    private final MetadatiRisorsaRepository metadatiRisorseRepository;
    private final MetricaRepository metricheRepository;
    private final Tika tika;

    public void extractMetadata(MetadataRequest request) {
        var start = System.nanoTime();
        List<File> allFiles = getAllFilesFromFolders(request.getPath());

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
        log.info("Cancellazione di tutti i dati dla db...");
        metadatiRisorseRepository.deleteAll();
        metricheRepository.deleteAll();
        log.info("Tutti i dati sono stati cancellati");
    }


    // TODO valutare l'uso dei threads o processi per scannerizare le cartelle e recuperare i file
    private List<File> getAllFilesFromFolders(String folderPath) {
        var start = System.nanoTime();
        List<File> fileList = new ArrayList<>();
        File rootDir = new File(pathBase, folderPath);

        if (rootDir.exists() && rootDir.isDirectory()) {
            scanDirectory(rootDir, fileList);
        }

        var end = System.nanoTime();
        log.info("Tempo di recupero dei file: {} ms", (end - start) / 1_000_000);
        return fileList;
    }

    private void scanDirectory(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files == null) return; // Evita NullPointerException

        for (File file : files) {
            if (file.isFile()) {
                fileList.add(file);
            } else if (file.isDirectory()) {
                scanDirectory(file, fileList); // Ricorsione per sottodirectory
            }
        }
    }


    // TODO inserire sistema per il riconoscimento del formato file dai primi 256 bytes cosí da non usare Tika
    private String getMimeType(File file) {
        try {
            String mimeType = Files.probeContentType(file.toPath());

            // Se il MIME è null o generico, usa Tika per un'analisi più accurata
            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                mimeType = tika.detect(file);
            }

            // Normalizza il MIME type rimuovendo eventuali charset extra (tranne per hOCR)
            if (mimeType.endsWith(";") &&!file.getName().endsWith(".hocr")) {
                mimeType = mimeType.substring(0, mimeType.length() - 1);
            }

            // Verifica se il MIME type è già valido
            if (MimeTypeEnum.fromString(mimeType)!= MimeTypeEnum.UNKNOWN) {
                return mimeType; // Il MIME type è già valido
            }

            // Corregge i formati speciali solo se necessario
            if (mimeType.equals("text/xml")) {
                return MimeTypeEnum.APPLICATION_XML.getMimeType();
            } else if (file.getName().endsWith(".j")) {
                return MimeTypeEnum.IMAGE_JPEG.getMimeType();
            } else if (file.getName().endsWith(".hocr")) {
                return MimeTypeEnum.APPLICATION_XHTML.getMimeType();
            } else {
                return MimeTypeEnum.UNKNOWN.getMimeType(); // MIME type non valido
            }

        } catch (IOException e) {
            log.error("Errore durante la lettura del file {}: {}", file.getAbsolutePath(), e.getMessage());
            return MimeTypeEnum.UNKNOWN.getMimeType();
        }
    }

    private List<String> estraiCodici(String percorso) {
        var start = System.nanoTime();
        Path path = Paths.get(percorso);
        int depth = path.getNameCount();

        var end = System.nanoTime();
        log.info("Tempo di estrazione dei codici cartelle: {} ms", (end - start) / 1_000_000);
        return List.of(
                depth > 0 ? path.getName(0).toString() : "N/A",
                depth > 1 ? path.getName(1).toString() : "N/A",
                depth > 2 ? path.getName(2).toString() : "N/A"
        );
    }

    private FileProcessati processaFile(List<File> allFiles, List<String> codici) {
        var start = System.nanoTime();

        int fileCount = allFiles.size();
        Map<String, Metrica.MetricheSummary> metricheMap = new HashMap<>(fileCount);
        List<MetadatiRisorsa> metadatiList = new ArrayList<>(fileCount);
        List<Metrica.DettaglioRisorsa> dettagliRisorse = new ArrayList<>();
        long dimTotale = 0;

        // Caching dei MIME type per evitare chiamate ripetute a Tika
        Map<File, String> mimeCache = new HashMap<>(fileCount);

        for (File file : allFiles) {
            long fileSize = file.length();

            // Recupera MIME type dalla cache o calcolalo se necessario
            String formatoFile = mimeCache.computeIfAbsent(file, this::getMimeType);
            MimeTypeEnum mimeEnum = MimeTypeEnum.fromString(formatoFile);
            String formatoAbbreviato = mimeEnum.getAbbreviation();
            String nomeOggetto = file.getName();

            // Se il file è image/jpeg e ha estensione .j, rinominalo in .jpg
            if (mimeEnum == MimeTypeEnum.IMAGE_JPEG && nomeOggetto.endsWith(".j")) {
                nomeOggetto = nomeOggetto.replace(".j", ".jpg");
            }

            // Creazione MetadatiRisorse
            metadatiList.add(MetadatiRisorsa.builder()
                    .urlOggetto(file.getAbsolutePath())
                    .nomeOggetto(nomeOggetto)
                    .dimensioneFile(fileSize)
                    .formatoFile(formatoFile)
                    .codiceCantiere(codici.get(0))
                    .codiceLotto(codici.get(1))
                    .codicePacchetto(codici.get(2))
                    .build()
            );

            // Aggiornamento Metriche
            metricheMap.computeIfAbsent(formatoAbbreviato, k -> Metrica.MetricheSummary.builder()
                            .numRisorse(0)
                            .dimTotale(0L)
                            .build())
                    .setDimTotale(metricheMap.get(formatoAbbreviato).getDimTotale() + fileSize);

            metricheMap.get(formatoAbbreviato).setNumRisorse(metricheMap.get(formatoAbbreviato).getNumRisorse() + 1);

            dimTotale += fileSize;
        }

        // Creazione dettagliRisorse direttamente nel primo ciclo
        for (var entry : metricheMap.entrySet()) {
            dettagliRisorse.add(Metrica.DettaglioRisorsa.builder()
                            .formatoFile(entry.getKey())
                            .metricheSummary(entry.getValue())
                            .build()
            );
        }

        // Creazione dell'oggetto Metriche
        Metrica metriche = Metrica.builder()
                .codiceCantiere(codici.get(0))
                .codiceLotto(codici.get(1))
                .codicePacchetto(codici.get(2))
                .metricheSummary(Metrica.MetricheSummary.builder()
                        .numRisorse(fileCount)
                        .dimTotale(dimTotale)
                        .build())
                .dettagliRisorse(dettagliRisorse)
                .build();

        var end = System.nanoTime();
        log.info("Tempo di elaborazione dei file: {} ms", (end - start) / 1_000_000);
        return new FileProcessati(metadatiList, metriche);
    }

}
