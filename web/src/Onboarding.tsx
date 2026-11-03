// Bienvenida con una pregunta por pantalla, como OnboardingView.swift y Onboarding.kt de las apps. Sin atajos.
import { useState, type ReactNode } from "react"
import type { WebApp } from "brivel-shared"
import { ChoiceCards, LEVEL_CHOICES, Segmented, SportPicker, STRENGTH_CHOICES, WeekBars, type Profile } from "./ProfileParts"

const QUESTIONS = 7  // objetivo, deportes, semana, fuerza, nivel, año y aviso de salud
const GOALS: [string, string, string][] = [
  ["RACE", "Preparar una carrera", "Un plan con fecha hacia tu prueba: carrera, bici, natación, triatlón…"],
  ["FITNESS", "Ponerme en forma", "Más resistencia y fuerza para el día a día, sin fecha."],
  ["STRENGTH", "Ganar fuerza y músculo", "Fuerza primero, con algo de cardio para la salud."],
  ["HEALTH", "Mejorar mi salud a largo plazo", "Cumplir lo que recomienda la OMS, con constancia."],
]
const RACE_SPORTS: [string, string][] = [["running", "Carrera a pie"], ["cycling", "Bici"], ["swimming_pool", "Natación"],
  ["triathlon", "Triatlón"], ["duathlon", "Duatlón"]]
const PRIORITIES: [string, string][] = [["A", "Principal"], ["B", "Importante"], ["C", "Preparatoria"]]

interface Race { name: string; sport: string; date: string; priority: string; distanceKm: number | null; trail: boolean }

function Question({ number, title, subtitle, onBack, next = "Siguiente", nextEnabled = true, onNext, children }: {
  number: number; title: string; subtitle?: string; onBack?: () => void; next?: string; nextEnabled?: boolean
  onNext: () => void; children: ReactNode
}) {
  return (
    <main className="question">
      <div className="row">
        {onBack && <button className="back" aria-label="Atrás" onClick={onBack}>‹</button>}
        {number > 0 && (
          <div className="progress" role="img" aria-label={`Paso ${number} de ${QUESTIONS}`}>
            {Array.from({ length: QUESTIONS }, (_, i) => <span key={i} className={i < number ? "on" : ""} />)}
          </div>
        )}
      </div>
      <h1 className="question-title">{title}</h1>
      {subtitle && <p className="muted">{subtitle}</p>}
      <div className="stack">{children}</div>
      <button className="primary next" disabled={!nextEnabled} onClick={onNext}>{next}</button>
    </main>
  )
}

