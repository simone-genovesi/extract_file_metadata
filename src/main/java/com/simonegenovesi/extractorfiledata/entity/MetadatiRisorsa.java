package com.simonegenovesi.extractorfiledata.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
@Document(collection = "metadati_risorse")
public class MetadatiRisorsa {

    @Id
    private String id;

    @Field("url_oggetto")
    private String urlOggetto;

    @Field("nome_oggetto")
    private String nomeOggetto;

    @Field("dimensione_file")
    private Long dimensioneFile;

    @Field("formato_file")
    private String formatoFile;

    @Field("codice_cantiere")
    private String codiceCantiere;

    @Field("codice_lotto")
    private String codiceLotto;

    @Field("codice_pacchetto")
    private String codicePacchetto;
}
