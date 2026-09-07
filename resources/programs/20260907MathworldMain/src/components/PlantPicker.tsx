import { plantChoices } from "../data/plants";
import type { RewardKind } from "../types";
import { PlantIcon } from "./PlantGraphic";

type PlantPickerProps = {
  allowedPlantKinds?: readonly RewardKind[];
  className?: string;
  label?: string;
  onSelectPlant: (rewardKind: RewardKind) => void;
  selectedPlantKind: RewardKind;
};

export function PlantPicker({
  allowedPlantKinds,
  className = "",
  label = "Saatgut auswählen",
  onSelectPlant,
  selectedPlantKind,
}: PlantPickerProps) {
  const availablePlantChoices = allowedPlantKinds
    ? plantChoices.filter((plant) => allowedPlantKinds.includes(plant.kind))
    : plantChoices;

  return (
    <fieldset className={`plant-picker ${className}`.trim()}>
      <legend>{label}</legend>
      <div
        className="plant-picker__options"
        style={{
          gridTemplateColumns: `repeat(${Math.min(3, Math.max(1, availablePlantChoices.length))}, minmax(0, 1fr))`,
        }}
      >
        {availablePlantChoices.map((plant) => {
          const isSelected = selectedPlantKind === plant.kind;

          return (
            <button
              aria-label={`${plant.label} auswählen`}
              aria-pressed={isSelected}
              className={
                isSelected
                  ? "plant-picker__option plant-picker__option--selected"
                  : "plant-picker__option"
              }
              key={plant.kind}
              onClick={() => onSelectPlant(plant.kind)}
              type="button"
            >
              <PlantIcon
                className="plant-picker__icon"
                decorative
                rewardKind={plant.kind}
                title={plant.label}
              />
              <span>{plant.label}</span>
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
