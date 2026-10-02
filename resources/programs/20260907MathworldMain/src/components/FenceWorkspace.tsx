import type { KeyboardEvent } from "react";
import { GRID_SIZE, SVG_HEIGHT, SVG_WIDTH, workspaceBounds } from "../config/workspace";
import { getBoundaryEdgeId } from "../logic/perimeter";
import type { BoundaryEdge, GridCell, RewardKind } from "../types";
import { PlantGraphic } from "./PlantGraphic";

type FenceWorkspaceProps = {
  boundaryEdges: BoundaryEdge[];
  fencedEdgeIds: Set<string>;
  isComplete: boolean;
  isFenceToolActive: boolean;
  onToggleFenceEdge: (edgeId: string) => void;
  rewardKind: RewardKind;
  targetCells: GridCell[];
};

function FenceSegment({
  edge,
  isFenceToolActive,
  isFenced,
  onToggle,
}: {
  edge: BoundaryEdge;
  isFenceToolActive: boolean;
  isFenced: boolean;
  onToggle: () => void;
}) {
  const isHorizontal = edge.direction === "top" || edge.direction === "bottom";
  const start = workspaceBounds.x + edge.start * GRID_SIZE;
  const end = workspaceBounds.x + edge.end * GRID_SIZE;
  const fixed = workspaceBounds.y + edge.fixed * GRID_SIZE;
  const x1 = isHorizontal ? start : workspaceBounds.x + edge.fixed * GRID_SIZE;
  const x2 = isHorizontal ? end : x1;
  const y1 = isHorizontal ? fixed : workspaceBounds.y + edge.start * GRID_SIZE;
  const y2 = isHorizontal ? fixed : workspaceBounds.y + edge.end * GRID_SIZE;
  const length = edge.end - edge.start;
  const labelX = isHorizontal
    ? (x1 + x2) / 2
    : x1 + (edge.direction === "left" ? -21 : 21);
  const labelY = isHorizontal
    ? y1 + (edge.direction === "top" ? -21 : 21)
    : (y1 + y2) / 2;
  const className = [
    "fence-edge",
    isFenceToolActive ? "fence-edge--active" : "",
    isFenced ? "fence-edge--placed" : "",
  ]
    .filter(Boolean)
    .join(" ");

  function handleKeyDown(event: KeyboardEvent<SVGGElement>) {
    if (!isFenceToolActive || (event.key !== "Enter" && event.key !== " ")) {
      return;
    }

    event.preventDefault();
    onToggle();
  }

  return (
    <g
      aria-label={`${length} Meter Zaun am ${edge.direction === "top" || edge.direction === "bottom" ? "waagerechten" : "senkrechten"} Beetrand`}
      aria-pressed={isFenced}
      className={className}
      onClick={() => isFenceToolActive && onToggle()}
      onKeyDown={handleKeyDown}
      role="button"
      tabIndex={isFenceToolActive ? 0 : -1}
    >
      <line className="fence-edge__guide" x1={x1} x2={x2} y1={y1} y2={y2} />
      {isFenced ? (
        <>
          <line
            className="fence-edge__rail"
            x1={isHorizontal ? x1 : x1 - 4}
            x2={isHorizontal ? x2 : x2 - 4}
            y1={isHorizontal ? y1 - 4 : y1}
            y2={isHorizontal ? y2 - 4 : y2}
          />
          <line
            className="fence-edge__rail fence-edge__rail--light"
            x1={isHorizontal ? x1 : x1 + 4}
            x2={isHorizontal ? x2 : x2 + 4}
            y1={isHorizontal ? y1 + 4 : y1}
            y2={isHorizontal ? y2 + 4 : y2}
          />
          {Array.from({ length: length + 1 }, (_, postIndex) => {
            const postOffset = postIndex * GRID_SIZE;
            const postX = isHorizontal ? x1 + postOffset : x1;
            const postY = isHorizontal ? y1 : y1 + postOffset;

            return (
              <line
                className="fence-edge__post"
                key={postIndex}
                x1={isHorizontal ? postX : postX - 9}
                x2={isHorizontal ? postX : postX + 9}
                y1={isHorizontal ? postY - 9 : postY}
                y2={isHorizontal ? postY + 9 : postY}
              />
            );
          })}
          <g className="fence-edge__label" transform={`translate(${labelX} ${labelY})`}>
            <rect height="25" rx="6" width="48" x="-24" y="-12.5" />
            <text dominantBaseline="middle" textAnchor="middle" y="1">
              {length} m
            </text>
          </g>
        </>
      ) : null}
      <line className="fence-edge__hit" x1={x1} x2={x2} y1={y1} y2={y2} />
    </g>
  );
}

