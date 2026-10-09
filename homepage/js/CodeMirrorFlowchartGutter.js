import {StateField} from "https://esm.sh/@codemirror/state@6.5.2";
import {EditorView, GutterMarker, gutter} from "https://esm.sh/@codemirror/view@6.38.6?deps=@codemirror/state@6.5.2";

const SVG_NS = "http://www.w3.org/2000/svg";
const STEP = 18;
const nodeX = node => 18 + node.depth * STEP;
const colors = {next: "#382b18", yes: "#176a39", no: "#a12e23"};

// Interval packing keeps independent branches compact and nested connectors
// distinct. Coordinates are independent of pixels: CodeMirror owns row height.
export function layoutFlowchart(chart = {}) {
  const nodes = new Map((chart.nodes ?? []).map(node => [node.line, node]));
  const maxX = Math.max(18, ...[...nodes.values()].map(nodeX));
  const lanes = [];
  const routes = (chart.edges ?? []).filter(edge => nodes.has(edge.from) && nodes.has(edge.to))
    .map(edge => ({...edge, low: Math.min(edge.from, edge.to), high: Math.max(edge.from, edge.to)}))
    .sort((a, b) => a.low - b.low || b.high - a.high || a.from - b.from)
    .map(edge => {
      const sourceX = nodeX(nodes.get(edge.from));
      const targetX = nodeX(nodes.get(edge.to));
      const terminal = edge.kind.endsWith("-end");
      const back = !terminal && edge.to <= edge.from;
      const direct = !back && edge.to === edge.from + 1 && edge.kind !== "no";
      if (direct) return {...edge, sourceX, targetX, back, terminal, lane: null};
      let lane = lanes.findIndex(end => end < edge.low);
      if (lane < 0) lane = lanes.length;
      lanes[lane] = edge.high;
      return {...edge, sourceX, targetX, back, terminal, lane: maxX + 20 + lane * 10};
    });
  return {nodes, routes, entries: new Set(chart.entries ?? []), width: maxX + 34 + lanes.length * 10};
}

const element = (tag, attributes = {}) => {
  const node = document.createElementNS(SVG_NS, tag);
  for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, String(value));
  return node;
};

export function renderFlowchartRow(layout, line) {
  const svg = element("svg", {viewBox: `0 0 ${layout.width} 20`, preserveAspectRatio: "none", "aria-hidden": "true"});
  const drawPath = (d, color, dashed = false) => svg.appendChild(element("path", {
    d, fill: "none", stroke: color, "stroke-width": 1.2,
    ...(dashed ? {"stroke-dasharray": "2 1.5"} : {})
  }));
  for (const edge of layout.routes) {
    if (line < edge.low || line > edge.high) continue;
    const branch = edge.kind.replace(/-end$/, "");
    const color = colors[branch] ?? colors.next;
    if (edge.terminal) {
      drawPath(`M ${edge.sourceX + 6} 10 H ${edge.lane} V 16`, color);
      svg.appendChild(element("circle", {cx: edge.lane, cy: 18, r: 1.4, fill: color}));
    } else if (edge.lane === null) {
      if (line === edge.from) drawPath(`M ${edge.sourceX} 15 V 18 L ${edge.targetX} 20`, color);
      if (line === edge.to) drawPath(`M ${edge.targetX} 0 V 5`, color);
    } else {
      const lane = edge.lane;
      if (edge.from === edge.to) {
        drawPath(`M ${edge.sourceX + 5} 10 H ${lane} V 2 H ${edge.targetX} V 5`, color, true);
      } else if (line === edge.from) {
        drawPath(`M ${edge.sourceX} 15 V 18 H ${lane} V ${edge.back ? 0 : 20}`, color, edge.back);
      } else if (line === edge.to) {
        drawPath(`M ${lane} ${edge.back ? 20 : 0} V 2 H ${edge.targetX} V 5`, color, edge.back);
      } else drawPath(`M ${lane} 0 V 20`, color, edge.back);
    }
    if (line === edge.to && !edge.terminal) svg.appendChild(element("path", {
      d: `M ${edge.targetX - 2} 2 L ${edge.targetX} 5 L ${edge.targetX + 2} 2`,
      stroke: color, fill: "none", "stroke-width": 1.2
    }));
    if (line === edge.from && (branch === "yes" || branch === "no")) {
      const label = element("text", {x: edge.sourceX + (branch === "yes" ? -10 : 7), y: 19,
        fill: color, "font-size": 6, "font-family": "sans-serif"});
      label.textContent = branch === "yes" ? "T" : "F";
      svg.appendChild(label);
    }
  }
  const node = layout.nodes.get(line);
  if (node) {
    const x = nodeX(node);
    const common = {fill: "#dbb573", stroke: colors.next, "stroke-width": 1.1};
    if (node.kind === "decision" || node.kind === "loop") {
      svg.appendChild(element("path", {d: `M ${x} 5 L ${x + 6} 10 L ${x} 15 L ${x - 6} 10 Z`, ...common}));
    } else {
      svg.appendChild(element("rect", {x: x - 5, y: 6, width: 10, height: 8,
        rx: node.kind === "return" ? 4 : 1, ...common}));
    }
    if (layout.entries.has(line)) svg.appendChild(element("path", {
      d: `M ${x - 2} 0 H ${x + 2} L ${x} 4 Z`, fill: colors.next
    }));
    if (!layout.routes.some(edge => edge.from === line)) {
      drawPath(`M ${x} 15 V 17`, colors.next);
      svg.appendChild(element("circle", {cx: x, cy: 18, r: 1.4, fill: colors.next}));
    }
  }
  return svg;
}

class FlowchartMarker extends GutterMarker {
  constructor(layout, line) {
    super();
    this.layout = layout;
    this.line = line;
  }
  eq(other) { return this.layout === other.layout && this.line === other.line; }
  toDOM() {
    const row = document.createElement("div");
    row.className = "cm-flowchart-row";
    row.style.width = `${this.layout.width}px`;
    const node = this.layout.nodes.get(this.line);
    if (node) row.title = `${node.kind}: ${node.label}`;
    row.appendChild(renderFlowchartRow(this.layout, this.line));
    return row;
  }
}

export function flowchartExtension(chartForDoc) {
  const build = state => layoutFlowchart(chartForDoc(state.doc.toString()));
  const field = StateField.define({
    create: build,
    update: (layout, transaction) => transaction.docChanged ? build(transaction.state) : layout
  });
  return [field, gutter({
    class: "cm-flowchart-gutter",
    renderEmptyElements: true,
    lineMarker(view, line) {
      const layout = view.state.field(field);
      if (!layout.nodes.size) return null;
      return new FlowchartMarker(layout, view.state.doc.lineAt(line.from).number);
    },
    initialSpacer: view => new FlowchartMarker(view.state.field(field), 0),
    updateSpacer: (_spacer, update) => new FlowchartMarker(update.state.field(field), 0),
    lineMarkerChange: update => update.docChanged
  }), EditorView.baseTheme({
    ".cm-flowchart-gutter": {backgroundColor: "#ad7621", color: "#382b18"},
    ".cm-flowchart-gutter .cm-activeLineGutter": {backgroundColor: "rgba(255,255,255,0.10)"},
    ".cm-flowchart-gutter .cm-gutterElement": {padding: "0", overflow: "hidden"},
    ".cm-flowchart-row": {position: "relative", height: "100%", minHeight: "1px"},
    ".cm-flowchart-row svg": {position: "absolute", inset: "0", width: "100%", height: "100%", display: "block"}
  })];
}
