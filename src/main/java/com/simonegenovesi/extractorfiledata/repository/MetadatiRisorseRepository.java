package com.simonegenovesi.extractorfiledata.repository;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetadatiRisorseRepository extends MongoRepository<MetadatiRisorse, String> {

}
