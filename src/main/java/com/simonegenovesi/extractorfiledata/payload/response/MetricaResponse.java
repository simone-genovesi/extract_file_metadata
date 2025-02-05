package com.simonegenovesi.extractorfiledata.payload.response;

import com.simonegenovesi.extractorfiledata.entity.Metrica;
import lombok.*;

import java.util.List;

@Builder @Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class MetricaResponse {

    private String codiceCantiere;

    private String codiceLotto;

    private String codicePacchetto;

    private Metrica.MetricheSummary metricheSummary;

    private List<Metrica.DettaglioRisorsa> dettagliRisorse;

    @Builder @Getter @Setter
    @AllArgsConstructor @NoArgsConstructor
    public static class MetricheSummary {
        private Integer numRisorse;

        private Long dimTotale;
    }

    @Builder @Getter @Setter
    @AllArgsConstructor @NoArgsConstructor
    public static class DettaglioRisorsa {
        private String formatoFile;

        private Metrica.MetricheSummary metricheSummary;
    }
}
