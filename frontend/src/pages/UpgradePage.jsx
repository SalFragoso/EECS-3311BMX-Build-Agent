import { useEffect, useState } from "react";
import { api } from "../api.js";
import { STYLES, money, slotLabel, load } from "../util.js";
import { ErrorBox, Loading, Notice, StatusChip, StepsLog } from "../components.jsx";

// F11 upgrade path advisor: a saved build or a described bike + budget -> staged upgrades
export default function UpgradePage({ buildId, openBuild }) {
  const profile = load("bmx.profile", null);
  const [builds, setBuilds] = useState([]);
  const [source, setSource] = useState(buildId ? "build" : "describe");
  const [chosenBuild, setChosenBuild] = useState(buildId || "");
  const [description, setDescription] = useState("");
  const [budget, setBudget] = useState("");
  const [style, setStyle] = useState(profile && profile.style ? profile.style : "STREET");
  const [plan, setPlan] = useState(null);
  const [applied, setApplied] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.listBuilds().then(setBuilds).catch((e) => setError(e.message)); }, []);

  async function advise(e) {
    e.preventDefault();
    setError("");
    setPlan(null);
    setApplied({});
    setBusy(true);
    try {
      setPlan(await api.upgrade({
        buildId: source === "build" ? chosenBuild : null,
        description: source === "describe" ? description : null,
        budget,
        style,
      }));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function applyStage(stage) {
    setError("");
    try {
      await api.applyParts(plan.buildId, stage.options.flatMap((o) => o.parts));
      setApplied((a) => ({ ...a, [stage.number]: true }));
    } catch (err) {
      setError(err.message);
    }
  }

  const canSubmit = budget && (source === "build" ? chosenBuild : description.trim());

  return (
    <div className="page">
      <section className="card">
        <h2>Upgrade my bike</h2>
        <p className="muted">Get upgrades in stages, best improvement for the money first. Every stage is checked so it still fits the parts you keep.</p>
        <form className="stack" onSubmit={advise}>
          <fieldset>
            <legend>Your current bike</legend>
            <label className="check">
              <input type="radio" name="src" checked={source === "build"} onChange={() => setSource("build")} /> A saved build
            </label>
            <label className="check">
              <input type="radio" name="src" checked={source === "describe"} onChange={() => setSource("describe")} /> Describe it
            </label>
            {source === "build" ? (
              <div className="row wrap" style={{ marginTop: 8 }}>
                <label htmlFor="up-build" className="sr-only">Saved build</label>
                <select id="up-build" value={chosenBuild} onChange={(e) => setChosenBuild(e.target.value)}>
                  <option value="">Choose a build...</option>
                  {builds.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              </div>
            ) : (
              <>
                <label htmlFor="desc" className="sr-only">Describe your bike</label>
                <textarea id="desc" rows="2" value={description} onChange={(e) => setDescription(e.target.value)}
                          placeholder="e.g. Fit Begin frame, stock complete with plastic pedals and one-piece cranks" style={{ marginTop: 8 }} />
              </>
            )}
          </fieldset>
          <div className="row wrap">
            <label htmlFor="up-budget">Budget $</label>
            <input id="up-budget" type="number" min="1" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="400" />
            <label htmlFor="up-style">Style</label>
            <select id="up-style" value={style} onChange={(e) => setStyle(e.target.value)}>
              {STYLES.map((s) => <option key={s}>{s}</option>)}
            </select>
            <button type="submit" disabled={!canSubmit || busy}>{busy ? "Planning..." : "Plan upgrades"}</button>
          </div>
        </form>
        {busy && <Loading label="Scoring upgrades and planning stages..." />}
        <ErrorBox error={error} />
      </section>

      {plan && (
        <section className="card">
          <div className="row-between">
            <h3>{plan.bikeName ? `Upgrades for ${plan.bikeName}` : "Upgrade plan"}</h3>
            {plan.stages.length > 0 && <span className="muted small">{money(plan.totalCost)} of {money(plan.budget)}</span>}
          </div>
          <p>{plan.summary}</p>
          {plan.notes.map((n, i) => <Notice key={i}>{n}</Notice>)}
          <StepsLog steps={plan.steps} />
          <div className="stages">
            {plan.stages.map((stage) => (
              <div key={stage.number} className="stage">
                <div className="row-between">
                  <h4>Stage {stage.number} · {money(stage.cost)}</h4>
                  <StatusChip status={stage.compatibility} />
                </div>
                {stage.reason && <p className="small muted">{stage.reason}</p>}
                <ul className="stage-options">
                  {stage.options.map((o) => (
                    <li key={o.number}>
                      <strong>{o.slots.map(slotLabel).join(" + ")}:</strong>{" "}
                      {o.parts.map((p) => p.name).join(" + ")} <span className="muted small">({money(o.cost)})</span>
                      <div className="small muted">Replaces {o.replacing.join(" + ")}. {o.reason}.</div>
                    </li>
                  ))}
                </ul>
                {plan.buildId && (
                  applied[stage.number]
                    ? <p className="ok small">Applied to your build.</p>
                    : <button onClick={() => applyStage(stage)}>Apply stage {stage.number}</button>
                )}
              </div>
            ))}
          </div>
          {plan.buildId && Object.keys(applied).length > 0 && (
            <button className="ghost" onClick={() => openBuild(plan.buildId)}>Open the build</button>
          )}
          <p className="muted small">{plan.aiStaged ? "Stages planned by the AI and checked by the app." : "Stages ordered by value for your style."}</p>
        </section>
      )}
    </div>
  );
}
