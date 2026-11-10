import { useEffect, useState } from "react";
import { api } from "../api.js";
import { SLOTS, STYLES, slotLabel, money, grams } from "../util.js";
import { ErrorBox, PriceTable } from "../components.jsx";

// F03 catalog search and filter, with F06 price comparison per part
export default function CatalogPage({ buildId }) {
  const [filters, setFilters] = useState({ keyword: "", category: "", minPrice: "", maxPrice: "", style: "" });
  const [parts, setParts] = useState([]);
  const [error, setError] = useState("");
  const [prices, setPrices] = useState(null);
  const [added, setAdded] = useState("");

  async function search(e) {
    if (e) e.preventDefault();
    setError("");
    setPrices(null);
    try {
      setParts(await api.searchParts(filters));
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => { search(); }, []);

  const set = (key) => (e) => setFilters((f) => ({ ...f, [key]: e.target.value }));

  async function showPrices(id, refresh = false) {
    setError("");
    try {
      setPrices(await api.partPrices(id, refresh));
    } catch (err) {
      setError(err.message);
    }
  }

  async function addToBuild(part) {
    setError("");
    try {
      await api.selectPart(buildId, part.category, part.id);
      setAdded(`Added ${part.name} to your build.`);
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="page">
      <section className="card">
        <h2>Parts catalog</h2>
        <form className="filters" onSubmit={search}>
          <div className="field">
            <label htmlFor="kw">Keyword</label>
            <input id="kw" value={filters.keyword} onChange={set("keyword")} placeholder="e.g. odyssey" />
          </div>
          <div className="field">
            <label htmlFor="cat">Category</label>
            <select id="cat" value={filters.category} onChange={set("category")}>
              <option value="">Any</option>
              {SLOTS.map((s) => <option key={s} value={s}>{slotLabel(s)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="min">Min $</label>
            <input id="min" type="number" min="0" value={filters.minPrice} onChange={set("minPrice")} />
          </div>
          <div className="field">
            <label htmlFor="max">Max $</label>
            <input id="max" type="number" min="0" value={filters.maxPrice} onChange={set("maxPrice")} />
          </div>
          <div className="field">
            <label htmlFor="sty">Style</label>
            <select id="sty" value={filters.style} onChange={set("style")}>
              <option value="">Any</option>
              {STYLES.map((s) => <option key={s}>{s}</option>)}
            </select>
          </div>
          <button type="submit">Search</button>
        </form>
        <ErrorBox error={error} />
        {added && <p className="ok">{added}</p>}
        <p className="muted small">{parts.length} part{parts.length === 1 ? "" : "s"}</p>
        {parts.length === 0 ? (
          <p>No parts match. Try removing a filter.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>#</th><th>Part</th><th>Category</th><th>Price</th><th>Weight</th><th>Styles</th><th>Specs</th><th></th></tr>
              </thead>
              <tbody>
                {parts.map((p) => (
                  <tr key={p.id}>
                    <td className="muted">{p.id}</td>
                    <td><strong>{p.name}</strong></td>
                    <td>{slotLabel(p.category)}</td>
                    <td>{money(p.price)}</td>
                    <td>{grams(p.weight)}</td>
                    <td className="small">{p.styleTags.join(", ").toLowerCase()}</td>
                    <td className="small specs">{Object.entries(p.specs).map(([k, v]) => `${k} ${v}`).join(" · ")}</td>
                    <td className="actions-cell"><div className="actions">
                      <button className="ghost" onClick={() => showPrices(p.id)}>Prices</button>
                      {buildId && <button className="ghost" onClick={() => addToBuild(p)}>Add to build</button>}
                    </div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {prices && (
        <section className="card">
          <PriceTable comparison={prices} onRefresh={() => showPrices(prices.partId, true)} />
        </section>
      )}
    </div>
  );
}
