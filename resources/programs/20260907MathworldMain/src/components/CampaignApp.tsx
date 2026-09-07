import { useMemo, useState, type CSSProperties, type KeyboardEvent } from "react";
import { campaignAreas, type CampaignArea } from "../data/campaign";
import { plantChoices } from "../data/plants";
import type { PlantFieldCounts, RewardKind } from "../types";
import { ParametricSack } from "./ParametricSack";
import { PlantGraphic, PlantIcon } from "./PlantGraphic";

type CampaignAppProps = {
  completedMissionIds: number[];
  missionPlantings: Record<number, PlantFieldCounts>;
  onOpenMission: (missionId: number, areaId: string) => void;
  onResetProgress: () => void;
  onSelectArea: (areaId: string) => void;
  selectedAreaId: string;
};

type CampaignAreaState = "complete" | "ready" | "locked" | "planned";

const areaStateLabels: Record<CampaignAreaState, string> = {
  complete: "Beet begonnen",
  ready: "Bereit",
  locked: "Noch gesperrt",
  planned: "In Planung",
};

function getCompletedLevelCount(area: CampaignArea, completedMissionIds: number[]) {
  return area.levels.filter(
    (level) => level.legacyMissionId !== undefined && completedMissionIds.includes(level.legacyMissionId),
  ).length;
}

function getDominantPlantKind(
  area: CampaignArea,
  completedMissionIds: number[],
  missionPlantings: Record<number, PlantFieldCounts>,
) {
  const fieldCounts = new Map<RewardKind, number>();

  area.levels.forEach((level) => {
    const missionId = level.legacyMissionId;
    if (missionId === undefined || !completedMissionIds.includes(missionId)) {
      return;
    }

    const planting = missionPlantings[missionId];
    const plantedFields = plantChoices.reduce(
      (fieldTotal, { kind }) => fieldTotal + (planting?.[kind] ?? 0),
      0,
    );

    if (plantedFields === 0) {
      fieldCounts.set("flower", (fieldCounts.get("flower") ?? 0) + 1);
      return;
    }

    plantChoices.forEach(({ kind }) => {
      const fieldCount = planting?.[kind] ?? 0;

      if (fieldCount > 0) {
        fieldCounts.set(kind, (fieldCounts.get(kind) ?? 0) + fieldCount);
      }
    });
  });

  return [...fieldCounts.entries()].sort(
    ([, firstFieldCount], [, secondFieldCount]) =>
      secondFieldCount - firstFieldCount,
  )[0]?.[0] ?? null;
}

function getPreviewPlantKinds(planting: PlantFieldCounts | undefined, cellCount: number) {
  const weightedPlants = plantChoices
    .map(({ kind }) => ({ kind, weight: planting?.[kind] ?? 0 }))
    .filter(({ weight }) => weight > 0)
    .sort((firstPlant, secondPlant) => secondPlant.weight - firstPlant.weight);

  if (weightedPlants.length === 0) {
    return Array.from({ length: cellCount }, () => "flower" as RewardKind);
  }

  if (weightedPlants.length >= cellCount) {
    return weightedPlants.slice(0, cellCount).map(({ kind }) => kind);
  }

  const plantKinds = weightedPlants.map(({ kind }) => kind);
  const remainingCellCount = cellCount - plantKinds.length;
  const totalWeight = weightedPlants.reduce((sum, { weight }) => sum + weight, 0);

  for (let cellIndex = 0; cellIndex < remainingCellCount; cellIndex += 1) {
    const targetWeight = ((cellIndex + 0.5) / remainingCellCount) * totalWeight;
    let accumulatedWeight = 0;
    let selectedKind = weightedPlants[weightedPlants.length - 1].kind;

    for (const plant of weightedPlants) {
      accumulatedWeight += plant.weight;

      if (targetWeight <= accumulatedWeight) {
        selectedKind = plant.kind;
        break;
      }
    }

    plantKinds.push(selectedKind);
  }

  return plantKinds;
}

