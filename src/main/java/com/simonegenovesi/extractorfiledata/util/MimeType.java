package com.simonegenovesi.extractorfiledata.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@Slf4j
public class MimeType {

    private static final Map<String, MimeTypeEnum> MAGIC_NUMBERS = Map.ofEntries(
            // Testo
            Map.entry("3C3F78", MimeTypeEnum.APPLICATION_XML),
            Map.entry("68746D", MimeTypeEnum.TEXT_HTML),

            // Immagini
            Map.entry("FFD8FF", MimeTypeEnum.IMAGE_JPEG),
            Map.entry("89504E", MimeTypeEnum.IMAGE_PNG),
            Map.entry("474946", MimeTypeEnum.IMAGE_GIF),
            Map.entry("49492A", MimeTypeEnum.IMAGE_TIFF),
            Map.entry("4D4D00", MimeTypeEnum.IMAGE_TIFF),

            // Audio
            Map.entry("494433", MimeTypeEnum.AUDIO_MPEG),
            Map.entry("4F6767", MimeTypeEnum.AUDIO_OGG),
            Map.entry("524946", MimeTypeEnum.AUDIO_WAV),

            // Video
            Map.entry("000001", MimeTypeEnum.VIDEO_MP4),

            // Applicazioni
            Map.entry("255044", MimeTypeEnum.APPLICATION_PDF),
            Map.entry("504B03", MimeTypeEnum.APPLICATION_ZIP),
            Map.entry("1F8B08", MimeTypeEnum.APPLICATION_GZIP),
            Map.entry("D0CF11", MimeTypeEnum.APPLICATION_MSWORD)
    );

    private MimeType() {
    }

    public static MimeTypeEnum deduciFormatoFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            var bytes = new byte[512]; // Leggiamo fino a 256 byte
            var bytesRead = fis.read(bytes);

            if (bytesRead < 4) {
                return MimeTypeEnum.APPLICATION_OCTET_STREAM; // Formato sconosciuto
            }

            // Convertiamo i primi 4 byte in esadecimale per identificazione veloce
            var hexSignature = bytesToHex(bytes).toUpperCase();

            // Controlliamo magic numbers noti
            for (Map.Entry<String, MimeTypeEnum> entry: MAGIC_NUMBERS.entrySet()) {
                if (hexSignature.startsWith(entry.getKey())) {
                    return getMimeTypeEnum(entry, bytes, bytesRead);
                }
            }

            return MimeTypeEnum.APPLICATION_OCTET_STREAM; // Formato sconosciuto
        } catch (IOException e) {
            log.error(e.getMessage());
            return MimeTypeEnum.APPLICATION_OCTET_STREAM; // Errore nel file
        }
    }

    private static MimeTypeEnum getMimeTypeEnum(Map.Entry<String, MimeTypeEnum> entry, byte[] bytes, int bytesRead) {
        var mime = entry.getValue();
        var fileContent = new String(bytes, 0, bytesRead).toLowerCase();

        // Se troviamo la classe "ocr_", è un hOCR
        if (fileContent.contains("ocr_")) {
            return MimeTypeEnum.APPLICATION_HOCR; // hOCR
        }
        // Se contiene il namespace XHTML, controlliamo se è hOCR
        if (fileContent.contains("xmlns=\"http://www.w3.org/1999/xhtml\"")) {
            return MimeTypeEnum.APPLICATION_XHTML; // XHTML generico
        }
        return mime;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

}
