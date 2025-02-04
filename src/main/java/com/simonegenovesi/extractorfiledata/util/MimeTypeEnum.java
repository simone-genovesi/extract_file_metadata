package com.simonegenovesi.extractorfiledata.util;

import lombok.Getter;

@Getter
public enum MimeTypeEnum {
    // Testo
    TEXT_PLAIN("text/plain", "TXT"),
    TEXT_HTML("text/html", "HTML"),
    TEXT_CSS("text/css", "CSS"),
    TEXT_XML("text/xml", "XML"),
    APPLICATION_XML("application/xml", "XML"),
    APPLICATION_XHTML("application/xhtml+xml; charset=UTF-8", "HOCR"),

    // Immagini
    IMAGE_JPEG("image/jpeg", "JPEG"),
    IMAGE_PNG("image/png", "PNG"),
    IMAGE_GIF("image/gif", "GIF"),
    IMAGE_TIFF("image/tiff", "TIFF"),
    IMAGE_BMP("image/bmp", "BMP"),
    IMAGE_SVG("image/svg+xml", "SVG"),

    // Audio
    AUDIO_MPEG("audio/mpeg", "MP3"),
    AUDIO_OGG("audio/ogg", "OGG"),
    AUDIO_WAV("audio/wav", "WAV"),

    // Video
    VIDEO_MP4("video/mp4", "MP4"),
    VIDEO_MPEG("video/mpeg", "MPEG"),
    VIDEO_OGG("video/ogg", "OGV"),
    VIDEO_WEBM("video/webm", "WEBM"),

    // Applicazioni
    APPLICATION_JSON("application/json", "JSON"),
    APPLICATION_JAVASCRIPT("application/javascript", "JS"),
    APPLICATION_PDF("application/pdf", "PDF"),
    APPLICATION_ZIP("application/zip", "ZIP"),
    APPLICATION_GZIP("application/gzip", "GZIP"),
    APPLICATION_MSWORD("application/msword", "DOC"),
    APPLICATION_OCTET_STREAM("application/octet-stream", "BIN"), // Tipo generico

    // Speciali
    UNKNOWN("unknown", "UNKNOWN");

    private final String mimeType;
    private final String abbreviation;

    MimeTypeEnum(String mimeType, String abbreviation) {
        this.mimeType = mimeType;
        this.abbreviation = abbreviation;
    }

    public static MimeTypeEnum fromString(String mimeType) {
        for (MimeTypeEnum type : values()) {
            if (type.mimeType.equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}


