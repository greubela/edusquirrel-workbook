import type { GardenDesignConfig, WorkspaceShape } from "../types";
import {
  cellKey,
  getCoveredCells,
  getShapeArea,
  getTargetBoundaryEdges,
} from "./geometry";

export const GARDEN_BED_TOOL_ID = "staking-thread";
export const GARDEN_SEED_SACK_TOOL_ID = "garden-seed-sack";

export type GardenDesignStatus = {
  bedCount: number;
  connectedBedCount: number;
  distinctSizeCount: number;
  hasOverlap: boolean;
  isConnectedLayout: boolean;
  isComplete: boolean;
  isOverThreadLimit: boolean;
  outerCornerCount: number;
  remainingThreadLength: number;
  threadLengthUsed: number;
  totalArea: number;
};

export function isGardenBedShape(shape: WorkspaceShape) {
  return shape.toolId === GARDEN_BED_TOOL_ID;
}

export function isGardenSeedSackShape(shape: WorkspaceShape) {
  return shape.toolId === GARDEN_SEED_SACK_TOOL_ID;
}

function getSizeKey(shape: WorkspaceShape) {
  const dimensions = [shape.widthCells, shape.heightCells].sort(
    (firstDimension, secondDimension) => firstDimension - secondDimension,
  );

  return dimensions.join("x");
}

export function getGardenDesignCells(shapes: WorkspaceShape[]) {
  const uniqueCells = new Map<string, ReturnType<typeof getCoveredCells>[number]>();

  shapes.filter(isGardenBedShape).forEach((shape) => {
    getCoveredCells(shape).forEach((cell) => {
      uniqueCells.set(cellKey(cell), cell);
    });
  });

  return [...uniqueCells.values()];
}

function getConnectedBedCount(shapes: WorkspaceShape[]) {
  const remainingCells = new Map(
    getGardenDesignCells(shapes).map((cell) => [cellKey(cell), cell]),
  );
  let connectedBedCount = 0;

  while (remainingCells.size > 0) {
    const firstCell = remainingCells.values().next().value;

    if (!firstCell) {
      break;
    }

    connectedBedCount += 1;
    const cellsToVisit = [firstCell];
    remainingCells.delete(cellKey(firstCell));

    while (cellsToVisit.length > 0) {
      const currentCell = cellsToVisit.pop();

      if (!currentCell) {
        continue;
      }

      [
        { x: currentCell.x - 1, y: currentCell.y },
        { x: currentCell.x + 1, y: currentCell.y },
        { x: currentCell.x, y: currentCell.y - 1 },
        { x: currentCell.x, y: currentCell.y + 1 },
      ].forEach((neighborCell) => {
        const neighborKey = cellKey(neighborCell);
        const unvisitedNeighbor = remainingCells.get(neighborKey);

        if (unvisitedNeighbor) {
          remainingCells.delete(neighborKey);
          cellsToVisit.push(unvisitedNeighbor);
        }
      });
    }
  }

  return connectedBedCount;
}

export function getGardenDesignThreadLength(shapes: WorkspaceShape[]) {
  return getTargetBoundaryEdges(getGardenDesignCells(shapes)).reduce(
    (lengthSum, edge) => lengthSum + edge.end - edge.start,
    0,
  );
}

export function getGardenDesignStatus(
  gardenDesign: GardenDesignConfig,
  shapes: WorkspaceShape[],
): GardenDesignStatus {
  const bedShapes = shapes.filter(isGardenBedShape);
  const occupiedCellCounts = new Map<string, number>();

  bedShapes.forEach((shape) => {
    getCoveredCells(shape).forEach((cell) => {
      const key = cellKey(cell);
      occupiedCellCounts.set(key, (occupiedCellCounts.get(key) ?? 0) + 1);
    });
  });

  const bedCount = bedShapes.length;
  const connectedBedCount = getConnectedBedCount(bedShapes);
  const isConnectedLayout = bedCount > 0 && connectedBedCount === 1;
  const distinctSizeCount = new Set(bedShapes.map(getSizeKey)).size;
  const hasOverlap = [...occupiedCellCounts.values()].some(
    (occupiedCount) => occupiedCount > 1,
  );
  const totalArea = bedShapes.reduce(
    (areaSum, shape) => areaSum + getShapeArea(shape),
    0,
  );
  const boundaryEdges = getTargetBoundaryEdges(getGardenDesignCells(bedShapes));
  const outerCornerCount = boundaryEdges.length;
  const threadLengthUsed = boundaryEdges.reduce(
    (lengthSum, edge) => lengthSum + edge.end - edge.start,
    0,
  );
  const remainingThreadLength = Math.max(
    gardenDesign.threadLength - threadLengthUsed,
    0,
  );
  const isOverThreadLimit = threadLengthUsed > gardenDesign.threadLength;
  const isComplete =
    !hasOverlap &&
    !isOverThreadLimit &&
    (!gardenDesign.requireConnectedLayout || isConnectedLayout) &&
    outerCornerCount >= (gardenDesign.minimumOuterCornerCount ?? 0) &&
    bedCount >= gardenDesign.minimumBedCount &&
    distinctSizeCount >= gardenDesign.minimumDistinctSizes &&
    totalArea >= gardenDesign.minimumTotalArea;

  return {
    bedCount,
    connectedBedCount,
    distinctSizeCount,
    hasOverlap,
    isConnectedLayout,
    isComplete,
    isOverThreadLimit,
    outerCornerCount,
    remainingThreadLength,
    threadLengthUsed,
    totalArea,
  };
}
