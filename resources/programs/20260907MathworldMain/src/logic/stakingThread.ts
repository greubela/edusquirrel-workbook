import type { GridPoint } from "../types";

export type StakedRectangle = {
  column: number;
  heightCells: number;
  pathPoints: GridPoint[];
  perimeter: number;
  row: number;
  widthCells: number;
};

export function getRectangleThreadLength(widthCells: number, heightCells: number) {
  return 2 * (widthCells + heightCells);
}

export function constrainStakingPoint(
  point: GridPoint,
  committedPoints: GridPoint[],
): GridPoint {
  const firstPoint = committedPoints[0];

  if (!firstPoint) {
    return point;
  }

  if (committedPoints.length === 1) {
    const horizontalDistance = Math.abs(point.x - firstPoint.x);
    const verticalDistance = Math.abs(point.y - firstPoint.y);

    return horizontalDistance >= verticalDistance
      ? { x: point.x, y: firstPoint.y }
      : { x: firstPoint.x, y: point.y };
  }

  const secondPoint = committedPoints[1];
  const firstEdgeIsHorizontal = firstPoint.y === secondPoint.y;

  return firstEdgeIsHorizontal
    ? { x: secondPoint.x, y: point.y }
    : { x: point.x, y: secondPoint.y };
}

export function getStakedRectangle(
  firstPoint: GridPoint,
  secondPoint: GridPoint,
  thirdPoint: GridPoint,
): StakedRectangle | null {
  const firstEdgeIsHorizontal =
    firstPoint.y === secondPoint.y && firstPoint.x !== secondPoint.x;
  const firstEdgeIsVertical =
    firstPoint.x === secondPoint.x && firstPoint.y !== secondPoint.y;

  if (!firstEdgeIsHorizontal && !firstEdgeIsVertical) {
    return null;
  }

  const thirdEdgeIsValid = firstEdgeIsHorizontal
    ? secondPoint.x === thirdPoint.x && secondPoint.y !== thirdPoint.y
    : secondPoint.y === thirdPoint.y && secondPoint.x !== thirdPoint.x;

  if (!thirdEdgeIsValid) {
    return null;
  }

  const fourthPoint = firstEdgeIsHorizontal
    ? { x: firstPoint.x, y: thirdPoint.y }
    : { x: thirdPoint.x, y: firstPoint.y };
  const widthCells =
    Math.abs(secondPoint.x - firstPoint.x) ||
    Math.abs(thirdPoint.x - secondPoint.x);
  const heightCells =
    Math.abs(thirdPoint.y - secondPoint.y) ||
    Math.abs(secondPoint.y - firstPoint.y);

  return {
    column: Math.min(firstPoint.x, secondPoint.x, thirdPoint.x, fourthPoint.x),
    heightCells,
    pathPoints: [firstPoint, secondPoint, thirdPoint, fourthPoint, firstPoint],
    perimeter: getRectangleThreadLength(widthCells, heightCells),
    row: Math.min(firstPoint.y, secondPoint.y, thirdPoint.y, fourthPoint.y),
    widthCells,
  };
}

export function getStakingPreviewPath(
  committedPoints: GridPoint[],
  previewPoint: GridPoint | null,
) {
  if (!previewPoint) {
    return committedPoints;
  }

  if (committedPoints.length < 2) {
    return [...committedPoints, previewPoint];
  }

  const rectangle = getStakedRectangle(
    committedPoints[0],
    committedPoints[1],
    previewPoint,
  );

  return rectangle?.pathPoints ?? committedPoints;
}
