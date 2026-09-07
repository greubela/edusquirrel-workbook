import type { CSSProperties, PointerEvent, RefObject } from "react";
import { CROP_GROWTH_STAGGER_MS, SOWING_ANIMATION_DURATION_MS } from "../config/animation";
import {
  GRID_SIZE,
  SVG_HEIGHT,
  SVG_WIDTH,
  workspaceBounds,
} from "../config/workspace";
import type {
  BoundaryEdge,
  DragState,
  DrawingState,
  EdgeLabel,
  FractionReadConfig,
  GardenDigAnimation,
  GardenMissionStage,
  GridCell,
  GridPoint,
  MissionConfig,
  MissionCoverage,
  MissionVariant,
  RewardKind,
  StakingPathState,
  WorkspaceShape,
} from "../types";
import {
  cellKey,
  createEdgeLabel,
  getCoveredCells,
  getDrawingRectangle,
  getShapeArea,
  getTargetBoundaryEdges,
} from "../logic/geometry";
import {
  GARDEN_BED_TOOL_ID,
  getGardenDesignCells,
  getGardenDesignStatus,
  getGardenDesignThreadLength,
  isGardenBedShape,
  isGardenSeedSackShape,
} from "../logic/gardenDesign";
import {
  getStakedRectangle,
  getStakingPreviewPath,
} from "../logic/stakingThread";
import { ParametricSack, ParametricSackSvg } from "./ParametricSack";
import { PlantGraphic } from "./PlantGraphic";
import { GardenSpadeArtwork } from "./GardenSpade";

type WorkspaceProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  canDrawCustomShape: boolean;
  canStakeGardenBed: boolean;
  coverage: MissionCoverage;
  dragState: DragState | null;
  drawingState: DrawingState | null;
  dugGardenCellKeys: Set<string>;
  gardenDigAnimations: GardenDigAnimation[];
  gardenMissionStage: GardenMissionStage;
  lastSettledShapeId: string | null;
  selectedShapeId: string | null;
  shapes: WorkspaceShape[];
  svgRef: RefObject<SVGSVGElement | null>;
  targetBoundaryEdges: BoundaryEdge[];
  targetCells: GridCell[];
  targetEdgeLabels: EdgeLabel[];
  onPointerEnd: () => void;
  onPointerMove: (event: PointerEvent<SVGSVGElement>) => void;
  onStartDrawing: (event: PointerEvent<SVGRectElement>) => void;
  onStartDragging: (event: PointerEvent<SVGRectElement>, shape: WorkspaceShape) => void;
  plantedShapeIds: Set<string>;
  showRearrangementAnimation: boolean;
  showSimplificationGridHint: boolean;
  plantingShapeIds: Set<string>;
  rewardKindOverride?: RewardKind;
  threadLengthLimit: number;
  stakingPath: StakingPathState;
  onGardenCellAction: (cell: GridCell) => void;
  onPlaceStakingPoint: (event: PointerEvent<SVGRectElement>) => void;
};

const SHAPE_TONE_CLASSES = [
  "placed-shape--sun",
  "placed-shape--berry",
  "placed-shape--leaf",
  "placed-shape--sky",
  "placed-shape--carrot",
];

type FractionSquareWorkspaceProps = {
  fraction: FractionReadConfig;
  rewardKind: RewardKind;
  showRearrangementAnimation: boolean;
  showSimplificationGridHint: boolean;
  variantName: string;
};

function SupplySackVisual({
  fraction,
  rewardKind,
}: {
  fraction: FractionReadConfig;
  rewardKind: RewardKind;
}) {
  const supply = fraction.supply;

  if (!supply) {
    return null;
  }

  return (
    <div className="fraction-supply-summary">
      <ParametricSack
        ariaHidden
        className={`fraction-supply-sack fraction-supply-sack--${supply.resourceKind}`}
        height={196}
        kind={supply.resourceKind === "seeds" ? "seed" : "fertilizer"}
        plantKind={supply.resourceKind === "seeds" ? rewardKind : undefined}
        title={supply.sackLabel}
        width={176}
      />
      <div className="fraction-supply-summary__copy">
        <strong>1 {supply.sackLabel}</strong>
        <span>{supply.contentsLabel}</span>
        <small>
          reicht fuer {fraction.denominator} {supply.bedLabel}
        </small>
      </div>
    </div>
  );
}

