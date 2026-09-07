import {
  EDGE_LABEL_OFFSET,
  GRID_SIZE,
  MAX_INPUT_DIMENSION,
  maxWorkspaceColumns,
  maxWorkspaceRows,
  workspaceBounds,
} from "../config/workspace";
import type {
  BoundaryEdge,
  DrawingState,
  EdgeDirection,
  EdgeLabel,
  GridCell,
  ToolConfig,
  WorkspaceShape,
} from "../types";

export function cellKey(cell: GridCell) {
  return `${cell.x}:${cell.y}`;
}

export function rectangleCells(x: number, y: number, width: number, height: number): GridCell[] {
  const cells: GridCell[] = [];

  for (let cellX = 0; cellX < width; cellX += 1) {
    for (let cellY = 0; cellY < height; cellY += 1) {
      cells.push({ x: x + cellX, y: y + cellY });
    }
  }

  return cells;
}

export function mergeCells(...cellGroups: GridCell[][]): GridCell[] {
  const uniqueCells = new Map<string, GridCell>();

  for (const cellGroup of cellGroups) {
    for (const cell of cellGroup) {
      uniqueCells.set(cellKey(cell), cell);
    }
  }

  return [...uniqueCells.values()];
}

export function getShapeGridCell(shape: WorkspaceShape): GridCell {
  return {
    x: Math.round((shape.x - workspaceBounds.x) / GRID_SIZE),
    y: Math.round((shape.y - workspaceBounds.y) / GRID_SIZE),
  };
}

export function getCoveredCells(shape: WorkspaceShape): GridCell[] {
  if (shape.kind === "triangle") {
    return [getShapeGridCell(shape)];
  }

  const origin = getShapeGridCell(shape);

  return rectangleCells(origin.x, origin.y, shape.widthCells, shape.heightCells);
}

export function getShapeArea(shape: WorkspaceShape) {
  if (shape.kind === "triangle") {
    return (shape.widthCells * shape.heightCells) / 2;
  }

  return shape.widthCells * shape.heightCells;
}

export function shapeMatchesTool(shape: WorkspaceShape, tool: ToolConfig) {
  return (
    shape.kind === tool.kind &&
    shape.widthCells === tool.widthCells &&
    shape.heightCells === tool.heightCells
  );
}

export function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

export function clampShapeToWorkspace(shape: WorkspaceShape): WorkspaceShape {
  const maxColumn = maxWorkspaceColumns - shape.widthCells;
  const maxRow = maxWorkspaceRows - shape.heightCells;
  const origin = getShapeGridCell(shape);
  const column = clamp(origin.x, 0, maxColumn);
  const row = clamp(origin.y, 0, maxRow);

  return {
    ...shape,
    x: workspaceBounds.x + column * GRID_SIZE,
    y: workspaceBounds.y + row * GRID_SIZE,
  };
}

export function getResizeLimits(shape: WorkspaceShape) {
  const origin = getShapeGridCell(shape);

  return {
    maxWidthCells: Math.max(1, Math.min(MAX_INPUT_DIMENSION, maxWorkspaceColumns - origin.x)),
    maxHeightCells: Math.max(1, Math.min(MAX_INPUT_DIMENSION, maxWorkspaceRows - origin.y)),
  };
}

export function normalizeDimensionInput(value: string) {
  const digitsOnly = value.replace(/\D/g, "");

  if (!digitsOnly) {
    return "";
  }

  return String(clamp(Number.parseInt(digitsOnly, 10), 1, MAX_INPUT_DIMENSION));
}

export function parseDimensionInput(value: string) {
  if (!value) {
    return null;
  }

  return clamp(Number.parseInt(value, 10), 1, MAX_INPUT_DIMENSION);
}

export function getDrawingRectangle(drawingState: DrawingState) {
  const minColumn = Math.min(drawingState.startColumn, drawingState.currentColumn);
  const minRow = Math.min(drawingState.startRow, drawingState.currentRow);
  const maxColumn = Math.max(drawingState.startColumn, drawingState.currentColumn);
  const maxRow = Math.max(drawingState.startRow, drawingState.currentRow);

  return {
    column: minColumn,
    row: minRow,
    widthCells: maxColumn - minColumn + 1,
    heightCells: maxRow - minRow + 1,
  };
}

