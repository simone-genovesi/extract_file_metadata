package com.simonegenovesi.extractorfiledata.repository;

import com.simonegenovesi.extractorfiledata.entity.Metriche;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricheRepository extends MongoRepository<Metriche, String> {

}
