import { useEffect, useMemo, useRef, useState, type PointerEvent } from "react";
import { MissionPanel } from "./components/MissionPanel";
import { FenceMissionPanel } from "./components/FenceMissionPanel";
import { FenceToolPanel } from "./components/FenceToolPanel";
import { FenceWorkspace } from "./components/FenceWorkspace";
import { GardenDesignMissionPanel } from "./components/GardenDesignMissionPanel";
import { GardenDesignToolPanel } from "./components/GardenDesignToolPanel";
import { MeasurementMissionPanel } from "./components/MeasurementMissionPanel";
import { MeasurementToolPanel } from "./components/MeasurementToolPanel";
import { MeasurementWorkspace } from "./components/MeasurementWorkspace";
import { MarketMissionPanel } from "./components/MarketMissionPanel";
import { MarketToolPanel } from "./components/MarketToolPanel";
import { MarketWorkspace } from "./components/MarketWorkspace";
import { ParkMissionPanel } from "./components/ParkMissionPanel";
import { ParkToolPanel } from "./components/ParkToolPanel";
import { ParkWorkspace } from "./components/ParkWorkspace";
import { ToolPanel } from "./components/ToolPanel";
import { TopBar } from "./components/TopBar";
import { Workspace } from "./components/Workspace";
import {
  CROP_GROWTH_ANIMATION_DURATION_MS,
  CROP_GROWTH_STAGGER_MS,
  PLANTING_ANIMATION_BUFFER_MS,
  SOWING_ANIMATION_DURATION_MS,
} from "./config/animation";
import { GRID_SIZE, SVG_HEIGHT, SVG_WIDTH, maxWorkspaceColumns, maxWorkspaceRows, workspaceBounds } from "./config/workspace";
import type { CampaignArea } from "./data/campaign";
import { missionConfigs } from "./data/missions";
import { plantChoices } from "./data/plants";
import {
  cellKey,
  clamp,
  clampShapeToWorkspace,
  createEdgeLabel,
  getDrawingRectangle,
  getCoveredCells,
  getResizeLimits,
  getShapeArea,
  getTargetBoundaryEdges,
  parseDimensionInput,
} from "./logic/geometry";
import { getMissionState, chooseRandomVariantIndex, createInitialVariantIndexes } from "./logic/missionNavigation";
import { calculateMissionCoverage } from "./logic/missionProgress";
import {
  GARDEN_BED_TOOL_ID,
  GARDEN_SEED_SACK_TOOL_ID,
  getGardenDesignCells,
  getGardenDesignStatus,
  getGardenDesignThreadLength,
  isGardenBedShape,
  isGardenSeedSackShape,
} from "./logic/gardenDesign";
import {
  constrainStakingPoint,
  getStakedRectangle,
} from "./logic/stakingThread";
import { getBoundaryEdgeId, getPerimeterLength } from "./logic/perimeter";
import {
  getMeasurementEdgeKey,
  getRectangleAreaTotal,
  getRequiredMeasurementKeys,
  type MeasurementDimension,
} from "./logic/measurement";
import {
  getMarketTotals,
  getMinimumMarketCost,
  isMarketSelectionComplete,
  type MarketQuantities,
} from "./logic/market";
import { getParkAreaTotal } from "./logic/parkArea";
import type {
  DragState,
  DrawingState,
  GardenDigAnimation,
  GardenMissionStage,
  GridCell,
  GridPoint,
  PlantFieldCounts,
  RewardKind,
  SownGardenCells,
  StakingPathState,
  ToolConfig,
  WorkspaceShape,
} from "./types";

type FractionAnswerInput = {
  numerator: string;
  denominator: string;
};

type SimplificationAnswer = "yes" | "no";

const showDevControls =
  import.meta.env.VITE_SHOW_DEV_CONTROLS !== undefined
    ? import.meta.env.VITE_SHOW_DEV_CONTROLS === "true"
    : import.meta.env.DEV;

const RESIZABLE_TOOL_IDS = new Set([
  "free-input",
  "free-drag",
  GARDEN_SEED_SACK_TOOL_ID,
]);
const ALL_PLANT_KINDS = plantChoices.map(({ kind }) => kind);
const EMPTY_STAKING_PATH: StakingPathState = {
  points: [],
  previewPoint: null,
};

function getGreatestCommonDivisor(firstValue: number, secondValue: number) {
  let firstNumber = Math.abs(firstValue);
  let secondNumber = Math.abs(secondValue);

  while (secondNumber !== 0) {
    const remainder = firstNumber % secondNumber;
    firstNumber = secondNumber;
    secondNumber = remainder;
  }

  return firstNumber;
}

type LegacyAppProps = {
  campaignArea?: CampaignArea;
  campaignCompletedMissionIds?: number[];
  initialMissionId?: number;
  onMissionCompleted?: (missionId: number, planting: PlantFieldCounts) => void;
  onReturnToCampaign?: () => void;
  onSelectPlant?: (rewardKind: RewardKind) => void;
  rewardKindOverride?: RewardKind;
};

