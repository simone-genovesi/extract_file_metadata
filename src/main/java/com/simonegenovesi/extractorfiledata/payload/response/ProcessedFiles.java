package com.simonegenovesi.extractorfiledata.payload.response;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorse;
import com.simonegenovesi.extractorfiledata.entity.Metriche;

import java.util.List;

public record ProcessedFiles(List<MetadatiRisorse> metadati, Metriche metriche) {}