function FractionSquareWorkspace({
  fraction,
  rewardKind,
  showRearrangementAnimation,
  showSimplificationGridHint,
  variantName,
}: FractionSquareWorkspaceProps) {
  const supply = fraction.supply;
  const size = 288;
  const cellWidth = size / fraction.columns;
  const cellHeight = size / fraction.rows;
  const filledCellIndexes = new Set(fraction.filledCellIndexes);
  const rearrangedTargets = new Map(
    fraction.filledCellIndexes.map((cellIndex, filledIndex) => {
      const targetRow = Math.floor(filledIndex / fraction.columns);
      const targetColumn = filledIndex % fraction.columns;

      return [cellIndex, { column: targetColumn, row: targetRow }];
    }),
  );
  const getCellIndex = (row: number, column: number) => row * fraction.columns + column;
  const isBoundaryBetweenFillStates = (firstIndex: number, secondIndex: number) =>
    filledCellIndexes.has(firstIndex) !== filledCellIndexes.has(secondIndex);
  const className = [
    "fraction-square",
    `fraction-square--${fraction.designKey ?? "default"}`,
    showSimplificationGridHint ? "fraction-square--simplification-hint" : "",
    showRearrangementAnimation ? "fraction-square--rearranging" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <section className="board-panel board-panel--fraction" aria-label="Bruchteil-Arbeitsflaeche">
      <div className="fraction-board">
        <div className={supply ? "fraction-figure-card fraction-figure-card--supply" : "fraction-figure-card"}>
          <p className="eyebrow">{supply ? "Vorrat aufteilen" : "Ganzes"}</p>
          <SupplySackVisual fraction={fraction} rewardKind={rewardKind} />
          {supply ? <p className="fraction-supply-plan-label">Verteilplan</p> : null}
          <svg
            aria-label={
              supply
                ? `${variantName}: ${fraction.numerator} von ${fraction.denominator} ${supply.bedLabel} sind ${supply.filledBedLabel}`
                : `${variantName}: ${fraction.numerator} von ${fraction.denominator} Teilen sind gefaerbt`
            }
            className={className}
            role="img"
            viewBox="0 0 320 320"
          >
            {supply ? (
              <defs>
                <pattern height="18" id="fraction-seeds-pattern" patternUnits="userSpaceOnUse" width="18">
                  <rect fill="#80b95d" height="18" width="18" />
                  <ellipse cx="5" cy="6" fill="#f8e19a" rx="2.4" ry="1.5" transform="rotate(-28 5 6)" />
                  <ellipse cx="13" cy="12" fill="#fff0b8" rx="2.4" ry="1.5" transform="rotate(32 13 12)" />
                </pattern>
                <pattern height="16" id="fraction-fertilizer-pattern" patternUnits="userSpaceOnUse" width="16">
                  <rect fill="#d69b4c" height="16" width="16" />
                  <circle cx="4" cy="5" fill="#f7dd8d" r="1.8" />
                  <circle cx="12" cy="11" fill="#fff0b6" r="1.6" />
                  <circle cx="8" cy="3" fill="#9e6c31" r="1.2" />
                </pattern>
              </defs>
            ) : null}
            <rect className="fraction-square__shadow" height="288" rx="14" width="288" x="24" y="26" />
            <rect className="fraction-square__whole" height="288" rx="10" width="288" x="16" y="16" />
            {Array.from({ length: fraction.rows * fraction.columns }, (_, cellIndex) => {
              const row = Math.floor(cellIndex / fraction.columns);
              const column = cellIndex % fraction.columns;
              const rearrangedTarget = rearrangedTargets.get(cellIndex);
              const cellStyle =
                showRearrangementAnimation && rearrangedTarget
                  ? ({
                      "--fraction-rearrange-x": `${(rearrangedTarget.column - column) * cellWidth}px`,
                      "--fraction-rearrange-y": `${(rearrangedTarget.row - row) * cellHeight}px`,
                    } as CSSProperties)
                  : undefined;

              return (
                <rect
                  className={
                    filledCellIndexes.has(cellIndex)
                      ? "fraction-square__cell fraction-square__cell--filled"
                      : "fraction-square__cell"
                  }
                  height={cellHeight}
                  key={`fraction-cell-${cellIndex}`}
                  style={cellStyle}
                  width={cellWidth}
                  x={16 + column * cellWidth}
                  y={16 + row * cellHeight}
                />
              );
            })}
            {Array.from({ length: Math.max(fraction.columns - 1, 0) }, (_, columnIndex) => {
              const column = columnIndex + 1;
              const x = 16 + column * cellWidth;

              return Array.from({ length: fraction.rows }, (_, row) => {
                const leftCellIndex = getCellIndex(row, column - 1);
                const rightCellIndex = getCellIndex(row, column);
                const isFillBoundary = isBoundaryBetweenFillStates(leftCellIndex, rightCellIndex);

                return (
                  <line
                    className={
                      isFillBoundary
                        ? "fraction-square__grid-line fraction-square__grid-line--boundary"
                        : "fraction-square__grid-line"
                    }
                    key={`fraction-grid-v-${column}-${row}`}
                    x1={x}
                    x2={x}
                    y1={16 + row * cellHeight}
                    y2={16 + (row + 1) * cellHeight}
                  />
                );
              });
            })}
            {Array.from({ length: Math.max(fraction.rows - 1, 0) }, (_, rowIndex) => {
              const row = rowIndex + 1;
              const y = 16 + row * cellHeight;

              return Array.from({ length: fraction.columns }, (_, column) => {
                const topCellIndex = getCellIndex(row - 1, column);
                const bottomCellIndex = getCellIndex(row, column);
                const isFillBoundary = isBoundaryBetweenFillStates(topCellIndex, bottomCellIndex);

                return (
                  <line
                    className={
                      isFillBoundary
                        ? "fraction-square__grid-line fraction-square__grid-line--boundary"
                        : "fraction-square__grid-line"
                    }
                    key={`fraction-grid-h-${row}-${column}`}
                    x1={16 + column * cellWidth}
                    x2={16 + (column + 1) * cellWidth}
                    y1={y}
                    y2={y}
                  />
                );
              });
            })}
            <rect className="fraction-square__outline" height="288" rx="10" width="288" x="16" y="16" />
          </svg>
          <p className="fraction-whole-label">{fraction.wholeLabel ?? "1 Ganzes"}</p>
        </div>
      </div>
    </section>
  );
}

function RewardPlant({ rewardKind }: { rewardKind: RewardKind }) {
  return <PlantGraphic rewardKind={rewardKind} />;
}

type GardenTuftProps = {
  className?: string;
  scale?: number;
  x: number;
  y: number;
};

function GardenTuft({ className = "", scale = 1, x, y }: GardenTuftProps) {
  return (
    <g className={`garden-tuft ${className}`.trim()} transform={`translate(${x} ${y}) scale(${scale})`}>
      <path d="M 0 10 C 1 5, 2 3, 4 0" />
      <path d="M 6 10 C 6 4, 7 2, 10 -1" />
      <path d="M 11 10 C 12 5, 15 3, 18 2" />
      <path d="M 5 10 C 3 7, 2 6, -1 5" />
      <path d="M 10 10 C 12 7, 14 6, 17 6" />
    </g>
  );
}

type GardenShrubProps = {
  className?: string;
  scale?: number;
  x: number;
  y: number;
};

function GardenShrub({ className = "", scale = 1, x, y }: GardenShrubProps) {
  return (
    <g className={`garden-shrub ${className}`.trim()} transform={`translate(${x} ${y}) scale(${scale})`}>
      <ellipse className="garden-shrub__shadow" cx="24" cy="31" rx="23" ry="6" />
      <circle className="garden-shrub__leaf garden-shrub__leaf--dark" cx="9" cy="23" r="8" />
      <circle className="garden-shrub__leaf" cx="18" cy="15" r="11" />
      <circle className="garden-shrub__leaf garden-shrub__leaf--light" cx="31" cy="18" r="10" />
      <circle className="garden-shrub__leaf" cx="39" cy="26" r="8" />
      <circle className="garden-shrub__flower" cx="17" cy="13" r="2.1" />
      <circle className="garden-shrub__flower garden-shrub__flower--blue" cx="33" cy="18" r="1.9" />
      <circle className="garden-shrub__flower garden-shrub__flower--yellow" cx="40" cy="25" r="1.8" />
    </g>
  );
}

function GardenDecorations() {
  return (
    <g className="garden-decorations" aria-hidden="true">
      <GardenShrub x={33} y={303} scale={0.72} />
      <GardenShrub x={425} y={35} scale={0.65} />
      <GardenShrub className="garden-shrub--muted" x={420} y={318} scale={0.58} />
      <GardenTuft x={63} y={64} scale={0.72} />
      <GardenTuft x={151} y={44} scale={0.52} />
      <GardenTuft x={325} y={72} scale={0.6} />
      <GardenTuft x={84} y={252} scale={0.58} />
      <GardenTuft x={194} y={311} scale={0.62} />
      <GardenTuft x={355} y={269} scale={0.56} />
      <GardenTuft x={466} y={210} scale={0.54} />
      <ellipse className="garden-stone garden-stone--one" cx="356" cy="339" rx="13" ry="6" />
      <ellipse className="garden-stone garden-stone--two" cx="389" cy="336" rx="10" ry="5" />
      <ellipse className="garden-stone garden-stone--three" cx="416" cy="340" rx="12" ry="5.5" />
      <circle className="garden-sparkle garden-sparkle--one" cx="116" cy="99" r="2" />
      <circle className="garden-sparkle garden-sparkle--two" cx="443" cy="142" r="1.8" />
      <circle className="garden-sparkle garden-sparkle--three" cx="245" cy="61" r="1.6" />
    </g>
  );
}

