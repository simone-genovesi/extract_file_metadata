package com.simonegenovesi.extractorfiledata.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
@Document(collection = "logs")
public class Log {

    @Id
    private String id;

    @Field("messagio")
    private String messagio;

    @Field("data_creazione")
    @CreatedDate
    private LocalDateTime dataCreazione;
}
