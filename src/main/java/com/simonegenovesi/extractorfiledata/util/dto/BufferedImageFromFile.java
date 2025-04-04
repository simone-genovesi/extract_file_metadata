package com.simonegenovesi.extractorfiledata.util.dto;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public record BufferedImageFromFile (String fileName, Path parentPath, BufferedImage image){}
