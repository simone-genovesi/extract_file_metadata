package com.simonegenovesi.extractorfiledata.repository;

import com.simonegenovesi.extractorfiledata.entity.Metrica;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MetricaRepository extends MongoRepository<Metrica, String> {

    boolean existsByCodiceCantiereAndCodiceLottoAndCodicePacchetto(
            String codiceCantiere,
            String codiceLotto,
            String codicePacchetto
    );

}
