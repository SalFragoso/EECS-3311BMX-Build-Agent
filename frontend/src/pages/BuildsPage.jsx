import { useEffect, useState } from "react";
import { api } from "../api.js";
import { money, slotLabel } from "../util.js";
import { ErrorBox, StatusChip } from "../components.jsx";

// F10 saved builds: open, rename, copy, delete, and compare two or three side by side
export default function BuildsPage({ openBuild, buildId, setBuildId }) {
  const [builds, setBuilds] = useState([]);
  const [selected, setSelected] = useState([]);
  const [comparison, setComparison] = useState(null);
  const [renaming, setRenaming] = useState(null);   // { id, name }
  const [error, setError] = useState("");

  async function refresh() {
    try {
      setBuilds(await api.listBuilds());
    } catch (e) {
      setError(e.message);
    }
  }

  useEffect(() => { refresh(); }, []);

  async function act(fn) {
    setError("");
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e.message);
    }
  }

  const toggle = (id) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : s.length < 3 ? [...s, id] : s));

  const compare = () => act(async () => setComparison(await api.compareBuilds(selected)));

  const remove = (b) => act(async () => {
    if (!window.confirm(`Delete "${b.name}"? This can't be undone.`)) return;
    await api.deleteBuild(b.id);
    setSelected((s) => s.filter((x) => x !== b.id));
    setComparison(null);
    if (b.id === buildId) setBuildId(null);
  });

  return (
    <div className="page">
      <section className="card">
        <div className="row-between">
          <h2>My builds</h2>
          <button onClick={compare} disabled={selected.length < 2}>
            Compare {selected.length >= 2 ? selected.length : ""} selected
          </button>
        </div>
        <p className="muted small">Tick two or three builds to compare them.</p>
        <ErrorBox error={error} />
        {builds.length === 0 && <p>No saved builds yet.</p>}
        <ul className="build-list">
          {builds.map((b) => (
            <li key={b.id}>
              <label className="check">
                <input type="checkbox" checked={selected.includes(b.id)} onChange={() => toggle(b.id)}
                       disabled={!selected.includes(b.id) && selected.length >= 3} aria-label={`Select ${b.name}`} />
              </label>
              <div className="grow">
                {renaming && renaming.id === b.id ? (
                  <form className="row" onSubmit={(e) => { e.preventDefault(); act(async () => { await api.renameBuild(b.id, renaming.name); setRenaming(null); }); }}>
                    <label htmlFor={`rn-${b.id}`} className="sr-only">New name</label>
                    <input id={`rn-${b.id}`} value={renaming.name} onChange={(e) => setRenaming({ id: b.id, name: e.target.value })} autoFocus />
                    <button type="submit">Save</button>
                    <button type="button" className="ghost" onClick={() => setRenaming(null)}>Cancel</button>
                  </form>
                ) : (
                  <>
                    <strong>{b.name}</strong> <span className="muted small">#{b.id}</span>
                    <div className="muted small">
                      {money(b.totalPrice)} · {b.complete ? "complete" : `${b.missingSlots.length} slots missing`}
                    </div>
                  </>
                )}
              </div>
              <StatusChip status={b.compatibility.overall} />
              <div className="actions">
                <button className="ghost" onClick={() => openBuild(b.id)}>Open</button>
                <button className="ghost" onClick={() => setRenaming({ id: b.id, name: b.name })}>Rename</button>
                <button className="ghost" onClick={() => act(() => api.duplicateBuild(b.id))}>Copy</button>
                <button className="ghost danger" onClick={() => remove(b)}>Delete</button>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {comparison && (
        <section className="card">
          <h3>Comparison</h3>
          <div className="table-wrap">
            <table className="compare">
              <thead>
                <tr>
                  <th>Slot</th>
                  {comparison.builds.map((b) => <th key={b.id}>{b.name}</th>)}
                </tr>
              </thead>
              <tbody>
                {comparison.rows.map((r) => (
                  <tr key={r.slot} className={r.differs ? "differs" : ""}>
                    <td>{slotLabel(r.slot)}</td>
                    {r.parts.map((p, i) => <td key={i}>{p || <span className="muted">Empty</span>}</td>)}
                  </tr>
                ))}
                <tr className="total-row">
                  <td>Total</td>
                  {comparison.builds.map((b) => <td key={b.id}>{money(b.totalPrice)}</td>)}
                </tr>
                <tr>
                  <td>Weight</td>
                  {comparison.builds.map((b) => <td key={b.id}>{Math.round(b.totalWeightGrams).toLocaleString()} g</td>)}
                </tr>
                <tr>
                  <td>Compatibility</td>
                  {comparison.builds.map((b) => <td key={b.id}><StatusChip status={b.compatibility.overall} /></td>)}
                </tr>
              </tbody>
            </table>
          </div>
          <p className="muted small">Highlighted rows are different between the builds.</p>
        </section>
      )}
    </div>
  );
}
