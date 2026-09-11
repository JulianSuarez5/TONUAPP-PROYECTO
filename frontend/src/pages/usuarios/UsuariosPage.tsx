// Usuarios - directorio y gestion de cuentas (RF-006/RF-009). Pantalla exclusiva
// del Administrador: listar, crear, editar, cambiar rol y desactivar (soft delete,
// D-04). Reglas que aplica la UI (las hace valer tambien el backend):
//  - no se puede desactivar al admin principal (409 si se intenta);
//  - un usuario no puede cambiar su propio rol (RF-009): para la sesion activa
//    no se ofrece esa accion.
// Reutiliza el patron de Materiales/Proveedores (D-28/D-30): framer-motion para
// hover de fila y modales con el estandar emilkowalski (Modal.tsx).
import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Modal } from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import * as rolService from '../../services/rolService'
import * as usuarioService from '../../services/usuarioService'
import type { RolResponse, UsuarioRequest, UsuarioResponse } from '../../types/usuarios'
import { extraerMensaje } from '../../utils/errores'
import './usuarios.css'

const EASE = [0.2, 0, 0, 1] as const

type ModalActual =
  | { tipo: 'formulario'; usuario: UsuarioResponse | null }
  | { tipo: 'rol'; usuario: UsuarioResponse }
  | { tipo: 'confirmar'; usuario: UsuarioResponse }
  | null

const filaVariants = {
  reposo: { backgroundColor: 'rgb(255 255 255 / 0)' },
  hover: { backgroundColor: 'var(--color-row-hover)' },
}

const costadoVariants = {
  reposo: { opacity: 0, x: -8, scaleY: 0 },
  hover: { opacity: 1, x: 0, scaleY: 1 },
  transition: { duration: 0.16, ease: EASE },
}

const paginaVariants = {
  reposo: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.03 } },
}

const bloqueVariants = {
  reposo: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.28, ease: EASE } },
}

