package com.simonegenovesi.extractorfiledata.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.List;

@Slf4j
@UtilityClass
public class Codici {

    public static List<String> estraiCodici(String percorso) {
        var start = System.nanoTime();
        var path = Paths.get(percorso);
        var depth = path.getNameCount();

        var end = System.nanoTime();
        log.info("Tempo di estrazione dei codici cartelle: {} ms", (end - start) / 1_000_000);
        return List.of(
                depth > 0 ? path.getName(0).toString() : "N/A",
                depth > 1 ? path.getName(1).toString() : "N/A",
                depth > 2 ? path.getName(2).toString() : "N/A"
        );
    }
}