type DimensionTagProps = {
  className?: string;
  orientation?: "horizontal" | "vertical";
  text: string;
  x: number;
  y: number;
};

function DimensionTag({ className = "", orientation = "horizontal", text, x, y }: DimensionTagProps) {
  const tagWidth = Math.max(24, text.length * 6.5 + 10);
  const tagHeight = 14;
  const transform = orientation === "vertical" ? `translate(${x} ${y}) rotate(-90)` : `translate(${x} ${y})`;

  return (
    <g className={`dimension-tag ${className}`.trim()} transform={transform}>
      <rect
        className="dimension-tag__background"
        height={tagHeight}
        rx="6"
        width={tagWidth}
        x={-tagWidth / 2}
        y={-tagHeight / 2}
      />
      <text className="edge-label dimension-tag__text" x="0" y="1">
        {text}
      </text>
    </g>
  );
}

function TargetEdgeLabel({ orientation = "horizontal", text, x, y }: DimensionTagProps) {
  const transform = orientation === "vertical" ? `translate(${x} ${y}) rotate(-90)` : `translate(${x} ${y})`;

  return (
    <text className="edge-label target-edge-label" transform={transform} x="0" y="1">
      {text}
    </text>
  );
}

type SeedSackProps = {
  className: string;
  isPlanting: boolean;
  rewardKind: RewardKind;
  shape: WorkspaceShape;
};

function LegacySeedSack({ className, isPlanting, shape }: SeedSackProps) {
  const width = shape.widthCells * GRID_SIZE;
  const height = shape.heightCells * GRID_SIZE;
  const smallSide = Math.min(width, height);
  const capTopY = shape.y + Math.max(2, height * 0.03);
  const rimY = shape.y + Math.max(8, height * 0.22);
  const bodyTopY = shape.y + Math.max(12, height * 0.3);
  const bodyBottomY = shape.y + height - Math.max(3, height * 0.07);
  const seamY = shape.y + height * 0.58;
  const leftTopX = shape.x + Math.max(6, width * 0.19);
  const rightTopX = shape.x + width - Math.max(6, width * 0.19);
  const leftBottomX = shape.x + Math.max(3, width * 0.06);
  const rightBottomX = shape.x + width - Math.max(3, width * 0.06);
  const centerX = shape.x + width / 2;
  const capLeftX = shape.x + width * 0.12;
  const capRightX = shape.x + width * 0.88;
  const showAreaLabel = width >= GRID_SIZE * 1.5 && height >= GRID_SIZE * 1.5;
  const showFineDetails = smallSide >= GRID_SIZE;
  const stitchCount = Math.max(3, Math.min(8, Math.round(width / 16)));

  return (
    <g className={className} aria-hidden="true">
      <g className="seed-sack__art">
        <path
          className="seed-sack__shadow"
          d={`M ${leftBottomX + 2} ${bodyBottomY - height * 0.08}
            C ${shape.x + width * 0.18} ${bodyBottomY + 7}, ${shape.x + width * 0.82} ${bodyBottomY + 7}, ${rightBottomX + 2} ${bodyBottomY - height * 0.08}
            C ${shape.x + width * 0.73} ${bodyBottomY + 12}, ${shape.x + width * 0.27} ${bodyBottomY + 12}, ${leftBottomX + 2} ${bodyBottomY - height * 0.08}
            Z`}
        />
        {showFineDetails ? (
          <>
            <path
              className="seed-sack__tie-outline"
              d={`M ${rightTopX - 1} ${rimY + 1}
                C ${shape.x + width * 0.98} ${rimY + 3}, ${shape.x + width * 1.04} ${rimY + height * 0.18}, ${shape.x + width * 0.93} ${rimY + height * 0.2}
                M ${rightTopX - 2} ${rimY + 2}
                C ${shape.x + width * 1.02} ${rimY + height * 0.05}, ${shape.x + width * 1.05} ${rimY + height * 0.25}, ${shape.x + width * 0.94} ${rimY + height * 0.28}`}
            />
            <path
              className="seed-sack__tie"
              d={`M ${rightTopX - 1} ${rimY + 1}
                C ${shape.x + width * 0.98} ${rimY + 3}, ${shape.x + width * 1.04} ${rimY + height * 0.18}, ${shape.x + width * 0.93} ${rimY + height * 0.2}
                M ${rightTopX - 2} ${rimY + 2}
                C ${shape.x + width * 1.02} ${rimY + height * 0.05}, ${shape.x + width * 1.05} ${rimY + height * 0.25}, ${shape.x + width * 0.94} ${rimY + height * 0.28}`}
            />
          </>
        ) : null}
        <path
          className="seed-sack__body"
          d={`M ${leftTopX} ${bodyTopY}
            C ${shape.x + width * 0.12} ${shape.y + height * 0.46}, ${leftBottomX} ${shape.y + height * 0.7}, ${leftBottomX} ${bodyBottomY - height * 0.12}
            C ${leftBottomX} ${bodyBottomY + height * 0.06}, ${shape.x + width * 0.28} ${bodyBottomY}, ${centerX} ${bodyBottomY}
            C ${shape.x + width * 0.72} ${bodyBottomY}, ${rightBottomX} ${bodyBottomY + height * 0.06}, ${rightBottomX} ${bodyBottomY - height * 0.12}
            C ${rightBottomX} ${shape.y + height * 0.7}, ${shape.x + width * 0.88} ${shape.y + height * 0.46}, ${rightTopX} ${bodyTopY}
            C ${shape.x + width * 0.68} ${bodyTopY + height * 0.04}, ${shape.x + width * 0.32} ${bodyTopY + height * 0.04}, ${leftTopX} ${bodyTopY}
            Z`}
        />
        <path
          className="seed-sack__lower-panel"
          d={`M ${leftBottomX + 1} ${seamY}
            C ${shape.x + width * 0.24} ${seamY + height * 0.05}, ${shape.x + width * 0.76} ${seamY + height * 0.05}, ${rightBottomX - 1} ${seamY}
            L ${rightBottomX} ${bodyBottomY - height * 0.1}
            C ${rightBottomX} ${bodyBottomY + height * 0.06}, ${shape.x + width * 0.72} ${bodyBottomY}, ${centerX} ${bodyBottomY}
            C ${shape.x + width * 0.28} ${bodyBottomY}, ${leftBottomX} ${bodyBottomY + height * 0.06}, ${leftBottomX} ${bodyBottomY - height * 0.1}
            Z`}
        />
        <path
          className="seed-sack__body-highlight"
          d={`M ${shape.x + width * 0.22} ${bodyTopY + height * 0.12}
            C ${shape.x + width * 0.12} ${shape.y + height * 0.52}, ${shape.x + width * 0.13} ${shape.y + height * 0.78}, ${shape.x + width * 0.24} ${bodyBottomY - height * 0.1}`}
        />
        <path
          className="seed-sack__side-fold"
          d={`M ${shape.x + width * 0.14} ${shape.y + height * 0.72}
            C ${shape.x + width * 0.09} ${shape.y + height * 0.8}, ${shape.x + width * 0.12} ${shape.y + height * 0.9}, ${shape.x + width * 0.21} ${shape.y + height * 0.87}`}
        />
        <path
          className="seed-sack__seam"
          d={`M ${leftBottomX + 2} ${seamY}
            C ${shape.x + width * 0.26} ${seamY + height * 0.04}, ${shape.x + width * 0.74} ${seamY + height * 0.04}, ${rightBottomX - 2} ${seamY}`}
        />
        <path
          className="seed-sack__cap"
          d={`M ${capLeftX} ${rimY}
            C ${shape.x + width * 0.08} ${capTopY + height * 0.08}, ${shape.x + width * 0.22} ${capTopY}, ${shape.x + width * 0.3} ${capTopY + height * 0.08}
            C ${shape.x + width * 0.38} ${capTopY + height * 0.16}, ${shape.x + width * 0.43} ${capTopY - height * 0.04}, ${centerX} ${capTopY + height * 0.05}
            C ${shape.x + width * 0.58} ${capTopY - height * 0.04}, ${shape.x + width * 0.64} ${capTopY + height * 0.16}, ${shape.x + width * 0.72} ${capTopY + height * 0.08}
            C ${shape.x + width * 0.8} ${capTopY}, ${shape.x + width * 0.93} ${capTopY + height * 0.08}, ${capRightX} ${rimY}
            C ${shape.x + width * 0.75} ${rimY + height * 0.09}, ${shape.x + width * 0.25} ${rimY + height * 0.09}, ${capLeftX} ${rimY}
            Z`}
        />
        <path
          className="seed-sack__rim-band"
          d={`M ${leftTopX - width * 0.06} ${rimY + height * 0.05}
            C ${shape.x + width * 0.26} ${rimY - height * 0.01}, ${shape.x + width * 0.74} ${rimY - height * 0.01}, ${rightTopX + width * 0.06} ${rimY + height * 0.05}
            C ${shape.x + width * 0.76} ${rimY + height * 0.13}, ${shape.x + width * 0.24} ${rimY + height * 0.13}, ${leftTopX - width * 0.06} ${rimY + height * 0.05}
            Z`}
        />
        <path
          className="seed-sack__outline"
          d={`M ${leftTopX} ${bodyTopY}
            C ${shape.x + width * 0.12} ${shape.y + height * 0.46}, ${leftBottomX} ${shape.y + height * 0.7}, ${leftBottomX} ${bodyBottomY - height * 0.12}
            C ${leftBottomX} ${bodyBottomY + height * 0.06}, ${shape.x + width * 0.28} ${bodyBottomY}, ${centerX} ${bodyBottomY}
            C ${shape.x + width * 0.72} ${bodyBottomY}, ${rightBottomX} ${bodyBottomY + height * 0.06}, ${rightBottomX} ${bodyBottomY - height * 0.12}
            C ${rightBottomX} ${shape.y + height * 0.7}, ${shape.x + width * 0.88} ${shape.y + height * 0.46}, ${rightTopX} ${bodyTopY}`}
        />
        {showFineDetails ? (
          <g className="seed-sack__stitches">
            {Array.from({ length: stitchCount }, (_, stitchIndex) => {
              const stitchX = shape.x + width * (0.29 + (0.42 * stitchIndex) / Math.max(stitchCount - 1, 1));
              const stitchY = rimY + height * 0.1 + (stitchIndex % 2 === 0 ? 0.8 : -0.4);

              return (
                <line
                  className="seed-sack__stitch"
                  key={`${shape.id}-stitch-${stitchIndex}`}
                  x1={stitchX - 1.6}
                  x2={stitchX + 1.6}
                  y1={stitchY}
                  y2={stitchY}
                />
              );
            })}
          </g>
        ) : null}
      </g>
      {showAreaLabel ? (
        <text className="shape-label seed-sack__area-label" pointerEvents="none" x={centerX} y={shape.y + height * 0.52}>
          {getShapeArea(shape)} m²
        </text>
      ) : null}
      {isPlanting ? (
        <g className="seed-pour">
          <circle cx={shape.x + width * 0.58} cy={shape.y + height * 0.48} r="2" />
          <circle cx={shape.x + width * 0.47} cy={shape.y + height * 0.6} r="1.8" />
          <circle cx={shape.x + width * 0.66} cy={shape.y + height * 0.63} r="1.7" />
          <circle cx={shape.x + width * 0.53} cy={shape.y + height * 0.74} r="1.6" />
        </g>
      ) : null}
    </g>
  );
}

