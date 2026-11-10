// Thin wrapper around the Spring Boot REST API. Every call goes through BuildService on the server,
// the same Facade the CLI uses, so the GUI and CLI always behave the same.

async function request(method, path, params) {
  const query = params
    ? "?" + new URLSearchParams(
        Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "")
      ).toString()
    : "";
  const res = await fetch("/api" + path + query, { method });
  if (res.status === 204) return null;
  const text = await res.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (!res.ok) {
    throw new Error((body && body.error) || `Request failed (${res.status})`);
  }
  return body;
}

export const api = {
  // F03 catalog
  searchParts: (filters) => request("GET", "/parts", filters),
  partPrices: (id, refresh) => request("GET", `/parts/${id}/prices`, { refresh }),

  // F04 / F05 / F10 builds
  listBuilds: () => request("GET", "/builds"),
  createBuild: (name) => request("POST", "/builds", { name }),
  getBuild: (id) => request("GET", `/builds/${id}`),
  selectPart: (id, slot, partId) => request("PUT", `/builds/${id}/parts/${slot}`, { partId }),
  removePart: (id, slot) => request("DELETE", `/builds/${id}/parts/${slot}`),
  compatibleParts: (id, slot) => request("GET", `/builds/${id}/compatible-parts`, { slot }),
  buildPrices: (id) => request("GET", `/builds/${id}/prices`),
  renameBuild: (id, name) => request("PATCH", `/builds/${id}`, { name }),
  duplicateBuild: (id) => request("POST", `/builds/${id}/duplicate`),
  deleteBuild: (id) => request("DELETE", `/builds/${id}`),
  compareBuilds: (ids) => request("GET", "/builds/compare", { ids: ids.join(",") }),

  // F07 budget optimizer
  optimize: (id, target, locked, style) =>
    request("POST", `/builds/${id}/optimize`, { target, locked: locked.join(","), style }),
  applySwaps: (id, swaps) =>
    request("POST", `/builds/${id}/apply-swaps`, {
      swaps: swaps.map((s) => `${s.slot}:${s.to.id}`).join(","),
    }),

  // AI agent: F02 planner, F09 Q&A, F01 style
  plan: (requestText, name) => request("POST", "/agent/plan", { request: requestText, name }),
  ask: (question, conversationId, buildId) =>
    request("POST", "/agent/ask", { question, conversationId, buildId }),
  recommendStyle: (p) =>
    request("POST", "/agent/recommend-style", {
      heightCm: p.heightCm,
      experience: p.experience,
      spots: p.spots.join(","),
      goals: p.goals,
      budget: p.budget,
      wantsBrakes: p.wantsBrakes,
    }),
};
