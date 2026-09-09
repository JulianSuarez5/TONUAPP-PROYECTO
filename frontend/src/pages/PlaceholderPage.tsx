// Pagina provisoria: solo texto plano hasta disenar cada pantalla con wireframe (Fase 11)
interface PlaceholderPageProps {
  titulo: string
}

export function PlaceholderPage({ titulo }: PlaceholderPageProps) {
  return (
    <main>
      <h1>{titulo}</h1>
      <p>Frontend inicializado. Pendiente de wireframes.</p>
    </main>
  )
}