package com.simonegenovesi.extractorfiledata.util;

import com.simonegenovesi.extractorfiledata.util.dto.BufferedImageFromFile;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@UtilityClass
@Slf4j
public class Thumbnail {

    public static void doThumbnail(List<File> files) {
        long start = System.nanoTime();
        logMemoryUsage("Prima di iniziare l'elaborazione di tutti i batch");
        log.info("Inizio creazione delle miniature...");

        // Carica i plugin TwelveMonkeys una sola volta
        ImageIO.scanForPlugins();

        // Parametri batch
        final int BATCH_SIZE = 4;

        // Executor riutilizzabile per tutti i batch
        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            for (int i = 0; i < files.size(); i += BATCH_SIZE) {
                List<File> batch = files.subList(i, Math.min(i + BATCH_SIZE, files.size()));
                logMemoryUsage("Prima del batch " + (i + 1) + "-" + Math.min(i + BATCH_SIZE, files.size()));
                List<BufferedImageFromFile> images = new ArrayList<>();

                // Caricamento immagini per il batch corrente
                for (var tiff : batch) {
                    try (var inputStream = Files.newInputStream(tiff.toPath())) {
                        var bufferedImage = ImageIO.read(inputStream);
                        bufferedImage = removeAlphaChannel(bufferedImage);
                        images.add(new BufferedImageFromFile(tiff.getName(), tiff.toPath().getParent(), bufferedImage));
                    } catch (IOException e) {
                        log.error("Errore nella lettura del file {}", tiff.getName(), e);
                    }
                }

                var futures = images.stream()
                        .map(tiff -> executor.submit(() -> {
                            processImage(tiff.fileName(), tiff.parentPath(), tiff.image());
                            return null;
                        }))
                        .toList();

                for (var future : futures) {
                    try {
                        future.get();
                    } catch (ExecutionException | InterruptedException e) {
                        log.error("Errore nell'elaborazione di un'immagine", e);
                        Thread.currentThread().interrupt(); // buona pratica
                    }
                }

                log.info("Batch {}-{} completato", i + 1, Math.min(i + BATCH_SIZE, files.size()));
                logMemoryUsage("Dopo il batch " + (i + 1) + "-" + Math.min(i + BATCH_SIZE, files.size()));
                log.info("Ripristino delle risorse in corso...");
                images.clear(); // Libera la memoria delle immagini
                System.gc(); // Suggerisce al GC di agire;
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    log.warn("Il thread pool non è terminato nei tempi previsti.");
                    executor.shutdownNow(); // forza la chiusura
                }
            } catch (InterruptedException e) {
                log.error("Thread interrotto durante la chiusura del thread pool", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        long end = System.nanoTime();
        log.info("Elaborazione completata. Tempo totale: {} secondi", (end - start) / 1_000_000_000.0);
    }



    private static void processImage(String fileName, Path parentPath, BufferedImage image) {
        try {
            long start = System.nanoTime();
            log.info("Generazione della thumbnail dell'immagine {} in corso...", fileName);
            // Carica l'immagine
            if (image == null) {
                log.warn("Formato non supportato o file corrotto: {}", fileName);
                return;
            }

            // Crea cartella di output
            Path outputDirectory = parentPath.resolve("thumbnails");
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
                    .resolve(fileName.replaceFirst("\\.\\w+$", ".jpg")); //Forza .jpg
            Thumbnails.of(image)
                    .size(newWidth, newHeight)
                    .outputQuality(0.6)
                    .toFile(thumbnailPath.toFile());

            long end = System.nanoTime();
            log.info("Thumbnail creata {} in {} secondi", thumbnailPath, (end - start) / 1_000_000_000.0);
        } catch (IOException e) {
            log.error("Errore nella creazione della thumbnail del file {}", fileName, e);
        }
    }

    private static BufferedImage removeAlphaChannel(BufferedImage bufferedImage) {
        if(!bufferedImage.getColorModel().hasAlpha()){ // se non ha canali alpha restituisci subito l'immagine.
            return bufferedImage;
        }
        var newImage = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = newImage.createGraphics();
        try {
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(bufferedImage, 0, 0, null);
        } finally {
            g.dispose();
        }
        return newImage;
    }

    private static void logMemoryUsage(String context) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory(); // memoria allocata dalla JVM
        long freeMemory = runtime.freeMemory();   // memoria ancora disponibile all’interno della JVM
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();     // massimo possibile che la JVM può allocare

        log.info("[{}] Memoria - Max: {} MB, Tot: {} MB, Usata: {} MB, Libera: {} MB",
                context,
                maxMemory / (1024 * 1024),
                totalMemory / (1024 * 1024),
                usedMemory / (1024 * 1024),
                freeMemory / (1024 * 1024));
    }

}