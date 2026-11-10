import { useRef, useState } from "react";

// F08 Live 2D Build Preview.
// A side view built from one layer per part, drawn back to front (wheels behind the frame, cockpit in
// front). Every layer is placed from the frame's anchor points. A slot with no part is drawn as a
// dashed grey outline, so the rider can see what's missing. Re-renders whenever the build changes.

const FRAME_COLOURS = {
  "Raw silver": "#9aa3ad",
  "Gloss black": "#20242b",
  "Cult red": "#c8302f",
  "Ocean blue": "#1f6fd1",
  "Lime": "#7cbf2b",
  "Purple": "#7d4bd8",
};

// Anchor points in a 640 x 380 drawing (20" wheels)
const A = {
  rearAxle: [165, 268],
  frontAxle: [475, 268],
  bb: [285, 280],
  seatTop: [250, 150],
  headTop: [420, 140],
  headBottom: [432, 190],
  wheelR: 98,
};

const PART_INK = "#2b3038";
const MISSING = { stroke: "#a9b0ba", dash: "7 6" };

export default function Preview({ build }) {
  const [colour, setColour] = useState("Cult red");
  const svgRef = useRef(null);
  const has = (slot) => Boolean(build && build.parts && build.parts[slot]);
  const frameInk = FRAME_COLOURS[colour];

  const style = (slot, ink = PART_INK) =>
    has(slot) ? { stroke: ink } : { stroke: MISSING.stroke, strokeDasharray: MISSING.dash };

  function exportPng() {
    const svg = svgRef.current;
    if (!svg) return;
    const data = new XMLSerializer().serializeToString(svg);
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = 1280;
      canvas.height = 760;
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      const link = document.createElement("a");
      link.download = `${(build && build.name) || "bmx-build"}.png`;
      link.href = canvas.toDataURL("image/png");
      link.click();
    };
    img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(data);
  }

  const [rx, ry] = A.rearAxle;
  const [fx, fy] = A.frontAxle;
  const [bx, by] = A.bb;
  const [sx, sy] = A.seatTop;
  const [hx, hy] = A.headTop;
  const [hbx, hby] = A.headBottom;

  return (
    <div className="preview">
      <div className="row-between">
        <h3>Preview</h3>
        <div className="row">
          <label className="small" htmlFor="frame-colour">Frame colour</label>
          <select id="frame-colour" value={colour} onChange={(e) => setColour(e.target.value)}>
            {Object.keys(FRAME_COLOURS).map((c) => <option key={c}>{c}</option>)}
          </select>
          <button className="ghost" onClick={exportPng}>Export PNG</button>
        </div>
      </div>
      <svg ref={svgRef} viewBox="0 0 640 380" xmlns="http://www.w3.org/2000/svg" role="img"
           aria-label={`Side view of ${build ? build.name : "the build"}`}
           fill="none" strokeLinecap="round" strokeLinejoin="round">
        <rect x="0" y="0" width="640" height="380" fill="#ffffff" stroke="none" />
        <line x1="40" y1="368" x2="600" y2="368" stroke="#e3e6ea" strokeWidth="2" />

        {/* 1. wheels: tires, rims, hubs (back layer) */}
        {[[rx, ry, "REAR_HUB"], [fx, fy, "FRONT_HUB"]].map(([x, y, hub]) => (
          <g key={hub}>
            <circle cx={x} cy={y} r={A.wheelR} strokeWidth="13" style={style("TIRES", "#1c1f24")} />
            <circle cx={x} cy={y} r={A.wheelR - 11} strokeWidth="5" style={style("RIMS", "#5b636e")} />
            {has("RIMS") && Array.from({ length: 12 }).map((_, i) => {
              const a = (i / 12) * Math.PI * 2;
              return <line key={i} x1={x} y1={y} x2={x + Math.cos(a) * (A.wheelR - 14)} y2={y + Math.sin(a) * (A.wheelR - 14)}
                           stroke="#b9c0c8" strokeWidth="1.2" />;
            })}
            <circle cx={x} cy={y} r="10" strokeWidth="5" style={style(hub, "#3c434d")} fill="#ffffff" />
          </g>
        ))}

        {/* 2. chain */}
        <g strokeWidth="3" style={style("CHAIN", "#6b737e")}>
          <line x1={bx} y1={by - 24} x2={rx} y2={ry - 9} />
          <line x1={bx} y1={by + 24} x2={rx} y2={ry + 9} />
        </g>

        {/* 3. frame (or a placeholder outline if there's no frame yet) */}
        <g strokeWidth="10" style={has("FRAME") ? { stroke: frameInk } : { stroke: MISSING.stroke, strokeDasharray: MISSING.dash }}>
          <line x1={sx} y1={sy} x2={hx} y2={hy + 4} />
          <line x1={bx} y1={by} x2={hbx} y2={hby} />
          <line x1={bx} y1={by} x2={sx} y2={sy} />
          <line x1={bx} y1={by} x2={rx} y2={ry} />
          <line x1={sx} y1={sy + 6} x2={rx} y2={ry} />
          <line x1={hx} y1={hy} x2={hbx} y2={hby} strokeWidth="14" />
        </g>

        {/* 4. fork */}
        <g strokeWidth="9" style={style("FORK", "#3a414b")}>
          <line x1={hbx} y1={hby} x2={fx} y2={fy} />
        </g>

        {/* 5. drivetrain: sprocket, cranks, pedals, bottom bracket */}
        <circle cx={bx} cy={by} r="24" strokeWidth="5" style={style("SPROCKET", "#4a525d")} />
        <circle cx={bx} cy={by} r="7" strokeWidth="4" style={style("BOTTOM_BRACKET", "#4a525d")} fill="#ffffff" />
        <g strokeWidth="8" style={style("CRANKS", "#2b3038")}>
          <line x1={bx} y1={by} x2={bx + 46} y2={by + 30} />
          <line x1={bx} y1={by} x2={bx - 46} y2={by - 30} opacity="0.55" />
        </g>
        <g strokeWidth="6" style={style("PEDALS", "#2b3038")}>
          <rect x={bx + 33} y={by + 26} width="28" height="10" rx="3" />
          <rect x={bx - 61} y={by - 36} width="28" height="10" rx="3" opacity="0.55" />
        </g>

        {/* 6. brake (optional) */}
        {has("BRAKES") && (
          <path d={`M ${sx - 38} ${sy + 52} q 10 -14 22 -4`} stroke="#3a414b" strokeWidth="5" />
        )}

        {/* 7. seat */}
        <g style={style("SEAT", "#1f2329")}>
          <line x1={sx} y1={sy} x2={sx - 6} y2={sy - 18} strokeWidth="6" />
          <path d={`M ${sx - 38} ${sy - 20} q 30 -14 62 0 q -30 10 -62 0 z`} strokeWidth="6"
                fill={has("SEAT") ? "#1f2329" : "none"} />
        </g>

        {/* 8. cockpit: stem then bars (front layer) */}
        <g strokeWidth="9" style={style("STEM", "#2b3038")}>
          <line x1={hx} y1={hy} x2={hx + 18} y2={hy - 12} />
        </g>
        <g strokeWidth="7" style={style("BARS", "#2b3038")}>
          <line x1={hx + 16} y1={hy - 10} x2={hx - 4} y2={hy - 78} />
          <line x1={hx - 24} y1={hy - 78} x2={hx + 16} y2={hy - 78} />
          <line x1={hx + 6} y1={hy - 44} x2={hx - 2} y2={hy - 74} strokeWidth="4" />
        </g>
      </svg>
      <p className="muted small">Dashed grey = slot still empty.</p>
    </div>
  );
}