function SeedSack({ className, isPlanting, rewardKind, shape }: SeedSackProps) {
  if (!window.SackGraphics) {
    return (
      <LegacySeedSack
        className={className}
        isPlanting={isPlanting}
        rewardKind={rewardKind}
        shape={shape}
      />
    );
  }

  const width = shape.widthCells * GRID_SIZE;
  const height = shape.heightCells * GRID_SIZE;
  const showAreaLabel =
    getShapeArea(shape) > 1 && Math.max(width, height) >= GRID_SIZE * 2;

  return (
    <g className={className} aria-hidden="true">
      <g className="seed-sack__art">
        <ParametricSackSvg
          height={height}
          kind="seed"
          plantKind={rewardKind}
          title={`${shape.widthCells} mal ${shape.heightCells} Saatgutsack`}
          width={width}
          x={shape.x}
          y={shape.y}
        />
      </g>
      {showAreaLabel ? (
        <text
          className="shape-label seed-sack__area-label"
          pointerEvents="none"
          x={shape.x + width / 2}
          y={shape.y + height * 0.8}
        >
          {getShapeArea(shape)} m²
        </text>
      ) : null}
      {isPlanting ? (
        <g className="seed-pour">
          <circle cx={shape.x + width * 0.58} cy={shape.y + height * 0.48} r="2" />
          <circle cx={shape.x + width * 0.47} cy={shape.y + height * 0.6} r="1.8" />
          <circle cx={shape.x + width * 0.66} cy={shape.y + height * 0.63} r="1.7" />
          <circle cx={shape.x + width * 0.53} cy={shape.y + height * 0.74} r="1.6" />
        </g>
      ) : null}
    </g>
  );
}

type PlantedCropsProps = {
  growthDelayMs?: number;
  isPlanting: boolean;
  rewardKind: RewardKind;
  shape: WorkspaceShape;
};