export function isShapeInsideWorkspace(shape: WorkspaceShape) {
  const shapeWidth = shape.widthCells * GRID_SIZE;
  const shapeHeight = shape.heightCells * GRID_SIZE;

  return (
    shape.x >= workspaceBounds.x &&
    shape.y >= workspaceBounds.y &&
    shape.x + shapeWidth <= workspaceBounds.x + workspaceBounds.width &&
    shape.y + shapeHeight <= workspaceBounds.y + workspaceBounds.height
  );
}

function addBoundaryEdge(edges: BoundaryEdge[], direction: EdgeDirection, fixed: number, start: number, end: number) {
  edges.push({
    direction,
    fixed,
    start,
    end,
  });
}

export function createEdgeLabel(edge: BoundaryEdge): EdgeLabel {
  const center = (edge.start + edge.end) / 2;
  const lengthCells = edge.end - edge.start;
  const labelOffset = EDGE_LABEL_OFFSET - 2;

  if (edge.direction === "top" || edge.direction === "bottom") {
    return {
      id: `${edge.direction}-${edge.fixed}-${edge.start}-${edge.end}`,
      direction: edge.direction,
      orientation: "horizontal",
      x: workspaceBounds.x + center * GRID_SIZE,
      y:
        workspaceBounds.y +
        edge.fixed * GRID_SIZE +
        (edge.direction === "top" ? -labelOffset : labelOffset),
      lengthCells,
    };
  }

  return {
    id: `${edge.direction}-${edge.fixed}-${edge.start}-${edge.end}`,
    direction: edge.direction,
    orientation: "vertical",
    x:
      workspaceBounds.x +
      edge.fixed * GRID_SIZE +
      (edge.direction === "left" ? -labelOffset : labelOffset),
    y: workspaceBounds.y + center * GRID_SIZE,
    lengthCells,
  };
}

export function getTargetBoundaryEdges(cells: GridCell[]): BoundaryEdge[] {
  const targetSet = new Set(cells.map(cellKey));
  const boundaryEdges: BoundaryEdge[] = [];

  for (const cell of cells) {
    if (!targetSet.has(cellKey({ x: cell.x, y: cell.y - 1 }))) {
      addBoundaryEdge(boundaryEdges, "top", cell.y, cell.x, cell.x + 1);
    }

    if (!targetSet.has(cellKey({ x: cell.x + 1, y: cell.y }))) {
      addBoundaryEdge(boundaryEdges, "right", cell.x + 1, cell.y, cell.y + 1);
    }

    if (!targetSet.has(cellKey({ x: cell.x, y: cell.y + 1 }))) {
      addBoundaryEdge(boundaryEdges, "bottom", cell.y + 1, cell.x, cell.x + 1);
    }

    if (!targetSet.has(cellKey({ x: cell.x - 1, y: cell.y }))) {
      addBoundaryEdge(boundaryEdges, "left", cell.x, cell.y, cell.y + 1);
    }
  }

  const groupedEdges = new Map<string, BoundaryEdge[]>();

  for (const edge of boundaryEdges) {
    const key = `${edge.direction}:${edge.fixed}`;
    groupedEdges.set(key, [...(groupedEdges.get(key) ?? []), edge]);
  }

  const mergedEdges: BoundaryEdge[] = [];

  for (const edges of groupedEdges.values()) {
    const sortedEdges = [...edges].sort((firstEdge, secondEdge) => firstEdge.start - secondEdge.start);

    for (const edge of sortedEdges) {
      const previousEdge = mergedEdges.at(-1);

      if (
        previousEdge &&
        previousEdge.direction === edge.direction &&
        previousEdge.fixed === edge.fixed &&
        previousEdge.end === edge.start
      ) {
        previousEdge.end = edge.end;
        continue;
      }

      mergedEdges.push({ ...edge });
    }
  }

  return mergedEdges;
}