function isAreaComplete(area: CampaignArea, completedMissionIds: number[]) {
  return getCompletedLevelCount(area, completedMissionIds) > 0;
}

function getAreaState(area: CampaignArea, completedMissionIds: number[]): CampaignAreaState {
  if (area.availability === "planned") {
    return "planned";
  }

  if (isAreaComplete(area, completedMissionIds)) {
    return "complete";
  }

  const playableAreas = campaignAreas.filter((candidate) => candidate.availability === "playable");
  const playableIndex = playableAreas.findIndex((candidate) => candidate.id === area.id);

  if (playableIndex === 0 || isAreaComplete(playableAreas[playableIndex - 1], completedMissionIds)) {
    return "ready";
  }

  return "locked";
}

function getUnlockRequirement(area: CampaignArea) {
  const playableAreas = campaignAreas.filter((candidate) => candidate.availability === "playable");
  const playableIndex = playableAreas.findIndex((candidate) => candidate.id === area.id);

  return playableIndex > 0 ? playableAreas[playableIndex - 1] : null;
}

function BedShapePreview({
  area,
  completedMissionIds,
  missionPlantings,
}: {
  area: CampaignArea;
  completedMissionIds: number[];
  missionPlantings: Record<number, PlantFieldCounts>;
}) {
  const cellsByLevel = area.levels.map(() => [] as number[]);

  area.bedPreview.cells.forEach((cellIndex, targetCellIndex) => {
    const levelIndex = Math.min(
      area.levels.length - 1,
      Math.floor((targetCellIndex * area.levels.length) / area.bedPreview.cells.length),
    );
    cellsByLevel[levelIndex].push(cellIndex);
  });

  const levelIndexByCell = new Map<number, number>();
  const plantKindByCell = new Map<number, RewardKind>();

  cellsByLevel.forEach((cellIndexes, levelIndex) => {
    const missionId = area.levels[levelIndex]?.legacyMissionId;
    const previewPlantKinds = getPreviewPlantKinds(
      missionId === undefined ? undefined : missionPlantings[missionId],
      cellIndexes.length,
    );

    cellIndexes.forEach((cellIndex, levelCellIndex) => {
      levelIndexByCell.set(cellIndex, levelIndex);
      plantKindByCell.set(cellIndex, previewPlantKinds[levelCellIndex]);
    });
  });

  const completedLevelCount = getCompletedLevelCount(area, completedMissionIds);

  return (
    <div
      aria-label={`Gesamtbeet für ${area.title}: ${completedLevelCount} von ${area.levels.length} Teilbeeten bepflanzt`}
      className="campaign-bed-preview"
      role="img"
      style={{
        gridTemplateColumns: `repeat(${area.bedPreview.columns}, 1fr)`,
        gridTemplateRows: `repeat(${area.bedPreview.rows}, 1fr)`,
      }}
    >
      {Array.from(
        { length: area.bedPreview.columns * area.bedPreview.rows },
        (_, cellIndex) => {
          const levelIndex = levelIndexByCell.get(cellIndex);
          const level = levelIndex === undefined ? null : area.levels[levelIndex];
          const missionId = level?.legacyMissionId;
          const isCompleted =
            missionId !== undefined && completedMissionIds.includes(missionId);
          const className = [
            "campaign-bed-preview__cell",
            level ? "campaign-bed-preview__cell--target" : "",
            level && missionId === undefined ? "campaign-bed-preview__cell--planned" : "",
            isCompleted ? "campaign-bed-preview__cell--completed" : "",
          ]
            .filter(Boolean)
            .join(" ");

          return (
            <span className={className} key={cellIndex}>
              {isCompleted && missionId !== undefined ? (
                <PlantIcon
                  className="campaign-bed-preview__plant"
                  decorative
                  rewardKind={plantKindByCell.get(cellIndex) ?? "flower"}
                  title="Bepflanztes Teilbeet"
                />
              ) : null}
            </span>
          );
        },
      )}
    </div>
  );
}

