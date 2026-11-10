import { useEffect, useState } from "react";
import { load, save } from "./util.js";
import BuildPage from "./pages/BuildPage.jsx";
import CatalogPage from "./pages/CatalogPage.jsx";
import PlannerPage from "./pages/PlannerPage.jsx";
import AskPage from "./pages/AskPage.jsx";
import ProfilePage from "./pages/ProfilePage.jsx";
import BuildsPage from "./pages/BuildsPage.jsx";

const TABS = [
  ["build", "Build"],
  ["catalog", "Catalog"],
  ["planner", "AI Planner"],
  ["ask", "Ask"],
  ["profile", "My Profile"],
  ["builds", "My Builds"],
];

const tabFromHash = () => {
  const h = window.location.hash.replace("#", "");
  return TABS.some(([id]) => id === h) ? h : "build";
};

export default function App() {
  const [tab, setTab] = useState(tabFromHash);
  const [buildId, setBuildIdState] = useState(() => load("bmx.buildId", null));

  useEffect(() => {
    const onHash = () => setTab(tabFromHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  const setBuildId = (id) => {
    setBuildIdState(id);
    save("bmx.buildId", id);
  };

  const go = (id) => { window.location.hash = id; };
  const openBuild = (id) => { setBuildId(id); go("build"); };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden="true">◎</span>
          <span>BMX Build Agent</span>
        </div>
        <nav aria-label="Main">
          {TABS.map(([id, label]) => (
            <a key={id} href={`#${id}`} className={tab === id ? "active" : ""} aria-current={tab === id ? "page" : undefined}>
              {label}
            </a>
          ))}
        </nav>
      </header>
      <main>
        {tab === "build" && <BuildPage buildId={buildId} setBuildId={setBuildId} />}
        {tab === "catalog" && <CatalogPage buildId={buildId} />}
        {tab === "planner" && <PlannerPage openBuild={openBuild} />}
        {tab === "ask" && <AskPage buildId={buildId} />}
        {tab === "profile" && <ProfilePage />}
        {tab === "builds" && <BuildsPage openBuild={openBuild} buildId={buildId} setBuildId={setBuildId} />}
      </main>
    </div>
  );
}
