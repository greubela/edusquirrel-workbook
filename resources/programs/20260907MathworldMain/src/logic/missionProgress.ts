import type { GridCell, MissionConfig, MissionCoverage, WorkspaceShape } from "../types";
import { cellKey, getCoveredCells, shapeMatchesTool } from "./geometry";

export function calculateMissionCoverage(
  activeMission: MissionConfig,
  shapes: WorkspaceShape[],
  targetCells: GridCell[],
  targetCellSet: Set<string>,
): MissionCoverage {
  const targetCounts = new Map<string, number>();
  const usedToolIds = new Set<string>();
  let hasPartialTargetShape = false;
  let hasUnusedShape = false;

  for (const shape of shapes) {
    const shapeCells = getCoveredCells(shape);
    const touchesTarget = shapeCells.some((cell) => targetCellSet.has(cellKey(cell)));

    if (!touchesTarget) {
      hasUnusedShape = true;
      continue;
    }

    const matchingTool = activeMission.tools.find((tool) => tool.id === shape.toolId);

    if (!matchingTool || shapeMatchesTool(shape, matchingTool)) {
      usedToolIds.add(shape.toolId);
    }

    for (const cell of shapeCells) {
      const key = cellKey(cell);

      if (!targetCellSet.has(key)) {
        hasPartialTargetShape = true;
        continue;
      }

      targetCounts.set(key, (targetCounts.get(key) ?? 0) + 1);
    }
  }

  const coveredTargetCells = new Set([...targetCounts].filter(([, count]) => count > 0).map(([key]) => key));
  const hasOverlap = [...targetCounts.values()].some((count) => count > 1);
  const missingRequiredToolIds = (activeMission.requiredToolIds ?? []).filter((toolId) => !usedToolIds.has(toolId));
  const isComplete =
    missingRequiredToolIds.length === 0 &&
    !hasPartialTargetShape &&
    !hasUnusedShape &&
    !hasOverlap &&
    targetCells.every((cell) => targetCounts.get(cellKey(cell)) === 1);

  return {
    coveredTargetCells,
    hasOverlap,
    hasPartialTargetShape,
    hasUnusedShape,
    isComplete,
    missingRequiredToolIds,
  };
}