export function UsuariosPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [usuarios, setUsuarios] = useState<UsuarioResponse[]>([])
  const [roles, setRoles] = useState<RolResponse[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refrescar, setRefrescar] = useState(0)

  const [modal, setModal] = useState<ModalActual>(null)
  const [guardando, setGuardando] = useState(false)

  // Carga el directorio y el catalogo de roles en paralelo.
  useEffect(() => {
    let vivo = true

    Promise.all([usuarioService.listar(), rolService.listarRoles()])
      .then(([u, r]) => {
        if (vivo) {
          setUsuarios(u)
          setRoles(r)
        }
      })
      .catch((e) => {
        if (vivo) setError(extraerMensaje(e))
      })
      .finally(() => {
        if (vivo) setCargando(false)
      })

    return () => {
      vivo = false
    }
  }, [refrescar])

  const guardarUsuario = async (target: UsuarioResponse | null, request: UsuarioRequest) => {
    setGuardando(true)
    try {
      if (target) {
        await usuarioService.actualizar(target.idUsuario, request)
      } else {
        await usuarioService.crear(request)
      }
      setModal(null)
      setRefrescar((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const cambiarRolUsuario = async (target: UsuarioResponse, idRol: number) => {
    setGuardando(true)
    try {
      await usuarioService.cambiarRol(target.idUsuario, idRol)
      setModal(null)
      setRefrescar((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const desactivarUsuario = async (target: UsuarioResponse) => {
    setGuardando(true)
    try {
      await usuarioService.desactivar(target.idUsuario)
      setModal(null)
      setRefrescar((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const activos = usuarios.filter((u) => u.activo)
  const modalUsuario = modal?.tipo === 'formulario' ? modal.usuario : null

  return (
    <motion.div
      className="usuarios"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              07
            </span>
            Usuarios
          </h1>
          <p className="materiales__subtitulo">Gestión de cuentas y roles · RF-006, RF-009</p>
        </div>
        {esAdmin && (
          <button
            type="button"
            className="btn btn--cta"
            onClick={() => setModal({ tipo: 'formulario', usuario: null })}
          >
            ＋ Nuevo usuario
          </button>
        )}
      </motion.section>

      {error && (
        <p className="materiales__error" role="alert">
          {error}
        </p>
      )}

      <motion.section variants={bloqueVariants} className="materiales__tabla-wrap" aria-live="polite">
        <table className="materiales__tabla">
          <thead>
            <tr>
              <th scope="col">Usuario</th>
              <th scope="col">Rol</th>
              <th scope="col">Estado</th>
              <th scope="col">Registrado</th>
              <th scope="col" className="acciones">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {cargando ? (
              <tr>
                <td colSpan={5} className="materiales__vacio">
                  Cargando usuarios…
                </td>
              </tr>
            ) : usuarios.length === 0 ? (
              <tr>
                <td colSpan={5} className="materiales__vacio">
                  Sin usuarios registrados todavía.
                </td>
              </tr>
            ) : (
              usuarios.map((u) => {
                const esPropio = usuario?.idUsuario === u.idUsuario
                return (
                  <motion.tr
                    key={u.idUsuario}
                    variants={filaVariants}
                    initial="reposo"
                    whileHover="hover"
                    transition={{ duration: 0.16, ease: EASE }}
                  >
                    <td className="materiales__primera">
                      <motion.span
                        className="materiales__fila-acento"
                        variants={costadoVariants}
                        aria-hidden="true"
                      />
                      <div className="usuarios__identidad">
                        <span className="usuarios__nombre">{u.nombre}</span>
                        <span className="usuarios__correo">{u.correo}</span>
                        {esPropio && <span className="usuarios__propio">· tú</span>}
                      </div>
                    </td>
                    <td>
                      <span className={`usuarios__rol usuarios__rol--${u.idRol}`}>
                        {u.nombreRol}
                      </span>
                    </td>
                    <td>
                      <span className={`usuarios__estado${u.activo ? '' : ' usuarios__estado--inactivo'}`}>
                        {u.activo ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    <td className="mono">{u.fechaCreacion ? u.fechaCreacion.replace('T', ' ') : '—'}</td>
                    <td className="acciones">
                      {u.activo && (
                        <>
                          <button
                            type="button"
                            className="accion accion--editar"
                            title="Editar"
                            aria-label={`Editar ${u.nombre}`}
                            onClick={() => setModal({ tipo: 'formulario', usuario: u })}
                          >
                            ✎
                          </button>
                          {!esPropio && (
                            <button
                              type="button"
                              className="accion"
                              title="Cambiar rol"
                              aria-label={`Cambiar rol de ${u.nombre}`}
                              onClick={() => setModal({ tipo: 'rol', usuario: u })}
                            >
                              ⟳
                            </button>
                          )}
                          {!u.esAdminPrincipal && (
                            <button
                              type="button"
                              className="accion accion--eliminar"
                              title="Desactivar"
                              aria-label={`Desactivar ${u.nombre}`}
                              onClick={() => setModal({ tipo: 'confirmar', usuario: u })}
                            >
                              🗑
                            </button>
                          )}
                        </>
                      )}
                    </td>
                  </motion.tr>
                )
              })
            )}
          </tbody>
        </table>

        {usuarios.length > 0 && (
          <div className="materiales__pie">
            <span className="materiales__total mono">
              {usuarios.length} cuenta{usuarios.length === 1 ? '' : 's'} · {activos.length} activa
              {activos.length === 1 ? '' : 's'}
            </span>
          </div>
        )}
      </motion.section>

      <AnimatePresence>
        {modal?.tipo === 'formulario' && (
          <UsuarioFormModal
            key={`formulario-${modalUsuario?.idUsuario ?? 'nuevo'}`}
            usuario={modalUsuario}
            roles={roles}
            guardando={guardando}
            onGuardar={guardarUsuario}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'rol' && (
          <CambiarRolModal
            key={`rol-${modal.usuario.idUsuario}`}
            usuario={modal.usuario}
            roles={roles}
            guardando={guardando}
            onConfirmar={cambiarRolUsuario}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'confirmar' && (
          <ConfirmarDesactivarModal
            key={`confirmar-${modal.usuario.idUsuario}`}
            usuario={modal.usuario}
            guardando={guardando}
            onConfirmar={desactivarUsuario}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  )
}

interface FormProps {
  usuario: UsuarioResponse | null
  roles: RolResponse[]
  guardando: boolean
  onGuardar: (usuario: UsuarioResponse | null, request: UsuarioRequest) => Promise<void>
  onCerrar: () => void
}

function UsuarioFormModal({ usuario, roles, guardando, onGuardar, onCerrar }: FormProps) {
  const [nombre, setNombre] = useState(usuario?.nombre ?? '')
  const [correo, setCorreo] = useState(usuario?.correo ?? '')
  // Los roles ya estan cargados cuando se abre el modal; el fallback de idRol=0
  // solo aplicaria si el catalogo estuviera vacio (la validacion lo atrapa).
  const [idRol, setIdRol] = useState<number>(() => usuario?.idRol ?? roles[0]?.idRol ?? 0)
  const [error, setError] = useState<string | null>(null)

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!nombre.trim()) return setError('El nombre es obligatorio.')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())) {
      return setError('Ingresa un correo con formato válido.')
    }
    if (!idRol) return setError('Selecciona un rol.')
    try {
      await onGuardar(
        usuario,
        { nombre: nombre.trim(), correo: correo.trim(), idRol },
      )
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo={usuario ? `Editar · ${usuario.nombre}` : 'Nuevo usuario'} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">Nombre</span>
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej. Juan Pérez" />
        </label>

        <label className="formulario__campo">
          <span className="formulario__label">Correo</span>
          <input
            type="email"
            value={correo}
            onChange={(e) => setCorreo(e.target.value)}
            placeholder="Ej. juan@correo.com"
          />
        </label>

        <label className="formulario__campo">
          <span className="formulario__label">Rol</span>
          <select value={idRol} onChange={(e) => setIdRol(Number(e.target.value))}>
            {roles.length === 0 && <option value={0}>Cargando roles…</option>}
            {roles.map((r) => (
              <option key={r.idRol} value={r.idRol}>
                {r.nombreRol}
              </option>
            ))}
          </select>
        </label>

        {usuario?.esAdminPrincipal && (
          <p className="detalle__alerta" role="note">
            Es el administrador principal: su correo no podrá desactivarse.
          </p>
        )}

        {error && (
          <p className="formulario__error" role="alert">
            {error}
          </p>
        )}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--cta" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface RolProps {
  usuario: UsuarioResponse
  roles: RolResponse[]
  guardando: boolean
  onConfirmar: (usuario: UsuarioResponse, idRol: number) => Promise<void>
  onCerrar: () => void
}

function CambiarRolModal({ usuario, roles, guardando, onConfirmar, onCerrar }: RolProps) {
  const [idRol, setIdRol] = useState<number>(usuario.idRol)
  const [error, setError] = useState<string | null>(null)

  const confirmar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (idRol === usuario.idRol) return setError('Selecciona un rol distinto al actual.')
    try {
      await onConfirmar(usuario, idRol)
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo={`Cambiar rol · ${usuario.nombre}`} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={confirmar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">
            Rol actual: <strong>{usuario.nombreRol}</strong>
          </span>
          <select value={idRol} onChange={(e) => setIdRol(Number(e.target.value))}>
            {roles.map((r) => (
              <option key={r.idRol} value={r.idRol}>
                {r.nombreRol}
              </option>
            ))}
          </select>
        </label>

        {error && (
          <p className="formulario__error" role="alert">
            {error}
          </p>
        )}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--cta" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface ConfirmarProps {
  usuario: UsuarioResponse
  guardando: boolean
  onConfirmar: (usuario: UsuarioResponse) => Promise<void>
  onCerrar: () => void
}

function ConfirmarDesactivarModal({ usuario, guardando, onConfirmar, onCerrar }: ConfirmarProps) {
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setError(null)
    try {
      await onConfirmar(usuario)
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo="Desactivar usuario" onCerrar={onCerrar}>
      <p className="modal__texto">
        ¿Desactivar a <strong>{usuario.nombre}</strong>? Dejará de poder iniciar sesión, pero su
        historial se conserva (soft delete, D-04).
      </p>
      {error && (
        <p className="formulario__error" role="alert">
          {error}
        </p>
      )}
      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onCerrar}>
          Cancelar
        </button>
        <button
          type="button"
          className="btn btn--danger"
          onClick={() => void confirmar()}
          disabled={guardando}
        >
          {guardando ? 'Desactivando…' : 'Desactivar'}
        </button>
      </div>
    </Modal>
  )
}