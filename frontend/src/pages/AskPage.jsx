import { useRef, useState } from "react";
import { api } from "../api.js";
import { ErrorBox, StepsLog } from "../components.jsx";

const STARTERS = ["Mid or Spanish bottom bracket?", "What's a freecoaster?", "What gearing should I run for street?"];

// F09 BMX Q&A assistant (retrieval + memory for follow-ups)
export default function AskPage({ buildId }) {
  const conversationId = useRef("web-" + Math.random().toString(36).slice(2, 10));
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState("");
  const [useBuild, setUseBuild] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function ask(text) {
    const q = (text || question).trim();
    if (!q) return;
    setError("");
    setBusy(true);
    setQuestion("");
    setMessages((m) => [...m, { role: "user", text: q }]);
    try {
      const a = await api.ask(q, conversationId.current, useBuild ? buildId : null);
      setMessages((m) => [...m, { role: "assistant", text: a.answer, sources: a.sources, steps: a.steps, ai: a.aiAvailable }]);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <section className="card chat">
        <h2>Ask about BMX</h2>
        <p className="muted">Answers come from the built-in knowledge base, with sources. Follow-up questions remember the conversation.</p>
        <div className="messages" aria-live="polite">
          {messages.length === 0 && (
            <div className="row wrap">
              {STARTERS.map((s) => <button key={s} className="chip-button" onClick={() => ask(s)}>{s}</button>)}
            </div>
          )}
          {messages.map((m, i) => (
            <div key={i} className={`msg msg-${m.role}`}>
              {m.role === "assistant" && !m.ai && <div className="warn small">AI unavailable: showing matching articles instead</div>}
              <p className="pre">{m.text}</p>
              {m.sources && m.sources.length > 0 && <p className="small muted">Sources: {m.sources.join("; ")}</p>}
              {m.steps && <StepsLog steps={m.steps} />}
            </div>
          ))}
          {busy && <div className="msg msg-assistant muted">Thinking...</div>}
        </div>
        <ErrorBox error={error} />
        <form className="row" onSubmit={(e) => { e.preventDefault(); ask(); }}>
          <label htmlFor="q" className="sr-only">Your question</label>
          <input id="q" className="grow" value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="Ask a BMX question" />
          <button type="submit" disabled={busy || !question.trim()}>Ask</button>
        </form>
        {buildId && (
          <label className="check small">
            <input type="checkbox" checked={useBuild} onChange={(e) => setUseBuild(e.target.checked)} />
            Include my current build so answers can refer to it
          </label>
        )}
      </section>
    </div>
  );
}
