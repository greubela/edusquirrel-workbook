import type { ParkAreaConfig, ParkAreaPart } from "../types";

export function getParkPartArea(part: ParkAreaPart) {
  const rectangleArea = part.widthCells * part.heightCells;

  return part.kind === "triangle" ? rectangleArea / 2 : rectangleArea;
}

export function getParkAreaTotal(parkArea: ParkAreaConfig) {
  return parkArea.parts.reduce((totalArea, part) => {
    const operationFactor = part.operation === "subtract" ? -1 : 1;

    return totalArea + operationFactor * getParkPartArea(part);
  }, 0);
}

export function getParkPartFormula(part: ParkAreaPart) {
  const rectangleFormula = `${part.widthCells} × ${part.heightCells}`;

  return part.kind === "triangle"
    ? `(${rectangleFormula}) ÷ 2`
    : rectangleFormula;
}

export function getParkAreaFormula(parkArea: ParkAreaConfig) {
  return parkArea.parts
    .map((part, partIndex) => {
      const formula = getParkPartFormula(part);

      if (partIndex === 0) {
        return formula;
      }

      return `${part.operation === "subtract" ? "−" : "+"} ${formula}`;
    })
    .join(" ");
}
