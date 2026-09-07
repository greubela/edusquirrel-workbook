import { missionConfigs } from "../data/missions";
import type { MissionConfig, MissionState } from "../types";

export function chooseRandomVariantIndex(mission: MissionConfig, currentIndex?: number) {
  if (mission.variants.length <= 1) {
    return 0;
  }

  let nextIndex = Math.floor(Math.random() * mission.variants.length);

  if (currentIndex === undefined) {
    return nextIndex;
  }

  while (nextIndex === currentIndex) {
    nextIndex = Math.floor(Math.random() * mission.variants.length);
  }

  return nextIndex;
}

export function createInitialVariantIndexes() {
  return Object.fromEntries(
    missionConfigs.map((mission) => [mission.id, chooseRandomVariantIndex(mission)]),
  ) as Record<number, number>;
}

export function getMissionState(
  missionIndex: number,
  activeMissionIndex: number,
  completedMissionIds: Set<number>,
): MissionState {
  if (missionIndex === activeMissionIndex) {
    return "active";
  }

  if (missionIndex === 0 || completedMissionIds.has(missionConfigs[missionIndex - 1].id)) {
    return "ready";
  }

  return "locked";
}
