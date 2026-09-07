export type CampaignLevel = {
  id: string;
  label: string;
  legacyMissionId?: number;
  title: string;
};

export type CampaignArea = {
  accent: string;
  availability: "playable" | "planned";
  bedPreview: {
    cells: number[];
    columns: number;
    rows: number;
  };
  demoMissionId?: number;
  description: string;
  hotspot: {
    anchorX: number;
    anchorY: number;
    points: string;
  };
  id: string;
  learningGoal: string;
  levels: CampaignLevel[];
  mapKind:
    | "starter-bed"
    | "seed-shed"
    | "planning-field"
    | "supply-shed"
    | "greenhouse"
    | "fence"
    | "park"
    | "market";
  number: number;
  sackKind?: "seed" | "fertilizer";
  title: string;
  tool: string;
};

export const campaignAreas: CampaignArea[] = [
  {
    id: "starter-bed",
    number: 1,
    title: "Das Startbeet",
    description: "Bereite die ersten Beete Feld für Feld vor und bringe den Schulgarten zum Blühen.",
    learningGoal: "Fläche als Anzahl gleich großer Quadratmeter verstehen.",
    tool: "1 x 1 Spatenfelder",
    availability: "playable",
    accent: "#ef6f61",
    mapKind: "starter-bed",
    demoMissionId: 1,
    bedPreview: { columns: 4, rows: 3, cells: [0, 4, 5, 6] },
    hotspot: {
      points: "127,390 205,429 283,390 205,351",
      anchorX: 205,
      anchorY: 390,
    },
    levels: [
      { id: "starter-1", label: "Level 1", title: "Vier erste Felder", legacyMissionId: 1 },
      { id: "starter-2", label: "Level 2", title: "Das Radieschenbeet", legacyMissionId: 4 },
      { id: "starter-3", label: "Level 3", title: "Die große Beetform" },
    ],
  },
  {
    id: "seed-shed",
    number: 2,
    title: "Das Saatgutlager",
    description: "Wähle passende Sackgrößen und setze immer größere Beetflächen zusammen.",
    learningGoal: "Gesamtflächen aus rechteckigen Teilflächen zusammensetzen.",
    tool: "Saatgutsäcke",
    availability: "playable",
    accent: "#e9a93d",
    mapKind: "seed-shed",
    sackKind: "seed",
    demoMissionId: 5,
    bedPreview: { columns: 6, rows: 2, cells: [0, 1, 2, 3, 4, 5] },
    hotspot: {
      points: "332,410 410,449 488,410 410,371",
      anchorX: 410,
      anchorY: 410,
    },
    levels: [
      { id: "seed-1", label: "Level 1", title: "Der Karottenstreifen", legacyMissionId: 5 },
      { id: "seed-2", label: "Level 2", title: "Drei Sackgrößen", legacyMissionId: 2 },
      { id: "seed-3", label: "Level 3", title: "Das Gemüsemix-Beet", legacyMissionId: 6 },
      { id: "seed-4", label: "Bonus", title: "Mit wenigen Säcken" },
    ],
  },
  {
    id: "planning-field",
    number: 3,
    title: "Die Planungswiese",
    description: "Stecke Beete ab, grabe sie Quadratmeter für Quadratmeter um und verteile anschließend selbst gewählte Saat-Säcke.",
    learningGoal: "Rechteckflächen konstruieren, zusammensetzen und als bearbeitbare Felder verstehen.",
    tool: "Absteckfaden, Spaten und Saat-Säcke",
    availability: "playable",
    accent: "#4ca88b",
    mapKind: "planning-field",
    demoMissionId: 3,
    bedPreview: { columns: 5, rows: 4, cells: [0, 1, 2, 5, 10, 11, 12, 13] },
    hotspot: {
      points: "222,265 300,304 378,265 300,226",
      anchorX: 300,
      anchorY: 265,
    },
    levels: [
      { id: "plan-1", label: "Level 1", title: "Eigene Rechtecke", legacyMissionId: 3 },
      { id: "plan-2", label: "Level 2", title: "Die Blumenecke", legacyMissionId: 7 },
      { id: "plan-3", label: "Level 3", title: "Das freie Gartenbeet", legacyMissionId: 8 },
      { id: "plan-4", label: "Bonus", title: "Planen mit Bedingung" },
    ],
  },
  {
    id: "supply-shed",
    number: 4,
    title: "Der Vorratsschuppen",
    description: "Teile Saatgut und Dünger in gleiche Portionen und prüfe die entstehenden Brüche.",
    learningGoal: "Brüche als Teil eines Ganzen ablesen und vollständig kürzen.",
    tool: "Samen- und Düngersäcke",
    availability: "playable",
    accent: "#8f78c9",
    mapKind: "supply-shed",
    sackKind: "fertilizer",
    demoMissionId: 12,
    bedPreview: { columns: 4, rows: 4, cells: [0, 2, 5, 7, 8, 10, 13, 15] },
    hotspot: {
      points: "312,185 390,224 468,185 390,146",
      anchorX: 390,
      anchorY: 185,
    },
    levels: [
      { id: "supply-1", label: "Level 1", title: "Vier Blumenbeete", legacyMissionId: 12 },
      { id: "supply-2", label: "Level 2", title: "Neun Gemüsebeete", legacyMissionId: 13 },
      { id: "supply-3", label: "Level 3", title: "Das Anzuchtfeld", legacyMissionId: 14 },
      { id: "supply-4", label: "Level 4", title: "Der Vorratscheck", legacyMissionId: 15 },
    ],
  },
  {
    id: "greenhouse",
    number: 5,
    title: "Das Gewächshaus",
    description: "Miss rechteckige Beete aus und berechne, wie viel Fläche für Jungpflanzen bereitsteht.",
    learningGoal: "Rechteckflächen mit Länge mal Breite berechnen.",
    tool: "Maßband",
    availability: "playable",
    accent: "#48a9c5",
    mapKind: "greenhouse",
    demoMissionId: 16,
    bedPreview: { columns: 5, rows: 3, cells: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9] },
    hotspot: {
      points: "522,235 600,274 678,235 600,196",
      anchorX: 600,
      anchorY: 235,
    },
    levels: [
      { id: "measure-1", label: "Level 1", title: "Seitenlängen ablesen", legacyMissionId: 16 },
      { id: "measure-2", label: "Level 2", title: "Fläche berechnen", legacyMissionId: 17 },
      { id: "measure-3", label: "Level 3", title: "Zwei Teilflächen", legacyMissionId: 18 },
    ],
  },
  {
    id: "fence",
    number: 6,
    title: "Die Zaunwerkstatt",
    description: "Plane Begrenzungen für Beete und unterscheide dabei sicher zwischen Fläche und Rand.",
    learningGoal: "Umfang berechnen und die Einheiten Meter und Quadratmeter unterscheiden.",
    tool: "Zaunrolle",
    availability: "playable",
    accent: "#d88453",
    mapKind: "fence",
    demoMissionId: 19,
    bedPreview: { columns: 5, rows: 3, cells: [0, 1, 2, 3, 4, 5, 9, 10, 11, 12, 13, 14] },
    hotspot: {
      points: "787,265 865,304 943,265 865,226",
      anchorX: 865,
      anchorY: 265,
    },
    levels: [
      { id: "fence-1", label: "Level 1", title: "Ein Rechteck einzäunen", legacyMissionId: 19 },
      { id: "fence-2", label: "Level 2", title: "Ecken und Aussparungen", legacyMissionId: 20 },
      { id: "fence-3", label: "Level 3", title: "Materialbedarf", legacyMissionId: 21 },
    ],
  },
  {
    id: "park",
    number: 7,
    title: "Die Parkecke",
    description: "Verbinde Rechtecke und Dreiecke zu einer kleinen Grünanlage mit Wegen und Blumeninseln.",
    learningGoal: "Restflächen, Dreiecke und zusammengesetzte Flächen berechnen.",
    tool: "Plan und Hilfsrechteck",
    availability: "playable",
    accent: "#cf6e9b",
    mapKind: "park",
    demoMissionId: 22,
    bedPreview: { columns: 5, rows: 4, cells: [0, 1, 2, 5, 6, 7, 8, 9, 12, 13, 14, 17, 18, 19] },
    hotspot: {
      points: "562,415 640,454 718,415 640,376",
      anchorX: 640,
      anchorY: 415,
    },
    levels: [
      { id: "park-1", label: "Level 1", title: "Rasen minus Beet", legacyMissionId: 22 },
      { id: "park-2", label: "Level 2", title: "Ein halbes Rechteck", legacyMissionId: 23 },
      { id: "park-3", label: "Level 3", title: "Der kleine Park", legacyMissionId: 24 },
    ],
  },
  {
    id: "market",
    number: 8,
    title: "Der Gartenmarkt",
    description: "Kaufe Saatgut und Material so ein, dass alles reicht und das vorgegebene Budget eingehalten wird.",
    learningGoal: "Mengen vergleichen, Kosten berechnen und Lösungen optimieren.",
    tool: "Einkaufskorb",
    availability: "playable",
    accent: "#e06565",
    mapKind: "market",
    demoMissionId: 25,
    bedPreview: { columns: 4, rows: 3, cells: [0, 1, 2, 3, 4, 6, 8, 9, 10, 11] },
    hotspot: {
      points: "772,390 850,429 928,390 850,351",
      anchorX: 850,
      anchorY: 390,
    },
    levels: [
      { id: "market-1", label: "Level 1", title: "Genug Saatgut kaufen", legacyMissionId: 25 },
      { id: "market-2", label: "Level 2", title: "Das Budget einhalten", legacyMissionId: 26 },
      { id: "market-3", label: "Level 3", title: "Die günstigste Lösung", legacyMissionId: 27 },
    ],
  },
];
