import type {
  ParkAreaConfig,
  ParkAreaPart,
  RewardKind,
  TriangleOrientation,
} from "../types";
import { PlantGraphic } from "./PlantGraphic";

type ParkWorkspaceProps = {
  isComplete: boolean;
  isPlanVisible: boolean;
  parkArea: ParkAreaConfig;
  rewardKind: RewardKind;
};

const CELL_SIZE = 54;
const ORIGIN_X = 75;
const ORIGIN_Y = 72;

function getTrianglePoints(part: ParkAreaPart, offsetX = 0, offsetY = 0) {
  const x = ORIGIN_X + part.column * CELL_SIZE + offsetX;
  const y = ORIGIN_Y + part.row * CELL_SIZE + offsetY;
  const width = part.widthCells * CELL_SIZE;
  const height = part.heightCells * CELL_SIZE;
  const orientation = part.orientation ?? "top-left";
  const pointsByOrientation: Record<TriangleOrientation, Array<[number, number]>> = {
    "top-left": [
      [x, y],
      [x + width, y],
      [x, y + height],
    ],
    "top-right": [
      [x, y],
      [x + width, y],
      [x + width, y + height],
    ],
    "bottom-left": [
      [x, y],
      [x, y + height],
      [x + width, y + height],
    ],
    "bottom-right": [
      [x + width, y],
      [x, y + height],
      [x + width, y + height],
    ],
  };

  return pointsByOrientation[orientation].map(([pointX, pointY]) => `${pointX},${pointY}`).join(" ");
}

function isPointInsidePart(part: ParkAreaPart, pointX: number, pointY: number) {
  const relativeX = (pointX - part.column) / part.widthCells;
  const relativeY = (pointY - part.row) / part.heightCells;

  if (relativeX < 0 || relativeX > 1 || relativeY < 0 || relativeY > 1) {
    return false;
  }

  if (part.kind === "rectangle") {
    return true;
  }

  switch (part.orientation ?? "top-left") {
    case "top-right":
      return relativeY <= relativeX;
    case "bottom-left":
      return relativeX <= relativeY;
    case "bottom-right":
      return relativeX + relativeY >= 1;
    default:
      return relativeX + relativeY <= 1;
  }
}

function getPlantPositions(parkArea: ParkAreaConfig) {
  const subtractParts = parkArea.parts.filter((part) => part.operation === "subtract");

  return parkArea.parts.flatMap((part) => {
    if (part.operation === "subtract") {
      return [];
    }

    return Array.from(
      { length: part.widthCells * part.heightCells },
      (_, cellIndex) => {
        const localColumn = cellIndex % part.widthCells;
        const localRow = Math.floor(cellIndex / part.widthCells);
        const pointX = part.column + localColumn + 0.5;
        const pointY = part.row + localRow + 0.5;
        const belongsToPart = isPointInsidePart(part, pointX, pointY);
        const isSubtracted = subtractParts.some((subtractPart) =>
          isPointInsidePart(subtractPart, pointX, pointY),
        );

        if (!belongsToPart || isSubtracted) {
          return null;
        }

        return {
          id: `${part.id}-${localColumn}-${localRow}`,
          x: ORIGIN_X + (part.column + localColumn) * CELL_SIZE + 7,
          y: ORIGIN_Y + (part.row + localRow) * CELL_SIZE + 7,
        };
      },
    ).filter((position): position is NonNullable<typeof position> => position !== null);
  });
}

function PartDimensions({ part }: { part: ParkAreaPart }) {
  const x = ORIGIN_X + part.column * CELL_SIZE;
  const y = ORIGIN_Y + part.row * CELL_SIZE;
  const width = part.widthCells * CELL_SIZE;
  const height = part.heightCells * CELL_SIZE;
  const horizontalY = y + height + 18;
  const verticalX = x + width + 18;

  return (
    <g className="park-dimensions">
      <line x1={x} x2={x + width} y1={horizontalY} y2={horizontalY} />
      <line x1={x} x2={x} y1={horizontalY - 7} y2={horizontalY + 7} />
      <line x1={x + width} x2={x + width} y1={horizontalY - 7} y2={horizontalY + 7} />
      <g transform={`translate(${x + width / 2} ${horizontalY})`}>
        <rect height="27" rx="6" width="54" x="-27" y="-13.5" />
        <text dominantBaseline="middle" textAnchor="middle" y="1">
          {part.widthCells} m
        </text>
      </g>

      <line x1={verticalX} x2={verticalX} y1={y} y2={y + height} />
      <line x1={verticalX - 7} x2={verticalX + 7} y1={y} y2={y} />
      <line x1={verticalX - 7} x2={verticalX + 7} y1={y + height} y2={y + height} />
      <g transform={`translate(${verticalX} ${y + height / 2})`}>
        <rect height="27" rx="6" width="54" x="-27" y="-13.5" />
        <text dominantBaseline="middle" textAnchor="middle" y="1">
          {part.heightCells} m
        </text>
      </g>
    </g>
  );
}