function AreaLandmark({ area, state }: { area: CampaignArea; state: CampaignAreaState }) {
  const { anchorX: x, anchorY: y } = area.hotspot;
  const completedClass = state === "complete" ? " map-landmark--complete" : "";

  if (area.mapKind === "starter-bed") {
    return (
      <g className={`map-landmark map-landmark--starter${completedClass}`} transform={`translate(${x} ${y})`}>
        <path className="map-landmark__soil" d="M -54 3 L 2 14 L 54 -6 L -3 -17 Z" />
        <path className="map-landmark__row" d="M -39 -3 L 4 5 M -21 -10 L 22 -2 M -2 -14 L 40 -7" />
        <g className="map-landmark__flowers">
          <path d="M -23 -4 L -23 -28 M 4 2 L 4 -25 M 29 -5 L 29 -30" />
          <circle cx="-23" cy="-31" r="7" />
          <circle cx="4" cy="-28" r="7" />
          <circle cx="29" cy="-33" r="7" />
        </g>
      </g>
    );
  }

  if (area.mapKind === "seed-shed" || area.mapKind === "supply-shed") {
    return (
      <g className={`map-landmark map-landmark--shed${completedClass}`} transform={`translate(${x} ${y})`}>
        <path className="map-landmark__fold" d="M -42 8 L 41 22 L 62 10 L -21 -5 Z" />
        <path className="map-landmark__wall" d="M -39 -58 L 30 -46 L 30 8 L -39 -4 Z" />
        <path className="map-landmark__side" d="M 30 -46 L 54 -60 L 54 -6 L 30 8 Z" />
        <path className="map-landmark__roof" d="M -51 -58 L 6 -91 L 64 -60 L 30 -40 Z" />
        <path className="map-landmark__door" d="M -15 -38 L 8 -34 L 8 4 L -15 0 Z" />
        <circle className="map-landmark__sign" cx="-29" cy="-47" r="10" />
        <path className="map-landmark__leaf" d="M -34 -48 Q -30 -58 -23 -51 Q -25 -42 -34 -48 Z" />
      </g>
    );
  }

  if (area.mapKind === "planning-field") {
    return (
      <g className={`map-landmark map-landmark--planning${completedClass}`} transform={`translate(${x} ${y})`}>
        <path className="map-landmark__planning-soil" d="M -62 5 L 4 19 L 61 -4 L -6 -18 Z" />
        <path className="map-landmark__rope" d="M -49 -5 L -49 -40 M 45 -7 L 45 -40 M -49 -35 Q -4 -13 45 -35" />
        <circle className="map-landmark__rope-knot" cx="-49" cy="-35" r="5" />
        <circle className="map-landmark__rope-knot" cx="45" cy="-35" r="5" />
      </g>
    );
  }

  if (area.mapKind === "greenhouse") {
    return (
      <g className={`map-landmark map-landmark--greenhouse${completedClass}`} transform={`translate(${x} ${y})`}>
        <path className="map-landmark__fold" d="M -52 10 L 34 23 L 62 8 L -25 -5 Z" />
        <path className="map-landmark__glass" d="M -42 -51 L 17 -42 L 17 10 L -42 1 Z" />
        <path className="map-landmark__glass-side" d="M 17 -42 L 49 -59 L 49 -7 L 17 10 Z" />
        <path className="map-landmark__glass-roof" d="M -42 -51 L -10 -72 L 49 -59 L 17 -42 Z" />
        <path className="map-landmark__frame" d="M -12 -46 L -12 5 M 17 -42 L 17 10 M -10 -72 L -10 -46 M -42 -25 L 17 -16 M 17 -16 L 49 -33" />
      </g>
    );
  }

  if (area.mapKind === "fence") {
    return (
      <g className={`map-landmark map-landmark--fence${completedClass}`} transform={`translate(${x} ${y})`}>
        <path className="map-landmark__fence-rail" d="M -58 -12 L 50 7 M -58 -27 L 50 -8" />
        {[-48, -19, 10, 39].map((postX, index) => (
          <path
            className="map-landmark__fence-post"
            d={`M ${postX} ${-34 + index * 5} L ${postX} ${8 + index * 5}`}
            key={postX}
          />
        ))}
      </g>
    );
  }

  if (area.mapKind === "park") {
    return (
      <g className={`map-landmark map-landmark--park${completedClass}`} transform={`translate(${x} ${y})`}>
        <ellipse className="map-landmark__park-lawn" cx="16" cy="3" rx="48" ry="18" />
        <circle className="map-landmark__park-flower" cx="22" cy="-2" r="5" />
        <circle className="map-landmark__park-flower map-landmark__park-flower--light" cx="38" cy="4" r="4" />
        <path className="map-landmark__tree-trunk" d="M -30 -8 L -30 -48" />
        <circle className="map-landmark__tree" cx="-43" cy="-54" r="22" />
        <circle className="map-landmark__tree map-landmark__tree--light" cx="-21" cy="-62" r="25" />
        <circle className="map-landmark__tree" cx="-7" cy="-47" r="19" />
      </g>
    );
  }

  return (
    <g className={`map-landmark map-landmark--market${completedClass}`} transform={`translate(${x} ${y})`}>
      <path className="map-landmark__fold" d="M -53 9 L 34 23 L 59 9 L -28 -5 Z" />
      <path className="map-landmark__market-wall" d="M -42 -43 L 37 -30 L 37 8 L -42 -5 Z" />
      <path className="map-landmark__market-roof" d="M -52 -46 L 47 -29 L 54 -45 L -45 -62 Z" />
      <path className="map-landmark__market-stripes" d="M -26 -58 L -22 -41 M 2 -53 L 6 -36 M 29 -49 L 34 -32" />
      <path className="map-landmark__counter" d="M -34 -17 L 31 -7 L 31 4 L -34 -6 Z" />
    </g>
  );
}

