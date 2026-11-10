import { useState } from "react";
import { api } from "../api.js";
import { money } from "../util.js";
import { CompatibilityPanel, ErrorBox, Loading, Notice, StatusChip, StepsLog } from "../components.jsx";

const EXAMPLES = [
  "Street bike under $2000, no brakes, I'm learning to grind",
  "Park bike for a beginner, around $1500, with a rear brake",
  "Light race bike, budget doesn't matter much",
];

// F02 AI build planner
export default function PlannerPage({ openBuild }) {
  const [request, setRequest] = useState("");
  const [name, setName] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function plan(e) {
    e.preventDefault();
    setError("");
    setResult(null);
    setBusy(true);
    try {
      setResult(await api.plan(request, name));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const b = result && result.build;
  return (
    <div className="page">
      <section className="card">
        <h2>AI build planner</h2>
        <p className="muted">Describe the bike you want. The agent searches the catalog, adds parts, checks every one for compatibility and fixes anything that doesn't fit.</p>
        <form onSubmit={plan} className="stack">
          <label htmlFor="req">What do you want?</label>
          <textarea id="req" rows="3" value={request} onChange={(e) => setRequest(e.target.value)}
                    placeholder={EXAMPLES[0]} />
          <div className="row wrap">
            {EXAMPLES.map((ex) => (
              <button type="button" key={ex} className="chip-button" onClick={() => setRequest(ex)}>{ex}</button>
            ))}
          </div>
          <div className="row wrap">
            <label htmlFor="pname">Build name</label>
            <input id="pname" value={name} onChange={(e) => setName(e.target.value)} placeholder="AI build" />
            <button type="submit" disabled={!request.trim() || busy}>{busy ? "Planning..." : "Plan my build"}</button>
          </div>
        </form>
        {busy && <Loading label="The agent is searching parts and checking compatibility. This can take 20 to 60 seconds." />}
        <ErrorBox error={error} />
      </section>

      {result && (
        <>
          {!result.aiAvailable && <Notice>{result.summary}</Notice>}
          <div className="grid-2">
            <section className="card">
              <div className="row-between">
                <h3>{b.name}</h3>
                <StatusChip status={b.compatibility.overall} />
              </div>
              <div className="totals">
                <div><span className="muted small">Total</span><strong>{money(b.totalPrice)}</strong></div>
                <div><span className="muted small">Status</span><strong>{b.complete ? "Complete" : `${b.missingSlots.length} slots missing`}</strong></div>
                {result.aiAvailable && <div><span className="muted small">Tokens</span><strong>{result.tokensUsed}</strong></div>}
              </div>
              {result.aiAvailable && <div className="explain"><span className="small muted">Agent's summary</span><p className="pre">{result.summary}</p></div>}
              {!result.finished && result.aiAvailable && <p className="warn small">The agent hit its step limit, so the build may be incomplete.</p>}
              <StepsLog steps={result.steps} />
              <button onClick={() => openBuild(b.id)}>Open in the configurator</button>
            </section>
            <section className="card"><CompatibilityPanel report={b.compatibility} /></section>
          </div>
        </>
      )}
    </div>
  );
}
