import { useEffect, useState } from "react";
import { api } from "../api.js";
import { SLOTS, STYLES, slotLabel, money, grams, load } from "../util.js";
import { CompatibilityPanel, ErrorBox, Loading, PriceTable, StatusChip } from "../components.jsx";
import Preview from "../Preview.jsx";

// F04 configurator + F05 compatibility + F08 preview + F06 build prices + F07 budget optimizer
export default function BuildPage({ buildId, setBuildId }) {
  const [builds, setBuilds] = useState([]);
  const [build, setBuild] = useState(null);
  const [error, setError] = useState("");
  const [newName, setNewName] = useState("");
  const [picker, setPicker] = useState(null);       // { slot, parts, fits:Set }
  const [prices, setPrices] = useState(null);       // F06 build summary
  const [partPrices, setPartPrices] = useState(null);
  const [busy, setBusy] = useState("");

  async function refreshList() {
    try {
      setBuilds(await api.listBuilds());
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => { refreshList(); }, []);

  useEffect(() => {
    setPrices(null);
    setPartPrices(null);
    if (!buildId) { setBuild(null); return; }
    api.getBuild(buildId).then(setBuild).catch((e) => { setError(e.message); setBuildId(null); });
  }, [buildId]);

  async function run(label, fn) {
    setError("");
    setBusy(label);
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy("");
    }
  }

  const createBuild = () => run("Creating build...", async () => {
    const b = await api.createBuild(newName);
    setNewName("");
    setBuildId(b.id);
    await refreshList();
  });

  const openPicker = (slot) => run("Loading parts...", async () => {
    const [parts, fits] = await Promise.all([
      api.searchParts({ category: slot }),
      api.compatibleParts(build.id, slot),
    ]);
    setPicker({ slot, parts, fits: new Set(fits.map((p) => p.id)) });
  });

  const choose = (slot, partId) => run("Adding part...", async () => {
    setBuild(await api.selectPart(build.id, slot, partId));
    setPicker(null);
    setPrices(null);
    refreshList();
  });

  const remove = (slot) => run("Removing part...", async () => {
    setBuild(await api.removePart(build.id, slot));
    setPrices(null);
    refreshList();
  });

  const loadPrices = () => run("Comparing prices...", async () => setPrices(await api.buildPrices(build.id)));
  const loadPartPrices = (partId, refresh = false) =>
    run("Comparing prices...", async () => setPartPrices(await api.partPrices(partId, refresh)));

  return (
    <div className="page">
      <section className="card toolbar">
        <div className="row wrap">
          <label htmlFor="build-select">Build</label>
          <select id="build-select" value={buildId || ""} onChange={(e) => setBuildId(e.target.value ? Number(e.target.value) : null)}>
            <option value="">Choose a build...</option>
            {builds.map((b) => <option key={b.id} value={b.id}>{b.name} ({money(b.totalPrice)})</option>)}
          </select>
        </div>
        <div className="row wrap">
          <label htmlFor="new-build" className="sr-only">New build name</label>
          <input id="new-build" placeholder="New build name" value={newName} onChange={(e) => setNewName(e.target.value)} />
          <button onClick={createBuild}>New build</button>
        </div>
      </section>

      <ErrorBox error={error} />
      {busy && <Loading label={busy} />}

      {!build ? (
        <section className="card empty">
          <h2>Start a build</h2>
          <p>Create a new build above, pick a saved one, or let the <a href="#planner">AI planner</a> make one for you.</p>
        </section>
      ) : (
        <>
          <div className="grid-2">
            <section className="card">
              <div className="row-between">
                <h2>{build.name}</h2>
                <StatusChip status={build.compatibility.overall} />
              </div>
              <div className="totals">
                <div><span className="muted small">Total</span><strong>{money(build.totalPrice)}</strong></div>
                <div><span className="muted small">Weight</span><strong>{grams(build.totalWeightGrams)}</strong></div>
                <div><span className="muted small">Status</span><strong>{build.complete ? "Complete" : `${build.missingSlots.length} slots missing`}</strong></div>
              </div>
              <div className="table-wrap">
                <table className="slots">
                  <thead><tr><th>Slot</th><th>Part</th><th>Price</th><th></th></tr></thead>
                  <tbody>
                    {SLOTS.map((slot) => {
                      const p = build.parts[slot];
                      const issue = build.compatibility.issues.find((r) => r.parts.some((x) => x.id === (p && p.id)));
                      return (
                        <tr key={slot} className={issue ? `has-${issue.status.toLowerCase()}` : ""}>
                          <td>{slotLabel(slot)}{slot === "BRAKES" && <span className="muted small"> (optional)</span>}</td>
                          <td>{p ? p.name : <span className="muted">Empty</span>}</td>
                          <td>{p ? money(p.price) : ""}</td>
                          <td className="actions-cell"><div className="actions">
                            <button className="ghost" onClick={() => openPicker(slot)}>{p ? "Change" : "Choose"}</button>
                            {p && <button className="ghost" onClick={() => loadPartPrices(p.id)}>Prices</button>}
                            {p && <button className="ghost danger" onClick={() => remove(slot)} aria-label={`Remove ${slotLabel(slot)}`}>Remove</button>}
                          </div></td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>

            <div className="stack">
              <section className="card"><Preview build={build} /></section>
              <section className="card"><CompatibilityPanel report={build.compatibility} /></section>
            </div>
          </div>

          {partPrices && (
            <section className="card">
              <PriceTable comparison={partPrices} onRefresh={() => loadPartPrices(partPrices.partId, true)} />
            </section>
          )}

          <div className="grid-2">
            <section className="card">
              <div className="row-between">
                <h3>Cheapest way to buy it</h3>
                <button onClick={loadPrices} disabled={Object.keys(build.parts).length === 0}>Compare prices</button>
              </div>
              {prices ? (
                <>
                  <div className="table-wrap">
                    <table>
                      <thead><tr><th>Slot</th><th>Part</th><th>Best price</th><th>Store</th></tr></thead>
                      <tbody>
                        {prices.slots.map((s) => (
                          <tr key={s.slot}>
                            <td>{slotLabel(s.slot)}</td>
                            <td>{s.partName}</td>
                            <td>{money(s.best ? s.best.totalCad : s.msrp)}</td>
                            <td>{s.best ? s.best.store : <span className="muted">MSRP, none in stock</span>}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p>MSRP {money(prices.msrpTotal)}, cheapest {money(prices.cheapestTotal)}. <strong>You save {money(prices.savings)}.</strong></p>
                  <p className="muted small">Sample store data (includes shipping, USD converted to CAD).</p>
                </>
              ) : (
                <p className="muted">Finds the cheapest in-stock store for each part (F06).</p>
              )}
            </section>
            <Optimizer build={build} onApplied={(b) => { setBuild(b); setPrices(null); refreshList(); }} />
          </div>
        </>
      )}

      {picker && (
        <div className="modal-backdrop" onClick={() => setPicker(null)}>
          <div className="modal" role="dialog" aria-modal="true" aria-labelledby="picker-title" onClick={(e) => e.stopPropagation()}>
            <div className="row-between">
              <h3 id="picker-title">Choose {slotLabel(picker.slot).toLowerCase()}</h3>
              <button className="ghost" onClick={() => setPicker(null)}>Close</button>
            </div>
            {picker.parts.length === 0 && <p className="muted">No parts in the catalog for this slot.</p>}
            <ul className="picker">
              {picker.parts.map((p) => {
                const fits = picker.fits.has(p.id);
                return (
                  <li key={p.id}>
                    <div>
                      <strong>{p.name}</strong> <span className="muted small">#{p.id}</span>
                      <div className="muted small">
                        {money(p.price)} · {grams(p.weight)} · {p.styleTags.join(", ").toLowerCase()}
                      </div>
                      <div className="small specs">{Object.entries(p.specs).map(([k, v]) => `${k} ${v}`).join(" · ")}</div>
                    </div>
                    <div className="picker-actions">
                      <StatusChip status={fits ? "PASS" : "FAIL"} />
                      <span className="small muted">{fits ? "Fits" : "Won't fit"}</span>
                      <button onClick={() => choose(picker.slot, p.id)}>{fits ? "Use this" : "Use anyway"}</button>
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}

// F07: target budget, locks and style in; deterministic swaps + the AI's explanation out
function Optimizer({ build, onApplied }) {
  const profile = load("bmx.profile", null);
  const [target, setTarget] = useState("");
  const [style, setStyle] = useState(profile && profile.style ? profile.style : "STREET");
  const [locked, setLocked] = useState([]);
  const [result, setResult] = useState(null);
  const [chosen, setChosen] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { setResult(null); }, [build.id]);

  async function optimize() {
    setError("");
    setBusy(true);
    try {
      const r = await api.optimize(build.id, target, locked, style);
      setResult(r);
      setChosen(Object.fromEntries(r.plan.swaps.map((s) => [s.slot, true])));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function apply() {
    setError("");
    try {
      const picks = result.plan.swaps.filter((s) => chosen[s.slot]);
      onApplied(await api.applySwaps(build.id, picks));
      setResult(null);
    } catch (e) {
      setError(e.message);
    }
  }

  const filled = Object.keys(build.parts);
  const toggleLock = (slot) =>
    setLocked((l) => (l.includes(slot) ? l.filter((s) => s !== slot) : [...l, slot]));

  return (
    <section className="card">
      <h3>Optimize for budget</h3>
      <div className="row wrap">
        <label htmlFor="target">Target $</label>
        <input id="target" type="number" min="1" step="1" value={target} onChange={(e) => setTarget(e.target.value)} placeholder="1500" />
        <label htmlFor="opt-style">Style</label>
        <select id="opt-style" value={style} onChange={(e) => setStyle(e.target.value)}>
          {STYLES.map((s) => <option key={s}>{s}</option>)}
        </select>
        <button onClick={optimize} disabled={!target || filled.length === 0 || busy}>{busy ? "Thinking..." : "Find swaps"}</button>
      </div>
      {filled.length > 0 && (
        <fieldset className="locks">
          <legend className="small muted">Lock parts you want to keep</legend>
          {filled.map((slot) => (
            <label key={slot} className="check">
              <input type="checkbox" checked={locked.includes(slot)} onChange={() => toggleLock(slot)} />
              {slotLabel(slot)}
            </label>
          ))}
        </fieldset>
      )}
      <ErrorBox error={error} />
      {result && (
        <div className="opt-result">
          <p className={result.plan.reachable ? "" : "warn"}>{result.plan.message}</p>
          {result.plan.swaps.length > 0 && (
            <>
              <ul className="swaps">
                {result.plan.swaps.map((s) => (
                  <li key={s.slot}>
                    <label className="check">
                      <input type="checkbox" checked={!!chosen[s.slot]} onChange={() => setChosen((c) => ({ ...c, [s.slot]: !c[s.slot] }))} />
                      <span><strong>{slotLabel(s.slot)}:</strong> {s.from.name} → {s.to.name}
                        <span className="muted small"> ({s.reason})</span></span>
                    </label>
                  </li>
                ))}
              </ul>
              <div className="explain">
                <span className="small muted">{result.aiExplained ? "AI explanation" : "Explanation"}</span>
                <p className="pre">{result.explanation}</p>
              </div>
              <button onClick={apply} disabled={!Object.values(chosen).some(Boolean)}>Apply selected swaps</button>
            </>
          )}
        </div>
      )}
    </section>
  );
}
