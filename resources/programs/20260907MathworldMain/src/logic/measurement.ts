import type { RectangleAreaConfig } from "../types";

export type MeasurementDimension = "width" | "height";

export function getMeasurementEdgeKey(
  bedId: string,
  dimension: MeasurementDimension,
) {
  return `${bedId}:${dimension}`;
}

export function getRequiredMeasurementKeys(measurement: RectangleAreaConfig) {
  return measurement.beds.flatMap((bed) => [
    getMeasurementEdgeKey(bed.id, "width"),
    getMeasurementEdgeKey(bed.id, "height"),
  ]);
}

export function getRectangleAreaTotal(measurement: RectangleAreaConfig) {
  return measurement.beds.reduce(
    (totalArea, bed) => totalArea + bed.widthCells * bed.heightCells,
    0,
  );
}