const progressPlantPositions = [
  { scale: 0.72, x: -30, y: 15 },
  { scale: 0.88, x: -7, y: 21 },
  { scale: 0.78, x: 18, y: 12 },
  { scale: 0.68, x: 39, y: 18 },
];

function CampaignProgressPlants({
  area,
  plantKinds,
}: {
  area: CampaignArea;
  plantKinds: RewardKind[];
}) {
  if (plantKinds.length === 0) {
    return null;
  }

  return (
    <g
      aria-hidden="true"
      className="campaign-progress-plants"
      transform={`translate(${area.hotspot.anchorX} ${area.hotspot.anchorY})`}
    >
      {progressPlantPositions.slice(0, plantKinds.length).map((position, plantIndex) => (
        <g
          key={`${area.id}-progress-plant-${plantIndex}`}
          transform={`translate(${position.x} ${position.y}) scale(${position.scale})`}
        >
          <g
            className="campaign-progress-plant"
            style={{ "--plant-delay": `${plantIndex * 90}ms` } as CSSProperties}
          >
            <g className="reward-plant" transform="translate(-20 -24)">
              <PlantGraphic rewardKind={plantKinds[plantIndex]} />
            </g>
          </g>
        </g>
      ))}
    </g>
  );
}

function CampaignHotspot({
  area,
  isSelected,
  onLockedHoverChange,
  onSelect,
  plantKinds,
  state,
}: {
  area: CampaignArea;
  isSelected: boolean;
  onLockedHoverChange: (areaId: string | null) => void;
  onSelect: (areaId: string) => void;
  plantKinds: RewardKind[];
  state: CampaignAreaState;
}) {
  const unlockRequirement = state === "locked" ? getUnlockRequirement(area) : null;
  const unlockDescription = unlockRequirement
    ? `Beende mindestens ein Beet von Mission ${unlockRequirement.number} ${unlockRequirement.title}, um diesen Bereich freizuschalten.`
    : null;

  function handleKeyDown(event: KeyboardEvent<SVGGElement>) {
    if (event.key !== "Enter" && event.key !== " ") {
      return;
    }

    event.preventDefault();
    onSelect(area.id);
  }

  const className = [
    "campaign-hotspot",
    `campaign-hotspot--${state}`,
    plantKinds.length > 0
      ? `campaign-hotspot--growth-${Math.min(
          4,
          Math.ceil((plantKinds.length / area.levels.length) * 4),
        )}`
      : "",
    isSelected ? "campaign-hotspot--selected" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <g
      aria-label={`${area.number}. ${area.title}, ${areaStateLabels[state]}${unlockDescription ? `. ${unlockDescription}` : ""}`}
      aria-pressed={isSelected}
      className={className}
      onBlur={() => onLockedHoverChange(null)}
      onClick={() => onSelect(area.id)}
      onFocus={() => state === "locked" && onLockedHoverChange(area.id)}
      onKeyDown={handleKeyDown}
      onMouseEnter={() => state === "locked" && onLockedHoverChange(area.id)}
      onMouseLeave={() => onLockedHoverChange(null)}
      role="button"
      style={{ "--area-accent": area.accent } as CSSProperties}
      tabIndex={0}
    >
      {state !== "locked" ? <title>{`${area.title}: ${areaStateLabels[state]}`}</title> : null}
      <polygon className="campaign-hotspot__shadow" points={area.hotspot.points} transform="translate(5 8)" />
      <polygon className="campaign-hotspot__ground" points={area.hotspot.points} />
      <polygon className="campaign-hotspot__outline" points={area.hotspot.points} />
      <AreaLandmark area={area} state={state} />
      <CampaignProgressPlants area={area} plantKinds={plantKinds} />
      <g
        className="campaign-hotspot__number"
        transform={`translate(${area.hotspot.anchorX - 59} ${area.hotspot.anchorY + 22})`}
      >
        <circle r="17" />
        <text dominantBaseline="middle" textAnchor="middle" y="1">
          {area.number}
        </text>
      </g>
      {state === "locked" ? (
        <g
          aria-hidden="true"
          className="campaign-hotspot__lock"
          transform={`translate(${area.hotspot.anchorX + 48} ${area.hotspot.anchorY + 18})`}
        >
          <rect height="20" rx="3" width="22" x="-11" y="-3" />
          <path d="M -7 -3 V -9 A 7 7 0 0 1 7 -9 V -3" />
        </g>
      ) : null}
    </g>
  );
}