function PlantedCrops({
  growthDelayMs = SOWING_ANIMATION_DURATION_MS,
  isPlanting,
  rewardKind,
  shape,
}: PlantedCropsProps) {
  return (
    <g className={isPlanting ? "planted-crops planted-crops--growing" : "planted-crops"} aria-hidden="true">
      {getCoveredCells(shape).map((cell, index) => (
        <g
          key={`${shape.id}-plant-${cell.x}-${cell.y}`}
          transform={`translate(${workspaceBounds.x + cell.x * GRID_SIZE} ${workspaceBounds.y + cell.y * GRID_SIZE})`}
        >
          <g
            className="planted-crop"
            style={{ animationDelay: `${growthDelayMs + index * CROP_GROWTH_STAGGER_MS}ms` }}
          >
            <RewardPlant rewardKind={rewardKind} />
          </g>
        </g>
      ))}
    </g>
  );
}

function DesignedBed({
  shape,
  showAreaLabel,
}: {
  shape: WorkspaceShape;
  showAreaLabel: boolean;
}) {
  const width = shape.widthCells * GRID_SIZE;
  const height = shape.heightCells * GRID_SIZE;

  return (
    <g className="designed-bed" aria-hidden="true">
      {showAreaLabel ? (
        <text
          className="designed-bed__area-label"
          pointerEvents="none"
          x={shape.x + width / 2}
          y={shape.y + height / 2 + 4}
        >
          {getShapeArea(shape)} m²
        </text>
      ) : null}
    </g>
  );
}

function DugGardenCells({
  cells,
  dugCellKeys,
}: {
  cells: GridCell[];
  dugCellKeys: Set<string>;
}) {
  return (
    <g className="garden-dug-cells" aria-hidden="true">
      {cells.map((cell) => {
        const key = cellKey(cell);

        if (!dugCellKeys.has(key)) {
          return null;
        }

        const x = workspaceBounds.x + cell.x * GRID_SIZE;
        const y = workspaceBounds.y + cell.y * GRID_SIZE;

        return (
          <g className="garden-dug-cell" key={`dug-${key}`}>
            <rect
              className="garden-dug-cell__soil"
              height={GRID_SIZE - 2}
              width={GRID_SIZE - 2}
              x={x + 1}
              y={y + 1}
            />
            <rect
              className="garden-dug-cell__texture"
              fill="url(#earth-speckles)"
              height={GRID_SIZE - 2}
              width={GRID_SIZE - 2}
              x={x + 1}
              y={y + 1}
            />
          </g>
        );
      })}
    </g>
  );
}

const GARDEN_EARTH_PARTICLES = [
  { color: "#6f3f23", radius: 2.3, x: -17, peak: -14, land: 5 },
  { color: "#9a6036", radius: 1.8, x: -9, peak: -19, land: 7 },
  { color: "#5b351f", radius: 2, x: 10, peak: -17, land: 6 },
  { color: "#b27643", radius: 1.6, x: 18, peak: -12, land: 8 },
  { color: "#78482b", radius: 1.5, x: 4, peak: -22, land: 4 },
] as const;

function GardenDigAnimations({
  animations,
}: {
  animations: GardenDigAnimation[];
}) {
  return (
    <g className="garden-dig-animations" aria-hidden="true">
      {animations.map((animation) => {
        const x = workspaceBounds.x + animation.cell.x * GRID_SIZE;
        const y = workspaceBounds.y + animation.cell.y * GRID_SIZE;

        return (
          <g className="garden-dig-animation" key={animation.id}>
            <g
              transform={`translate(${x - 1} ${y - 72}) scale(0.55)`}
            >
              <g className="garden-dig-animation__spade">
                <GardenSpadeArtwork />
              </g>
            </g>
            {[150, 430].flatMap((burstDelay, burstIndex) =>
              GARDEN_EARTH_PARTICLES.map((particle, particleIndex) => {
                const startX = x + GRID_SIZE / 2;
                const startY = y + GRID_SIZE * 0.62;
                const animationDelay =
                  burstDelay + particleIndex * 18;

                return (
                  <circle
                    className="garden-earth-particle"
                    cx={startX}
                    cy={startY}
                    fill={particle.color}
                    key={`${animation.id}-${burstIndex}-${particleIndex}`}
                    opacity={0}
                    r={particle.radius}
                  >
                    <animate
                      attributeName="cx"
                      begin={`${animationDelay}ms`}
                      calcMode="spline"
                      dur="420ms"
                      fill="freeze"
                      keySplines="0.2 0.7 0.35 1; 0.2 0.7 0.35 1"
                      keyTimes="0; 0.55; 1"
                      values={`${startX}; ${
                        startX + particle.x * 0.58
                      }; ${startX + particle.x}`}
                    />
                    <animate
                      attributeName="cy"
                      begin={`${animationDelay}ms`}
                      calcMode="spline"
                      dur="420ms"
                      fill="freeze"
                      keySplines="0.2 0.7 0.35 1; 0.35 0 0.75 0.4"
                      keyTimes="0; 0.55; 1"
                      values={`${startY}; ${startY + particle.peak}; ${
                        startY + particle.land
                      }`}
                    />
                    <animate
                      attributeName="opacity"
                      begin={`${animationDelay}ms`}
                      dur="420ms"
                      fill="freeze"
                      keyTimes="0; 0.12; 0.72; 1"
                      values="0; 1; 1; 0"
                    />
                    <animate
                      attributeName="r"
                      begin={`${animationDelay}ms`}
                      dur="420ms"
                      fill="freeze"
                      keyTimes="0; 0.35; 1"
                      values={`${particle.radius * 0.65}; ${
                        particle.radius
                      }; ${particle.radius * 0.8}`}
                    />
                  </circle>
                );
              }),
            )}
          </g>
        );
      })}
    </g>
  );
}

function GardenCellHitAreas({
  cells,
  stage,
  onGardenCellAction,
}: {
  cells: GridCell[];
  stage: GardenMissionStage;
  onGardenCellAction: (cell: GridCell) => void;
}) {
  if (stage !== "dig") {
    return null;
  }

  return (
    <g className="garden-cell-actions garden-cell-actions--dig">
      {cells.map((cell) => (
        <rect
          aria-label={`Quadratmeter bei Spalte ${cell.x + 1}, Reihe ${
            cell.y + 1
          } umgraben`}
          className="garden-cell-action"
          height={GRID_SIZE - 4}
          key={`garden-action-${cellKey(cell)}`}
          onPointerDown={(event) => {
            if (event.button !== 0) {
              return;
            }

            event.preventDefault();
            event.stopPropagation();
            onGardenCellAction(cell);
          }}
          width={GRID_SIZE - 4}
          x={workspaceBounds.x + cell.x * GRID_SIZE + 2}
          y={workspaceBounds.y + cell.y * GRID_SIZE + 2}
        />
      ))}
    </g>
  );
}

