package com.simonegenovesi.extractorfiledata.util;

import com.simonegenovesi.extractorfiledata.util.dto.BufferedImageFromFile;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class Thumbnail {

    private Thumbnail() {
    }

    public static void doThumbnail(List<File> files) {
        long start = System.nanoTime();
        List<BufferedImageFromFile> images = new ArrayList<>();
        log.info("Starting creating thumbnails...");

        // Carica i plugin una sola volta
        ImageIO.scanForPlugins();

        // Ottimizzo i tiff rimuovendo i canali alpha.
        for (var tiff : files) {
            try (var inputStream = Files.newInputStream(tiff.toPath())) {
                var bufferedImage = ImageIO.read(inputStream);
                bufferedImage = removeAlphaChannel(bufferedImage); // per rimuovere canali alpha se presenti
                images.add(new BufferedImageFromFile(tiff.getName(), tiff.toPath().getParent(), bufferedImage));
            } catch (IOException e) {
                log.error("Errore nella lettura del file {}", tiff.getName(), e);
            }
        }

        ExecutorService executor = new ThreadPoolExecutor(
                2, // core pool size
                4, // max pool size
                60L, TimeUnit.SECONDS, // tempo massimo di inattività
                new LinkedBlockingQueue<>(5), // Limita la coda per evitare saturazione
                new ThreadPoolExecutor.CallerRunsPolicy() // Evita RejectedExecutionException
        );


        var futures = images.stream()
                .map(tiff -> executor.submit(() -> {
                    processImage(tiff.fileName(), tiff.parentPath(), tiff.image());
                    return null;
                }))
                .toList(); //return null perche deve restituire qualcosa comunque...

        // Aspettando il completamento di tutte le task
        for (var future: futures){
            try{
                future.get();
            } catch (ExecutionException | InterruptedException e) {
                log.error("Errore nell'elaborazione di un'immagine", e);
            }
        }

        executor.shutdown();

        long end = System.nanoTime();
        log.info("Tempo medio per thumbnail: {} ms", ((double) (end - start) / 1_000_000) / files.size());
        log.info("Tempo totale: {} ms", (double) (end - start) / 1_000_000);
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
            log.info("Thumbnail creata {} in {} secondi", thumbnailPath, (double) (end - start) / 1_000_000);
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
}