function CampaignUnlockTooltip({ area }: { area: CampaignArea }) {
  const unlockRequirement = getUnlockRequirement(area);

  if (!unlockRequirement) {
    return null;
  }

  return (
    <g
      aria-hidden="true"
      className="campaign-hotspot__tooltip"
      transform={`translate(${area.hotspot.anchorX} ${Math.max(area.hotspot.anchorY - 90, 120)})`}
    >
      <rect height="76" rx="8" width="390" x="-195" y="-52" />
      <path d="M -10 24 L 0 38 L 10 24 Z" />
      <text textAnchor="middle">
        <tspan className="campaign-hotspot__tooltip-title" x="0" y="-31">
          Noch gesperrt
        </tspan>
        <tspan x="0" y="-9">
          Beende mindestens ein Beet von Mission {unlockRequirement.number}
        </tspan>
        <tspan x="0" y="12">
          „{unlockRequirement.title}“, um diesen Bereich freizuschalten.
        </tspan>
      </text>
    </g>
  );
}

function IsometricTree({
  scale = 1,
  x,
  y,
}: {
  scale?: number;
  x: number;
  y: number;
}) {
  return (
    <g className="campaign-world-tree" transform={`translate(${x} ${y}) scale(${scale})`}>
      <ellipse className="campaign-world-tree__shadow" cx="0" cy="9" rx="25" ry="8" />
      <path className="campaign-world-tree__trunk" d="M 0 8 V -29" />
      <circle className="campaign-world-tree__crown campaign-world-tree__crown--dark" cx="-13" cy="-32" r="17" />
      <circle className="campaign-world-tree__crown campaign-world-tree__crown--light" cx="6" cy="-42" r="21" />
      <circle className="campaign-world-tree__crown" cx="19" cy="-27" r="16" />
    </g>
  );
}

