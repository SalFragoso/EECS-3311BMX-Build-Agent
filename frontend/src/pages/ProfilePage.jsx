import { useState } from "react";
import { api } from "../api.js";
import { load, save } from "../util.js";
import { ErrorBox, Notice } from "../components.jsx";

const SPOTS = ["street", "skatepark", "dirt jumps", "track", "flat ground"];

// F01 rider profile and style recommendation
export default function ProfilePage() {
  const saved = load("bmx.profile", null);
  const [form, setForm] = useState(saved ? saved.form : {
    heightCm: "", experience: "BEGINNER", spots: [], goals: "", budget: "", wantsBrakes: false,
  });
  const [result, setResult] = useState(saved ? saved.result : null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [savedNote, setSavedNote] = useState("");

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));
  const toggleSpot = (s) => setForm((f) => ({ ...f, spots: f.spots.includes(s) ? f.spots.filter((x) => x !== s) : [...f.spots, s] }));

  async function recommend(e) {
    e.preventDefault();
    setError("");
    setSavedNote("");
    setBusy(true);
    try {
      setResult(await api.recommendStyle(form));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  function saveProfile(style) {
    save("bmx.profile", { form, result, style });
    setSavedNote(`Saved. ${style} will be the default style for the budget optimizer.`);
  }

  return (
    <div className="page">
      <div className="grid-2">
        <section className="card">
          <h2>My profile</h2>
          <form className="stack" onSubmit={recommend}>
            <div className="row wrap">
              <div className="field">
                <label htmlFor="h">Height (cm)</label>
                <input id="h" type="number" min="100" max="230" value={form.heightCm} onChange={set("heightCm")} required />
              </div>
              <div className="field">
                <label htmlFor="exp">Experience</label>
                <select id="exp" value={form.experience} onChange={set("experience")}>
                  <option>BEGINNER</option><option>INTERMEDIATE</option><option>ADVANCED</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="bud">Budget $ (optional)</label>
                <input id="bud" type="number" min="0" value={form.budget} onChange={set("budget")} />
              </div>
            </div>
            <fieldset>
              <legend>Where do you ride?</legend>
              <div className="row wrap">
                {SPOTS.map((s) => (
                  <label key={s} className="check">
                    <input type="checkbox" checked={form.spots.includes(s)} onChange={() => toggleSpot(s)} /> {s}
                  </label>
                ))}
              </div>
            </fieldset>
            <label htmlFor="goals">What do you want to learn?</label>
            <textarea id="goals" rows="2" value={form.goals} onChange={set("goals")} placeholder="e.g. grind ledges, 180s, bigger jumps" />
            <label className="check">
              <input type="checkbox" checked={form.wantsBrakes} onChange={set("wantsBrakes")} /> I want brakes
            </label>
            <button type="submit" disabled={busy}>{busy ? "Thinking..." : "Get recommendation"}</button>
          </form>
          <ErrorBox error={error} />
        </section>

        <section className="card">
          <h2>Recommendation</h2>
          {!result ? (
            <p className="muted">Fill in the quiz to get a riding style and frame size.</p>
          ) : (
            <>
              {result.note && <Notice>{result.note}</Notice>}
              <p>Frame top tube: <strong>{result.topTube}</strong> <span className="muted small">(from your height)</span></p>
              <ol className="styles">
                {result.ranked.map((r) => (
                  <li key={r.style}>
                    <div className="row-between">
                      <strong>{r.style}</strong>
                      <span className="muted small">{r.score}/100</span>
                    </div>
                    <div className="bar"><span style={{ width: `${r.score}%` }} /></div>
                    <p className="small">{r.reason}</p>
                    <button className="ghost" onClick={() => saveProfile(r.style)}>Save with {r.style.toLowerCase()}</button>
                  </li>
                ))}
              </ol>
              {result.close && <p className="small">Your top two styles are close, so pick whichever sounds more fun.</p>}
              <p className="muted small">{result.aiBased ? `Ranked by the AI (${result.tokensUsed} tokens).` : "Rule-based recommendation."}</p>
              {savedNote && <p className="ok">{savedNote}</p>}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
