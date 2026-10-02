import type { RewardKind } from "../types";

export type PlantChoice = {
  kind: RewardKind;
  label: string;
};

export const plantChoices: PlantChoice[] = [
  { kind: "flower", label: "Wiesenblume" },
  { kind: "sunflower", label: "Sonnenblume" },
  { kind: "tulip", label: "Tulpe" },
  { kind: "radish", label: "Radieschen" },
  { kind: "carrot", label: "Karotte" },
  { kind: "tomato", label: "Tomate" },
];

export function isRewardKind(value: unknown): value is RewardKind {
  return plantChoices.some((plant) => plant.kind === value);
}