function DesignedGardenBoundary({ shapes }: { shapes: WorkspaceShape[] }) {
  const uniqueCells = new Map<string, GridCell>();

  shapes.forEach((shape) => {
    getCoveredCells(shape).forEach((cell) => {
      uniqueCells.set(cellKey(cell), cell);
    });
  });

  const boundaryEdges = getTargetBoundaryEdges([...uniqueCells.values()]);
  const stakePoints = new Map<string, GridPoint>();

  boundaryEdges.forEach((edge) => {
    if (edge.direction === "top" || edge.direction === "bottom") {
      [
        { x: edge.start, y: edge.fixed },
        { x: edge.end, y: edge.fixed },
      ].forEach((point) => stakePoints.set(`${point.x}:${point.y}`, point));
      return;
    }

    [
      { x: edge.fixed, y: edge.start },
      { x: edge.fixed, y: edge.end },
    ].forEach((point) => stakePoints.set(`${point.x}:${point.y}`, point));
  });

  return (
    <g className="designed-garden-boundary" aria-hidden="true">
      {boundaryEdges.map((edge) => {
        const isHorizontal =
          edge.direction === "top" || edge.direction === "bottom";
        const x1 =
          workspaceBounds.x + (isHorizontal ? edge.start : edge.fixed) * GRID_SIZE;
        const x2 =
          workspaceBounds.x + (isHorizontal ? edge.end : edge.fixed) * GRID_SIZE;
        const y1 =
          workspaceBounds.y + (isHorizontal ? edge.fixed : edge.start) * GRID_SIZE;
        const y2 =
          workspaceBounds.y + (isHorizontal ? edge.fixed : edge.end) * GRID_SIZE;
        const edgeKey = `${edge.direction}-${edge.fixed}-${edge.start}-${edge.end}`;

        return (
          <g key={edgeKey}>
            <line
              className="designed-garden-boundary__shadow"
              x1={x1}
              x2={x2}
              y1={y1}
              y2={y2}
            />
            <line
              className="designed-garden-boundary__thread"
              x1={x1}
              x2={x2}
              y1={y1}
              y2={y2}
            />
          </g>
        );
      })}
      {[...stakePoints.values()].map((point) => (
        <g
          className="staking-thread-stake"
          key={`garden-boundary-stake-${point.x}-${point.y}`}
          transform={`translate(${
            workspaceBounds.x + point.x * GRID_SIZE
          } ${workspaceBounds.y + point.y * GRID_SIZE})`}
        >
          <line x1="0" x2="0" y1="-5" y2="7" />
          <circle cx="0" cy="-4" r="2.6" />
        </g>
      ))}
    </g>
  );
}

function StakingThreadPreview({
  isInvalid,
  pathPoints,
}: {
  isInvalid: boolean;
  pathPoints: GridPoint[];
}) {
  if (pathPoints.length === 0) {
    return null;
  }

  const svgPoints = pathPoints.map(
    (point) =>
      `${workspaceBounds.x + point.x * GRID_SIZE},${
        workspaceBounds.y + point.y * GRID_SIZE
      }`,
  );
  const uniqueMarkerPoints = pathPoints.filter(
    (point, pointIndex) =>
      pointIndex ===
      pathPoints.findIndex(
        (candidatePoint) =>
          candidatePoint.x === point.x && candidatePoint.y === point.y,
      ),
  );
  const className = isInvalid
    ? "staking-thread-preview staking-thread-preview--invalid"
    : "staking-thread-preview";

  return (
    <g className={className} aria-hidden="true">
      {svgPoints.length > 1 ? (
        <>
          <polyline
            className="staking-thread-preview__shadow"
            points={svgPoints.join(" ")}
          />
          <polyline
            className="staking-thread-preview__cord"
            points={svgPoints.join(" ")}
          />
        </>
      ) : null}
      {uniqueMarkerPoints.map((point, pointIndex) => (
        <g
          className="staking-thread-stake staking-thread-stake--preview"
          key={`staking-preview-point-${pointIndex}`}
          transform={`translate(${
            workspaceBounds.x + point.x * GRID_SIZE
          } ${workspaceBounds.y + point.y * GRID_SIZE})`}
        >
          <line x1="0" x2="0" y1="-7" y2="8" />
          <circle cx="0" cy="-6" r="3.2" />
        </g>
      ))}
    </g>
  );
}

