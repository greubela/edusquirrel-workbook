import { useCallback, useEffect, useState } from "react";
import { CampaignApp } from "./components/CampaignApp";
import { campaignAreas } from "./data/campaign";
import { isRewardKind } from "./data/plants";
import LegacyApp from "./LegacyApp";
import type { PlantFieldCounts, RewardKind } from "./types";

type AppView = "campaign" | "campaign-mission" | "legacy";

const CAMPAIGN_PROGRESS_KEY = "mathworld-campaign-completed-missions";
const CAMPAIGN_PLANTINGS_KEY = "mathworld-campaign-plantings";
const CAMPAIGN_SELECTED_PLANT_KEY = "mathworld-campaign-selected-plant";

function loadCampaignProgress() {
  try {
    const storedValue = window.localStorage.getItem(CAMPAIGN_PROGRESS_KEY);
    const parsedValue = storedValue ? JSON.parse(storedValue) : [];

    return Array.isArray(parsedValue)
      ? parsedValue.filter((missionId): missionId is number => Number.isInteger(missionId))
      : [];
  } catch {
    return [];
  }
}

function loadCampaignPlantings() {
  try {
    const storedValue = window.localStorage.getItem(CAMPAIGN_PLANTINGS_KEY);
    const parsedValue: unknown = storedValue ? JSON.parse(storedValue) : {};
    const plantings: Record<number, PlantFieldCounts> = {};

    if (!parsedValue || typeof parsedValue !== "object" || Array.isArray(parsedValue)) {
      return plantings;
    }

    Object.entries(parsedValue).forEach(([missionId, storedPlanting]) => {
      const numericMissionId = Number(missionId);

      if (!Number.isInteger(numericMissionId)) {
        return;
      }

      if (isRewardKind(storedPlanting)) {
        plantings[numericMissionId] = { [storedPlanting]: 1 };
        return;
      }

      if (!storedPlanting || typeof storedPlanting !== "object" || Array.isArray(storedPlanting)) {
        return;
      }

      const fieldCounts: PlantFieldCounts = {};

      Object.entries(storedPlanting).forEach(([rewardKind, fieldCount]) => {
        if (
          isRewardKind(rewardKind) &&
          typeof fieldCount === "number" &&
          Number.isFinite(fieldCount) &&
          fieldCount > 0
        ) {
          fieldCounts[rewardKind] = fieldCount;
        }
      });

      plantings[numericMissionId] = fieldCounts;
    });

    return plantings;
  } catch {
    return {};
  }
}

function loadSelectedPlant(): RewardKind {
  try {
    const storedValue = window.localStorage.getItem(CAMPAIGN_SELECTED_PLANT_KEY);
    return isRewardKind(storedValue) ? storedValue : "flower";
  } catch {
    return "flower";
  }
}

function App() {
  const [activeView, setActiveView] = useState<AppView>("campaign");
  const [initialLegacyMissionId, setInitialLegacyMissionId] = useState(1);
  const [activeCampaignAreaId, setActiveCampaignAreaId] = useState<string | null>(null);
  const [selectedCampaignAreaId, setSelectedCampaignAreaId] = useState(campaignAreas[0].id);
  const [completedCampaignMissionIds, setCompletedCampaignMissionIds] = useState<number[]>(loadCampaignProgress);
  const [campaignPlantings, setCampaignPlantings] =
    useState<Record<number, PlantFieldCounts>>(loadCampaignPlantings);
  const [selectedPlantKind, setSelectedPlantKind] = useState<RewardKind>(loadSelectedPlant);

  useEffect(() => {
    try {
      window.localStorage.setItem(CAMPAIGN_PROGRESS_KEY, JSON.stringify(completedCampaignMissionIds));
    } catch {
      // The campaign remains playable when browser storage is unavailable.
    }
  }, [completedCampaignMissionIds]);

  useEffect(() => {
    try {
      window.localStorage.setItem(CAMPAIGN_PLANTINGS_KEY, JSON.stringify(campaignPlantings));
    } catch {
      // The campaign remains playable when browser storage is unavailable.
    }
  }, [campaignPlantings]);

  useEffect(() => {
    try {
      window.localStorage.setItem(CAMPAIGN_SELECTED_PLANT_KEY, selectedPlantKind);
    } catch {
      // The campaign remains playable when browser storage is unavailable.
    }
  }, [selectedPlantKind]);

  const completeCampaignMission = useCallback((missionId: number, planting: PlantFieldCounts) => {
    const completedArea = campaignAreas.find((area) =>
      area.levels.some((level) => level.legacyMissionId === missionId),
    );

    if (completedArea) {
      setSelectedCampaignAreaId(completedArea.id);
    }

    setCompletedCampaignMissionIds((currentMissionIds) =>
      currentMissionIds.includes(missionId) ? currentMissionIds : [...currentMissionIds, missionId],
    );
    setCampaignPlantings((currentPlantings) => ({
      ...currentPlantings,
      [missionId]: planting,
    }));
  }, []);

  function openLegacyMission(missionId: number, areaId: string) {
    setInitialLegacyMissionId(missionId);
    setActiveCampaignAreaId(areaId);
    setSelectedCampaignAreaId(areaId);
    setActiveView("campaign-mission");
  }

  function openLegacyDemo() {
    setInitialLegacyMissionId(1);
    setActiveCampaignAreaId(null);
    setActiveView("legacy");
  }

  function resetCampaignProgress() {
    setCompletedCampaignMissionIds([]);
    setCampaignPlantings({});
  }

  const activeCampaignArea =
    activeView === "campaign-mission"
      ? campaignAreas.find((area) => area.id === activeCampaignAreaId) ?? null
      : null;

  return (
    <div className="experience-shell">
      <nav aria-label="Programmversion wählen" className="experience-switcher">
        <span className="experience-switcher__label">Ansicht</span>
        <button
          aria-pressed={activeView === "campaign" || activeView === "campaign-mission"}
          className={activeView === "campaign" || activeView === "campaign-mission" ? "experience-switcher__button experience-switcher__button--active" : "experience-switcher__button"}
          onClick={() => setActiveView("campaign")}
          type="button"
        >
          Neue Kampagne
        </button>
        <button
          aria-pressed={activeView === "legacy"}
          className={activeView === "legacy" ? "experience-switcher__button experience-switcher__button--active" : "experience-switcher__button"}
          onClick={openLegacyDemo}
          type="button"
        >
          Bisherige Demo
        </button>
      </nav>

      {activeView === "campaign" ? (
        <CampaignApp
          completedMissionIds={completedCampaignMissionIds}
          missionPlantings={campaignPlantings}
          onOpenMission={openLegacyMission}
          onResetProgress={resetCampaignProgress}
          onSelectArea={setSelectedCampaignAreaId}
          selectedAreaId={selectedCampaignAreaId}
        />
      ) : (
        <LegacyApp
          campaignArea={activeCampaignArea ?? undefined}
          campaignCompletedMissionIds={completedCampaignMissionIds}
          initialMissionId={initialLegacyMissionId}
          key={
            activeCampaignArea
              ? `campaign-${activeCampaignArea.id}-${initialLegacyMissionId}`
              : `legacy-${initialLegacyMissionId}`
          }
          onMissionCompleted={completeCampaignMission}
          onReturnToCampaign={() => setActiveView("campaign")}
          onSelectPlant={setSelectedPlantKind}
          rewardKindOverride={selectedPlantKind}
        />
      )}
    </div>
  );
}

export default App;
