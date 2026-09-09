package com.tonuapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del modulo de reportes (RF-014, D-21): directorio donde se guardan
 * los archivos generados. Default relativo al directorio de trabajo (./reportes),
 * sobreescribible con TONUAPP_REPORTES_DIR o application-local.yml.
 */
@ConfigurationProperties(prefix = "tonuapp.reportes")
public class ReporteProperties {

    private String directorio = "./reportes";

    public String getDirectorio() {
        return directorio;
    }

    public void setDirectorio(String directorio) {
        this.directorio = directorio;
    }
}