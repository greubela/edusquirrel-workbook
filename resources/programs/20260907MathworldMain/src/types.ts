export type MissionState = "active" | "locked" | "ready";

export type MissionKind =
  | "area-grid"
  | "fraction-read"
  | "rectangle-area"
  | "perimeter"
  | "park-area"
  | "market-budget"
  | "garden-design";

export type ShapeKind = "rectangle" | "triangle";

export type TriangleOrientation = "top-left" | "top-right" | "bottom-left" | "bottom-right";

export type RewardKind =
  | "flower"
  | "sunflower"
  | "tulip"
  | "radish"
  | "carrot"
  | "tomato";

export type PlantFieldCounts = Partial<Record<RewardKind, number>>;

export type FractionSupplyConfig = {
  resourceKind: "seeds" | "fertilizer";
  sackLabel: string;
  contentsLabel: string;
  bedLabel: string;
  filledBedLabel: string;
};

export type GridCell = {
  x: number;
  y: number;
};

export type FractionReadConfig = {
  numerator: number;
  denominator: number;
  rows: number;
  columns: number;
  filledCellIndexes: number[];
  designKey?: string;
  wholeLabel?: string;
  supply?: FractionSupplyConfig;
};

export type RectangleAreaBed = {
  id: string;
  label: string;
  rewardKind?: RewardKind;
  column: number;
  row: number;
  widthCells: number;
  heightCells: number;
};

export type RectangleAreaConfig = {
  beds: RectangleAreaBed[];
};

export type ParkAreaMode = "remaining" | "triangle" | "composite";

export type ParkAreaPart = {
  id: string;
  label: string;
  kind: ShapeKind;
  operation: "add" | "subtract";
  column: number;
  row: number;
  widthCells: number;
  heightCells: number;
  orientation?: TriangleOrientation;
};

export type ParkAreaConfig = {
  mode: ParkAreaMode;
  resultLabel: string;
  parts: ParkAreaPart[];
};

export type MarketMissionMode = "coverage" | "budget" | "optimization";

export type MarketProduct = {
  id: string;
  label: string;
  coverage: number;
  price: number;
};

export type MarketConfig = {
  mode: MarketMissionMode;
  requiredCoverage: number;
  budget?: number;
  products: MarketProduct[];
};

export type GardenDesignConfig = {
  minimumBedCount: number;
  minimumDistinctSizes: number;
  minimumOuterCornerCount?: number;
  minimumTotalArea: number;
  requireConnectedLayout?: boolean;
  threadLength: number;
};

export type GridPoint = {
  x: number;
  y: number;
};

export type GardenMissionStage = "stake" | "dig" | "sow";

export type GardenDigAnimation = {
  cell: GridCell;
  id: number;
};

export type SownGardenCells = Record<string, RewardKind>;

export type StakingPathState = {
  points: GridPoint[];
  previewPoint: GridPoint | null;
};

export type ToolConfig = {
  id: string;
  label: string;
  kind: ShapeKind;
  widthCells: number;
  heightCells: number;
  orientation?: TriangleOrientation;
  disabled?: boolean;
};

export type MissionVariant = {
  id: string;
  name: string;
  targetCells?: GridCell[];
  fractionRead?: FractionReadConfig;
  rectangleArea?: RectangleAreaConfig;
  parkArea?: ParkAreaConfig;
  market?: MarketConfig;
  gardenDesign?: GardenDesignConfig;
};

export type MissionConfig = {
  id: number;
  kind?: MissionKind;
  title: string;
  goal: string;
  tools: ToolConfig[];
  variants: MissionVariant[];
  allowedPlantKinds?: RewardKind[];
  allowFreeDrawing?: boolean;
  requiredToolIds?: string[];
  requireSimplification?: boolean;
  rewardKind?: RewardKind;
};

export type WorkspaceShape = {
  id: string;
  toolId: string;
  kind: ShapeKind;
  rewardKind: RewardKind;
  widthCells: number;
  heightCells: number;
  orientation?: TriangleOrientation;
  x: number;
  y: number;
};

export type DragState = {
  shapeId: string;
  offsetX: number;
  offsetY: number;
};

export type DrawingState = {
  startColumn: number;
  startRow: number;
  currentColumn: number;
  currentRow: number;
};

export type EdgeDirection = "top" | "right" | "bottom" | "left";

export type BoundaryEdge = {
  direction: EdgeDirection;
  fixed: number;
  start: number;
  end: number;
};

export type EdgeLabel = {
  id: string;
  direction: EdgeDirection;
  orientation: "horizontal" | "vertical";
  x: number;
  y: number;
  lengthCells: number;
};

export type MissionCoverage = {
  coveredTargetCells: Set<string>;
  hasOverlap: boolean;
  hasPartialTargetShape: boolean;
  hasUnusedShape: boolean;
  isComplete: boolean;
  missingRequiredToolIds: string[];
};
