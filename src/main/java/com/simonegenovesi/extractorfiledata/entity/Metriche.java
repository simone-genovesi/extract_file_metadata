package com.simonegenovesi.extractorfiledata.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
@Document(collection = "metriche")
public class Metriche {

    @Id
    private String id;

    @Field("cod_cantiere")
    private String codiceCantiere;

    @Field("cod_lotto")
    private String codiceLotto;

    @Field("cod_pacchetto")
    private String codicePacchetto;

    @Field("metriche_summary")
    private MetricheSummary metricheSummary;

    @Field("dettagli_risorse")
    private List<DettaglioRisorsa> dettagliRisorse;

    @Data @Builder
    @AllArgsConstructor @NoArgsConstructor
    public static class MetricheSummary {
        @Field("num_risorse")
        private Integer numRisorse;

        @Field("dim_totale")
        private Long dimTotale;
    }

    @Data @Builder
    @AllArgsConstructor @NoArgsConstructor
    public static class DettaglioRisorsa {
        @Field("formato_file")
        private String formatoFile;

        @Field("metriche_summary")
        private MetricheSummary metricheSummary;
    }
}
