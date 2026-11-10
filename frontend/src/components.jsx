import { money, when } from "./util.js";

export function StatusChip({ status }) {
  return <span className={`chip chip-${(status || "").toLowerCase()}`}>{(status || "").replace("_", " ")}</span>;
}

export function ErrorBox({ error }) {
  if (!error) return null;
  return <div className="error" role="alert">{error}</div>;
}

export function Notice({ children }) {
  return <div className="notice">{children}</div>;
}

export function Loading({ label = "Working..." }) {
  return <div className="loading" aria-live="polite">{label}</div>;
}

/** The compatibility report: overall status plus every rule that ran. */
export function CompatibilityPanel({ report }) {
  if (!report) return null;
  const checked = report.results.filter((r) => r.status !== "NOT_CHECKED");
  const waiting = report.results.filter((r) => r.status === "NOT_CHECKED");
  return (
    <div className="compat">
      <div className="compat-head">
        <h3>Compatibility</h3>
        <StatusChip status={report.overall} />
      </div>
      {checked.length === 0 && <p className="muted">Add parts to start checking.</p>}
      <ul className="rules">
        {checked.map((r) => (
          <li key={r.rule} className={`rule rule-${r.status.toLowerCase()}`}>
            <StatusChip status={r.status} />
            <div>
              <strong>{r.rule}</strong>
              <div className="muted">{r.message}</div>
            </div>
          </li>
        ))}
      </ul>
      {waiting.length > 0 && (
        <p className="muted small">Not checked yet: {waiting.map((r) => r.rule).join(", ")}</p>
      )}
    </div>
  );
}

/** Store offers for one part (F06). */
export function PriceTable({ comparison, onRefresh }) {
  if (!comparison) return null;
  return (
    <div className="prices">
      <div className="row-between">
        <h4>Prices for {comparison.partName}</h4>
        {onRefresh && <button className="ghost" onClick={onRefresh}>Refresh prices</button>}
      </div>
      <p className="muted small">Sample store data. Cheapest total first.</p>
      {comparison.quotes.length === 0 ? (
        <p>No store prices. Catalog estimate: {money(comparison.estimate)}</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Store</th><th>Price</th><th>Shipping</th><th>Total (CAD)</th><th>Stock</th><th>Updated</th></tr>
            </thead>
            <tbody>
              {comparison.quotes.map((q) => (
                <tr key={q.store}>
                  <td>{q.store}</td>
                  <td>{money(q.price)}</td>
                  <td>{money(q.shipping)}</td>
                  <td><strong>{money(q.totalCad)}</strong></td>
                  <td>{q.inStock ? "In stock" : <span className="warn">Out of stock</span>}</td>
                  <td className="small">{when(q.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {comparison.unavailableSources.map((s) => (
        <p key={s} className="warn small">{s} is unavailable right now.</p>
      ))}
    </div>
  );
}

export function StepsLog({ steps }) {
  if (!steps || steps.length === 0) return null;
  return (
    <details className="steps">
      <summary>What the agent did ({steps.length} tool call{steps.length === 1 ? "" : "s"})</summary>
      <ol>
        {steps.map((s, i) => <li key={i}><code>{s}</code></li>)}
      </ol>
    </details>
  );
}
