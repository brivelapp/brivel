// Portada de brivel.app (lienzo de Pulso, opción A): para quien llega sin datos en este navegador. Sigue el modo claro u
// oscuro del sistema con los mismos colores que el resto (styles.css)

/** El pulso de Brivel (el mismo trazo que el icono de la app). */
export const Pulse = ({ size = 24 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true">
    <polyline points="5,27 15,27 19,17 25,35 30,21 34,27 43,27" stroke="currentColor" strokeWidth="4.5"
      strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const FEATURES: [string, string][] = [
  ["Cabe en tu semana", "Le dices qué días y cuánto tiempo tienes, y el plan se ajusta a eso."],
  ["Se adapta a cómo estás", "Si dormiste mal o vas cargado, la sesión de hoy es más suave, y te dice por qué."],
  ["Tus datos son tuyos", "Se guardan en tu dispositivo. Si sincronizas, van cifrados y solo tus dispositivos los leen."],
  ["Gratis y abierto", "Sin pagos, sin anuncios y con el código a la vista de cualquiera."],
]

export function Landing({ onStart, onLogin }: { onStart: () => void; onLogin: () => void }) {
  return (
    <div className="landing">
      <header className="landing-head">
        <span className="brand"><span className="brand-mark"><Pulse /></span>brivel</span>
        <button className="primary" onClick={onLogin}>Iniciar sesión</button>
      </header>
      <section className="hero">
        <div className="hero-text">
          <h1>El sistema operativo de tu cuerpo</h1>
          <p className="lead">Brivel te dice qué entrenar hoy y por qué, con tus datos: tu sueño, tu pulso y el tiempo que tienes de verdad.</p>
          <div className="row wrap">
            <button className="primary big-button" onClick={onStart}>Empezar</button>
            <button className="secondary big-button" onClick={onLogin}>Iniciar sesión</button>
          </div>
          <p className="muted small-text">¿Ya usas Brivel en el móvil? Inicia sesión con su código y tendrás aquí los mismos datos.</p>
        </div>
        {/* Una sesión de ejemplo, como se ve en Hoy */}
        <div className="hero-card" aria-hidden="true">
          <span className="muted small-text">Ejemplo</span>
          <span className="hero-card-title">Hoy</span>
          <span className="hero-state">Hoy: ritmo cómodo, 52 min</span>
          <div className="hero-session">
            <div className="row between"><span className="muted small-text">Carrera</span><span className="mono">52 min</span></div>
            <strong className="hero-session-title">Ritmo cómodo</strong>
            <span className="row"><span className="zone" style={{ background: "var(--z1)" }}>Z1</span>Calentamiento · 10 min</span>
            <span className="row"><span className="zone" style={{ background: "var(--z2)" }}>Z2</span>Ritmo cómodo · 37 min</span>
            <span className="row"><span className="zone" style={{ background: "var(--z1)" }}>Z1</span>Vuelta a la calma · 5 min</span>
          </div>
        </div>
      </section>
      <section className="features">
        {FEATURES.map(([title, text]) => (
          <div key={title} className="module">
            <h3>{title}</h3>
            <p className="muted">{text}</p>
          </div>
        ))}
      </section>
    </div>
  )
}