function LegacyApp({
  campaignArea,
  campaignCompletedMissionIds = [],
  initialMissionId = 1,
  onMissionCompleted,
  onReturnToCampaign,
  onSelectPlant,
  rewardKindOverride,
}: LegacyAppProps) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const nextShapeId = useRef(1);
  const nextGardenDigAnimationId = useRef(1);
  const reportedCompletedMissionId = useRef<number | null>(null);
  const [activeMissionIndex, setActiveMissionIndex] = useState(() => {
    const initialIndex = missionConfigs.findIndex((mission) => mission.id === initialMissionId);
    return initialIndex >= 0 ? initialIndex : 0;
  });
  const [variantIndexes, setVariantIndexes] = useState(createInitialVariantIndexes);
  const [completedMissionIds, setCompletedMissionIds] = useState<Set<number>>(
    () => new Set(campaignArea ? campaignCompletedMissionIds : []),
  );
  const [reopenedCompletedMissionId, setReopenedCompletedMissionId] = useState<number | null>(
    campaignArea && campaignCompletedMissionIds.includes(initialMissionId)
      ? initialMissionId
      : null,
  );
  const [shapes, setShapes] = useState<WorkspaceShape[]>([]);
  const [dragState, setDragState] = useState<DragState | null>(null);
  const [drawingState, setDrawingState] = useState<DrawingState | null>(null);
  const [lastSettledShapeId, setLastSettledShapeId] = useState<string | null>(null);
  const [plantingShapeIds, setPlantingShapeIds] = useState<Set<string>>(() => new Set());
  const [selectedShapeId, setSelectedShapeId] = useState<string | null>(null);
  const [customWidthCells, setCustomWidthCells] = useState("2");
  const [customHeightCells, setCustomHeightCells] = useState("2");
  const [stakingPath, setStakingPath] =
    useState<StakingPathState>(EMPTY_STAKING_PATH);
  const [stakingError, setStakingError] = useState("");
  const [gardenMissionStage, setGardenMissionStage] =
    useState<GardenMissionStage>("stake");
  const [dugGardenCellKeys, setDugGardenCellKeys] = useState<Set<string>>(
    () => new Set(),
  );
  const [gardenDigAnimations, setGardenDigAnimations] = useState<
    GardenDigAnimation[]
  >([]);
  const [placedGardenSeedSackIds, setPlacedGardenSeedSackIds] = useState<
    Set<string>
  >(() => new Set());
  const [fractionAnswer, setFractionAnswer] = useState<FractionAnswerInput>({ numerator: "", denominator: "" });
  const [simplifiedFractionAnswer, setSimplifiedFractionAnswer] = useState<FractionAnswerInput>({
    numerator: "",
    denominator: "",
  });
  const [simplificationAnswer, setSimplificationAnswer] = useState<SimplificationAnswer | null>(null);
  const [fractionTipIndex, setFractionTipIndex] = useState<number | null>(null);
  const [measurementAnswer, setMeasurementAnswer] = useState("");
  const [measuredEdges, setMeasuredEdges] = useState<Set<string>>(() => new Set());
  const [isMeasureToolActive, setIsMeasureToolActive] = useState(false);
  const [perimeterAnswer, setPerimeterAnswer] = useState("");
  const [fencedEdgeIds, setFencedEdgeIds] = useState<Set<string>>(() => new Set());
  const [isFenceToolActive, setIsFenceToolActive] = useState(false);
  const [parkAreaAnswer, setParkAreaAnswer] = useState("");
  const [isParkPlanVisible, setIsParkPlanVisible] = useState(false);
  const [marketQuantities, setMarketQuantities] = useState<MarketQuantities>({});
  const plantingTimers = useRef(new Map<string, number>());
  const gardenDigAnimationTimers = useRef(new Map<number, number>());

  const activeMission = missionConfigs[activeMissionIndex];
  const activeVariant = activeMission.variants[variantIndexes[activeMission.id] ?? 0];
  const allowedPlantKinds =
    activeMission.allowedPlantKinds ??
    (activeMission.rewardKind ? [activeMission.rewardKind] : ALL_PLANT_KINDS);
  const selectedRewardKind =
    rewardKindOverride && allowedPlantKinds.includes(rewardKindOverride)
      ? rewardKindOverride
      : allowedPlantKinds[0] ?? activeMission.rewardKind ?? "flower";
  const activeCampaignLevelIndex =
    campaignArea?.levels.findIndex((level) => level.legacyMissionId === activeMission.id) ?? -1;
  const activeCampaignLevel =
    activeCampaignLevelIndex >= 0 ? campaignArea?.levels[activeCampaignLevelIndex] ?? null : null;
  const nextCampaignLevel =
    activeCampaignLevelIndex >= 0
      ? campaignArea?.levels
          .slice(activeCampaignLevelIndex + 1)
          .find((level) => level.legacyMissionId !== undefined) ?? null
      : null;
  const targetCells = activeVariant.targetCells ?? [];
  const fractionTarget = activeVariant.fractionRead ?? null;
  const rectangleAreaTarget = activeVariant.rectangleArea ?? null;
  const parkAreaTarget = activeVariant.parkArea ?? null;
  const marketTarget = activeVariant.market ?? null;
  const gardenDesignTarget = activeVariant.gardenDesign ?? null;
  const isFractionMission = activeMission.kind === "fraction-read";
  const isMeasurementMission = activeMission.kind === "rectangle-area";
  const isPerimeterMission = activeMission.kind === "perimeter";
  const isParkAreaMission = activeMission.kind === "park-area";
  const isMarketMission = activeMission.kind === "market-budget";
  const isGardenDesignMission = activeMission.kind === "garden-design";
  const usesSeedSacks =
    !isFractionMission &&
    !isMeasurementMission &&
    !isPerimeterMission &&
    !isParkAreaMission &&
    !isMarketMission;
  const requiredMeasurementKeys = useMemo(
    () => (rectangleAreaTarget ? getRequiredMeasurementKeys(rectangleAreaTarget) : []),
    [rectangleAreaTarget],
  );
  const measuredEdgeCount = requiredMeasurementKeys.filter((edgeKey) =>
    measuredEdges.has(edgeKey),
  ).length;
  const hasAllMeasurements =
    requiredMeasurementKeys.length > 0 && measuredEdgeCount === requiredMeasurementKeys.length;
  const expectedRectangleArea = rectangleAreaTarget
    ? getRectangleAreaTotal(rectangleAreaTarget)
    : 0;
  const parsedMeasurementAnswer = Number.parseInt(measurementAnswer, 10);
  const isMeasurementTaskComplete =
    isMeasurementMission &&
    hasAllMeasurements &&
    parsedMeasurementAnswer === expectedRectangleArea;
  const targetCellSet = useMemo(() => new Set(targetCells.map(cellKey)), [targetCells]);
  const targetBoundaryEdges = useMemo(() => getTargetBoundaryEdges(targetCells), [targetCells]);
  const targetEdgeLabels = useMemo(() => targetBoundaryEdges.map(createEdgeLabel), [targetBoundaryEdges]);
  const requiredFenceEdgeIds = useMemo(
    () => targetBoundaryEdges.map(getBoundaryEdgeId),
    [targetBoundaryEdges],
  );
  const fencedEdgeCount = requiredFenceEdgeIds.filter((edgeId) =>
    fencedEdgeIds.has(edgeId),
  ).length;
  const hasAllFenceEdges =
    requiredFenceEdgeIds.length > 0 && fencedEdgeCount === requiredFenceEdgeIds.length;
  const expectedPerimeterLength = getPerimeterLength(targetBoundaryEdges);
  const isPerimeterTaskComplete =
    isPerimeterMission &&
    hasAllFenceEdges &&
    Number.parseInt(perimeterAnswer, 10) === expectedPerimeterLength;
  const expectedParkArea = parkAreaTarget ? getParkAreaTotal(parkAreaTarget) : 0;
  const isParkAreaTaskComplete =
    isParkAreaMission &&
    isParkPlanVisible &&
    Number.parseInt(parkAreaAnswer, 10) === expectedParkArea;
  const marketTotals = useMemo(
    () =>
      marketTarget
        ? getMarketTotals(marketTarget, marketQuantities)
        : { cost: 0, coverage: 0, itemCount: 0 },
    [marketQuantities, marketTarget],
  );
  const isMarketTaskComplete =
    isMarketMission &&
    marketTarget !== null &&
    isMarketSelectionComplete(marketTarget, marketTotals);
  const selectedShape = useMemo(
    () => shapes.find((shape) => shape.id === selectedShapeId) ?? null,
    [selectedShapeId, shapes],
  );
  const canResizeSelectedShape = selectedShape ? RESIZABLE_TOOL_IDS.has(selectedShape.toolId) : false;
  const selectedResizeLimits = selectedShape ? getResizeLimits(selectedShape) : null;

  const settledShapes = useMemo(
    () => (dragState ? shapes.filter((shape) => shape.id !== dragState.shapeId) : shapes),
    [dragState, shapes],
  );
  const gardenDesignShapes = useMemo(
    () => settledShapes.filter(isGardenBedShape),
    [settledShapes],
  );
  const settledGardenSeedSacks = useMemo(
    () => settledShapes.filter(isGardenSeedSackShape),
    [settledShapes],
  );

  const gardenDesignCells = useMemo(
    () => getGardenDesignCells(gardenDesignShapes),
    [gardenDesignShapes],
  );
  const gardenDesignCellKeySet = useMemo(
    () => new Set(gardenDesignCells.map(cellKey)),
    [gardenDesignCells],
  );
  const dugGardenCellCount = gardenDesignCells.filter((cell) =>
    dugGardenCellKeys.has(cellKey(cell)),
  ).length;
  const plantedGardenSeedSackIds = useMemo(() => {
    const gardenSeedCellCounts = new Map<string, number>();
    const sackCells = new Map<string, GridCell[]>();
    const placedGardenSeedSacks = settledGardenSeedSacks.filter((sack) =>
      placedGardenSeedSackIds.has(sack.id),
    );

    placedGardenSeedSacks.forEach((sack) => {
      const coveredCells = getCoveredCells(sack);
      sackCells.set(sack.id, coveredCells);

      coveredCells.forEach((cell) => {
        const key = cellKey(cell);
        gardenSeedCellCounts.set(
          key,
          (gardenSeedCellCounts.get(key) ?? 0) + 1,
        );
      });
    });

    return new Set(
      placedGardenSeedSacks
        .filter((sack) => {
          return (sackCells.get(sack.id) ?? []).every((cell) => {
            const key = cellKey(cell);
            return (
              gardenDesignCellKeySet.has(key) &&
              dugGardenCellKeys.has(key) &&
              gardenSeedCellCounts.get(key) === 1
            );
          });
        })
        .map((sack) => sack.id),
    );
  }, [
    dugGardenCellKeys,
    gardenDesignCellKeySet,
    placedGardenSeedSackIds,
    settledGardenSeedSacks,
  ]);
  const sownGardenCells = useMemo<SownGardenCells>(() => {
    const nextSownCells: SownGardenCells = {};

    settledGardenSeedSacks.forEach((sack) => {
      if (!plantedGardenSeedSackIds.has(sack.id)) {
        return;
      }

      getCoveredCells(sack).forEach((cell) => {
        nextSownCells[cellKey(cell)] = sack.rewardKind;
      });
    });

    return nextSownCells;
  }, [plantedGardenSeedSackIds, settledGardenSeedSacks]);
  const sownGardenCellCount = gardenDesignCells.filter(
    (cell) => sownGardenCells[cellKey(cell)] !== undefined,
  ).length;
  const remainingGardenDigArea = Math.max(
    gardenDesignCells.length - dugGardenCellCount,
    0,
  );
  const remainingGardenSowArea = Math.max(
    gardenDesignCells.length - sownGardenCellCount,
    0,
  );
  const unplantedGardenSeedSackCount =
    settledGardenSeedSacks.length - plantedGardenSeedSackIds.size;
  const isGardenDiggingComplete =
    gardenDesignCells.length > 0 &&
    dugGardenCellCount === gardenDesignCells.length;
  const isGardenSowingComplete =
    gardenMissionStage === "sow" &&
    gardenDesignCells.length > 0 &&
    sownGardenCellCount === gardenDesignCells.length;

  const gardenDesignStatus = useMemo(
    () =>
      gardenDesignTarget
        ? getGardenDesignStatus(gardenDesignTarget, gardenDesignShapes)
        : {
            bedCount: 0,
            connectedBedCount: 0,
            distinctSizeCount: 0,
            hasOverlap: false,
            isConnectedLayout: false,
            isComplete: false,
            isOverThreadLimit: false,
            outerCornerCount: 0,
            remainingThreadLength: 0,
            threadLengthUsed: 0,
            totalArea: 0,
          },
    [gardenDesignShapes, gardenDesignTarget],
  );

  const coverage = useMemo(
    () => calculateMissionCoverage(activeMission, settledShapes, targetCells, targetCellSet),
    [activeMission, settledShapes, targetCells, targetCellSet],
  );

  const plantedShapeIds = useMemo(() => {
    if (isGardenDesignMission) {
      return plantedGardenSeedSackIds;
    }

    const cellCounts = new Map<string, number>();
    const shapeCells = new Map<string, GridCell[]>();

    for (const shape of settledShapes) {
      const coveredCells = getCoveredCells(shape);
      shapeCells.set(shape.id, coveredCells);

      for (const cell of coveredCells) {
        const key = cellKey(cell);
        cellCounts.set(key, (cellCounts.get(key) ?? 0) + 1);
      }
    }

    return new Set(
      settledShapes
        .filter((shape) => {
          const coveredCells = shapeCells.get(shape.id) ?? [];

          return coveredCells.every((cell) => {
            const key = cellKey(cell);
            return targetCellSet.has(key) && cellCounts.get(key) === 1;
          });
        })
        .map((shape) => shape.id),
    );
  }, [
    isGardenDesignMission,
    plantedGardenSeedSackIds,
    settledShapes,
    targetCellSet,
  ]);
  const missionPlanting = useMemo<PlantFieldCounts>(() => {
    if (isGardenDesignMission) {
      return Object.values(sownGardenCells).reduce<PlantFieldCounts>(
        (fieldCounts, rewardKind) => {
          fieldCounts[rewardKind] = (fieldCounts[rewardKind] ?? 0) + 1;
          return fieldCounts;
        },
        {},
      );
    }

    if (isFractionMission) {
      return fractionTarget?.supply?.resourceKind === "seeds"
        ? { [selectedRewardKind]: fractionTarget.numerator }
        : {};
    }

    if (isMeasurementMission) {
      return rectangleAreaTarget
        ? rectangleAreaTarget.beds.reduce<PlantFieldCounts>((fieldCounts, bed) => {
            const bedRewardKind =
              bed.rewardKind ?? activeMission.rewardKind ?? "flower";
            fieldCounts[bedRewardKind] =
              (fieldCounts[bedRewardKind] ?? 0) +
              bed.widthCells * bed.heightCells;
            return fieldCounts;
          }, {})
        : {};
    }

    if (isPerimeterMission) {
      return {
        [activeMission.rewardKind ?? "flower"]: targetCells.length,
      };
    }

    if (isParkAreaMission) {
      return {
        [activeMission.rewardKind ?? "flower"]: expectedParkArea,
      };
    }

    if (isMarketMission) {
      return marketTarget
        ? {
            [activeMission.rewardKind ?? "flower"]: marketTarget.requiredCoverage,
          }
        : {};
    }

    return settledShapes.reduce<PlantFieldCounts>((fieldCounts, shape) => {
      if (!plantedShapeIds.has(shape.id)) {
        return fieldCounts;
      }

      fieldCounts[shape.rewardKind] =
        (fieldCounts[shape.rewardKind] ?? 0) + getShapeArea(shape);
      return fieldCounts;
    }, {});
  }, [
    activeMission.rewardKind,
    fractionTarget,
    isFractionMission,
    isGardenDesignMission,
    isMeasurementMission,
    isMarketMission,
    isParkAreaMission,
    isPerimeterMission,
    expectedParkArea,
    marketTarget,
    plantedShapeIds,
    rectangleAreaTarget,
    selectedRewardKind,
    settledShapes,
    sownGardenCells,
    targetCells.length,
  ]);

  const coveredArea = coverage.coveredTargetCells.size;
  const totalShapeArea = shapes.reduce((sum, shape) => sum + getShapeArea(shape), 0);
  const completedMissionCount = completedMissionIds.size;
  const totalMissionCount = missionConfigs.length;
  const totalGardenMissionCount = missionConfigs.filter(
    (mission) => mission.kind === undefined || mission.kind === "area-grid",
  ).length;
  const completedGardenMissionCount = missionConfigs.filter(
    (mission) =>
      (mission.kind === undefined || mission.kind === "area-grid") &&
      completedMissionIds.has(mission.id),
  ).length;
  const completedCampaignLevelCount =
    campaignArea?.levels.filter(
      (level) =>
        level.legacyMissionId !== undefined &&
        completedMissionIds.has(level.legacyMissionId),
    ).length ?? 0;
  const parsedFractionAnswer = {
    numerator: Number.parseInt(fractionAnswer.numerator, 10),
    denominator: Number.parseInt(fractionAnswer.denominator, 10),
  };
  const hasCompleteFractionAnswer =
    Number.isInteger(parsedFractionAnswer.numerator) &&
    Number.isInteger(parsedFractionAnswer.denominator) &&
    parsedFractionAnswer.denominator > 0;
  const fractionGreatestCommonDivisor = hasCompleteFractionAnswer
    ? getGreatestCommonDivisor(parsedFractionAnswer.numerator, parsedFractionAnswer.denominator)
    : 1;
  const canSimplifyFraction = fractionGreatestCommonDivisor > 1;
  const simplifiedFraction = canSimplifyFraction
    ? {
        numerator: parsedFractionAnswer.numerator / fractionGreatestCommonDivisor,
        denominator: parsedFractionAnswer.denominator / fractionGreatestCommonDivisor,
      }
    : null;
  const isFractionAnswerCorrect =
    Boolean(fractionTarget) &&
    parsedFractionAnswer.numerator === fractionTarget?.numerator &&
    parsedFractionAnswer.denominator === fractionTarget?.denominator;
  const parsedSimplifiedFractionAnswer = {
    numerator: Number.parseInt(simplifiedFractionAnswer.numerator, 10),
    denominator: Number.parseInt(simplifiedFractionAnswer.denominator, 10),
  };
  const isSimplifiedFractionAnswerCorrect =
    Boolean(simplifiedFraction) &&
    parsedSimplifiedFractionAnswer.numerator === simplifiedFraction?.numerator &&
    parsedSimplifiedFractionAnswer.denominator === simplifiedFraction?.denominator;
  const shouldShowSimplificationPrompt = isFractionMission && isFractionAnswerCorrect;
  const isSimplificationChoiceWrong =
    shouldShowSimplificationPrompt &&
    ((simplificationAnswer === "yes" && !canSimplifyFraction) || (simplificationAnswer === "no" && canSimplifyFraction));
  const isRequiredSimplificationComplete =
    !activeMission.requireSimplification ||
    (canSimplifyFraction
      ? simplificationAnswer === "yes" && isSimplifiedFractionAnswerCorrect
      : simplificationAnswer === "no");
  const isFractionTaskComplete = isFractionAnswerCorrect && isRequiredSimplificationComplete;
  const shouldShowTipGridHint = isFractionMission && (fractionTipIndex === 1 || fractionTipIndex === 4);
  const shouldShowRearrangementAnimation = isFractionMission && fractionTipIndex === 3;
  const shouldShowSimplificationGridHint =
    (shouldShowSimplificationPrompt && isSimplificationChoiceWrong) || shouldShowTipGridHint;
  const isMissionComplete = isFractionMission
    ? isFractionTaskComplete
    : isMeasurementMission
      ? isMeasurementTaskComplete
      : isPerimeterMission
        ? isPerimeterTaskComplete
        : isParkAreaMission
          ? isParkAreaTaskComplete
          : isMarketMission
            ? isMarketTaskComplete
            : isGardenDesignMission
              ? gardenDesignStatus.isComplete && isGardenSowingComplete
              : coverage.isComplete;
  const canAdvance = isMissionComplete || completedMissionIds.has(activeMission.id);
  const hasNextMission = campaignArea
    ? Boolean(nextCampaignLevel || onReturnToCampaign)
    : activeMissionIndex < missionConfigs.length - 1;
  const parsedCustomWidthCells = parseDimensionInput(customWidthCells);
  const parsedCustomHeightCells = parseDimensionInput(customHeightCells);
  const canSpawnCustomShape = parsedCustomWidthCells !== null && parsedCustomHeightCells !== null;

  useEffect(() => {
    if (!isMissionComplete) {
      reportedCompletedMissionId.current = null;
      return;
    }

    if (reportedCompletedMissionId.current === activeMission.id) {
      return;
    }

    reportedCompletedMissionId.current = activeMission.id;
    onMissionCompleted?.(activeMission.id, missionPlanting);

    setCompletedMissionIds((currentCompletedIds) => {
      if (currentCompletedIds.has(activeMission.id)) {
        return currentCompletedIds;
      }

      const nextCompletedIds = new Set(currentCompletedIds);
      nextCompletedIds.add(activeMission.id);
      return nextCompletedIds;
    });
  }, [activeMission.id, isMissionComplete, missionPlanting, onMissionCompleted]);

  useEffect(() => {
    setFractionAnswer({ numerator: "", denominator: "" });
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
    setFractionTipIndex(null);
  }, [activeMission.id, activeVariant.id]);

  useEffect(() => {
    setMeasurementAnswer("");
    setMeasuredEdges(new Set());
    setIsMeasureToolActive(false);
  }, [activeMission.id, activeVariant.id]);

  useEffect(() => {
    setPerimeterAnswer("");
    setFencedEdgeIds(new Set());
    setIsFenceToolActive(false);
  }, [activeMission.id, activeVariant.id]);

  useEffect(() => {
    setParkAreaAnswer("");
    setIsParkPlanVisible(false);
  }, [activeMission.id, activeVariant.id]);

  useEffect(() => {
    setMarketQuantities({});
  }, [activeMission.id, activeVariant.id]);

  useEffect(() => {
    setStakingPath(EMPTY_STAKING_PATH);
    setStakingError("");
    setGardenMissionStage("stake");
    setDugGardenCellKeys(new Set());
    setPlacedGardenSeedSackIds(new Set());
    clearGardenDigAnimations();
  }, [activeMission.id, activeVariant.id]);

  useEffect(() => {
    if (
      !usesSeedSacks ||
      !onSelectPlant ||
      rewardKindOverride === selectedRewardKind
    ) {
      return;
    }

    onSelectPlant(selectedRewardKind);
  }, [
    onSelectPlant,
    rewardKindOverride,
    selectedRewardKind,
    usesSeedSacks,
  ]);

  useEffect(() => {
    if (selectedShapeId && !selectedShape) {
      setSelectedShapeId(null);
    }
  }, [selectedShape, selectedShapeId]);

  useEffect(() => {
    return () => {
      for (const timerId of plantingTimers.current.values()) {
        window.clearTimeout(timerId);
      }

      for (const timerId of gardenDigAnimationTimers.current.values()) {
        window.clearTimeout(timerId);
      }
    };
  }, []);

  useEffect(() => {
    if (!lastSettledShapeId) {
      return;
    }

    const plantedShape = settledShapes.find((shape) => shape.id === lastSettledShapeId) ?? null;

    if (plantedShapeIds.has(lastSettledShapeId) && plantedShape) {
      triggerPlantingAnimation(plantedShape);
    }

    setLastSettledShapeId(null);
  }, [lastSettledShapeId, plantedShapeIds, settledShapes]);

  function getPlantingAnimationDuration(shape: WorkspaceShape) {
    const lastCellStagger = Math.max(getCoveredCells(shape).length - 1, 0) * CROP_GROWTH_STAGGER_MS;

    return (
      SOWING_ANIMATION_DURATION_MS +
      lastCellStagger +
      CROP_GROWTH_ANIMATION_DURATION_MS +
      PLANTING_ANIMATION_BUFFER_MS
    );
  }

  function triggerPlantingAnimation(shape: WorkspaceShape) {
    const shapeId = shape.id;
    const previousTimerId = plantingTimers.current.get(shapeId);

    if (previousTimerId) {
      window.clearTimeout(previousTimerId);
    }

    setPlantingShapeIds((currentShapeIds) => {
      const nextShapeIds = new Set(currentShapeIds);
      nextShapeIds.add(shapeId);
      return nextShapeIds;
    });

    const timerId = window.setTimeout(() => {
      setPlantingShapeIds((currentShapeIds) => {
        const nextShapeIds = new Set(currentShapeIds);
        nextShapeIds.delete(shapeId);
        return nextShapeIds;
      });
      plantingTimers.current.delete(shapeId);
    }, getPlantingAnimationDuration(shape));

    plantingTimers.current.set(shapeId, timerId);
  }

  function clearPlantingAnimation(shapeId: string) {
    const timerId = plantingTimers.current.get(shapeId);

    if (timerId) {
      window.clearTimeout(timerId);
      plantingTimers.current.delete(shapeId);
    }

    setPlantingShapeIds((currentShapeIds) => {
      if (!currentShapeIds.has(shapeId)) {
        return currentShapeIds;
      }

      const nextShapeIds = new Set(currentShapeIds);
      nextShapeIds.delete(shapeId);
      return nextShapeIds;
    });
  }

  function clearAllPlantingAnimations() {
    for (const timerId of plantingTimers.current.values()) {
      window.clearTimeout(timerId);
    }

    plantingTimers.current.clear();
    setPlantingShapeIds(new Set());
    setLastSettledShapeId(null);
  }

  function clearGardenDigAnimations() {
    for (const timerId of gardenDigAnimationTimers.current.values()) {
      window.clearTimeout(timerId);
    }

    gardenDigAnimationTimers.current.clear();
    setGardenDigAnimations([]);
  }

  function triggerGardenDigAnimation(cell: GridCell) {
    const animationId = nextGardenDigAnimationId.current++;

    setGardenDigAnimations((currentAnimations) => [
      ...currentAnimations,
      { cell, id: animationId },
    ]);

    const timerId = window.setTimeout(() => {
      setGardenDigAnimations((currentAnimations) =>
        currentAnimations.filter((animation) => animation.id !== animationId),
      );
      gardenDigAnimationTimers.current.delete(animationId);
    }, 980);

    gardenDigAnimationTimers.current.set(animationId, timerId);
  }

  function resetMeasurementTask() {
    setMeasurementAnswer("");
    setMeasuredEdges(new Set());
    setIsMeasureToolActive(false);
  }

  function measureRectangleEdge(bedId: string, dimension: MeasurementDimension) {
    if (!isMeasureToolActive) {
      return;
    }

    const edgeKey = getMeasurementEdgeKey(bedId, dimension);

    setMeasuredEdges((currentEdges) => {
      if (currentEdges.has(edgeKey)) {
        return currentEdges;
      }

      const nextEdges = new Set(currentEdges);
      nextEdges.add(edgeKey);
      return nextEdges;
    });
  }

  function resetPerimeterTask() {
    setPerimeterAnswer("");
    setFencedEdgeIds(new Set());
    setIsFenceToolActive(false);
  }

  function resetParkAreaTask() {
    setParkAreaAnswer("");
    setIsParkPlanVisible(false);
  }

  function resetMarketTask() {
    setMarketQuantities({});
  }

  function resetStakingTask() {
    setStakingPath(EMPTY_STAKING_PATH);
    setStakingError("");
  }

  function resetGardenWorkTask() {
    resetStakingTask();
    setGardenMissionStage("stake");
    setDugGardenCellKeys(new Set());
    setPlacedGardenSeedSackIds(new Set());
    clearGardenDigAnimations();
  }

  function completeGardenStaking() {
    if (
      gardenMissionStage !== "stake" ||
      !gardenDesignStatus.isComplete
    ) {
      return;
    }

    resetStakingTask();
    setSelectedShapeId(null);
    setDragState(null);
    setGardenMissionStage("dig");
  }

  function completeGardenDigging() {
    if (
      gardenMissionStage !== "dig" ||
      !isGardenDiggingComplete
    ) {
      return;
    }

    clearGardenDigAnimations();
    setGardenMissionStage("sow");
  }

  function workGardenCell(cell: GridCell) {
    const key = cellKey(cell);

    if (
      gardenMissionStage !== "dig" ||
      !gardenDesignCellKeySet.has(key) ||
      dugGardenCellKeys.has(key)
    ) {
      return;
    }

    triggerGardenDigAnimation(cell);
    setDugGardenCellKeys((currentDugCellKeys) => {
      const nextDugCellKeys = new Set(currentDugCellKeys);
      nextDugCellKeys.add(key);
      return nextDugCellKeys;
    });
  }

  function changeMarketQuantity(productId: string, delta: number) {
    setMarketQuantities((currentQuantities) => ({
      ...currentQuantities,
      [productId]: clamp((currentQuantities[productId] ?? 0) + delta, 0, 9),
    }));
  }

  function toggleFenceEdge(edgeId: string) {
    if (!isFenceToolActive) {
      return;
    }

    setFencedEdgeIds((currentEdgeIds) => {
      const nextEdgeIds = new Set(currentEdgeIds);

      if (nextEdgeIds.has(edgeId)) {
        nextEdgeIds.delete(edgeId);
      } else {
        nextEdgeIds.add(edgeId);
      }

      return nextEdgeIds;
    });
  }

  function getSvgPoint(event: PointerEvent<SVGSVGElement | SVGRectElement>) {
    const svg = svgRef.current;

    if (!svg) {
      return { x: 0, y: 0 };
    }

    const bounds = svg.getBoundingClientRect();

    return {
      x: ((event.clientX - bounds.left) / bounds.width) * SVG_WIDTH,
      y: ((event.clientY - bounds.top) / bounds.height) * SVG_HEIGHT,
    };
  }

  function pointToGridCell(point: GridCell) {
    return {
      x: clamp(Math.floor((point.x - workspaceBounds.x) / GRID_SIZE), 0, maxWorkspaceColumns - 1),
      y: clamp(Math.floor((point.y - workspaceBounds.y) / GRID_SIZE), 0, maxWorkspaceRows - 1),
    };
  }

  function pointToGridIntersection(point: GridPoint) {
    return {
      x: clamp(
        Math.round((point.x - workspaceBounds.x) / GRID_SIZE),
        0,
        maxWorkspaceColumns,
      ),
      y: clamp(
        Math.round((point.y - workspaceBounds.y) / GRID_SIZE),
        0,
        maxWorkspaceRows,
      ),
    };
  }

  function addShape(tool: ToolConfig) {
    if (tool.disabled) {
      return;
    }

    const shapeId = `shape-${nextShapeId.current++}`;

    setShapes((currentShapes) => {
      const shapeIndex = currentShapes.length;
      const maxColumn = workspaceBounds.width / GRID_SIZE - tool.widthCells;
      const maxRow = workspaceBounds.height / GRID_SIZE - tool.heightCells;
      const spawnColumn = clamp(8 + (shapeIndex % 3), 0, maxColumn);
      const spawnRow = clamp(1 + Math.floor(shapeIndex / 3), 0, maxRow);

      return [
        ...currentShapes,
        {
          id: shapeId,
          toolId: tool.id,
          kind: tool.kind,
          rewardKind: selectedRewardKind,
          widthCells: tool.widthCells,
          heightCells: tool.heightCells,
          x: workspaceBounds.x + spawnColumn * GRID_SIZE,
          y: workspaceBounds.y + spawnRow * GRID_SIZE,
        },
      ];
    });
    setSelectedShapeId(shapeId);
  }

  function addCustomShape(widthCells: number, heightCells: number, toolId = "free-input") {
    const shapeId = `shape-${nextShapeId.current++}`;

    setShapes((currentShapes) => {
      const shapeIndex = currentShapes.length;
      const maxColumn = workspaceBounds.width / GRID_SIZE - widthCells;
      const maxRow = workspaceBounds.height / GRID_SIZE - heightCells;
      const spawnColumn = clamp(8 + (shapeIndex % 3), 0, maxColumn);
      const spawnRow = clamp(1 + Math.floor(shapeIndex / 3), 0, maxRow);

      return [
        ...currentShapes,
        {
          id: shapeId,
          toolId,
          kind: "rectangle",
          rewardKind: selectedRewardKind,
          widthCells,
          heightCells,
          x: workspaceBounds.x + spawnColumn * GRID_SIZE,
          y: workspaceBounds.y + spawnRow * GRID_SIZE,
        },
      ];
    });
    setSelectedShapeId(shapeId);
  }

  function addGardenSeedSack(widthCells: number, heightCells: number) {
    if (!isGardenDesignMission || gardenMissionStage !== "sow") {
      return;
    }

    addCustomShape(widthCells, heightCells, GARDEN_SEED_SACK_TOOL_ID);
  }

  function restartMission() {
    setShapes([]);
    setDragState(null);
    setDrawingState(null);
    setSelectedShapeId(null);
    setFractionAnswer({ numerator: "", denominator: "" });
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
    setFractionTipIndex(null);
    clearAllPlantingAnimations();
    resetMeasurementTask();
    resetPerimeterTask();
    resetParkAreaTask();
    resetMarketTask();
    resetGardenWorkTask();
    setVariantIndexes((currentVariantIndexes) => ({
      ...currentVariantIndexes,
      [activeMission.id]: chooseRandomVariantIndex(activeMission, currentVariantIndexes[activeMission.id]),
    }));
  }

  function deleteSelectedShape() {
    if (!selectedShapeId) {
      return;
    }

    const shapeIdToDelete = selectedShapeId;

    clearPlantingAnimation(shapeIdToDelete);
    setShapes((currentShapes) => currentShapes.filter((shape) => shape.id !== shapeIdToDelete));
    setPlacedGardenSeedSackIds((currentShapeIds) => {
      if (!currentShapeIds.has(shapeIdToDelete)) {
        return currentShapeIds;
      }

      const nextShapeIds = new Set(currentShapeIds);
      nextShapeIds.delete(shapeIdToDelete);
      return nextShapeIds;
    });
    setDragState((currentDragState) =>
      currentDragState?.shapeId === shapeIdToDelete ? null : currentDragState,
    );
    setSelectedShapeId(null);
  }

  function updateSelectedShapeDimension(dimension: "widthCells" | "heightCells", delta: number) {
    if (!selectedShapeId) {
      return;
    }

    clearPlantingAnimation(selectedShapeId);
    setPlacedGardenSeedSackIds((currentShapeIds) => {
      if (!currentShapeIds.has(selectedShapeId)) {
        return currentShapeIds;
      }

      const nextShapeIds = new Set(currentShapeIds);
      nextShapeIds.delete(selectedShapeId);
      return nextShapeIds;
    });
    setShapes((currentShapes) =>
      currentShapes.map((shape) => {
        if (shape.id !== selectedShapeId || shape.kind !== "rectangle") {
          return shape;
        }

        if (!RESIZABLE_TOOL_IDS.has(shape.toolId)) {
          return shape;
        }

        const resizeLimits = getResizeLimits(shape);
        const nextWidthCells = dimension === "widthCells" ? shape.widthCells + delta : shape.widthCells;
        const nextHeightCells = dimension === "heightCells" ? shape.heightCells + delta : shape.heightCells;

        return clampShapeToWorkspace({
          ...shape,
          widthCells: clamp(nextWidthCells, 1, resizeLimits.maxWidthCells),
          heightCells: clamp(nextHeightCells, 1, resizeLimits.maxHeightCells),
        });
      }),
    );
  }

  function goToNextMission() {
    if (!canAdvance || !hasNextMission) {
      return;
    }

    if (campaignArea) {
      if (nextCampaignLevel?.legacyMissionId !== undefined) {
        const nextMissionIndex = missionConfigs.findIndex(
          (mission) => mission.id === nextCampaignLevel.legacyMissionId,
        );

        if (nextMissionIndex >= 0) {
          selectMission(nextMissionIndex);
        }

        return;
      }

      onReturnToCampaign?.();
      return;
    }

    setShapes([]);
    setDragState(null);
    setDrawingState(null);
    setSelectedShapeId(null);
    setReopenedCompletedMissionId(null);
    setFractionAnswer({ numerator: "", denominator: "" });
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
    setFractionTipIndex(null);
    clearAllPlantingAnimations();
    resetMeasurementTask();
    resetPerimeterTask();
    resetParkAreaTask();
    resetMarketTask();
    resetGardenWorkTask();
    setActiveMissionIndex((currentIndex) => currentIndex + 1);
  }

  function selectCampaignLevel(levelIndex: number) {
    const missionId = campaignArea?.levels[levelIndex]?.legacyMissionId;

    if (missionId === undefined) {
      return;
    }

    const missionIndex = missionConfigs.findIndex((mission) => mission.id === missionId);

    if (missionIndex >= 0) {
      selectMission(missionIndex);
    }
  }

  function goToNextMissionInDevMode() {
    setShapes([]);
    setDragState(null);
    setDrawingState(null);
    setSelectedShapeId(null);
    setReopenedCompletedMissionId(null);
    setFractionAnswer({ numerator: "", denominator: "" });
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
    setFractionTipIndex(null);
    clearAllPlantingAnimations();
    resetMeasurementTask();
    resetPerimeterTask();
    resetParkAreaTask();
    resetMarketTask();
    resetGardenWorkTask();
    setActiveMissionIndex((currentIndex) => (currentIndex + 1) % missionConfigs.length);
  }

  function goToPreviousMissionInDevMode() {
    setShapes([]);
    setDragState(null);
    setDrawingState(null);
    setSelectedShapeId(null);
    setReopenedCompletedMissionId(null);
    setFractionAnswer({ numerator: "", denominator: "" });
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
    setFractionTipIndex(null);
    clearAllPlantingAnimations();
    resetMeasurementTask();
    resetPerimeterTask();
    resetParkAreaTask();
    resetMarketTask();
    resetGardenWorkTask();
    setActiveMissionIndex((currentIndex) => (currentIndex - 1 + missionConfigs.length) % missionConfigs.length);
  }

  function selectMission(missionIndex: number) {
    if (missionIndex === activeMissionIndex) {
      return;
    }

    setShapes([]);
    setDragState(null);
    setDrawingState(null);
    setSelectedShapeId(null);
    setReopenedCompletedMissionId(completedMissionIds.has(missionConfigs[missionIndex].id) ? missionConfigs[missionIndex].id : null);
    setFractionAnswer({ numerator: "", denominator: "" });
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
    setFractionTipIndex(null);
    clearAllPlantingAnimations();
    resetMeasurementTask();
    resetPerimeterTask();
    resetParkAreaTask();
    resetMarketTask();
    resetGardenWorkTask();
    setActiveMissionIndex(missionIndex);
  }

  function startDrawing(event: PointerEvent<SVGRectElement>) {
    if (
      isGardenDesignMission ||
      !activeMission.allowFreeDrawing ||
      event.button !== 0
    ) {
      return;
    }

    const point = getSvgPoint(event);
    const cell = pointToGridCell(point);
    event.currentTarget.setPointerCapture(event.pointerId);
    setSelectedShapeId(null);
    setDrawingState({
      startColumn: cell.x,
      startRow: cell.y,
      currentColumn: cell.x,
      currentRow: cell.y,
    });
  }

  function placeStakingPoint(event: PointerEvent<SVGRectElement>) {
    if (
      !isGardenDesignMission ||
      !gardenDesignTarget ||
      gardenMissionStage !== "stake" ||
      event.button !== 0
    ) {
      return;
    }

    const point = pointToGridIntersection(getSvgPoint(event));
    const nextPoint = constrainStakingPoint(point, stakingPath.points);

    event.preventDefault();
    setSelectedShapeId(null);
    setStakingError("");

    if (stakingPath.points.length === 0) {
      setStakingPath({
        points: [nextPoint],
        previewPoint: nextPoint,
      });
      return;
    }

    if (stakingPath.points.length === 1) {
      const firstPoint = stakingPath.points[0];

      if (firstPoint.x === nextPoint.x && firstPoint.y === nextPoint.y) {
        setStakingError("Die erste Kante braucht mindestens ein Rasterfeld.");
        return;
      }

      setStakingPath({
        points: [firstPoint, nextPoint],
        previewPoint: nextPoint,
      });
      return;
    }

    const rectangle = getStakedRectangle(
      stakingPath.points[0],
      stakingPath.points[1],
      nextPoint,
    );

    if (!rectangle) {
      setStakingError("Die zweite Kante braucht mindestens ein Rasterfeld.");
      return;
    }

    const candidateShape: WorkspaceShape = {
      id: "staking-preview",
      toolId: GARDEN_BED_TOOL_ID,
      kind: "rectangle",
      rewardKind: selectedRewardKind,
      widthCells: rectangle.widthCells,
      heightCells: rectangle.heightCells,
      x: workspaceBounds.x + rectangle.column * GRID_SIZE,
      y: workspaceBounds.y + rectangle.row * GRID_SIZE,
    };
    const prospectiveThreadLength = getGardenDesignThreadLength([
      ...settledShapes,
      candidateShape,
    ]);
    const prospectiveDesignStatus = getGardenDesignStatus(
      gardenDesignTarget,
      [...settledShapes, candidateShape],
    );

    if (prospectiveDesignStatus.hasOverlap) {
      setStakingError(
        "Rechteckteile dürfen sich an den Kanten berühren, aber nicht überlappen.",
      );
      setStakingPath((currentPath) => ({
        ...currentPath,
        previewPoint: nextPoint,
      }));
      return;
    }

    if (prospectiveThreadLength > gardenDesignTarget.threadLength) {
      setStakingError(
        `Die neue Außenkante wäre ${
          prospectiveThreadLength - gardenDesignTarget.threadLength
        } m länger als dein Fadenvorrat.`,
      );
      setStakingPath((currentPath) => ({
        ...currentPath,
        previewPoint: nextPoint,
      }));
      return;
    }

    const shapeId = `shape-${nextShapeId.current++}`;

    setShapes((currentShapes) => [
      ...currentShapes,
      {
        ...candidateShape,
        id: shapeId,
      },
    ]);
    setSelectedShapeId(shapeId);
    setLastSettledShapeId(shapeId);
    setStakingPath(EMPTY_STAKING_PATH);
  }

  function startDragging(event: PointerEvent<SVGRectElement>, shape: WorkspaceShape) {
    const canDragGardenShape =
      (gardenMissionStage === "stake" && isGardenBedShape(shape)) ||
      (gardenMissionStage === "sow" && isGardenSeedSackShape(shape));

    if (
      event.button !== 0 ||
      (isGardenDesignMission && !canDragGardenShape)
    ) {
      return;
    }

    const point = getSvgPoint(event);
    event.currentTarget.setPointerCapture(event.pointerId);
    setSelectedShapeId(shape.id);
    resetStakingTask();
    clearPlantingAnimation(shape.id);
    setDragState({
      shapeId: shape.id,
      offsetX: point.x - shape.x,
      offsetY: point.y - shape.y,
    });
  }

  function moveDraggedShape(event: PointerEvent<SVGSVGElement>) {
    if (!dragState) {
      return;
    }

    const point = getSvgPoint(event);

    setShapes((currentShapes) =>
      currentShapes.map((shape) => {
        if (shape.id !== dragState.shapeId) {
          return shape;
        }

        const shapeWidth = shape.widthCells * GRID_SIZE;
        const shapeHeight = shape.heightCells * GRID_SIZE;

        return {
          ...shape,
          x: clamp(point.x - dragState.offsetX, 0, SVG_WIDTH - shapeWidth),
          y: clamp(point.y - dragState.offsetY, 0, SVG_HEIGHT - shapeHeight),
        };
      }),
    );
  }

  function updateDrawing(event: PointerEvent<SVGSVGElement>) {
    if (!drawingState) {
      return;
    }

    const point = getSvgPoint(event);
    const cell = pointToGridCell(point);
    setDrawingState((currentDrawingState) => {
      if (!currentDrawingState) {
        return currentDrawingState;
      }

      return {
        ...currentDrawingState,
        currentColumn: cell.x,
        currentRow: cell.y,
      };
    });
  }

  function updateStakingPreview(event: PointerEvent<SVGSVGElement>) {
    if (
      !isGardenDesignMission ||
      gardenMissionStage !== "stake" ||
      dragState ||
      stakingPath.points.length === 0
    ) {
      return;
    }

    const point = pointToGridIntersection(getSvgPoint(event));
    const previewPoint = constrainStakingPoint(point, stakingPath.points);

    setStakingPath((currentPath) => ({
      ...currentPath,
      previewPoint,
    }));
  }

  function stopDragging() {
    if (!dragState) {
      return;
    }

    setShapes((currentShapes) =>
      currentShapes.map((shape) => {
        if (shape.id !== dragState.shapeId) {
          return shape;
        }

        return clampShapeToWorkspace(shape);
      }),
    );
    setLastSettledShapeId(dragState.shapeId);
    const draggedShape = shapes.find((shape) => shape.id === dragState.shapeId);

    if (
      isGardenDesignMission &&
      gardenMissionStage === "sow" &&
      draggedShape &&
      isGardenSeedSackShape(draggedShape)
    ) {
      setPlacedGardenSeedSackIds((currentShapeIds) => {
        const nextShapeIds = new Set(currentShapeIds);
        nextShapeIds.add(dragState.shapeId);
        return nextShapeIds;
      });
    }

    setDragState(null);
  }

  function finishDrawing() {
    if (isGardenDesignMission || !drawingState) {
      return;
    }

    const drawingRectangle = getDrawingRectangle(drawingState);
    const shapeId = `shape-${nextShapeId.current++}`;

    setShapes((currentShapes) => [
      ...currentShapes,
      {
        id: shapeId,
        toolId: "free-drag",
        kind: "rectangle",
        rewardKind: selectedRewardKind,
        widthCells: drawingRectangle.widthCells,
        heightCells: drawingRectangle.heightCells,
        x: workspaceBounds.x + drawingRectangle.column * GRID_SIZE,
        y: workspaceBounds.y + drawingRectangle.row * GRID_SIZE,
      },
    ]);
    setSelectedShapeId(shapeId);
    setLastSettledShapeId(shapeId);
    setCustomWidthCells(String(drawingRectangle.widthCells));
    setCustomHeightCells(String(drawingRectangle.heightCells));
    setDrawingState(null);
  }

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    moveDraggedShape(event);
    updateDrawing(event);
    updateStakingPreview(event);
  }

  function handlePointerEnd() {
    stopDragging();
    finishDrawing();
  }

  function updateFractionAnswer(answer: FractionAnswerInput) {
    setFractionAnswer(answer);
    setSimplifiedFractionAnswer({ numerator: "", denominator: "" });
    setSimplificationAnswer(null);
  }

  function getFeedbackText() {
    if (isGardenDesignMission && gardenDesignTarget) {
      if (isGardenSowingComplete) {
        return `Geschafft: Alle ${gardenDesignCells.length} m² Beetfläche sind umgegraben und ausgesät.`;
      }

      if (gardenMissionStage === "dig") {
        return isGardenDiggingComplete
          ? "Die gesamte Beetfläche ist umgegraben. Jetzt kann ausgesät werden."
          : `Noch ${remainingGardenDigArea} m² müssen umgegraben werden.`;
      }

      if (gardenMissionStage === "sow") {
        if (unplantedGardenSeedSackCount > 0) {
          return "Ziehe jeden Saat-Sack vollständig auf braune, noch freie Erde.";
        }

        if (settledGardenSeedSacks.length === 0) {
          return "Bestimme eine Sackgröße und stelle den ersten Saat-Sack bereit.";
        }

        return `Noch ${remainingGardenSowArea} m² müssen ausgesät werden.`;
      }

      if (gardenDesignStatus.isComplete) {
        return "Die Form erfüllt alle Bedingungen. Schließe das Abstecken ab.";
      }

      if (
        activeMission.id === reopenedCompletedMissionId &&
        shapes.length === 0 &&
        stakingPath.points.length === 0
      ) {
        return "Dieser Beetplan ist schon abgeschlossen. Du kannst ihn hier neu entwerfen.";
      }

      if (gardenDesignStatus.hasOverlap) {
        return "Zwei Beete überlappen sich. Verschiebe oder verkleinere eines davon.";
      }

      if (stakingError) {
        return stakingError;
      }

      if (stakingPath.points.length === 1) {
        return "Setze den zweiten Punkt für die erste gerade Kante.";
      }

      if (stakingPath.points.length === 2) {
        return "Setze den dritten Punkt. Das Rechteck schließt sich automatisch rechtwinklig.";
      }

      if (gardenDesignStatus.isOverThreadLimit) {
        return `Die Außenkante ist ${
          gardenDesignStatus.threadLengthUsed - gardenDesignTarget.threadLength
        } m länger als dein Fadenvorrat.`;
      }

      if (
        gardenDesignTarget.requireConnectedLayout &&
        gardenDesignStatus.bedCount > 1 &&
        !gardenDesignStatus.isConnectedLayout
      ) {
        return "Verbinde alle Rechteckteile Kante an Kante zu einem Beet.";
      }

      if (
        gardenDesignTarget.minimumOuterCornerCount &&
        gardenDesignStatus.outerCornerCount <
          gardenDesignTarget.minimumOuterCornerCount
      ) {
        return "Setze das nächste Rechteck versetzt an, damit eine L- oder Stufenform entsteht.";
      }

      if (gardenDesignStatus.bedCount < gardenDesignTarget.minimumBedCount) {
        const missingBedCount =
          gardenDesignTarget.minimumBedCount - gardenDesignStatus.bedCount;
        return `Stecke noch ${missingBedCount} ${
          gardenDesignTarget.requireConnectedLayout
            ? missingBedCount === 1
              ? "Rechteckteil"
              : "Rechteckteile"
            : missingBedCount === 1
              ? "Beet"
              : "Beete"
        } ab.`;
      }

      if (
        gardenDesignStatus.distinctSizeCount <
        gardenDesignTarget.minimumDistinctSizes
      ) {
        const missingSizeCount =
          gardenDesignTarget.minimumDistinctSizes -
          gardenDesignStatus.distinctSizeCount;
        return `Plane noch ${missingSizeCount} ${
          missingSizeCount === 1 ? "weitere Beetgröße" : "weitere Beetgrößen"
        }.`;
      }

      if (gardenDesignStatus.totalArea < gardenDesignTarget.minimumTotalArea) {
        return `Vergrößere deinen Plan noch um mindestens ${
          gardenDesignTarget.minimumTotalArea - gardenDesignStatus.totalArea
        } m².`;
      }

      return "Setze den ersten Eckpunkt für dein nächstes Beet.";
    }

    if (isMeasurementMission) {
      if (isMeasurementTaskComplete) {
        return `Richtig gemessen und berechnet: ${expectedRectangleArea} m².`;
      }

      if (activeMission.id === reopenedCompletedMissionId) {
        return "Dieses Gewächshausbeet ist schon vorbereitet. Du kannst es erneut ausmessen.";
      }

      if (!hasAllMeasurements) {
        return `${measuredEdgeCount} von ${requiredMeasurementKeys.length} Seiten sind gemessen.`;
      }

      if (measurementAnswer) {
        return rectangleAreaTarget && rectangleAreaTarget.beds.length > 1
          ? "Berechne Länge mal Breite für jedes Beet und addiere beide Ergebnisse."
          : "Prüfe noch einmal: Fläche = Länge mal Breite.";
      }

      return "Alle Seiten sind gemessen. Berechne jetzt die Fläche.";
    }

    if (isPerimeterMission) {
      if (isPerimeterTaskComplete) {
        return `Der ganze Rand ist geschützt: ${expectedPerimeterLength} m Zaun.`;
      }

      if (activeMission.id === reopenedCompletedMissionId) {
        return "Dieses Beet war schon eingezäunt. Du kannst den Umfang erneut prüfen.";
      }

      if (!hasAllFenceEdges) {
        return `${fencedEdgeCount} von ${requiredFenceEdgeIds.length} Randabschnitten sind eingezäunt.`;
      }

      if (perimeterAnswer) {
        return "Addiere die Längen der Zaunstücke. Der Umfang wird in Metern angegeben.";
      }

      return "Der ganze Rand ist eingezäunt. Berechne jetzt den Umfang.";
    }

    if (isParkAreaMission) {
      if (isParkAreaTaskComplete) {
        return `Der Parkplan stimmt: ${expectedParkArea} m² ${parkAreaTarget?.resultLabel.toLocaleLowerCase("de-DE")}.`;
      }

      if (activeMission.id === reopenedCompletedMissionId) {
        return "Dieser Parkabschnitt ist schon geplant. Du kannst die Fläche erneut berechnen.";
      }

      if (!isParkPlanVisible) {
        return "Lege das Hilfsrechteck über den Plan, um die Seitenlängen zu sehen.";
      }

      if (parkAreaAnswer) {
        return parkAreaTarget?.mode === "remaining"
          ? "Berechne die ganze Fläche und ziehe die Aussparung ab."
          : parkAreaTarget?.mode === "triangle"
            ? "Das Dreieck ist halb so groß wie sein Hilfsrechteck."
            : "Berechne beide Teilflächen und addiere sie.";
      }

      return `Der Plan ist vorbereitet. Berechne jetzt die ${parkAreaTarget?.resultLabel.toLocaleLowerCase("de-DE") ?? "Fläche"}.`;
    }

    if (isMarketMission && marketTarget) {
      if (isMarketTaskComplete) {
        return marketTarget.mode === "optimization"
          ? `Das ist der günstigste ausreichende Einkauf: ${marketTotals.cost} €.`
          : `Der Warenkorb reicht für ${marketTotals.coverage} m² und kostet ${marketTotals.cost} €.`;
      }

      if (activeMission.id === reopenedCompletedMissionId) {
        return "Dieser Einkauf ist schon abgeschlossen. Du kannst die Sackgrößen erneut vergleichen.";
      }

      if (marketTotals.coverage < marketTarget.requiredCoverage) {
        return `Es fehlen noch ${marketTarget.requiredCoverage - marketTotals.coverage} m² Saatgut.`;
      }

      if (
        marketTarget.mode === "budget" &&
        marketTarget.budget !== undefined &&
        marketTotals.cost > marketTarget.budget
      ) {
        return `Der Einkauf ist ${marketTotals.cost - marketTarget.budget} € zu teuer.`;
      }

      if (
        marketTarget.mode === "optimization" &&
        marketTotals.cost > getMinimumMarketCost(marketTarget)
      ) {
        return "Die Menge reicht, aber eine andere Kombination ist noch günstiger.";
      }

      return "Vergleiche Reichweite und Preis der Säcke.";
    }

    if (isFractionMission) {
      if (isFractionTaskComplete) {
        return fractionTarget?.supply ? "Richtig verteilt und geprueft!" : "Richtig abgelesen!";
      }

      if (isFractionAnswerCorrect && activeMission.requireSimplification) {
        return "Richtig abgelesen. Pruefe jetzt, ob du den Bruch kuerzen kannst.";
      }

      if (activeMission.id === reopenedCompletedMissionId) {
        return "Diese Aufgabe ist schon geloest. Du kannst sie hier noch einmal ausprobieren.";
      }

      if (fractionAnswer.numerator || fractionAnswer.denominator) {
        return fractionTarget?.supply
          ? "Zaehle die versorgten Beete und alle gleich grossen Beete."
          : "Zaehle die gefaerbten Teile und alle gleich grossen Teile des Ganzen.";
      }

      return fractionTarget?.supply
        ? "Trage den Bruchteil des verteilten Sacks ein."
        : "Trage den Zaehler und den Nenner des Bruchs ein.";
    }

    if (coverage.isComplete) {
      return "Super, das Beet ist bepflanzt!";
    }

    if (activeMission.id === reopenedCompletedMissionId) {
      return "Dieses Beet ist schon bepflanzt. Du kannst es hier noch einmal ausprobieren.";
    }

    if (coverage.missingRequiredToolIds.length > 0) {
      return "Nutze jeden benötigten Samen-Sack mindestens einmal.";
    }

    if (coverage.hasUnusedShape) {
      return "Ein Samen-Sack liegt noch neben dem Beet.";
    }

    if (coverage.hasPartialTargetShape) {
      return "Ein Samen-Sack schaut über den Beetrand hinaus.";
    }

    if (coverage.hasOverlap) {
      return "Auf einem Feld liegen mehrere Samen-Säcke übereinander.";
    }

    return `${coveredArea} von ${targetCells.length} m² sind ausgesäht.`;
  }

  return (
    <main className="app-shell">
      <TopBar
        activeCampaignLevelIndex={
          activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex : undefined
        }
        campaignArea={campaignArea}
        completedMissionCount={completedMissionCount}
        completedMissionIds={completedMissionIds}
        getMissionState={(missionIndex) => getMissionState(missionIndex, activeMissionIndex, completedMissionIds)}
        missions={missionConfigs}
        onReturnToCampaign={onReturnToCampaign}
        onSelectCampaignLevel={selectCampaignLevel}
        onSelectMission={selectMission}
        totalMissionCount={totalMissionCount}
      />

      <section className="workspace-layout" aria-label="MathWorld Arbeitsbereich">
        {isGardenDesignMission && gardenDesignTarget ? (
          <GardenDesignMissionPanel
            activeMission={activeMission}
            activeVariant={activeVariant}
            design={gardenDesignTarget}
            displayMissionNumber={
              activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex + 1 : undefined
            }
            displayMissionTitle={activeCampaignLevel?.title}
            dugCellCount={dugGardenCellCount}
            feedbackText={getFeedbackText()}
            gardenMissionStage={gardenMissionStage}
            gardenProgressCopy={
              campaignArea
                ? `${completedCampaignLevelCount} von ${campaignArea.levels.length} Beetplänen sind fertiggestellt.`
                : undefined
            }
            isComplete={isMissionComplete}
            missionEyebrow={
              activeCampaignLevelIndex >= 0 && campaignArea
                ? `Beetplan ${activeCampaignLevelIndex + 1} von ${campaignArea.levels.length}`
                : undefined
            }
            sownCellCount={sownGardenCellCount}
            status={gardenDesignStatus}
            totalCellCount={gardenDesignCells.length}
          />
        ) : isMeasurementMission && rectangleAreaTarget ? (
          <MeasurementMissionPanel
            activeMission={activeMission}
            activeVariant={activeVariant}
            answer={measurementAnswer}
            displayMissionNumber={
              activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex + 1 : undefined
            }
            displayMissionTitle={activeCampaignLevel?.title}
            feedbackText={getFeedbackText()}
            gardenProgressCopy={
              campaignArea
                ? `${completedCampaignLevelCount} von ${campaignArea.levels.length} Gewächshausbeeten sind ausgemessen.`
                : undefined
            }
            isComplete={isMeasurementTaskComplete}
            measuredEdgeCount={measuredEdgeCount}
            measurement={rectangleAreaTarget}
            missionEyebrow={
              activeCampaignLevelIndex >= 0 && campaignArea
                ? `Teilbeet ${activeCampaignLevelIndex + 1} von ${campaignArea.levels.length}`
                : undefined
            }
            requiredEdgeCount={requiredMeasurementKeys.length}
          />
        ) : isPerimeterMission ? (
          <FenceMissionPanel
            activeMission={activeMission}
            activeVariant={activeVariant}
            answer={perimeterAnswer}
            displayMissionNumber={
              activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex + 1 : undefined
            }
            displayMissionTitle={activeCampaignLevel?.title}
            feedbackText={getFeedbackText()}
            fencedEdgeCount={fencedEdgeCount}
            gardenProgressCopy={
              campaignArea
                ? `${completedCampaignLevelCount} von ${campaignArea.levels.length} Zaunaufträgen sind abgeschlossen.`
                : undefined
            }
            isComplete={isPerimeterTaskComplete}
            missionEyebrow={
              activeCampaignLevelIndex >= 0 && campaignArea
                ? `Teilbeet ${activeCampaignLevelIndex + 1} von ${campaignArea.levels.length}`
                : undefined
            }
            perimeterLength={expectedPerimeterLength}
            requiredEdgeCount={requiredFenceEdgeIds.length}
          />
        ) : isParkAreaMission && parkAreaTarget ? (
          <ParkMissionPanel
            activeMission={activeMission}
            activeVariant={activeVariant}
            answer={parkAreaAnswer}
            displayMissionNumber={
              activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex + 1 : undefined
            }
            displayMissionTitle={activeCampaignLevel?.title}
            feedbackText={getFeedbackText()}
            gardenProgressCopy={
              campaignArea
                ? `${completedCampaignLevelCount} von ${campaignArea.levels.length} Parkplänen sind fertiggestellt.`
                : undefined
            }
            isComplete={isParkAreaTaskComplete}
            isPlanVisible={isParkPlanVisible}
            missionEyebrow={
              activeCampaignLevelIndex >= 0 && campaignArea
                ? `Teilfläche ${activeCampaignLevelIndex + 1} von ${campaignArea.levels.length}`
                : undefined
            }
            parkArea={parkAreaTarget}
          />
        ) : isMarketMission && marketTarget ? (
          <MarketMissionPanel
            activeMission={activeMission}
            activeVariant={activeVariant}
            displayMissionNumber={
              activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex + 1 : undefined
            }
            displayMissionTitle={activeCampaignLevel?.title}
            feedbackText={getFeedbackText()}
            gardenProgressCopy={
              campaignArea
                ? `${completedCampaignLevelCount} von ${campaignArea.levels.length} Einkäufen sind abgeschlossen.`
                : undefined
            }
            isComplete={isMarketTaskComplete}
            market={marketTarget}
            missionEyebrow={
              activeCampaignLevelIndex >= 0 && campaignArea
                ? `Einkauf ${activeCampaignLevelIndex + 1} von ${campaignArea.levels.length}`
                : undefined
            }
            totals={marketTotals}
          />
        ) : (
          <MissionPanel
            activeMission={activeMission}
            activeVariant={activeVariant}
            completedGardenMissionCount={completedGardenMissionCount}
            coverage={coverage}
            coveredArea={coveredArea}
            displayMissionNumber={
              activeCampaignLevelIndex >= 0 ? activeCampaignLevelIndex + 1 : undefined
            }
            displayMissionTitle={activeCampaignLevel?.title}
            feedbackText={getFeedbackText()}
            fractionAnswer={fractionAnswer}
            gardenProgressCopy={
              campaignArea
                ? `${completedCampaignLevelCount} von ${campaignArea.levels.length} Teilbeeten dieses Gartenbereichs sind bepflanzt.`
                : undefined
            }
            isFractionAnswerCorrect={isFractionAnswerCorrect}
            isFractionTaskComplete={isFractionTaskComplete}
            missionEyebrow={
              activeCampaignLevelIndex >= 0 && campaignArea
                ? `Teilbeet ${activeCampaignLevelIndex + 1} von ${campaignArea.levels.length}`
                : undefined
            }
            shapeCount={shapes.length}
            targetArea={targetCells.length}
            totalGardenMissionCount={totalGardenMissionCount}
            totalShapeArea={totalShapeArea}
          />
        )}

        {isMeasurementMission && rectangleAreaTarget ? (
          <MeasurementWorkspace
            isComplete={isMeasurementTaskComplete}
            isMeasureToolActive={isMeasureToolActive}
            measuredEdges={measuredEdges}
            measurement={rectangleAreaTarget}
            onMeasureEdge={measureRectangleEdge}
            rewardKind={activeMission.rewardKind ?? "flower"}
          />
        ) : isPerimeterMission ? (
          <FenceWorkspace
            boundaryEdges={targetBoundaryEdges}
            fencedEdgeIds={fencedEdgeIds}
            isComplete={isPerimeterTaskComplete}
            isFenceToolActive={isFenceToolActive}
            onToggleFenceEdge={toggleFenceEdge}
            rewardKind={activeMission.rewardKind ?? "flower"}
            targetCells={targetCells}
          />
        ) : isParkAreaMission && parkAreaTarget ? (
          <ParkWorkspace
            isComplete={isParkAreaTaskComplete}
            isPlanVisible={isParkPlanVisible}
            parkArea={parkAreaTarget}
            rewardKind={activeMission.rewardKind ?? "flower"}
          />
        ) : isMarketMission && marketTarget ? (
          <MarketWorkspace
            isComplete={isMarketTaskComplete}
            market={marketTarget}
            quantities={marketQuantities}
            rewardKind={activeMission.rewardKind ?? "flower"}
            totals={marketTotals}
          />
        ) : (
          <Workspace
            activeMission={activeMission}
            activeVariant={activeVariant}
            canDrawCustomShape={
              Boolean(activeMission.allowFreeDrawing) && !isGardenDesignMission
            }
            canStakeGardenBed={
              isGardenDesignMission &&
              gardenDesignTarget !== null &&
              gardenMissionStage === "stake"
            }
            coverage={coverage}
            dragState={dragState}
            drawingState={drawingState}
            dugGardenCellKeys={dugGardenCellKeys}
            gardenDigAnimations={gardenDigAnimations}
            gardenMissionStage={gardenMissionStage}
            lastSettledShapeId={lastSettledShapeId}
            onGardenCellAction={workGardenCell}
            onPointerEnd={handlePointerEnd}
            onPointerMove={handlePointerMove}
            onPlaceStakingPoint={placeStakingPoint}
            onStartDragging={startDragging}
            onStartDrawing={startDrawing}
            showRearrangementAnimation={shouldShowRearrangementAnimation}
            showSimplificationGridHint={shouldShowSimplificationGridHint}
            plantedShapeIds={plantedShapeIds}
            plantingShapeIds={plantingShapeIds}
            rewardKindOverride={selectedRewardKind}
            selectedShapeId={selectedShapeId}
            shapes={shapes}
            stakingPath={stakingPath}
            svgRef={svgRef}
            targetBoundaryEdges={targetBoundaryEdges}
            targetCells={targetCells}
            targetEdgeLabels={targetEdgeLabels}
            threadLengthLimit={gardenDesignTarget?.threadLength ?? 0}
          />
        )}

        {isGardenDesignMission && gardenDesignTarget ? (
          <GardenDesignToolPanel
            allowedPlantKinds={allowedPlantKinds}
            canAdvance={canAdvance}
            canCompleteDigging={isGardenDiggingComplete}
            canSpawnCustomShape={canSpawnCustomShape}
            customHeightCells={customHeightCells}
            customWidthCells={customWidthCells}
            design={gardenDesignTarget}
            dugCellCount={dugGardenCellCount}
            gardenMissionStage={gardenMissionStage}
            hasNextMission={hasNextMission}
            onAddSeedSack={addGardenSeedSack}
            onCancelStaking={resetStakingTask}
            onCompleteDigging={completeGardenDigging}
            onCompleteStaking={completeGardenStaking}
            onDeleteSelectedShape={deleteSelectedShape}
            onGoToNextMission={goToNextMission}
            onGoToNextMissionInDevMode={goToNextMissionInDevMode}
            onGoToPreviousMissionInDevMode={goToPreviousMissionInDevMode}
            onRestartMission={restartMission}
            onSelectPlant={onSelectPlant}
            onCustomHeightChange={setCustomHeightCells}
            onCustomWidthChange={setCustomWidthCells}
            parsedCustomHeightCells={parsedCustomHeightCells}
            parsedCustomWidthCells={parsedCustomWidthCells}
            primaryActionLabel={
              campaignArea
                ? nextCampaignLevel
                  ? "Nächster Beetplan"
                  : "Zurück zum Garten"
                : undefined
            }
            selectedPlantKind={selectedRewardKind}
            selectedShape={selectedShape}
            showDevControls={showDevControls && !campaignArea}
            showPlantPicker={usesSeedSacks && onSelectPlant !== undefined}
            sownCellCount={sownGardenCellCount}
            stakingPointCount={stakingPath.points.length}
            status={gardenDesignStatus}
            totalCellCount={gardenDesignCells.length}
          />
        ) : isMeasurementMission && rectangleAreaTarget ? (
          <MeasurementToolPanel
            activeMissionId={activeMission.id}
            answer={measurementAnswer}
            canAdvance={canAdvance}
            hasNextMission={hasNextMission}
            isComplete={isMeasurementTaskComplete}
            isMeasureToolActive={isMeasureToolActive}
            measuredEdges={measuredEdges}
            measurement={rectangleAreaTarget}
            onAnswerChange={setMeasurementAnswer}
            onGoToNextMission={goToNextMission}
            onGoToNextMissionInDevMode={goToNextMissionInDevMode}
            onGoToPreviousMissionInDevMode={goToPreviousMissionInDevMode}
            onRestartMission={restartMission}
            onToggleMeasureTool={() =>
              setIsMeasureToolActive((currentIsActive) => !currentIsActive)
            }
            primaryActionLabel={
              campaignArea
                ? nextCampaignLevel
                  ? "Nächstes Teilbeet"
                  : "Zurück zum Garten"
                : undefined
            }
            showDevControls={showDevControls && !campaignArea}
            variantId={activeVariant.id}
          />
        ) : isPerimeterMission ? (
          <FenceToolPanel
            activeMissionId={activeMission.id}
            answer={perimeterAnswer}
            boundaryEdges={targetBoundaryEdges}
            canAdvance={canAdvance}
            fencedEdgeIds={fencedEdgeIds}
            hasNextMission={hasNextMission}
            isComplete={isPerimeterTaskComplete}
            isFenceToolActive={isFenceToolActive}
            onAnswerChange={setPerimeterAnswer}
            onGoToNextMission={goToNextMission}
            onGoToNextMissionInDevMode={goToNextMissionInDevMode}
            onGoToPreviousMissionInDevMode={goToPreviousMissionInDevMode}
            onRestartMission={restartMission}
            onToggleFenceTool={() =>
              setIsFenceToolActive((currentIsActive) => !currentIsActive)
            }
            primaryActionLabel={
              campaignArea
                ? nextCampaignLevel
                  ? "Nächstes Teilbeet"
                  : "Zurück zum Garten"
                : undefined
            }
            showDevControls={showDevControls && !campaignArea}
            variantId={activeVariant.id}
          />
        ) : isParkAreaMission && parkAreaTarget ? (
          <ParkToolPanel
            activeMissionId={activeMission.id}
            answer={parkAreaAnswer}
            canAdvance={canAdvance}
            hasNextMission={hasNextMission}
            isComplete={isParkAreaTaskComplete}
            isPlanVisible={isParkPlanVisible}
            onAnswerChange={setParkAreaAnswer}
            onGoToNextMission={goToNextMission}
            onGoToNextMissionInDevMode={goToNextMissionInDevMode}
            onGoToPreviousMissionInDevMode={goToPreviousMissionInDevMode}
            onRestartMission={restartMission}
            onTogglePlan={() =>
              setIsParkPlanVisible((currentIsVisible) => !currentIsVisible)
            }
            parkArea={parkAreaTarget}
            primaryActionLabel={
              campaignArea
                ? nextCampaignLevel
                  ? "Nächster Parkplan"
                  : "Zurück zum Garten"
                : undefined
            }
            showDevControls={showDevControls && !campaignArea}
            variantId={activeVariant.id}
          />
        ) : isMarketMission && marketTarget ? (
          <MarketToolPanel
            canAdvance={canAdvance}
            hasNextMission={hasNextMission}
            isComplete={isMarketTaskComplete}
            market={marketTarget}
            onChangeQuantity={changeMarketQuantity}
            onClearBasket={resetMarketTask}
            onGoToNextMission={goToNextMission}
            onGoToNextMissionInDevMode={goToNextMissionInDevMode}
            onGoToPreviousMissionInDevMode={goToPreviousMissionInDevMode}
            primaryActionLabel={
              campaignArea
                ? nextCampaignLevel
                  ? "Nächster Einkauf"
                  : "Zurück zum Garten"
                : undefined
            }
            quantities={marketQuantities}
            showDevControls={showDevControls && !campaignArea}
            totals={marketTotals}
          />
        ) : (
          <ToolPanel
          activeMission={activeMission}
          allowedPlantKinds={allowedPlantKinds}
          canAdvance={canAdvance}
          canSpawnCustomShape={canSpawnCustomShape}
          customHeightCells={customHeightCells}
          customWidthCells={customWidthCells}
          fractionAnswer={fractionAnswer}
          fractionTipIndex={fractionTipIndex}
          fractionTarget={fractionTarget}
          canSimplifyFraction={canSimplifyFraction}
          hasNextMission={hasNextMission}
          onAddCustomShape={addCustomShape}
          onAddShape={addShape}
          onCustomHeightChange={setCustomHeightCells}
          onCustomWidthChange={setCustomWidthCells}
          onDeleteSelectedShape={deleteSelectedShape}
          onFractionAnswerChange={updateFractionAnswer}
          onFractionTipIndexChange={setFractionTipIndex}
          onGoToNextMission={goToNextMission}
          onGoToNextMissionInDevMode={goToNextMissionInDevMode}
          onGoToPreviousMissionInDevMode={goToPreviousMissionInDevMode}
          onRestartMission={restartMission}
          onSelectPlant={onSelectPlant}
          onUpdateSelectedShapeDimension={updateSelectedShapeDimension}
          parsedCustomHeightCells={parsedCustomHeightCells}
          parsedCustomWidthCells={parsedCustomWidthCells}
          primaryActionLabel={
            campaignArea
              ? nextCampaignLevel
                ? "Nächstes Teilbeet"
                : "Zurück zum Garten"
              : undefined
          }
          selectedPlantKind={selectedRewardKind}
          canResizeSelectedShape={canResizeSelectedShape}
          selectedResizeLimits={selectedResizeLimits}
          selectedShape={selectedShape}
          shouldShowSimplificationPrompt={shouldShowSimplificationPrompt}
          showDevControls={showDevControls && !campaignArea}
          simplifiedFractionAnswer={simplifiedFractionAnswer}
          simplifiedFraction={simplifiedFraction}
          simplificationAnswer={simplificationAnswer}
          showPlantPicker={usesSeedSacks && onSelectPlant !== undefined}
          onSimplifiedFractionAnswerChange={setSimplifiedFractionAnswer}
          onSimplificationAnswerChange={setSimplificationAnswer}
          />
        )}
      </section>
    </main>
  );
}

export default LegacyApp;
