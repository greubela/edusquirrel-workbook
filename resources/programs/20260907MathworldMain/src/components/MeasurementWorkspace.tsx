import type { KeyboardEvent } from "react";
import {
  getMeasurementEdgeKey,
  type MeasurementDimension,
} from "../logic/measurement";
import type { RectangleAreaBed, RectangleAreaConfig, RewardKind } from "../types";
import { PlantGraphic } from "./PlantGraphic";

type MeasurementWorkspaceProps = {
  isComplete: boolean;
  isMeasureToolActive: boolean;
  measuredEdges: Set<string>;
  measurement: RectangleAreaConfig;
  onMeasureEdge: (bedId: string, dimension: MeasurementDimension) => void;
  rewardKind: RewardKind;
};

const CELL_SIZE = 54;
const BED_ORIGIN_X = 88;
const BED_ORIGIN_Y = 96;

function BedDimension({
  bed,
  dimension,
  isMeasureToolActive,
  isMeasured,
  onMeasure,
}: {
  bed: RectangleAreaBed;
  dimension: MeasurementDimension;
  isMeasureToolActive: boolean;
  isMeasured: boolean;
  onMeasure: () => void;
}) {
  const x = BED_ORIGIN_X + bed.column * CELL_SIZE;
  const y = BED_ORIGIN_Y + bed.row * CELL_SIZE;
  const width = bed.widthCells * CELL_SIZE;
  const height = bed.heightCells * CELL_SIZE;
  const isWidth = dimension === "width";
  const className = [
    "measurement-edge",
    isMeasureToolActive ? "measurement-edge--active" : "",
    isMeasured ? "measurement-edge--measured" : "",
  ]
    .filter(Boolean)
    .join(" ");
  const label = `${isWidth ? bed.widthCells : bed.heightCells} m`;

  function handleKeyDown(event: KeyboardEvent<SVGGElement>) {
    if (!isMeasureToolActive || (event.key !== "Enter" && event.key !== " ")) {
      return;
    }

    event.preventDefault();
    onMeasure();
  }

  if (isWidth) {
    const edgeY = y + height + 18;

    return (
      <g
        aria-disabled={!isMeasureToolActive}
        aria-label={`Länge von ${bed.label} messen`}
        className={className}
        onClick={() => isMeasureToolActive && onMeasure()}
        onKeyDown={handleKeyDown}
        role="button"
        tabIndex={isMeasureToolActive ? 0 : -1}
      >
        <line className="measurement-edge__guide" x1={x} x2={x + width} y1={edgeY} y2={edgeY} />
        <line className="measurement-edge__tick" x1={x} x2={x} y1={edgeY - 8} y2={edgeY + 8} />
        <line
          className="measurement-edge__tick"
          x1={x + width}
          x2={x + width}
          y1={edgeY - 8}
          y2={edgeY + 8}
        />
        <line className="measurement-edge__hit" x1={x} x2={x + width} y1={edgeY} y2={edgeY} />
        <g transform={`translate(${x + width / 2} ${edgeY})`}>
          <rect className="measurement-edge__label" height="30" rx="7" width="62" x="-31" y="-15" />
          <text className="measurement-edge__text" dominantBaseline="middle" textAnchor="middle" y="1">
            {isMeasured ? label : "?"}
          </text>
        </g>
      </g>
    );
  }

  const edgeX = x + width + 18;

  return (
    <g
      aria-disabled={!isMeasureToolActive}
      aria-label={`Breite von ${bed.label} messen`}
      className={className}
      onClick={() => isMeasureToolActive && onMeasure()}
      onKeyDown={handleKeyDown}
      role="button"
      tabIndex={isMeasureToolActive ? 0 : -1}
    >
      <line className="measurement-edge__guide" x1={edgeX} x2={edgeX} y1={y} y2={y + height} />
      <line className="measurement-edge__tick" x1={edgeX - 8} x2={edgeX + 8} y1={y} y2={y} />
      <line
        className="measurement-edge__tick"
        x1={edgeX - 8}
        x2={edgeX + 8}
        y1={y + height}
        y2={y + height}
      />
      <line className="measurement-edge__hit" x1={edgeX} x2={edgeX} y1={y} y2={y + height} />
      <g transform={`translate(${edgeX} ${y + height / 2})`}>
        <rect className="measurement-edge__label" height="30" rx="7" width="62" x="-31" y="-15" />
        <text className="measurement-edge__text" dominantBaseline="middle" textAnchor="middle" y="1">
          {isMeasured ? label : "?"}
        </text>
      </g>
    </g>
  );
}