/** Primera vez: siete preguntas. Con perfil (al cambiar de objetivo), solo el objetivo. `onExit`: volver a la portada o al Perfil. */
export function Onboarding({ core, onExit, onDone }: { core: WebApp; onExit: () => void; onDone: (error?: string) => void }) {
  const goalOnly = core.hasProfile
  const [step, setStep] = useState(1)
  const [goal, setGoal] = useState<string | null>(null)
  const [race, setRace] = useState<Race>({ name: "", sport: "running", date: "", priority: "A", distanceKm: null, trail: false })
  const [profile, setProfile] = useState<Profile>(() => JSON.parse(core.profileJson()))
  const [accepted, setAccepted] = useState(false)
  const today = new Date().toISOString().slice(0, 10)
  const raceOk = goal !== "RACE" || (race.name.trim() !== "" && race.date > today)
  const year = profile.birthYear
  const ageOk = year != null && core.ageOk(year)

  const finish = () => {
    try {
      if (!goalOnly) core.saveProfileJson(JSON.stringify(profile))
      core.setGoal(goal!, goal === "RACE" ? JSON.stringify({ ...race, name: race.name.trim() }) : null)
      onDone()
    } catch (e) {
      onDone(e instanceof Error && e.message ? e.message : "No se pudo guardar")
    }
  }

  switch (step) {
    case 1:
      return (
        <Question number={goalOnly ? 0 : 1} title="¿Qué quieres conseguir?" subtitle="Podrás cambiarlo o abandonarlo cuando quieras."
          onBack={goalOnly ? undefined : onExit} next={goalOnly ? "Guardar objetivo" : "Siguiente"} nextEnabled={goal != null && raceOk}
          onNext={() => (goalOnly ? finish() : setStep(2))}>
          <ChoiceCards options={GOALS} selected={goal} onSelect={setGoal} label="Objetivo" />
          {goal === "RACE" && (
            <section className="module">
              <label className="field">Nombre de la carrera
                <input value={race.name} onChange={(e) => setRace({ ...race, name: e.target.value })} />
              </label>
              <Segmented options={RACE_SPORTS} selected={race.sport} onSelect={(sport) => setRace({ ...race, sport })} label="Deporte" />
              <label className="field">Fecha
                <input type="date" min={today} value={race.date} onChange={(e) => setRace({ ...race, date: e.target.value })} />
              </label>
              <label className="field">Distancia en km (opcional)
                <input inputMode="decimal" value={race.distanceKm ?? ""}
                  onChange={(e) => setRace({ ...race, distanceKm: parseFloat(e.target.value.replace(",", ".")) || null })} />
              </label>
              {race.sport === "running" && (
                <label className="check"><input type="checkbox" checked={race.trail}
                  onChange={(e) => setRace({ ...race, trail: e.target.checked })} /><span>Es por montaña (trail)</span></label>
              )}
              <Segmented options={PRIORITIES} selected={race.priority} onSelect={(priority) => setRace({ ...race, priority })} label="Prioridad" />
            </section>
          )}
        </Question>
      )
    case 2:
      return (
        <Question number={2} title="¿Qué haces además de caminar?" subtitle="Si no marcas nada, el plan es de caminar."
          onBack={() => setStep(1)} onNext={() => setStep(3)}>
          <SportPicker core={core} cardio={profile.cardio} onChange={(cardio) => setProfile({ ...profile, cardio })} />
        </Question>
      )
    case 3:
      return (
        <Question number={3} title="¿Cuánto tiempo tienes cada día?" subtitle="Lo podrás cambiar cuando quieras."
          onBack={() => setStep(2)} nextEnabled={Object.values(profile.days).some((m) => m > 0)} onNext={() => setStep(4)}>
          <section className="module">
            <WeekBars core={core} days={profile.days} onCommit={(days) => setProfile({ ...profile, days })} />
          </section>
        </Question>
      )
    case 4:
      return (
        <Question number={4} title="¿Dónde haces fuerza?" subtitle="La fuerza ayuda a cualquier deporte y a la salud."
          onBack={() => setStep(3)} onNext={() => setStep(5)}>
          <ChoiceCards options={STRENGTH_CHOICES} selected={profile.strength} onSelect={(strength) => setProfile({ ...profile, strength })} label="Fuerza" />
        </Question>
      )
    case 5:
      return (
        <Question number={5} title="¿Cuánto entrenas ahora?" subtitle="Para empezar a tu medida." onBack={() => setStep(4)} onNext={() => setStep(6)}>
          <ChoiceCards options={LEVEL_CHOICES} selected={profile.level} onSelect={(level) => setProfile({ ...profile, level })} label="Nivel" />
        </Question>
      )
    case 6:
      return (
        <Question number={6} title="¿En qué año naciste?" subtitle="Para calcular tus zonas de pulso." onBack={() => setStep(5)}
          nextEnabled={ageOk} onNext={() => setStep(7)}>
          <label className="field">Año de nacimiento
            <input inputMode="numeric" autoComplete="bday-year" value={year ?? ""}
              onChange={(e) => setProfile({ ...profile, birthYear: parseInt(e.target.value.replace(/\D/g, "").slice(0, 4)) || null })} />
          </label>
          {year != null && year >= 1000 && !ageOk && <p className="caution-text">Hace falta tener al menos {core.minAge} años.</p>}
        </Question>
      )
    default:
      return (
        <Question number={7} title="Antes de empezar" onBack={() => setStep(6)} next="Ver mi plan" nextEnabled={accepted} onNext={finish}>
          <section className="state caution"><p>{core.healthNotice}</p></section>
          <label className="check"><input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
            <span>Lo he leído y entreno bajo mi responsabilidad</span></label>
        </Question>
      )
  }
}
