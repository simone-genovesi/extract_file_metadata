package com.simonegenovesi.extractorfiledata.util;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorsa;
import com.simonegenovesi.extractorfiledata.entity.Metrica;
import com.simonegenovesi.extractorfiledata.util.dto.FileProcessati;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.simonegenovesi.extractorfiledata.util.MimeType.deduciFormatoFile;

@Slf4j
public class Elementi {

    private Elementi() {
    }

    public static List<File> getAllFilesFromFolders(String pathBase, String folderPath) {
        var start = System.nanoTime();
        List<File> fileList = new ArrayList<>();
        var rootDir = Paths.get(pathBase, folderPath);

        log.info("Searching in... {}", rootDir);

        if (Files.exists(rootDir) && Files.isDirectory(rootDir)) {
            try (var stream = Files.walk(rootDir)) {
                fileList = stream
                        .filter(Files::isRegularFile) // Filtra solo i file
                        .map(Path::toFile)         // Converte Path in File
                        .toList();
            } catch (IOException e) {
                log.error("Errore durante la lettura della directory: {}", e.getMessage());
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
        List<File> listaTiffImages = new ArrayList<>();
        long dimTotale = 0;

        // Caching dei MIME type per evitare chiamate ripetute a Tika
        Map<File, String> mimeCache = new HashMap<>(fileCount);

        for (File file : allFiles) {
            long fileSize = file.length();

            // Recupera MIME type dalla cache o calcolalo se necessario
            MimeTypeEnum mimeEnum = deduciFormatoFile(file);
            String formatoFile = mimeCache.computeIfAbsent(file, m -> mimeEnum.getAbbreviation().toLowerCase());
            String formatoAbbreviato = mimeEnum.getAbbreviation();
            String nomeOggetto = getString(file, mimeEnum);

            if (isTiffImage(formatoAbbreviato)) {
                listaTiffImages.add(file);
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
        return new FileProcessati(metadatiList, metriche, listaTiffImages);
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

    public static void doThumbnail(List<File> imageFiles) {
        long start = System.nanoTime();
        log.info("Starting creating thumbnails...");

        // Carica i plugin una sola volta
        ImageIO.scanForPlugins();

        imageFiles.parallelStream().forEach(Elementi::processImage);

        long end = System.nanoTime();
        log.info("Tempo medio per thumbnail: {} ms", ((double) (end - start) / 1_000_000) / imageFiles.size());
        log.info("Tempo totale: {} ms", (double) (end - start) / 1_000_000);
    }

    private static void processImage(File imageFile) {
        try {
            // Carica l'immagine
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                log.warn("Formato non supportato o file corrotto: {}", imageFile.getName());
                return;
            }

            // Crea cartella di output
            Path outputDirectory = imageFile.toPath().getParent().resolve("thumbnails");
            if (Files.notExists(outputDirectory)) {
                Files.createDirectories(outputDirectory);
            }

            // Calcola le nuove dimensioni
            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            int maxLongSide = 1200;

            int newWidth = (originalWidth >= originalHeight) ? maxLongSide
                    : (int) ((double) maxLongSide / originalHeight * originalWidth);
            int newHeight = (originalWidth >= originalHeight) ?
                    (int) ((double) maxLongSide / originalWidth * originalHeight) : maxLongSide;

            // Genera la thumbnail
            Path thumbnailPath = outputDirectory
                    .resolve(imageFile.getName().replaceFirst("\\.\\w+$", ".jpg")); //Forza .jpg
            Thumbnails.of(image)
                    .size(newWidth, newHeight)
                    .outputQuality(0.9)
                    .toFile(thumbnailPath.toFile());

            log.info("Thumbnail created: {}", thumbnailPath);
        } catch (IOException e) {
            log.error("Errore nella creazione della thumbnail per {}", imageFile, e);
        }
    }

    private static boolean isTiffImage(String formatoFile){
        return formatoFile.equals(MimeTypeEnum.IMAGE_TIFF.getAbbreviation());
    }
}
