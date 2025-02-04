package com.simonegenovesi.extractorfiledata.util;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorsa;
import com.simonegenovesi.extractorfiledata.entity.Metrica;

import java.util.List;

public record FileProcessati(List<MetadatiRisorsa> metadati, Metrica metrica) {}
