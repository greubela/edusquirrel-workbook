import type { CampaignArea } from "../data/campaign";
import type { MissionConfig, MissionState } from "../types";

type TopBarProps = {
  activeCampaignLevelIndex?: number;
  campaignArea?: CampaignArea;
  completedMissionCount: number;
  completedMissionIds: Set<number>;
  getMissionState: (missionIndex: number) => MissionState;
  missions: MissionConfig[];
  totalMissionCount: number;
  onReturnToCampaign?: () => void;
  onSelectCampaignLevel?: (levelIndex: number) => void;
  onSelectMission: (missionIndex: number) => void;
};

export function TopBar({
  activeCampaignLevelIndex,
  campaignArea,
  completedMissionCount,
  completedMissionIds,
  getMissionState,
  missions,
  onReturnToCampaign,
  onSelectCampaignLevel,
  onSelectMission,
  totalMissionCount,
}: TopBarProps) {
  const isCampaignMission =
    campaignArea !== undefined &&
    activeCampaignLevelIndex !== undefined &&
    onSelectCampaignLevel !== undefined;
  const completedCampaignLevelCount =
    campaignArea?.levels.filter(
      (level) =>
        level.legacyMissionId !== undefined &&
        completedMissionIds.has(level.legacyMissionId),
    ).length ?? 0;
  const areAllMissionsComplete = completedMissionCount === totalMissionCount && totalMissionCount > 0;

  return (
    <header className="top-bar">
      <div className="brand-lockup">
        {onReturnToCampaign ? (
          <button
            aria-label="Zur Kampagnenkarte"
            className="campaign-return-button"
            onClick={onReturnToCampaign}
            title="Zur Kampagnenkarte"
            type="button"
          >
            <span aria-hidden="true">←</span>
            <span>Karte</span>
          </button>
        ) : null}
        <span className="brand-mark" aria-hidden="true">
          MW
        </span>
        <div>
          <p className="eyebrow">
            {isCampaignMission ? `Gartenbereich ${campaignArea.number}` : "MathWorld Garten"}
          </p>
          <h1>{isCampaignMission ? campaignArea.title : "Beet-Abenteuer"}</h1>
        </div>
      </div>

      {isCampaignMission ? (
        <nav className="mission-chips" aria-label={`Teilbeete in ${campaignArea.title}`}>
          {campaignArea.levels.map((level, levelIndex) => {
            const isActive = levelIndex === activeCampaignLevelIndex;
            const isCompleted =
              level.legacyMissionId !== undefined &&
              completedMissionIds.has(level.legacyMissionId);
            const levelState = isActive
              ? "active"
              : level.legacyMissionId === undefined
                ? "locked"
                : "available";

            return (
              <button
                aria-current={isActive ? "step" : undefined}
                aria-label={`${level.label}: ${level.title}`}
                className={`mission-chip mission-chip--${levelState}${isCompleted ? " mission-chip--completed" : ""}`}
                disabled={level.legacyMissionId === undefined}
                key={level.id}
                onClick={() => onSelectCampaignLevel(levelIndex)}
                title={level.title}
                type="button"
              >
                {levelIndex + 1}
              </button>
            );
          })}
        </nav>
      ) : (
        <nav className="mission-chips" aria-label="Missionen">
          {missions.map((mission, index) => {
            const missionState = getMissionState(index);
            const isLocked = missionState === "locked";
            const isCompleted = completedMissionIds.has(mission.id);

            return (
              <button
                aria-current={missionState === "active" ? "step" : undefined}
                aria-label={`Mission ${mission.id}: ${mission.title}`}
                className={`mission-chip mission-chip--${missionState}${isCompleted ? " mission-chip--completed" : ""}`}
                disabled={isLocked}
                key={mission.id}
                onClick={() => onSelectMission(index)}
                title={mission.title}
                type="button"
              >
                {mission.id}
              </button>
            );
          })}
        </nav>
      )}

      <div className="garden-status" aria-label="Missionsfortschritt">
        <strong>
          {isCampaignMission
            ? `${completedCampaignLevelCount}/${campaignArea.levels.length}`
            : `${completedMissionCount}/${totalMissionCount}`}
        </strong>
        <span>{isCampaignMission ? "Teilbeete" : "Missionen"}</span>
        {!isCampaignMission && areAllMissionsComplete ? (
          <span className="garden-complete-message">Alles geschafft!</span>
        ) : null}
      </div>
    </header>
  );
}