function IsometricGardenWorld() {
  const gardenPath =
    "M 246 486 C 288 455 292 406 326 374 C 356 344 363 300 401 270 C 438 239 448 205 489 181 C 537 152 600 137 654 158 C 707 179 744 221 733 266 C 725 300 699 314 700 344 C 702 384 742 402 784 420 C 818 435 830 461 874 480";
  const trees = [
    { scale: 0.62, x: 126, y: 350 },
    { scale: 0.5, x: 188, y: 305 },
    { scale: 0.58, x: 270, y: 195 },
    { scale: 0.54, x: 520, y: 110 },
    { scale: 0.62, x: 630, y: 105 },
    { scale: 0.68, x: 900, y: 175 },
    { scale: 0.56, x: 985, y: 312 },
    { scale: 0.66, x: 1004, y: 370 },
    { scale: 0.6, x: 760, y: 510 },
    { scale: 0.72, x: 688, y: 568 },
    { scale: 0.5, x: 562, y: 604 },
    { scale: 0.66, x: 444, y: 568 },
    { scale: 0.56, x: 392, y: 520 },
    { scale: 0.52, x: 470, y: 500 },
  ];

  return (
    <g aria-hidden="true" className="campaign-world">
      <ellipse className="campaign-world__shadow" cx="560" cy="642" rx="508" ry="52" />
      <path className="campaign-world__side campaign-world__side--right" d="M 560 73 L 1076 350 L 1076 384 L 560 680 Z" />
      <path className="campaign-world__side campaign-world__side--front" d="M 44 350 L 560 646 L 1076 350 L 1076 384 L 560 680 L 44 384 Z" />
      <path className="campaign-world__ground" d="M 560 54 L 1076 350 L 560 646 L 44 350 Z" />
      <path className="campaign-world__texture" d="M 560 54 L 1076 350 L 560 646 L 44 350 Z" />
      <path className="campaign-world__edge" d="M 560 54 L 1076 350 L 560 646 L 44 350 Z" />

      <g className="campaign-world-paths">
        <path className="campaign-world-paths__edge" d={gardenPath} />
        <path className="campaign-world-paths__surface" d={gardenPath} />
      </g>

      <g className="campaign-world-pond" transform="translate(603 330)">
        <ellipse className="campaign-world-pond__bank" rx="94" ry="47" />
        <ellipse className="campaign-world-pond__water" rx="81" ry="37" />
        <path className="campaign-world-pond__shine" d="M -55 -5 Q -23 -23 15 -11 M 28 13 Q 48 6 60 12" />
        <g className="campaign-world-pond__lilies">
          <ellipse cx="-27" cy="12" rx="12" ry="5" />
          <ellipse cx="30" cy="-9" rx="10" ry="4" />
          <circle cx="-23" cy="8" r="3" />
        </g>
      </g>

      <g className="campaign-world-reeds">
        <path d="M 504 348 Q 500 327 507 310 M 514 352 Q 520 329 516 315 M 690 338 Q 694 317 689 302 M 700 342 Q 707 321 704 307" />
      </g>

      {trees.map((tree) => (
        <IsometricTree key={`${tree.x}-${tree.y}`} {...tree} />
      ))}

      <g className="campaign-world-stones">
        <ellipse cx="212" cy="408" rx="12" ry="6" />
        <ellipse cx="229" cy="417" rx="8" ry="4" />
        <ellipse cx="900" cy="335" rx="11" ry="5" />
        <ellipse cx="920" cy="326" rx="7" ry="4" />
      </g>

      <g className="campaign-world-gate" transform="translate(275 475)">
        <path d="M -48 10 V -39 M 48 10 V -39" />
        <rect height="28" rx="4" width="104" x="-52" y="-48" />
        <text textAnchor="middle" y="-29">
          SCHULGARTEN
        </text>
      </g>
    </g>
  );
}

