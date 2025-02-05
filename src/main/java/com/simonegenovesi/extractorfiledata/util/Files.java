package com.simonegenovesi.extractorfiledata.util;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorsa;
import com.simonegenovesi.extractorfiledata.entity.Metrica;
import com.simonegenovesi.extractorfiledata.util.dto.FileProcessati;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.simonegenovesi.extractorfiledata.util.MimeType.deduciFormatoFile;

@Slf4j
public class Files {

    public static List<File> getAllFilesFromFolders(String pathBase, String folderPath) {
        var start = System.nanoTime();
        List<File> fileList = new ArrayList<>();
        var rootDir = Paths.get(pathBase, folderPath);

        if (java.nio.file.Files.exists(rootDir) && java.nio.file.Files.isDirectory(rootDir)) {
            try (var stream = java.nio.file.Files.walk(rootDir)) {
                fileList = stream
                        .filter(java.nio.file.Files::isRegularFile) // Filtra solo i file
                        .map(Path::toFile)         // Converte Path in File
                        .collect(Collectors.toList());
            } catch (IOException e) {
                log.error("Errore durante la lettura della directory: {}", e.getMessage());
                // Gestisci l'eccezione, ad esempio ritornando una lista vuota o null
                return new ArrayList<>(); // Restituisce una lista vuota in caso di errore
            }
        }

        var end = System.nanoTime();
        log.info("Tempo di recupero dei file: {} ms", (end - start) / 1_000_000);
        return fileList;
    }

    public static FileProcessati processaFile(List<File> allFiles, List<String> codici) {
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
            MimeTypeEnum mimeEnum = deduciFormatoFile(file);
            String formatoFile = mimeCache.computeIfAbsent(file, m -> mimeEnum.getAbbreviation().toLowerCase());
//            MimeTypeEnum mimeEnum = MimeTypeEnum.fromString(formatoFile);
            String formatoAbbreviato = mimeEnum.getAbbreviation();
            String nomeOggetto = getString(file, mimeEnum);

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

    private static String getString(File file, MimeTypeEnum mimeEnum) {
        if (mimeEnum != MimeTypeEnum.IMAGE_JPEG) {
            return file.getName();
        }

        String nomeOggetto = file.getName();
        int ultimoPunto = nomeOggetto.lastIndexOf('.');

        // Se c'è un'estensione ed è diversa da ".jpg", la modifichiamo
        if (ultimoPunto > 0) {
            String estensione = nomeOggetto.substring(ultimoPunto);
            if (!estensione.equals(".jpg")) {
                return nomeOggetto.substring(0, ultimoPunto) + ".jpg";
            }
        }
        return nomeOggetto;
    }

}
