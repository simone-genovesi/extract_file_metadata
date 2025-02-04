package com.simonegenovesi.extractorfiledata.service.impl;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorse;
import com.simonegenovesi.extractorfiledata.entity.Metriche;
import com.simonegenovesi.extractorfiledata.payload.response.ProcessedFiles;
import com.simonegenovesi.extractorfiledata.repository.MetadatiRisorseRepository;
import com.simonegenovesi.extractorfiledata.repository.MetricheRepository;
import com.simonegenovesi.extractorfiledata.service.MetadataService;
import com.simonegenovesi.extractorfiledata.util.MimeTypeEnum;
import jakarta.annotation.PostConstruct;
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
public class MetadataServiceImpl implements MetadataService {

    @Value("${path.base}")
    private String pathBase;

    private final MetadatiRisorseRepository metadatiRisorseRepository;
    private final MetricheRepository metricheRepository;
    private final Tika tika;

    @Override
    public void extractMetadata(String folderPath) {
        var start = System.nanoTime();
        List<File> allFiles = getAllFilesFromFolders(folderPath);

        var codici = estraiCodici(folderPath);
        var fileProcessati = processaFile(allFiles, codici);
        var metadati = fileProcessati.metadati();
        var metriche = fileProcessati.metriche();

        metadatiRisorseRepository.saveAll(metadati);
        metricheRepository.save(metriche);
        var end = System.nanoTime();
        if (!allFiles.isEmpty()) {
            log.info("Tempo medio di salvataggio: {} ms", ((double) (end - start) / 1_000_000) / allFiles.size());
        }
    }

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

    private ProcessedFiles processaFile(List<File> allFiles, List<String> codici) {
        var start = System.nanoTime();
        Map<String, Metriche.MetricheSummary> metricheMap = new HashMap<>();
        List<MetadatiRisorse> metadatiList = new ArrayList<>();
        long dimTotale = 0;

        for (File file: allFiles) {
            String formatoFile = getMimeType(file);
            MimeTypeEnum mimeEnum = MimeTypeEnum.fromString(formatoFile);
            String formatoAbbreviato = mimeEnum.getAbbreviation(); // Usa abbreviazione per metriche

            long fileSize = file.length();
            String nomeOggetto = file.getName();

            // Se il file è image/jpeg e ha estensione.j, rinominalo in.jpg
            if (mimeEnum == MimeTypeEnum.IMAGE_JPEG && nomeOggetto.endsWith(".j")) {
                nomeOggetto = nomeOggetto.replace(".j", ".jpg");
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
            metricheMap.putIfAbsent(formatoAbbreviato, new Metriche.MetricheSummary(0, 0L));
            Metriche.MetricheSummary metriche = metricheMap.get(formatoAbbreviato);

            metriche.setNumRisorse(metriche.getNumRisorse() + 1);
            metriche.setDimTotale(metriche.getDimTotale() + fileSize);

            dimTotale += fileSize;
        }

        List<Metriche.DettaglioRisorsa> dettagliRisorse = new ArrayList<>();
        for (Map.Entry<String, Metriche.MetricheSummary> entry: metricheMap.entrySet()) {
            dettagliRisorse.add(Metriche.DettaglioRisorsa.builder()
                    .formatoFile(entry.getKey()) // Ora usa l'abbreviazione (es. "JPEG", "HOCR")
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

        var end = System.nanoTime();
        log.info("Tempo di elaborazione dei file: {} ms", (end - start) / 1_000_000);
        return new ProcessedFiles(metadatiList, metriche);
    }

}