export function FenceWorkspace({
  boundaryEdges,
  fencedEdgeIds,
  isComplete,
  isFenceToolActive,
  onToggleFenceEdge,
  rewardKind,
  targetCells,
}: FenceWorkspaceProps) {
  return (
    <section className="board-panel board-panel--fence" aria-label="Zaunwerkstatt-Arbeitsfläche">
      <div className="fence-board">
        <svg
          aria-label="Gartenbeet mit Beetrand für Zaunabschnitte"
          role="group"
          viewBox={`0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`}
        >
          <defs>
            <linearGradient id="fence-grass" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0" stopColor="#dff4a8" />
              <stop offset=".58" stopColor="#a8dc75" />
              <stop offset="1" stopColor="#78bd66" />
            </linearGradient>
            <pattern height="54" id="fence-grass-speckles" patternUnits="userSpaceOnUse" width="67">
              <circle cx="9" cy="12" fill="#5b9c58" opacity=".23" r="1.5" />
              <circle cx="43" cy="36" fill="#fff9bb" opacity=".38" r="1.8" />
              <path d="M 21 49 Q 23 43 28 40" fill="none" stroke="#5caa63" strokeWidth="1.5" />
            </pattern>
          </defs>
          <rect className="fence-board__grass" height={SVG_HEIGHT} rx="16" width={SVG_WIDTH} />
          <rect fill="url(#fence-grass-speckles)" height={SVG_HEIGHT} rx="16" width={SVG_WIDTH} />
          <g className="fence-board__shed" transform="translate(431 55)">
            <path d="M -44 2 L 0 -28 L 45 2 L 31 13 L -31 13 Z" />
            <path d="M -31 13 H 31 V 72 H -31 Z" />
            <path d="M 31 13 L 45 2 V 60 L 31 72 Z" />
            <rect height="42" width="22" x="-11" y="30" />
          </g>
          {targetCells.map((cell) => {
            const x = workspaceBounds.x + cell.x * GRID_SIZE;
            const y = workspaceBounds.y + cell.y * GRID_SIZE;

            return (
              <g key={`${cell.x}-${cell.y}`}>
                <rect
                  className="fence-board__soil-cell"
                  height={GRID_SIZE}
                  width={GRID_SIZE}
                  x={x}
                  y={y}
                />
                {isComplete ? (
                  <g
                    transform={`translate(${x} ${y})`}
                  >
                    <g
                      className="reward-plant fence-board__plant"
                      style={{ animationDelay: `${(cell.x + cell.y) * 45}ms` }}
                    >
                      <PlantGraphic rewardKind={rewardKind} />
                    </g>
                  </g>
                ) : null}
              </g>
            );
          })}
          {boundaryEdges.map((edge) => {
            const edgeId = getBoundaryEdgeId(edge);

            return (
              <FenceSegment
                edge={edge}
                isFenceToolActive={isFenceToolActive}
                isFenced={fencedEdgeIds.has(edgeId)}
                key={edgeId}
                onToggle={() => onToggleFenceEdge(edgeId)}
              />
            );
          })}
        </svg>
        <div
          className={
            isFenceToolActive
              ? "fence-board__status fence-board__status--active"
              : "fence-board__status"
          }
        >
          <span aria-hidden="true" className="fence-roll-icon" />
          {isFenceToolActive
            ? "Zaunrolle aktiv: Wähle die Randabschnitte."
            : "Wähle zuerst die Zaunrolle."}
        </div>
      </div>
    </section>
  );
}