function ParkPartGraphic({
  isPlanVisible,
  mode,
  part,
}: {
  isPlanVisible: boolean;
  mode: ParkAreaConfig["mode"];
  part: ParkAreaPart;
}) {
  const x = ORIGIN_X + part.column * CELL_SIZE;
  const y = ORIGIN_Y + part.row * CELL_SIZE;
  const width = part.widthCells * CELL_SIZE;
  const height = part.heightCells * CELL_SIZE;
  const className = [
    "park-part",
    `park-part--${part.kind}`,
    `park-part--${part.operation}`,
    `park-part--mode-${mode}`,
  ].join(" ");
  const clipPathId = `park-part-clip-${part.id}`;

  return (
    <g className={className}>
      {part.kind === "triangle" ? (
        <polygon className="park-part__shadow" points={getTrianglePoints(part, 7, 8)} />
      ) : (
        <rect
          className="park-part__shadow"
          height={height}
          rx="7"
          width={width}
          x={x + 7}
          y={y + 8}
        />
      )}
      {part.kind === "triangle" ? (
        <polygon className="park-part__surface" points={getTrianglePoints(part)} />
      ) : (
        <rect
          className="park-part__surface"
          height={height}
          rx="7"
          width={width}
          x={x}
          y={y}
        />
      )}
      <g clipPath={part.kind === "triangle" ? `url(#${clipPathId})` : undefined}>
        {Array.from({ length: part.widthCells - 1 }, (_, columnIndex) => (
          <line
            className="park-part__grid"
            key={`column-${columnIndex}`}
            x1={x + (columnIndex + 1) * CELL_SIZE}
            x2={x + (columnIndex + 1) * CELL_SIZE}
            y1={y}
            y2={y + height}
          />
        ))}
        {Array.from({ length: part.heightCells - 1 }, (_, rowIndex) => (
          <line
            className="park-part__grid"
            key={`row-${rowIndex}`}
            x1={x}
            x2={x + width}
            y1={y + (rowIndex + 1) * CELL_SIZE}
            y2={y + (rowIndex + 1) * CELL_SIZE}
          />
        ))}
      </g>
      {isPlanVisible && part.kind === "triangle" ? (
        <rect
          className="park-part__helper"
          height={height}
          width={width}
          x={x}
          y={y}
        />
      ) : null}
      <g className="park-part__name" transform={`translate(${x + width / 2} ${y - 16})`}>
        <rect
          height="27"
          rx="7"
          width={Math.max(104, part.label.length * 8)}
          x={-Math.max(52, part.label.length * 4)}
          y="-13.5"
        />
        <text dominantBaseline="middle" textAnchor="middle" y="1">
          {part.label}
        </text>
      </g>
      {isPlanVisible ? <PartDimensions part={part} /> : null}
    </g>
  );
}

export function ParkWorkspace({
  isComplete,
  isPlanVisible,
  parkArea,
  rewardKind,
}: ParkWorkspaceProps) {
  const plantPositions = getPlantPositions(parkArea);

  return (
    <section className="board-panel board-panel--park" aria-label="Parkplan-Arbeitsfläche">
      <div className="park-board">
        <svg
          aria-label="Parkplan mit Rechtecken und Dreiecken"
          role="img"
          viewBox="0 0 760 520"
        >
          <defs>
            <linearGradient id="park-grass" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0" stopColor="#e6f3a9" />
              <stop offset=".58" stopColor="#a9d97e" />
              <stop offset="1" stopColor="#78ba72" />
            </linearGradient>
            <pattern height="34" id="park-speckles" patternUnits="userSpaceOnUse" width="40">
              <circle cx="8" cy="11" fill="#4d9859" opacity=".2" r="1.5" />
              <circle cx="29" cy="24" fill="#fff8b4" opacity=".4" r="1.8" />
              <path d="M 13 31 Q 15 25 20 23" fill="none" stroke="#579b61" strokeWidth="1.4" />
            </pattern>
            {parkArea.parts
              .filter((part) => part.kind === "triangle")
              .map((part) => (
                <clipPath id={`park-part-clip-${part.id}`} key={part.id}>
                  <polygon points={getTrianglePoints(part)} />
                </clipPath>
              ))}
          </defs>

          <rect className="park-board__grass" height="520" rx="16" width="760" />
          <rect fill="url(#park-speckles)" height="520" rx="16" width="760" />
          <path className="park-board__path" d="M -20 448 C 132 414 183 450 306 420 C 431 390 516 430 780 362" />
          <g className="park-board__bench" transform="translate(630 438)">
            <path d="M -43 -15 H 43 M -38 0 H 38 M -32 0 L -39 27 M 32 0 L 39 27" />
            <path d="M -44 -20 V 5 M 44 -20 V 5" />
          </g>

          {parkArea.parts.map((part) => (
            <ParkPartGraphic
              isPlanVisible={isPlanVisible}
              key={part.id}
              mode={parkArea.mode}
              part={part}
            />
          ))}

          {isComplete
            ? plantPositions.map((position, plantIndex) => (
                <g
                  key={position.id}
                  transform={`translate(${position.x} ${position.y})`}
                >
                  <g
                    className="reward-plant park-board__plant"
                    style={{ animationDelay: `${plantIndex * 45}ms` }}
                  >
                    <PlantGraphic rewardKind={rewardKind} />
                  </g>
                </g>
              ))
            : null}
        </svg>
        <div
          className={
            isPlanVisible
              ? "park-board__status park-board__status--active"
              : "park-board__status"
          }
        >
          <span aria-hidden="true" className="park-plan-icon" />
          {isPlanVisible
            ? "Hilfsrechteck aktiv: Die Seitenlängen und Teilflächen sind sichtbar."
            : "Lege das Hilfsrechteck über den Parkplan."}
        </div>
      </div>
    </section>
  );
}
