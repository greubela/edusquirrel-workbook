declare module "*sack-generator-v3.js";

type SackGraphicKind = "seed" | "fertilizer";
type SackPlantKind = "flower" | "sunflower" | "tulip" | "radish" | "carrot" | "tomato";

type SackGraphicOptions = {
  width: number;
  height: number;
  kind: SackGraphicKind;
  plantKind?: SackPlantKind;
  title?: string;
  idPrefix?: string;
};

type SackGraphicsApi = {
  markup: (options: SackGraphicOptions) => string;
};

declare global {
  interface Window {
    SackGraphics: SackGraphicsApi;
  }
}

export {};
