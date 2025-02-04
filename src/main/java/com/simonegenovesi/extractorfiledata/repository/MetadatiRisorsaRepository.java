package com.simonegenovesi.extractorfiledata.repository;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorsa;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MetadatiRisorsaRepository extends MongoRepository<MetadatiRisorsa, String> {

}