function CampaignMap({
  areaStates,
  completedMissionIds,
  missionPlantings,
  selectedAreaId,
  onSelectArea,
}: {
  areaStates: Record<string, CampaignAreaState>;
  completedMissionIds: number[];
  missionPlantings: Record<number, PlantFieldCounts>;
  selectedAreaId: string;
  onSelectArea: (areaId: string) => void;
}) {
  const [lockedTooltipAreaId, setLockedTooltipAreaId] = useState<string | null>(null);
  const lockedTooltipArea =
    campaignAreas.find(
      (area) => area.id === lockedTooltipAreaId && areaStates[area.id] === "locked",
    ) ?? null;

  return (
    <div className="campaign-map-stage">
      <svg
        aria-label="Isometrische MathWorld-Schulgartenkarte"
        className="campaign-map"
        role="group"
        viewBox="0 0 1120 700"
      >
        <defs>
          <filter height="180%" id="campaign-outline-glow" width="180%" x="-40%" y="-40%">
            <feGaussianBlur result="blur" stdDeviation="6" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <pattern height="26" id="campaign-ground-speckles" patternUnits="userSpaceOnUse" width="26">
            <circle cx="5" cy="8" fill="#5a8d4d" opacity=".19" r="1.4" />
            <circle cx="20" cy="19" fill="#e7d47c" opacity=".18" r="1.1" />
          </pattern>
        </defs>

        <rect className="campaign-stage-sky" height="700" width="1120" />
        <IsometricGardenWorld />

        {campaignAreas.map((area) => {
          const dominantPlantKind = getDominantPlantKind(
            area,
            completedMissionIds,
            missionPlantings,
          );
          const completedLevelCount = getCompletedLevelCount(area, completedMissionIds);

          return (
            <CampaignHotspot
              area={area}
              isSelected={selectedAreaId === area.id}
              key={area.id}
              onLockedHoverChange={setLockedTooltipAreaId}
              onSelect={onSelectArea}
              plantKinds={
                dominantPlantKind
                  ? Array.from({ length: completedLevelCount }, () => dominantPlantKind)
                  : []
              }
              state={areaStates[area.id]}
            />
          );
        })}
        {lockedTooltipArea ? <CampaignUnlockTooltip area={lockedTooltipArea} /> : null}
      </svg>
    </div>
  );
}

