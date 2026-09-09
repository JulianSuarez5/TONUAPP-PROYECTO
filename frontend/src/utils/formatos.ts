// Formateo de datos de TONUAPP: cantidades (BigDecimal llega como numero JSON,
// 0.00). Fira Code + tabular-nums en la UI.
export function formatearCantidad(valor: number): string {
  if (!Number.isFinite(valor)) return '-'
  return valor.toLocaleString('es-CO', { maximumFractionDigits: 2 })
}