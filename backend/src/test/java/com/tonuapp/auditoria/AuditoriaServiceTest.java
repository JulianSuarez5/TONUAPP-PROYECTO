package com.tonuapp.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.auditoria.dto.AuditoriaResponse;
import com.tonuapp.domain.AuditLog;
import com.tonuapp.domain.Usuario;
import com.tonuapp.repository.AuditLogRepository;
import com.tonuapp.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

class AuditoriaServiceTest {

    private AuditLogRepository repository;
    private AuditoriaService service;
    private AuditLog log;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditoriaService(repository);

        Usuario autor = new Usuario();
        autor.setIdUsuario(7);
        autor.setNombre("Juan Pérez");
        autor.setCorreo("juan@tonusco.test");

        log = new AuditLog();
        log.setId(42L);
        log.setEntidad("materiales");
        log.setIdRegistro("9");
        log.setOperacion("UPDATE");
        log.setIdUsuario(7);
        log.setFecha(LocalDateTime.of(2026, 9, 5, 10, 30));
        log.setValoresAntes("{\"idMaterial\":9}");
        log.setValoresDespues("{\"idMaterial\":9,\"stock\":12}");
        log.setUsuario(autor);
    }

    private void stubVacia() {
        when(repository.buscar(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(log));
    }

    @Test
    @DisplayName("consultar: sin filtros usa default (100) y mapes autor + valores")
    void consultaSinFiltros() {
        stubVacia();

        List<AuditoriaResponse> resultado = service.consultar(null, null, null, null, null, null);

        assertThat(resultado).hasSize(1);
        verify(repository).buscar(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
        AuditoriaResponse r = resultado.get(0);
        assertThat(r.id()).isEqualTo(42L);
        assertThat(r.entidad()).isEqualTo("materiales");
        assertThat(r.idRegistro()).isEqualTo("9");
        assertThat(r.operacion()).isEqualTo("UPDATE");
        assertThat(r.fecha()).isEqualTo(log.getFecha());
        assertThat(r.nombreUsuario()).isEqualTo("Juan Pérez");
        assertThat(r.correoUsuario()).isEqualTo("juan@tonusco.test");
        assertThat(r.valoresAntes()).contains("idMaterial");
        assertThat(r.valoresDespues()).contains("stock");
    }

    @Test
    @DisplayName("consultar: normaliza filtros (trim, vacio = nulo) y convierte fechas")
    void consultaConFiltros() {
        when(repository.buscar(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(log));

        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);
        service.consultar("  materiales ", " update ", 7, desde, hasta, null);

        verify(repository).buscar("materiales", "update", 7,
                desde.atStartOfDay(), hasta.atTime(LocalTime.MAX), PageRequest.of(0, 100));
    }

    @Test
    @DisplayName("consultar: size respetado y acotado por la topologia (Pageable)")
    void consultaConSize() {
        when(repository.buscar(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(List.of(log));

        service.consultar(null, null, null, null, null, 25);

        verify(repository).buscar(isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 25)));
    }

    @Test
    @DisplayName("consultar: size fuera de 1..500 responde 400 (D-23)")
    void consultaSizeInvalido() {
        assertThatThrownBy(() -> service.consultar(null, null, null, null, null, 0))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.consultar(null, null, null, null, null, 501))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("consultar: rango invertido responde 400")
    void consultaRangoInvertido() {
        assertThatThrownBy(() -> service.consultar(null, null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("consultar: autor desconocido no rompe se queda null en el DTO")
    void consultaSinAutor() {
        log.setUsuario(null);
        stubVacia();

        AuditoriaResponse r = service.consultar(null, null, null, null, null, null).get(0);

        assertThat(r.nombreUsuario()).isNull();
        assertThat(r.correoUsuario()).isNull();
        assertThat(r.idUsuario()).isEqualTo(7);
    }
}