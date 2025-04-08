package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.entity.Log;
import com.simonegenovesi.extractorfiledata.repository.LogRepository;
import com.simonegenovesi.extractorfiledata.util.dto.TileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class Thumbnail {

    private final LogRepository logRepository;

    public void doThumbnail(List<File> files) {
        long start = System.nanoTime();
        log.info("Inizio creazione delle miniature... Trovati {} file TIFF.", files.size());
        logMemoryUsage("Prima di iniziare l'elaborazione di tutti i batch");

        saveLog("Inizio elaborazione miniature per " + files.size() + " file."); // Salva log di inizio

        ImageIO.scanForPlugins();

        // Configurazione - Regola questi valori!
        final long TARGET_BATCH_MEMORY = 500 * 1024 * 1024; // 500MB - Esempio, regola questo valore!
        final int MAX_BATCH_SIZE = 20;  // Opzionale: limite massimo sulla dimensione del batch
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() - 2); // Tieni un paio di core liberi
        int maxPoolSize = corePoolSize * 2; // Permetti una certa crescita
        int queueSize = maxPoolSize * 2; // Regola secondo necessità

        ExecutorService batchExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadPoolExecutor.CallerRunsPolicy() // O DiscardPolicy, a seconda delle tue esigenze
        );

        try {
            List<File> currentBatch = new ArrayList<>();
            long currentBatchSize = 0;
            int fileCount = 0; // Contatore per il progresso

            for (File tiff : files) {
                long fileSize = tiff.length();

                // Controlla se l'aggiunta di questo file supererebbe il target
                if (currentBatchSize + fileSize > TARGET_BATCH_MEMORY && !currentBatch.isEmpty() || currentBatch.size() >= MAX_BATCH_SIZE) {
                    processBatch(batchExecutor, currentBatch); // Elabora il batch corrente
                    currentBatch = new ArrayList<>();         // Inizia un nuovo batch
                    currentBatchSize = 0;
                }

                currentBatch.add(tiff);
                currentBatchSize += fileSize;
                fileCount++;
                log.info("Elaborazione file {} di {} : {}", fileCount, files.size(), tiff.getName()); // Log di progresso
            }

            // Elabora l'ultimo batch, se presente
            if (!currentBatch.isEmpty()) {
                processBatch(batchExecutor, currentBatch);
            }

        } finally {
            shutdownAndAwaitTermination(batchExecutor, "batchExecutor");
        }

        long end = System.nanoTime();
        log.info("Elaborazione completata. Tempo totale: {} secondi", (end - start) / 1_000_000_000.0);
        saveLog("Elaborazione completata. Tempo totale: " + (end - start) / 1_000_000_000.0 + " secondi"); // Salva log di fine
    }

    private void processBatch(ExecutorService executor, List<File> batch) {
        log.info("Elaborazione di un batch di {} file", batch.size());
        logMemoryUsage("Prima del batch");

        List<Future<Void>> futures = new ArrayList<>();
        for (var tiff : batch) {
            futures.add(executor.submit(() -> {
                processTiffInTiles(tiff);
                return null;
            }));
        }

        for (var future : futures) {
            try {
                future.get();
            } catch (ExecutionException | InterruptedException e) {
                log.error("Errore nell'elaborazione di un'immagine", e);
                saveLog("Errore nell'elaborazione di un'immagine: " + e.getMessage()); // Salva log di errore
                Thread.currentThread().interrupt();
            }
        }
        logMemoryUsage("Dopo il batch");
        System.gc(); // Suggerisci garbage collection (non è garantito che venga eseguito immediatamente)
    }

    private void processTiffInTiles(File tiff) {
        try (ImageInputStream input = ImageIO.createImageInputStream(tiff)) {
            long start = System.nanoTime();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                log.warn("Nessun reader trovato per il file {}", tiff.getName());
                saveLog("Nessun reader trovato per il file: " + tiff.getName()); // Salva log di warning
                return;
            }
            ImageReader reader = readers.next();
            reader.setInput(input);
            int numPages = reader.getNumImages(true);
            //log.info("{} contiene {} pagina/e.", tiff.getName(), numPages);

            for (int page = 0; page < numPages; page++) {
                BufferedImage fullImage = reader.read(page);
                fullImage = removeAlphaChannel(fullImage);
                Path parentPath = tiff.toPath().getParent();
                log.info("Elaborazione tiles per {} pagina {}", tiff.getName(), page); // Log elaborazione tiles
                processImageAsTiledThumbnail(tiff.getName(), parentPath, fullImage, page);
            }
            long end = System.nanoTime();
            log.info("Thumbnail creata {} in {} secondi", tiff.getName(), (end - start) / 1_000_000_000.0);
            saveLog("Thumbnail creata " + tiff.getName() + " in " + (end - start) / 1_000_000_000.0 + " secondi"); // Salva log di successo
        } catch (IOException e) {
            log.error("Errore durante la lettura del TIFF {}", tiff.getName(), e);
            saveLog("Errore durante la lettura del TIFF " + tiff.getName() + ": " + e.getMessage()); // Salva log di errore
        }
    }

    private void processImageAsTiledThumbnail(String fileName, Path parentPath, BufferedImage image, int pageIndex) {
        int tileSize = 1024;
        int width = image.getWidth();
        int height = image.getHeight();

        int xTiles = (int) Math.ceil((double) width / tileSize);
        int yTiles = (int) Math.ceil((double) height / tileSize);

        BufferedImage stitchedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = stitchedImage.createGraphics();

        int cores = Runtime.getRuntime().availableProcessors();
        int queueSize = Math.max(1, (xTiles * yTiles) - cores);
        ExecutorService tileExecutor = new ThreadPoolExecutor(
                cores,
                cores,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try {
            List<Future<TileResult>> futures = new ArrayList<>();

            for (int y = 0; y < yTiles; y++) {
                for (int x = 0; x < xTiles; x++) {
                    final int tileX = x;
                    final int tileY = y;
                    futures.add(tileExecutor.submit(() -> {
                        int tileWidth = Math.min(tileSize, width - tileX * tileSize);
                        int tileHeight = Math.min(tileSize, height - tileY * tileSize);
                        BufferedImage tile = image.getSubimage(tileX * tileSize, tileY * tileSize, tileWidth, tileHeight);
                        return new TileResult(tileX, tileY, tile);
                    }));
                }
            }

            for (Future<TileResult> future : futures) {
                TileResult result = future.get();
                g.drawImage(result.image(), result.x() * tileSize, result.y() * tileSize, null);
                //log.info("Tile {}-{} elaborata.", result.x(), result.y()); // Rimossa log tile individuale
            }

            g.dispose();
            processImage(fileName.replace(".tif", "_page_" + pageIndex + ".tif"), parentPath, stitchedImage);

        } catch (Exception e) {
            log.error("Errore durante l'unione delle tiles per {} pagina {}", fileName, pageIndex, e);
            saveLog("Errore durante l'unione delle tiles per " + fileName + " pagina " + pageIndex + ": " + e.getMessage()); // Salva log di errore
        } finally {
            tileExecutor.shutdown();
        }
    }

    private void processImage(String fileName, Path parentPath, BufferedImage image) {
        try {
            log.info("Generazione della thumbnail dell'immagine {} in corso...", fileName);

            if (image == null) {
                log.warn("Formato non supportato o file corrotto: {}", fileName);
                saveLog("Formato non supportato o file corrotto: " + fileName); // Salva log di warning
                return;
            }

            Path outputDirectory = parentPath.resolve("thumbnails");
            if (Files.notExists(outputDirectory)) {
                Files.createDirectories(outputDirectory);
            }

            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            int maxLongSide = 1200;

            int newWidth = (originalWidth >= originalHeight) ? maxLongSide
                    : (int) ((double) maxLongSide / originalHeight * originalWidth);
            int newHeight = (originalWidth >= originalHeight) ?
                    (int) ((double) maxLongSide / originalWidth * originalHeight) : maxLongSide;

            Path thumbnailPath = outputDirectory
                    .resolve(fileName.replaceFirst("\\.\\w+$", ".jpg"));
            Thumbnails.of(image)
                    .size(newWidth, newHeight)
                    .outputQuality(0.6)
                    .toFile(thumbnailPath.toFile());
        } catch (IOException e) {
            log.error("Errore nella creazione della thumbnail del file {}", fileName, e);
            saveLog("Errore nella creazione della thumbnail del file " + fileName + ": " + e.getMessage()); // Salva log di errore
        }
    }

    private BufferedImage removeAlphaChannel(BufferedImage bufferedImage) {
        if (!bufferedImage.getColorModel().hasAlpha()) {
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

    private void logMemoryUsage(String context) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        log.info("[{}] Memoria - Max: {} MB, Tot: {} MB, Usata: {} MB, Libera: {} MB",
                context,
                maxMemory / (1024 * 1024),
                totalMemory / (1024 * 1024),
                usedMemory / (1024 * 1024),
                freeMemory / (1024 * 1024));
    }

    private void shutdownAndAwaitTermination(ExecutorService executor, String poolName) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log.warn("Il thread pool {} non è terminato nei tempi previsti.", poolName);
                saveLog("Il thread pool " + poolName + " non è terminato nei tempi previsti."); // Salva log di warning
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Thread interrotto durante la chiusura del thread pool {}", poolName, e);
            saveLog("Thread interrotto durante la chiusura del thread pool " + poolName + ": " + e.getMessage()); // Salva log di errore
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void saveLog(String message) {
        Log log = Log.builder().messagio(message).build();
        logRepository.save(log);
    }
}