export function Workspace({
  activeMission,
  activeVariant,
  canDrawCustomShape,
  canStakeGardenBed,
  coverage,
  dragState,
  drawingState,
  dugGardenCellKeys,
  gardenDigAnimations,
  gardenMissionStage,
  lastSettledShapeId,
  onGardenCellAction,
  onPointerEnd,
  onPointerMove,
  onPlaceStakingPoint,
  onStartDrawing,
  onStartDragging,
  plantedShapeIds,
  showRearrangementAnimation,
  showSimplificationGridHint,
  plantingShapeIds,
  rewardKindOverride,
  selectedShapeId,
  shapes,
  stakingPath,
  svgRef,
  targetBoundaryEdges,
  targetCells,
  targetEdgeLabels,
  threadLengthLimit,
}: WorkspaceProps) {
  const drawingRectangle = drawingState ? getDrawingRectangle(drawingState) : null;
  const rewardKind = rewardKindOverride ?? activeMission.rewardKind ?? "flower";
  const isGardenDesignMission = activeMission.kind === "garden-design";
  const gardenDesignCells = isGardenDesignMission
    ? getGardenDesignCells(shapes)
    : [];
  const boundaryShapes = shapes.filter(
    (shape) =>
      isGardenBedShape(shape) &&
      (!dragState || shape.id !== dragState.shapeId),
  );
  const gardenBoundaryEdgeLabels =
    isGardenDesignMission && gardenMissionStage === "dig"
      ? getTargetBoundaryEdges(gardenDesignCells).map(createEdgeLabel)
      : [];
  const stakingPreviewPath = getStakingPreviewPath(
    stakingPath.points,
    stakingPath.previewPoint,
  );
  const stakingPreviewRectangle =
    stakingPath.points.length >= 2 && stakingPath.previewPoint
      ? getStakedRectangle(
          stakingPath.points[0],
          stakingPath.points[1],
          stakingPath.previewPoint,
        )
      : null;
  const stakingPreviewShape: WorkspaceShape | null = stakingPreviewRectangle
    ? {
        id: "staking-preview",
        toolId: GARDEN_BED_TOOL_ID,
        kind: "rectangle",
        rewardKind,
        widthCells: stakingPreviewRectangle.widthCells,
        heightCells: stakingPreviewRectangle.heightCells,
        x:
          workspaceBounds.x +
          stakingPreviewRectangle.column * GRID_SIZE,
        y: workspaceBounds.y + stakingPreviewRectangle.row * GRID_SIZE,
      }
    : null;
  const stakingPreviewStatus =
    stakingPreviewShape && activeVariant.gardenDesign
      ? getGardenDesignStatus(activeVariant.gardenDesign, [
          ...shapes,
          stakingPreviewShape,
        ])
      : null;
  const isStakingPreviewInvalid =
    stakingPreviewShape !== null &&
    (Boolean(stakingPreviewStatus?.hasOverlap) ||
      getGardenDesignThreadLength([...shapes, stakingPreviewShape]) >
        threadLengthLimit);

  if (activeMission.kind === "fraction-read" && activeVariant.fractionRead) {
    return (
      <FractionSquareWorkspace
        fraction={activeVariant.fractionRead}
        rewardKind={rewardKind}
        showRearrangementAnimation={showRearrangementAnimation}
        showSimplificationGridHint={showSimplificationGridHint}
        variantName={activeVariant.name}
      />
    );
  }

  return (
    <section className="board-panel" aria-label="Arbeitsfläche">
      <div
        className={
          isGardenDesignMission
            ? "workspace-board workspace-board--garden-design"
            : "workspace-board"
        }
      >
        <svg
          aria-label={
            isGardenDesignMission
              ? "Planungswiese mit frei angelegten Beeten"
              : "Arbeitsfläche mit Zielfläche und beweglichen Samen-Säcken"
          }
          onPointerCancel={onPointerEnd}
          onPointerLeave={onPointerEnd}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerEnd}
          ref={svgRef}
          role="img"
          viewBox={`0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`}
        >
          <defs>
            <linearGradient id="garden-surface-gradient" x1="20" x2="500" y1="20" y2="380" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#dff6a8" />
              <stop offset="0.52" stopColor="#bce987" />
              <stop offset="1" stopColor="#91d870" />
            </linearGradient>
            <radialGradient id="garden-soft-light" cx="34%" cy="18%" r="76%">
              <stop offset="0" stopColor="#fbffd9" stopOpacity="0.68" />
              <stop offset="1" stopColor="#fbffd9" stopOpacity="0" />
            </radialGradient>
            <pattern height="97" id="grass-speckles" patternUnits="userSpaceOnUse" width="149">
              <circle className="grass-dot grass-dot--large" cx="15" cy="19" r="2.2" />
              <circle className="grass-dot" cx="42" cy="41" r="1.1" />
              <circle className="grass-dot grass-dot--light" cx="82" cy="24" r="1.6" />
              <circle className="grass-dot" cx="121" cy="65" r="1.3" />
              <circle className="grass-dot grass-dot--large" cx="138" cy="27" r="1.8" />
              <circle className="grass-dot" cx="28" cy="83" r="1.4" />
              <circle className="grass-dot grass-dot--light" cx="67" cy="90" r="1.1" />
              <circle className="grass-dot" cx="102" cy="84" r="1.5" />
            </pattern>
            <pattern height="137" id="grass-blades" patternUnits="userSpaceOnUse" width="203">
              <path className="grass-blade" d="M 18 34 C 22 26, 25 23, 30 18" />
              <path className="grass-blade grass-blade--light" d="M 95 18 C 98 24, 103 27, 108 33" />
              <path className="grass-blade" d="M 148 69 C 151 61, 157 57, 163 52" />
              <path className="grass-blade grass-blade--light" d="M 45 112 C 49 104, 55 99, 62 94" />
              <path className="grass-blade" d="M 128 128 C 132 119, 138 116, 145 111" />
            </pattern>
            <linearGradient id="earth-surface-gradient" x1="20" x2="500" y1="20" y2="380" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#c58a55" />
              <stop offset="0.54" stopColor="#9f643d" />
              <stop offset="1" stopColor="#744529" />
            </linearGradient>
            <pattern height="103" id="earth-speckles" patternUnits="userSpaceOnUse" width="151">
              <circle className="earth-dot earth-dot--light" cx="13" cy="17" r="2.1" />
              <circle className="earth-dot" cx="39" cy="45" r="1.2" />
              <circle className="earth-dot earth-dot--dark" cx="79" cy="25" r="1.8" />
              <circle className="earth-dot" cx="119" cy="64" r="1.4" />
              <circle className="earth-dot earth-dot--light" cx="136" cy="32" r="1.7" />
              <circle className="earth-dot earth-dot--dark" cx="27" cy="84" r="1.5" />
              <circle className="earth-dot" cx="71" cy="92" r="1.1" />
              <circle className="earth-dot earth-dot--light" cx="107" cy="87" r="1.6" />
            </pattern>
            <pattern height="151" id="earth-fibers" patternUnits="userSpaceOnUse" width="211">
              <path className="earth-fiber" d="M 18 34 C 32 29, 44 31, 54 26" />
              <path className="earth-fiber earth-fiber--light" d="M 95 18 C 110 22, 122 18, 135 24" />
              <path className="earth-fiber" d="M 148 69 C 160 64, 173 67, 187 60" />
              <path className="earth-fiber earth-fiber--light" d="M 45 112 C 62 108, 77 115, 91 109" />
              <path className="earth-fiber" d="M 128 128 C 139 122, 154 124, 164 119" />
            </pattern>
            <pattern
              height={GRID_SIZE}
              id="grid"
              patternUnits="userSpaceOnUse"
              width={GRID_SIZE}
              x={workspaceBounds.x}
              y={workspaceBounds.y}
            >
              <path d={`M ${GRID_SIZE} 0 L 0 0 0 ${GRID_SIZE}`} className="grid-line" fill="none" strokeWidth="1" />
            </pattern>
          </defs>
          <rect
            className="garden-bed-shadow"
            height={workspaceBounds.height}
            rx="18"
            width={workspaceBounds.width}
            x={workspaceBounds.x + 8}
            y={workspaceBounds.y + 10}
          />
          <rect
            className="workspace-surface workspace-surface--grass"
            height={workspaceBounds.height}
            rx="18"
            width={workspaceBounds.width}
            x={workspaceBounds.x}
            y={workspaceBounds.y}
          />
          <rect
            fill="url(#garden-soft-light)"
            height={workspaceBounds.height}
            rx="18"
            width={workspaceBounds.width}
            x={workspaceBounds.x}
            y={workspaceBounds.y}
          />
          <rect
            fill="url(#grass-speckles)"
            height={workspaceBounds.height}
            rx="18"
            width={workspaceBounds.width}
            x={workspaceBounds.x}
            y={workspaceBounds.y}
          />
          <rect
            fill="url(#grass-blades)"
            height={workspaceBounds.height}
            rx="18"
            width={workspaceBounds.width}
            x={workspaceBounds.x}
            y={workspaceBounds.y}
          />
          <GardenDecorations />
          <rect
            fill="url(#grid)"
            height={workspaceBounds.height}
            rx="18"
            width={workspaceBounds.width}
            x={workspaceBounds.x}
            y={workspaceBounds.y}
          />
          {targetCells.map((cell) => (
            <rect
              className={coverage.coveredTargetCells.has(cellKey(cell)) ? "target-cell target-cell--filled" : "target-cell"}
              height={GRID_SIZE}
              key={`${activeVariant.id}-${cell.x}-${cell.y}`}
              width={GRID_SIZE}
              x={workspaceBounds.x + cell.x * GRID_SIZE}
              y={workspaceBounds.y + cell.y * GRID_SIZE}
            />
          ))}
          {targetBoundaryEdges.map((edge) =>
            edge.direction === "top" || edge.direction === "bottom" ? (
              <line
                className="target-outline"
                key={`target-outline-${edge.direction}-${edge.fixed}-${edge.start}-${edge.end}`}
                x1={workspaceBounds.x + edge.start * GRID_SIZE}
                x2={workspaceBounds.x + edge.end * GRID_SIZE}
                y1={workspaceBounds.y + edge.fixed * GRID_SIZE}
                y2={workspaceBounds.y + edge.fixed * GRID_SIZE}
              />
            ) : (
              <line
                className="target-outline"
                key={`target-outline-${edge.direction}-${edge.fixed}-${edge.start}-${edge.end}`}
                x1={workspaceBounds.x + edge.fixed * GRID_SIZE}
                x2={workspaceBounds.x + edge.fixed * GRID_SIZE}
                y1={workspaceBounds.y + edge.start * GRID_SIZE}
                y2={workspaceBounds.y + edge.end * GRID_SIZE}
              />
            ),
          )}
          {targetEdgeLabels.map((label) => (
            <TargetEdgeLabel
              key={`target-edge-${label.id}`}
              orientation={label.orientation}
              text={`${label.lengthCells} m`}
              x={label.x}
              y={label.y}
            />
          ))}
          {isGardenDesignMission ? (
            <DugGardenCells
              cells={gardenDesignCells}
              dugCellKeys={dugGardenCellKeys}
            />
          ) : null}
          <rect
            className={
              canDrawCustomShape || canStakeGardenBed
                ? "drawing-hit-area drawing-hit-area--enabled"
                : "drawing-hit-area"
            }
            height={workspaceBounds.height}
            onPointerDown={
              canStakeGardenBed ? onPlaceStakingPoint : onStartDrawing
            }
            width={workspaceBounds.width}
            x={workspaceBounds.x}
            y={workspaceBounds.y}
          />
          {drawingRectangle ? (
            <rect
              className={
                isGardenDesignMission
                  ? "drawing-preview drawing-preview--garden-bed"
                  : "drawing-preview"
              }
              height={drawingRectangle.heightCells * GRID_SIZE}
              width={drawingRectangle.widthCells * GRID_SIZE}
              x={workspaceBounds.x + drawingRectangle.column * GRID_SIZE}
              y={workspaceBounds.y + drawingRectangle.row * GRID_SIZE}
            />
          ) : null}
          {shapes.map((shape, index) => {
            const isGardenBed =
              isGardenDesignMission && isGardenBedShape(shape);
            const isGardenSeedSack =
              isGardenDesignMission && isGardenSeedSackShape(shape);
            const isSeedSack = !isGardenDesignMission || isGardenSeedSack;
            const isSelected = selectedShapeId === shape.id;
            const isDragging = dragState?.shapeId === shape.id;
            const isPlanted =
              isSeedSack && plantedShapeIds.has(shape.id);
            const isPlanting =
              isSeedSack &&
              (plantingShapeIds.has(shape.id) ||
                lastSettledShapeId === shape.id) &&
              isPlanted;
            const isInteractive =
              !isGardenDesignMission ||
              (isGardenBed && gardenMissionStage === "stake") ||
              (isGardenSeedSack && gardenMissionStage === "sow");
            const toneClassName = SHAPE_TONE_CLASSES[index % SHAPE_TONE_CLASSES.length];
            const seedSackClassName = [
              "seed-sack",
              toneClassName,
              isSelected ? "seed-sack--selected" : "",
              isDragging ? "seed-sack--dragging" : "",
              isPlanted ? "seed-sack--planted" : "",
              isPlanting ? "seed-sack--planting" : "",
            ]
              .filter(Boolean)
              .join(" ");
            const hitAreaClassName = [
              "seed-hit-area",
              isDragging ? "seed-hit-area--dragging" : "",
            ]
              .filter(Boolean)
              .join(" ");
            const hitAreaInset = isGardenBed ? 7 : 0;

            return (
              <g key={shape.id}>
                {isSelected ? (
                  <rect
                    className="shape-selection"
                    height={shape.heightCells * GRID_SIZE + 8}
                    rx="7"
                    width={shape.widthCells * GRID_SIZE + 8}
                    x={shape.x - 4}
                    y={shape.y - 4}
                  />
                ) : null}
                {isGardenBed ? (
                  <DesignedBed
                    shape={shape}
                    showAreaLabel={gardenMissionStage === "stake"}
                  />
                ) : isSeedSack ? (
                  <SeedSack
                    className={seedSackClassName}
                    isPlanting={isPlanting}
                    rewardKind={shape.rewardKind}
                    shape={shape}
                  />
                ) : null}
                {isSeedSack && isPlanted ? (
                  <PlantedCrops
                    isPlanting={isPlanting}
                    rewardKind={shape.rewardKind}
                    shape={shape}
                  />
                ) : null}
                {isInteractive ? (
                  <rect
                    aria-label={`${shape.widthCells} x ${shape.heightCells} ${
                      isGardenBed ? "Beet" : "Saat-Sack"
                    } auswählen`}
                    className={hitAreaClassName}
                    height={shape.heightCells * GRID_SIZE - hitAreaInset * 2}
                    onPointerDown={(event) => onStartDragging(event, shape)}
                    width={shape.widthCells * GRID_SIZE - hitAreaInset * 2}
                    x={shape.x + hitAreaInset}
                    y={shape.y + hitAreaInset}
                  />
                ) : null}
                {isSelected &&
                isInteractive &&
                shape.kind === "rectangle" &&
                (shape.widthCells > 1 || shape.heightCells > 1) ? (
                  <>
                    <DimensionTag
                      className="shape-dimension-tag"
                      text={`${shape.widthCells} m`}
                      x={shape.x + (shape.widthCells * GRID_SIZE) / 2}
                      y={shape.y - 4}
                    />
                    <DimensionTag
                      className="shape-dimension-tag"
                      orientation="vertical"
                      text={`${shape.heightCells} m`}
                      x={shape.x + shape.widthCells * GRID_SIZE + 4}
                      y={shape.y + (shape.heightCells * GRID_SIZE) / 2}
                    />
                  </>
                ) : null}
              </g>
            );
          })}
          {isGardenDesignMission && boundaryShapes.length > 0 ? (
            <DesignedGardenBoundary shapes={boundaryShapes} />
          ) : null}
          {gardenBoundaryEdgeLabels.map((label) => (
            <TargetEdgeLabel
              key={`garden-edge-${label.id}`}
              orientation={label.orientation}
              text={`${label.lengthCells} m`}
              x={label.x}
              y={label.y}
            />
          ))}
          {isGardenDesignMission ? (
            <StakingThreadPreview
              isInvalid={isStakingPreviewInvalid}
              pathPoints={stakingPreviewPath}
            />
          ) : null}
          {isGardenDesignMission && gardenMissionStage === "dig" ? (
            <GardenDigAnimations animations={gardenDigAnimations} />
          ) : null}
          {isGardenDesignMission ? (
            <GardenCellHitAreas
              cells={gardenDesignCells}
              onGardenCellAction={onGardenCellAction}
              stage={gardenMissionStage}
            />
          ) : null}
        </svg>
      </div>
    </section>
  );
}
