// Contrato del modulo Alertas (backend Fase 8 / RF-012, D-20): misma forma que
// AlertaResponse de Spring. Estado en minuscula a juego con el CHECK de la BD
// (activa, atendida). El cierre es MANUAL por el Administrador (D-20): 'atendida'
// implica que alguien la confirmo/actuo. Modulo de uso interno: solo Administrador
// (backend @PreAuthorize a nivel de clase, RF-009).
export type EstadoAlerta = 'activa' | 'atendida'

export interface AlertaResponse {
  idAlerta: number
  idMaterial: number
  nombreMaterial: string
  estado: EstadoAlerta
  mensaje: string
  fechaGenerada: string
}