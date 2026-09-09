package com.tonuapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Alerta de baja disponibilidad (RF-012, D-05): se genera cuando una operacion de
 * stock deja el material bajo su stock_minimo y NO hay otra alerta activa del mismo
 * material (evita duplicados por cada movimiento). Se almacenan permanentemente y se
 * cierran manualmente (D-20); quien/cuando la atendio queda en audit_log (D-06),
 * porque la tabla no guarda columnas de atencion.
 */
@Entity
@Table(name = "alertas_inventario")
public class AlertaInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Integer idAlerta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @Column(name = "fecha_generada", nullable = false)
    private LocalDateTime fechaGenerada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private AlertaEstado estado;

    @Column(name = "mensaje", nullable = false, length = 200)
    private String mensaje;

    public Integer getIdAlerta() {
        return idAlerta;
    }

    public void setIdAlerta(Integer idAlerta) {
        this.idAlerta = idAlerta;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public LocalDateTime getFechaGenerada() {
        return fechaGenerada;
    }

    public void setFechaGenerada(LocalDateTime fechaGenerada) {
        this.fechaGenerada = fechaGenerada;
    }

    public AlertaEstado getEstado() {
        return estado;
    }

    public void setEstado(AlertaEstado estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}