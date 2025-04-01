package com.simonegenovesi.extractorfiledata.util.dto;

import com.simonegenovesi.extractorfiledata.entity.MetadatiRisorsa;
import com.simonegenovesi.extractorfiledata.entity.Metrica;

import java.io.File;
import java.util.List;

public record FileProcessati(List<MetadatiRisorsa> metadati, Metrica metrica, List<File> listaTiffImages) {}