function CampaignAreaPreview({
  area,
  completedMissionIds,
  missionPlantings,
  onOpenMission,
  state,
}: {
  area: CampaignArea;
  completedMissionIds: number[];
  missionPlantings: Record<number, PlantFieldCounts>;
  onOpenMission: (missionId: number, areaId: string) => void;
  state: CampaignAreaState;
}) {
  const completedLevelCount = getCompletedLevelCount(area, completedMissionIds);
  const dominantPlantKind = getDominantPlantKind(
    area,
    completedMissionIds,
    missionPlantings,
  );
  const canPlay = state === "ready" || state === "complete";
  const unlockRequirement = state === "locked" ? getUnlockRequirement(area) : null;

  return (
    <aside className="campaign-preview" style={{ "--area-accent": area.accent } as CSSProperties}>
      <div className="campaign-preview__heading">
        <p className="campaign-preview__eyebrow">Gartenbereich {area.number}</p>
        <span className={`campaign-preview__state campaign-preview__state--${state}`}>
          {areaStateLabels[state]}
        </span>
        <h2>{area.title}</h2>
        <p>{area.description}</p>
      </div>

      <div className="campaign-preview__visual">
        <BedShapePreview
          area={area}
          completedMissionIds={completedMissionIds}
          missionPlantings={missionPlantings}
        />
        {area.sackKind ? (
          <ParametricSack
            className="campaign-preview__sack"
            height={190}
            kind={area.sackKind}
            plantKind={
              area.sackKind === "seed"
                ? dominantPlantKind ?? "flower"
                : undefined
            }
            title={area.sackKind === "seed" ? "Parametrischer Saatgutsack" : "Parametrischer Düngersack"}
            width={150}
          />
        ) : (
          <div aria-hidden="true" className={`campaign-tool-emblem campaign-tool-emblem--${area.mapKind}`}>
            <span />
          </div>
        )}
      </div>

      <dl className="campaign-preview__facts">
        <div>
          <dt>Lernziel</dt>
          <dd>{area.learningGoal}</dd>
        </div>
        <div>
          <dt>Werkzeug</dt>
          <dd>{area.tool}</dd>
        </div>
      </dl>

      <section className="campaign-levels" aria-label={`Level in ${area.title}`}>
        <div className="campaign-levels__heading">
          <h3>Teilbeete</h3>
          <span>
            {completedLevelCount}/{area.levels.length}
          </span>
        </div>
        <div className="campaign-levels__list">
          {area.levels.map((level) => {
            const isCompleted =
              level.legacyMissionId !== undefined && completedMissionIds.includes(level.legacyMissionId);
            const isPlayableLevel = canPlay && level.legacyMissionId !== undefined;

            return (
              <button
                className={isCompleted ? "campaign-level campaign-level--complete" : "campaign-level"}
                disabled={!isPlayableLevel}
                key={level.id}
                onClick={() =>
                  level.legacyMissionId !== undefined && onOpenMission(level.legacyMissionId, area.id)
                }
                type="button"
              >
                <span className="campaign-level__marker">{isCompleted ? "✓" : area.levels.indexOf(level) + 1}</span>
                <span>
                  <small>{level.label}</small>
                  <strong>{level.title}</strong>
                </span>
              </button>
            );
          })}
        </div>
      </section>

      <div className="campaign-preview__action">
        {unlockRequirement ? (
          <p>
            Beende mindestens ein Beet von Mission {unlockRequirement.number} „{unlockRequirement.title}“,
            um diesen Bereich freizuschalten.
          </p>
        ) : null}
        {state === "planned" ? <p>Dieser Bereich zeigt bereits, wie die Kampagne später weiterwächst.</p> : null}
        <button
          className="campaign-primary-action"
          disabled={!canPlay || area.demoMissionId === undefined}
          onClick={() =>
            area.demoMissionId !== undefined && onOpenMission(area.demoMissionId, area.id)
          }
          type="button"
        >
          {state === "complete" ? "Kernlevel öffnen" : state === "ready" ? "Kernlevel starten" : areaStateLabels[state]}
        </button>
      </div>
    </aside>
  );
}

export function CampaignApp({
  completedMissionIds,
  missionPlantings,
  onOpenMission,
  onResetProgress,
  onSelectArea,
  selectedAreaId,
}: CampaignAppProps) {
  const selectedArea = campaignAreas.find((area) => area.id === selectedAreaId) ?? campaignAreas[0];
  const areaStates = useMemo(
    () =>
      Object.fromEntries(
        campaignAreas.map((area) => [area.id, getAreaState(area, completedMissionIds)]),
      ) as Record<string, CampaignAreaState>,
    [completedMissionIds],
  );
  const completedPlayableAreas = campaignAreas.filter(
    (area) => area.availability === "playable" && areaStates[area.id] === "complete",
  ).length;
  const totalPlayableAreas = campaignAreas.filter((area) => area.availability === "playable").length;

  return (
    <main className="campaign-app">
      <header className="campaign-header">
        <div>
          <p className="campaign-header__eyebrow">MathWorld Kampagne</p>
          <h1>Unser Schulgarten</h1>
        </div>
        <div className="campaign-header__progress" aria-label="Kampagnenfortschritt">
          <span>
            <strong>{completedPlayableAreas}</strong> von {totalPlayableAreas}
          </span>
          <small>Gartenbereiche begonnen</small>
        </div>
        <button className="campaign-reset-button" onClick={onResetProgress} type="button">
          Fortschritt zurücksetzen
        </button>
      </header>

      <div className="campaign-layout">
        <CampaignMap
          areaStates={areaStates}
          completedMissionIds={completedMissionIds}
          missionPlantings={missionPlantings}
          onSelectArea={onSelectArea}
          selectedAreaId={selectedArea.id}
        />
        <CampaignAreaPreview
          area={selectedArea}
          completedMissionIds={completedMissionIds}
          missionPlantings={missionPlantings}
          onOpenMission={onOpenMission}
          state={areaStates[selectedArea.id]}
        />
      </div>
    </main>
  );
}