function MeasuredBed({
  bed,
  isComplete,
  isMeasureToolActive,
  measuredEdges,
  onMeasureEdge,
  rewardKind,
}: {
  bed: RectangleAreaBed;
  isComplete: boolean;
  isMeasureToolActive: boolean;
  measuredEdges: Set<string>;
  onMeasureEdge: (bedId: string, dimension: MeasurementDimension) => void;
  rewardKind: RewardKind;
}) {
  const x = BED_ORIGIN_X + bed.column * CELL_SIZE;
  const y = BED_ORIGIN_Y + bed.row * CELL_SIZE;
  const width = bed.widthCells * CELL_SIZE;
  const height = bed.heightCells * CELL_SIZE;
  const bedRewardKind = bed.rewardKind ?? rewardKind;

  return (
    <g className="measurement-bed">
      <rect
        className="measurement-bed__shadow"
        height={height}
        rx="7"
        width={width}
        x={x + 7}
        y={y + 9}
      />
      <rect
        className="measurement-bed__soil"
        height={height}
        rx="7"
        width={width}
        x={x}
        y={y}
      />
      {Array.from({ length: bed.widthCells - 1 }, (_, columnIndex) => (
        <line
          className="measurement-bed__grid"
          key={`column-${columnIndex}`}
          x1={x + (columnIndex + 1) * CELL_SIZE}
          x2={x + (columnIndex + 1) * CELL_SIZE}
          y1={y}
          y2={y + height}
        />
      ))}
      {Array.from({ length: bed.heightCells - 1 }, (_, rowIndex) => (
        <line
          className="measurement-bed__grid"
          key={`row-${rowIndex}`}
          x1={x}
          x2={x + width}
          y1={y + (rowIndex + 1) * CELL_SIZE}
          y2={y + (rowIndex + 1) * CELL_SIZE}
        />
      ))}
      <g className="measurement-bed__name" transform={`translate(${x + width / 2} ${y - 16})`}>
        <rect height="26" rx="7" width={Math.max(116, bed.label.length * 8)} x={-Math.max(58, bed.label.length * 4)} y="-13" />
        <text dominantBaseline="middle" textAnchor="middle" y="1">
          {bed.label}
        </text>
      </g>
      {isComplete
        ? Array.from({ length: bed.widthCells * bed.heightCells }, (_, cellIndex) => {
            const column = cellIndex % bed.widthCells;
            const row = Math.floor(cellIndex / bed.widthCells);

            return (
              <g
                key={`plant-${cellIndex}`}
                transform={`translate(${x + column * CELL_SIZE + 8} ${y + row * CELL_SIZE + 8})`}
              >
                <g
                  className="reward-plant measurement-bed__plant"
                  style={{ animationDelay: `${cellIndex * 55}ms` }}
                >
                  <PlantGraphic rewardKind={bedRewardKind} />
                </g>
              </g>
            );
          })
        : null}
      <BedDimension
        bed={bed}
        dimension="width"
        isMeasured={measuredEdges.has(getMeasurementEdgeKey(bed.id, "width"))}
        isMeasureToolActive={isMeasureToolActive}
        onMeasure={() => onMeasureEdge(bed.id, "width")}
      />
      <BedDimension
        bed={bed}
        dimension="height"
        isMeasured={measuredEdges.has(getMeasurementEdgeKey(bed.id, "height"))}
        isMeasureToolActive={isMeasureToolActive}
        onMeasure={() => onMeasureEdge(bed.id, "height")}
      />
    </g>
  );
}

export function MeasurementWorkspace({
  isComplete,
  isMeasureToolActive,
  measuredEdges,
  measurement,
  onMeasureEdge,
  rewardKind,
}: MeasurementWorkspaceProps) {
  return (
    <section className="board-panel board-panel--measurement" aria-label="Gewächshaus-Arbeitsfläche">
      <div className="measurement-board">
        <svg
          aria-label="Rechteckige Beete im Gewächshaus mit messbaren Seiten"
          role="img"
          viewBox="0 0 760 520"
        >
          <defs>
            <linearGradient id="greenhouse-glass" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0" stopColor="#f5fdff" stopOpacity=".92" />
              <stop offset=".52" stopColor="#aee4e9" stopOpacity=".6" />
              <stop offset="1" stopColor="#d7f2df" stopOpacity=".76" />
            </linearGradient>
            <pattern height="28" id="greenhouse-floor-grid" patternUnits="userSpaceOnUse" width="28">
              <path d="M 28 0 H 0 V 28" fill="none" stroke="#86b790" strokeOpacity=".2" />
            </pattern>
          </defs>
          <rect className="measurement-board__sky" height="520" width="760" />
          <path className="measurement-board__floor" d="M 42 116 H 718 V 480 H 42 Z" />
          <path className="measurement-board__floor-grid" d="M 42 116 H 718 V 480 H 42 Z" />
          <g className="measurement-greenhouse">
            <path d="M 47 116 L 164 34 H 596 L 713 116" />
            <path d="M 164 34 L 380 8 L 596 34" />
            <path d="M 47 116 V 480 M 713 116 V 480 M 164 34 V 480 M 596 34 V 480 M 380 8 V 480" />
            <path d="M 47 116 H 713 M 58 238 H 702 M 58 360 H 702" />
          </g>
          <g className="measurement-greenhouse__glints">
            <path d="M 107 97 L 171 51 M 621 74 L 658 104 M 232 37 L 292 27" />
          </g>
          {measurement.beds.map((bed) => (
            <MeasuredBed
              bed={bed}
              isComplete={isComplete}
              isMeasureToolActive={isMeasureToolActive}
              key={bed.id}
              measuredEdges={measuredEdges}
              onMeasureEdge={onMeasureEdge}
              rewardKind={rewardKind}
            />
          ))}
        </svg>
        <div
          className={
            isMeasureToolActive
              ? "measurement-board__status measurement-board__status--active"
              : "measurement-board__status"
          }
        >
          <span aria-hidden="true" className="measurement-tape-icon" />
          {isMeasureToolActive
            ? "Maßband aktiv: Wähle die markierten Seiten."
            : "Wähle zuerst das Maßband."}
        </div>
      </div>
    </section>
  );
}
