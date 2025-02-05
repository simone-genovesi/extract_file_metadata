package com.simonegenovesi.extractorfiledata.payload.response;

import lombok.*;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MetadatiRisorsaResponse {

    private String urlOggetto;

    private String nomeOggetto;

    private Long dimensioneFile;

    private String formatoFile;

    private String codiceCantiere;

    private String codiceLotto;

    private String codicePacchetto;
}
