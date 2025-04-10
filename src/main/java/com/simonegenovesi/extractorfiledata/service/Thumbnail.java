package com.simonegenovesi.extractorfiledata.service;

import com.simonegenovesi.extractorfiledata.entity.Log;
import com.simonegenovesi.extractorfiledata.exceptions.ThumbnailProcessingException;
import com.simonegenovesi.extractorfiledata.repository.LogRepository;
import com.simonegenovesi.extractorfiledata.util.dto.TileResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Servizio Spring che gestisce la generazione di miniature da file TIFF multipagina.
 * Utilizza batching e multithreading per gestire le risorse in modo efficiente.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Thumbnail {

    private final LogRepository logRepository;

    /**
     * Avvia il processo di generazione delle miniature per una lista di file TIFF.
     * L'elaborazione avviene in batch e sfrutta il multithreading per prestazioni migliori.
     * @param files lista di file TIFF da elaborare
     */
    public void doThumbnail(List<File> files) {
        long start = System.nanoTime(); // tempo di inizio
        log.info("Inizio creazione delle miniature... Trovati {} file TIFF.", files.size());
        logMemoryUsage("Prima di iniziare l'elaborazione di tutti i batch");

        saveLog("Inizio elaborazione miniature per " + files.size() + " file."); // Salva log di inizio

        try {
            ImageIO.scanForPlugins(); // garantisce il caricamento dei plugin necessari
        } catch (Exception e) {
            log.error("Errore nella scansione dei plugin ImageIO", e);
            throw new ThumbnailProcessingException("Errore nella scansione dei plugin ImageIO", e);
        }

        final long TARGET_BATCH_MEMORY = 500 * 1024 * 1024; // soglia massima memoria batch (500MB)
        final int MAX_BATCH_SIZE = 20;  // massimo numero di file per batch

        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() - 2); // evita di saturare la CPU
        int maxPoolSize = corePoolSize * 2;
        int queueSize = maxPoolSize * 2;

        ExecutorService batchExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try {
            List<File> currentBatch = new ArrayList<>();
            long currentBatchSize = 0;
            int fileCount = 0; // Contatore per il progresso

            for (File tiff : files) {
                if (!tiff.exists() || !tiff.isFile()) {
                    log.error("File TIFF non valido: {}", tiff);
                    throw new ThumbnailProcessingException("File TIFF non valido: " + tiff);
                }

                long fileSize = tiff.length(); // dimensione del file corrente

                // verifica se superiamo il limite di memoria o di numero file
                if (currentBatchSize + fileSize > TARGET_BATCH_MEMORY
                        && !currentBatch.isEmpty()
                        || currentBatch.size() >= MAX_BATCH_SIZE) {
                    processBatch(batchExecutor, currentBatch); // elabora batch corrente
                    currentBatch = new ArrayList<>();
                    currentBatchSize = 0;
                }

                currentBatch.add(tiff); // aggiunge il file al batch
                currentBatchSize += fileSize;
                fileCount++;
                log.info("Elaborazione file {} di {} : {}", fileCount, files.size(), tiff.getName()); // Log di progresso
            }

            // Elabora l'ultimo batch, se presente
            if (!currentBatch.isEmpty()) {
                processBatch(batchExecutor, currentBatch); // elabora batch rimanente
            }

        } catch (Exception e) {
            log.error("Errore durante l'elaborazione delle miniature", e);
            throw new ThumbnailProcessingException("Errore durante l'elaborazione delle miniature", e);
        } finally {
            shutdownAndAwaitTermination(batchExecutor, "batchExecutor"); // chiude pool
        }

        long end = System.nanoTime();
        log.info("Elaborazione completata. Tempo totale: {} secondi", (end - start) / 1_000_000_000.0);
        saveLog("Elaborazione completata. Tempo totale: " + (end - start) / 1_000_000_000.0 + " secondi"); // Salva log di fine
    }

    /**
     * Elabora un batch di file TIFF in parallelo.
     * @param executor executor che gestisce i thread
     * @param batch lista dei file da elaborare
     */
    private void processBatch(ExecutorService executor, List<File> batch) {
        log.info("Elaborazione di un batch di {} file", batch.size());
        logMemoryUsage("Prima del batch");

        List<Future<Void>> futures = new ArrayList<>();
        for (var tiff : batch) {
            futures.add(executor.submit(() -> {
                processTiffInTiles(tiff); // elabora ogni file TIFF nel batch
                return null;
            }));
        }

        for (var future : futures) {
            try {
                future.get(); // attende il completamento
            } catch (ExecutionException | InterruptedException e) {
                log.error("Errore nell'elaborazione di un'immagine", e);
                saveLog("Errore nell'elaborazione di un'immagine: " + e.getMessage()); // Salva log di errore
                Thread.currentThread().interrupt();
                throw new ThumbnailProcessingException("Errore nell'elaborazione di un'immagine del batch", e);
            }
        }
        logMemoryUsage("Dopo il batch");
        System.gc(); // suggerimento per liberare memoria
    }

    /**
     * Elabora un TIFF multipagina dividendo ogni pagina in tile.
     * @param tiff file TIFF da elaborare
     */
    private void processTiffInTiles(File tiff) {
        try (ImageInputStream input = ImageIO.createImageInputStream(tiff)) {
            var start = System.nanoTime();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                log.warn("Nessun reader trovato per il file {}", tiff.getName());
                saveLog("Nessun reader trovato per il file: " + tiff.getName()); // Salva log di warning
                throw new ThumbnailProcessingException("Nessun reader trovato per il file: " + tiff.getName());
            }
            var reader = readers.next();
            reader.setInput(input);
            var numPages = reader.getNumImages(true); // numero di pagine nel TIFF

            if(numPages > 1) { // eseguito solo se il Tiff possiede piu di una pagina
                for (int page = 0; page < numPages; page++) {
                    var fullImage = reader.read(page);
                    fullImage = removeAlphaChannel(fullImage); // rimuove eventuale trasparenza
                    var parentPath = tiff.toPath().getParent();
                    log.info("Elaborazione tiles per {} pagina {}", tiff.getName(), page);
                    processImageAsTiledThumbnail(tiff.getName(), parentPath, fullImage, page);
                }
            } else {
                var fullImage = reader.read(0);
                fullImage = removeAlphaChannel(fullImage);
                var parentPath = tiff.toPath().getParent();
                log.info("Elaborazione tiles per {}", tiff.getName());
                processImageAsTiledThumbnail(tiff.getName(), parentPath, fullImage, -1);
            }


            var end = System.nanoTime();
            log.info("Thumbnail creata {} in {} secondi", tiff.getName(), (end - start) / 1_000_000_000.0);
            saveLog("Thumbnail creata " + tiff.getName() + " in " + (end - start) / 1_000_000_000.0 + " secondi"); // Salva log di successo
        } catch (IOException e) {
            log.error("Errore durante la lettura del TIFF {}", tiff.getName(), e);
            saveLog("Errore durante la lettura del TIFF " + tiff.getName() + ": " + e.getMessage()); // Salva log di errore
            throw new ThumbnailProcessingException("Errore durante la lettura del TIFF " + tiff.getName(), e);
        }
    }

    /**
     * Divide l'immagine in tile ed elabora ogni tile in parallelo, quindi le ricompone in una sola immagine.
     */
    private void processImageAsTiledThumbnail(String fileName, Path parentPath, BufferedImage image, int pageIndex) {
        var tileSize = 1024;
        var width = image.getWidth();
        var height = image.getHeight();

        var xTiles = (int) Math.ceil((double) width / tileSize);
        var yTiles = (int) Math.ceil((double) height / tileSize);

        var stitchedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = stitchedImage.createGraphics();

        var cores = Runtime.getRuntime().availableProcessors();
        var queueSize = Math.max(1, (xTiles * yTiles) - cores);
        var tileExecutor = new ThreadPoolExecutor(
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
                    final var tileX = x;
                    final var tileY = y;
                    futures.add(tileExecutor.submit(() -> {
                        var tileWidth = Math.min(tileSize, width - tileX * tileSize);
                        var tileHeight = Math.min(tileSize, height - tileY * tileSize);
                        var tile = image.getSubimage(tileX * tileSize, tileY * tileSize, tileWidth, tileHeight);
                        return new TileResult(tileX, tileY, tile);
                    }));
                }
            }

            for (Future<TileResult> future : futures) {
                var result = future.get();
                g.drawImage(result.image(), result.x() * tileSize, result.y() * tileSize, null);
                //log.info("Tile {}-{} elaborata.", result.x(), result.y()); // Rimossa log tile individuale
            }

            g.dispose();
            if(pageIndex < 0){
                processImage(fileName, parentPath, stitchedImage);
            } else {
                processImage(
                        fileName.replace(".tif", "_page_" + pageIndex + ".tif"),
                        parentPath,
                        stitchedImage
                );
            }


        } catch (Exception e) {
            if(pageIndex < 0){
                log.error("Errore durante l'unione delle tiles per {}", fileName, e);
                saveLog("Errore durante l'unione delle tiles per " + fileName + ": " + e.getMessage()); // Salva log di errore
                throw new ThumbnailProcessingException("Errore durante l'unione delle tiles per " + fileName, e);
            } else {
                log.error("Errore durante l'unione delle tiles per {} pagina {}", fileName, pageIndex, e);
                saveLog("Errore durante l'unione delle tiles per " + fileName + " pagina " + pageIndex + ": " + e.getMessage());
                throw new ThumbnailProcessingException("Errore durante l'unione delle tiles per " + fileName + " pagina " + pageIndex, e);
            }
        } finally {
            tileExecutor.shutdown();
        }
    }

    /**
     * Ridimensiona l'immagine ricomposta e la salva come thumbnail JPG.
     */
    private void processImage(String fileName, Path parentPath, BufferedImage image) {
        try {
            log.info("Generazione della thumbnail dell'immagine {} in corso...", fileName);

            if (image == null) {
                log.warn("Formato non supportato o file corrotto: {}", fileName);
                saveLog("Formato non supportato o file corrotto: " + fileName); // Salva log di warning
                throw new ThumbnailProcessingException("Formato non supportato o file corrotto: " + fileName);
            }

            var outputDirectory = parentPath.resolve("thumbnails");
            if (Files.notExists(outputDirectory)) {
                Files.createDirectories(outputDirectory);
            }

            var originalWidth = image.getWidth();
            var originalHeight = image.getHeight();
            var maxLongSide = 1200;

            var newWidth = (originalWidth >= originalHeight) ? maxLongSide
                    : (int) ((double) maxLongSide / originalHeight * originalWidth);
            var newHeight = (originalWidth >= originalHeight) ?
                    (int) ((double) maxLongSide / originalWidth * originalHeight) : maxLongSide;

            var thumbnailPath = outputDirectory
                    .resolve(fileName.replaceFirst("\\.\\w+$", ".jpg"));
            Thumbnails.of(image)
                    .size(newWidth, newHeight)
                    .outputQuality(0.6)
                    .toFile(thumbnailPath.toFile());
        } catch (IOException e) {
            log.error("Errore nella creazione della thumbnail del file {}", fileName, e);
            saveLog("Errore nella creazione della thumbnail del file " + fileName + ": " + e.getMessage()); // Salva log di errore
            throw new ThumbnailProcessingException("Errore nella creazione della thumbnail del file " + fileName, e);
        }
    }

    /**
     * Rimuove il canale alpha da un'immagine (trasparenza), se presente.
     */
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

    /**
     * Stampa l'utilizzo attuale della memoria JVM nel log.
     */
    private void logMemoryUsage(String context) {
        var runtime = Runtime.getRuntime();
        var totalMemory = runtime.totalMemory();
        var freeMemory = runtime.freeMemory();
        var usedMemory = totalMemory - freeMemory;
        var maxMemory = runtime.maxMemory();

        log.info("[{}] Memoria - Max: {} MB, Tot: {} MB, Usata: {} MB, Libera: {} MB",
                context,
                maxMemory / (1024 * 1024),
                totalMemory / (1024 * 1024),
                usedMemory / (1024 * 1024),
                freeMemory / (1024 * 1024));
    }

    /**
     * Tenta la chiusura sicura del pool di thread. Logga e forza chiusura in caso di timeout.
     */
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

    /**
     * Salva un messaggio di log nel database tramite LogRepository.
     */
    private void saveLog(String message) {
        var log = Log.builder().messagio(message).build();
        logRepository.save(log);
    }
}