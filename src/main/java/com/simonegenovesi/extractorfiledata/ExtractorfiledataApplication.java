package com.simonegenovesi.extractorfiledata;

import org.apache.tika.Tika;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ExtractorfiledataApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExtractorfiledataApplication.class, args);
	}

	@Bean
	public Tika tika (){
		return new Tika();
	}

}
