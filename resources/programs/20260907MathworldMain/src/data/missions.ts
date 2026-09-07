import type { FractionSupplyConfig, MissionConfig, MissionVariant } from "../types";
import { mergeCells, rectangleCells } from "../logic/geometry";

type SupplyVariantOptions = {
  id: string;
  name: string;
  rows: number;
  columns: number;
  filledCellIndexes: number[];
  supply: FractionSupplyConfig;
};

const flowerSeedSupply: FractionSupplyConfig = {
  resourceKind: "seeds",
  sackLabel: "Samensack",
  contentsLabel: "Blumensamen",
  bedLabel: "Blumenbeete",
  filledBedLabel: "ausgesaet",
};

const vegetableFertilizerSupply: FractionSupplyConfig = {
  resourceKind: "fertilizer",
  sackLabel: "Duengersack",
  contentsLabel: "Gemueseduenger",
  bedLabel: "Gemuesebeete",
  filledBedLabel: "geduengt",
};

const nurserySeedSupply: FractionSupplyConfig = {
  resourceKind: "seeds",
  sackLabel: "Samensack",
  contentsLabel: "Karottensamen",
  bedLabel: "Anzuchtbeete",
  filledBedLabel: "ausgesaet",
};

function supplyVariant({
  id,
  name,
  rows,
  columns,
  filledCellIndexes,
  supply,
}: SupplyVariantOptions): MissionVariant {
  const denominator = rows * columns;

  return {
    id,
    name,
    fractionRead: {
      numerator: filledCellIndexes.length,
      denominator,
      rows,
      columns,
      filledCellIndexes,
      designKey: `supply-${supply.resourceKind}`,
      wholeLabel: `1 Sack = ${denominator} gleiche Portionen`,
      supply,
    },
  };
}

export const missionConfigs: MissionConfig[] = [
  {
    id: 1,
    title: "Zielfläche füllen",
    goal: "Füllen der 4 m² großen Zielfläche mit 1 m² Quadraten.",
    tools: [
      {
        id: "square-1",
        label: "1 x 1",
        kind: "rectangle",
        widthCells: 1,
        heightCells: 1,
      },
    ],
    variants: [
      {
        id: "l-shape",
        name: "L-Form",
        targetCells: [
          { x: 1, y: 1 },
          { x: 1, y: 2 },
          { x: 2, y: 2 },
          { x: 3, y: 2 },
        ],
      },
      {
        id: "square-2",
        name: "Quadrat",
        targetCells: rectangleCells(1, 1, 2, 2),
      },
      {
        id: "row-4",
        name: "Reihe",
        targetCells: rectangleCells(1, 2, 4, 1),
      },
      {
        id: "stair",
        name: "Treppe",
        targetCells: [
          { x: 1, y: 2 },
          { x: 2, y: 2 },
          { x: 2, y: 1 },
          { x: 3, y: 1 },
        ],
      },
    ],
  },
  {
    id: 2,
    title: "Größere Formen",
    goal: "Benutze alle drei Formen: 2 x 2, 1 x 3 und 3 x 2.",
    requiredToolIds: ["rect-2x2", "rect-1x3", "rect-3x2"],
    tools: [
      {
        id: "rect-2x2",
        label: "2 x 2",
        kind: "rectangle",
        widthCells: 2,
        heightCells: 2,
      },
      {
        id: "rect-1x3",
        label: "1 x 3",
        kind: "rectangle",
        widthCells: 1,
        heightCells: 3,
      },
      {
        id: "rect-3x2",
        label: "3 x 2",
        kind: "rectangle",
        widthCells: 3,
        heightCells: 2,
      },
    ],
    variants: [
      {
        id: "tool-mix-corner",
        name: "Kombibeet mit Ecke",
        targetCells: mergeCells(
          rectangleCells(1, 1, 2, 2),
          rectangleCells(3, 0, 1, 3),
          rectangleCells(3, 3, 3, 2),
        ),
      },
      {
        id: "tool-mix-bridge",
        name: "Versetztes Kombibeet",
        targetCells: mergeCells(
          rectangleCells(1, 1, 3, 2),
          rectangleCells(4, 2, 1, 3),
          rectangleCells(2, 3, 2, 2),
        ),
      },
      {
        id: "tool-mix-stair",
        name: "Stufiges Kombibeet",
        targetCells: mergeCells(
          rectangleCells(2, 1, 1, 3),
          rectangleCells(3, 0, 3, 2),
          rectangleCells(0, 3, 2, 2),
        ),
      },
    ],
  },
  {
    id: 3,
    kind: "garden-design",
    title: "Vierecke bauen",
    goal: "Stecke eigene rechteckige Beete ab, grabe jeden Quadratmeter um und säe sie anschließend mit selbst gewählten Saat-Säcken aus.",
    allowFreeDrawing: true,
    tools: [],
    variants: [
      {
        id: "free-corner",
        name: "Zwei erste Beete",
        gardenDesign: {
          minimumBedCount: 2,
          minimumDistinctSizes: 2,
          minimumTotalArea: 6,
          threadLength: 20,
        },
      },
      {
        id: "free-step",
        name: "Eine kleine Beetgruppe",
        gardenDesign: {
          minimumBedCount: 3,
          minimumDistinctSizes: 2,
          minimumTotalArea: 8,
          threadLength: 20,
        },
      },
      {
        id: "free-wide-corner",
        name: "Drei verschiedene Beetformen",
        gardenDesign: {
          minimumBedCount: 3,
          minimumDistinctSizes: 3,
          minimumTotalArea: 9,
          threadLength: 20,
        },
      },
    ],
  },
  {
    id: 4,
    title: "Radieschen-Beet",
    goal: "Bepflanze das 6 m² große Radieschen-Beet mit 1 m² großen Radieschen-Feldern.",
    rewardKind: "radish",
    allowedPlantKinds: ["radish"],
    tools: [
      {
        id: "radish-square-1",
        label: "1 x 1",
        kind: "rectangle",
        widthCells: 1,
        heightCells: 1,
      },
    ],
    variants: [
      {
        id: "radish-bed-2x3",
        name: "Radieschen-Beet",
        targetCells: rectangleCells(1, 1, 2, 3),
      },
    ],
  },
  {
    id: 5,
    title: "Karotten-Streifen",
    goal: "Bepflanze den 6 m² großen Karotten-Streifen mit passenden Karotten-Rechtecken.",
    rewardKind: "carrot",
    allowedPlantKinds: ["carrot"],
    tools: [
      {
        id: "carrot-square-1",
        label: "1 x 1",
        kind: "rectangle",
        widthCells: 1,
        heightCells: 1,
      },
      {
        id: "carrot-strip-1x2",
        label: "1 x 2",
        kind: "rectangle",
        widthCells: 2,
        heightCells: 1,
      },
      {
        id: "carrot-strip-1x4",
        label: "1 x 4",
        kind: "rectangle",
        widthCells: 4,
        heightCells: 1,
      },
    ],
    variants: [
      {
        id: "carrot-strip-1x6",
        name: "Karotten-Streifen",
        targetCells: rectangleCells(1, 2, 6, 1),
      },
    ],
  },
  {
    id: 6,
    title: "Gemüse-Mix-Beet",
    goal: "Bepflanze das Gemüse-Mix-Beet mit allen drei Formen: 2 x 2, 1 x 3 und 3 x 1.",
    allowedPlantKinds: ["carrot", "radish", "tomato"],
    requiredToolIds: ["vegetable-mix-2x2", "vegetable-mix-1x3", "vegetable-mix-3x1"],
    tools: [
      {
        id: "vegetable-mix-2x2",
        label: "2 x 2",
        kind: "rectangle",
        widthCells: 2,
        heightCells: 2,
      },
      {
        id: "vegetable-mix-1x3",
        label: "1 x 3",
        kind: "rectangle",
        widthCells: 1,
        heightCells: 3,
      },
      {
        id: "vegetable-mix-3x1",
        label: "3 x 1",
        kind: "rectangle",
        widthCells: 3,
        heightCells: 1,
      },
    ],
    variants: [
      {
        id: "vegetable-mix-step",
        name: "Gemüse-Mix-Beet",
        targetCells: mergeCells(
          rectangleCells(1, 1, 2, 2),
          rectangleCells(3, 1, 3, 1),
          rectangleCells(5, 2, 1, 3),
        ),
      },
    ],
  },
  {
    id: 7,
    kind: "garden-design",
    title: "Blumen-Ecke",
    goal: "Stecke eine verbundene Blumen-Ecke ab, grabe die gesamte Fläche um und säe sie mit selbst gewählten Saat-Säcken aus.",
    allowedPlantKinds: ["flower", "sunflower", "tulip"],
    allowFreeDrawing: true,
    tools: [],
    variants: [
      {
        id: "flower-corner-l-shape",
        name: "Die eigene Blumen-Ecke",
        gardenDesign: {
          minimumBedCount: 3,
          minimumDistinctSizes: 2,
          minimumOuterCornerCount: 6,
          minimumTotalArea: 12,
          requireConnectedLayout: true,
          threadLength: 32,
        },
      },
    ],
  },
  {
    id: 8,
    kind: "garden-design",
    title: "Freies Gartenbeet",
    goal: "Plane ein komplexes Gartenbeet, grabe es Quadratmeter für Quadratmeter um und bepflanze es mit selbst gewählten Saat-Säcken.",
    allowFreeDrawing: true,
    tools: [],
    variants: [
      {
        id: "free-garden-bed",
        name: "Dein eigener Gartenplan",
        gardenDesign: {
          minimumBedCount: 4,
          minimumDistinctSizes: 3,
          minimumOuterCornerCount: 8,
          minimumTotalArea: 16,
          requireConnectedLayout: true,
          threadLength: 40,
        },
      },
    ],
  },
  {
    id: 9,
    kind: "fraction-read",
    title: "Bruch im 2 x 2 Quadrat",
    goal: "Lies am 2 x 2 Quadrat ab, welcher Bruchteil vom Ganzen gefaerbt ist.",
    tools: [],
    variants: [
      {
        id: "fraction-1-4",
        name: "Ein Viertel",
        fractionRead: {
          numerator: 1,
          denominator: 4,
          rows: 2,
          columns: 2,
          filledCellIndexes: [0],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-2-4",
        name: "Zwei Viertel",
        fractionRead: {
          numerator: 2,
          denominator: 4,
          rows: 2,
          columns: 2,
          filledCellIndexes: [0, 1],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-2-4-diagonal",
        name: "Zwei Viertel diagonal",
        fractionRead: {
          numerator: 2,
          denominator: 4,
          rows: 2,
          columns: 2,
          filledCellIndexes: [0, 3],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-3-4",
        name: "Drei Viertel",
        fractionRead: {
          numerator: 3,
          denominator: 4,
          rows: 2,
          columns: 2,
          filledCellIndexes: [0, 1, 2],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
    ],
  },
  {
    id: 10,
    kind: "fraction-read",
    title: "Bruch im 3 x 3 Quadrat",
    goal: "Lies am 3 x 3 Quadrat ab, welcher Bruchteil vom Ganzen gefaerbt ist.",
    tools: [],
    variants: [
      {
        id: "fraction-2-9",
        name: "Zwei Neuntel",
        fractionRead: {
          numerator: 2,
          denominator: 9,
          rows: 3,
          columns: 3,
          filledCellIndexes: [0, 1],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-5-9",
        name: "Fuenf Neuntel",
        fractionRead: {
          numerator: 5,
          denominator: 9,
          rows: 3,
          columns: 3,
          filledCellIndexes: [0, 1, 2, 3, 4],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-6-9",
        name: "Sechs Neuntel",
        fractionRead: {
          numerator: 6,
          denominator: 9,
          rows: 3,
          columns: 3,
          filledCellIndexes: [0, 1, 2, 3, 4, 5],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-6-9-scattered",
        name: "Sechs Neuntel gemischt",
        fractionRead: {
          numerator: 6,
          denominator: 9,
          rows: 3,
          columns: 3,
          filledCellIndexes: [0, 2, 3, 5, 6, 8],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-7-9",
        name: "Sieben Neuntel",
        fractionRead: {
          numerator: 7,
          denominator: 9,
          rows: 3,
          columns: 3,
          filledCellIndexes: [0, 1, 2, 3, 4, 5, 6],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
    ],
  },
  {
    id: 11,
    kind: "fraction-read",
    title: "Bruch im 4 x 4 Quadrat",
    goal: "Lies am 4 x 4 Quadrat ab, welcher Bruchteil vom Ganzen gefaerbt ist.",
    tools: [],
    variants: [
      {
        id: "fraction-5-16",
        name: "Fuenf Sechzehntel",
        fractionRead: {
          numerator: 5,
          denominator: 16,
          rows: 4,
          columns: 4,
          filledCellIndexes: [0, 1, 2, 3, 4],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-8-16",
        name: "Acht Sechzehntel",
        fractionRead: {
          numerator: 8,
          denominator: 16,
          rows: 4,
          columns: 4,
          filledCellIndexes: [0, 1, 2, 3, 4, 5, 6, 7],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-8-16-scattered",
        name: "Acht Sechzehntel gemischt",
        fractionRead: {
          numerator: 8,
          denominator: 16,
          rows: 4,
          columns: 4,
          filledCellIndexes: [0, 2, 5, 7, 8, 10, 13, 15],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-12-16",
        name: "Zwoelf Sechzehntel",
        fractionRead: {
          numerator: 12,
          denominator: 16,
          rows: 4,
          columns: 4,
          filledCellIndexes: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-12-16-scattered",
        name: "Zwoelf Sechzehntel gemischt",
        fractionRead: {
          numerator: 12,
          denominator: 16,
          rows: 4,
          columns: 4,
          filledCellIndexes: [0, 1, 3, 4, 6, 7, 8, 9, 11, 12, 14, 15],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
      {
        id: "fraction-13-16",
        name: "Dreizehn Sechzehntel",
        fractionRead: {
          numerator: 13,
          denominator: 16,
          rows: 4,
          columns: 4,
          filledCellIndexes: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
          designKey: "square-grid",
          wholeLabel: "1 Ganzes",
        },
      },
    ],
  },
  {
    id: 12,
    kind: "fraction-read",
    title: "Blumensamen verteilen",
    goal: "Ein Samensack reicht fuer vier gleich grosse Blumenbeete. Bestimme den verteilten Bruchteil und pruefe, ob du ihn kuerzen kannst.",
    requireSimplification: true,
    tools: [],
    variants: [
      supplyVariant({
        id: "flower-seeds-ringelblume",
        name: "Ringelblumen",
        rows: 2,
        columns: 2,
        filledCellIndexes: [0],
        supply: flowerSeedSupply,
      }),
      supplyVariant({
        id: "flower-seeds-sonnenblume",
        name: "Sonnenblumen",
        rows: 2,
        columns: 2,
        filledCellIndexes: [0, 1],
        supply: flowerSeedSupply,
      }),
      supplyVariant({
        id: "flower-seeds-wildblume",
        name: "Wildblumen",
        rows: 2,
        columns: 2,
        filledCellIndexes: [0, 3],
        supply: flowerSeedSupply,
      }),
      supplyVariant({
        id: "flower-seeds-mohnblume",
        name: "Mohnblumen",
        rows: 2,
        columns: 2,
        filledCellIndexes: [0, 1, 2],
        supply: flowerSeedSupply,
      }),
    ],
  },
  {
    id: 13,
    kind: "fraction-read",
    title: "Gemuesebeete duengen",
    goal: "Ein Duengersack reicht fuer neun gleich grosse Gemuesebeete. Bestimme den verteilten Bruchteil und pruefe, ob du ihn kuerzen kannst.",
    requireSimplification: true,
    tools: [],
    variants: [
      supplyVariant({
        id: "fertilizer-herbs",
        name: "Kraeuterbeete",
        rows: 3,
        columns: 3,
        filledCellIndexes: [0, 1],
        supply: vegetableFertilizerSupply,
      }),
      supplyVariant({
        id: "fertilizer-lettuce",
        name: "Salatbeete",
        rows: 3,
        columns: 3,
        filledCellIndexes: [0, 1, 2, 3, 4],
        supply: vegetableFertilizerSupply,
      }),
      supplyVariant({
        id: "fertilizer-tomatoes",
        name: "Tomatenbeete",
        rows: 3,
        columns: 3,
        filledCellIndexes: [0, 1, 2, 3, 4, 5],
        supply: vegetableFertilizerSupply,
      }),
      supplyVariant({
        id: "fertilizer-mixed",
        name: "Gemischte Beete",
        rows: 3,
        columns: 3,
        filledCellIndexes: [0, 2, 3, 5, 6, 8],
        supply: vegetableFertilizerSupply,
      }),
      supplyVariant({
        id: "fertilizer-vegetables",
        name: "Gemuesebeete",
        rows: 3,
        columns: 3,
        filledCellIndexes: [0, 1, 2, 3, 4, 5, 6],
        supply: vegetableFertilizerSupply,
      }),
    ],
  },
  {
    id: 14,
    kind: "fraction-read",
    title: "Das grosse Anzuchtfeld",
    goal: "Ein Samensack reicht fuer sechzehn gleich grosse Anzuchtbeete. Lies den verteilten Bruchteil ab und pruefe, ob du ihn kuerzen kannst.",
    requireSimplification: true,
    tools: [],
    variants: [
      supplyVariant({
        id: "nursery-seeds-first-row",
        name: "Fruehe Aussaat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 1, 2, 3, 4],
        supply: nurserySeedSupply,
      }),
      supplyVariant({
        id: "nursery-seeds-half",
        name: "Morgenaussaat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 1, 2, 3, 4, 5, 6, 7],
        supply: nurserySeedSupply,
      }),
      supplyVariant({
        id: "nursery-seeds-half-mixed",
        name: "Verteilte Aussaat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 2, 5, 7, 8, 10, 13, 15],
        supply: nurserySeedSupply,
      }),
      supplyVariant({
        id: "nursery-seeds-three-quarters",
        name: "Nachmittagsaussaat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
        supply: nurserySeedSupply,
      }),
      supplyVariant({
        id: "nursery-seeds-almost-full",
        name: "Spaete Aussaat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
        supply: nurserySeedSupply,
      }),
    ],
  },
  {
    id: 15,
    kind: "fraction-read",
    title: "Vorratscheck",
    goal: "Pruefe den letzten Sack: Lies den verteilten Bruchteil bei wechselnden Beetfeldern ab und kuerze ihn, wenn es moeglich ist.",
    requireSimplification: true,
    tools: [],
    variants: [
      supplyVariant({
        id: "supply-check-quarters",
        name: "Blumenvorrat",
        rows: 2,
        columns: 2,
        filledCellIndexes: [0, 3],
        supply: flowerSeedSupply,
      }),
      supplyVariant({
        id: "supply-check-ninths",
        name: "Duengervorrat",
        rows: 3,
        columns: 3,
        filledCellIndexes: [0, 2, 3, 5, 6, 8],
        supply: vegetableFertilizerSupply,
      }),
      supplyVariant({
        id: "supply-check-sixteenths-half",
        name: "Saatgutvorrat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 2, 5, 7, 8, 10, 13, 15],
        supply: nurserySeedSupply,
      }),
      supplyVariant({
        id: "supply-check-sixteenths-three-quarters",
        name: "Anzuchtvorrat",
        rows: 4,
        columns: 4,
        filledCellIndexes: [0, 1, 3, 4, 6, 7, 8, 9, 11, 12, 14, 15],
        supply: nurserySeedSupply,
      }),
    ],
  },
  {
    id: 16,
    kind: "rectangle-area",
    title: "Das erste Jungpflanzenbeet",
    goal: "Miss Länge und Breite des Beets und berechne seine Fläche.",
    rewardKind: "tulip",
    tools: [],
    variants: [
      {
        id: "greenhouse-small-3x2",
        name: "Das kleine Tulpenbeet",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Jungpflanzenbeet",
              column: 2,
              row: 2,
              widthCells: 3,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "greenhouse-small-4x2",
        name: "Das breite Tulpenbeet",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Jungpflanzenbeet",
              column: 2,
              row: 2,
              widthCells: 4,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "greenhouse-small-3x3",
        name: "Das quadratische Tulpenbeet",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Jungpflanzenbeet",
              column: 2,
              row: 1,
              widthCells: 3,
              heightCells: 3,
            },
          ],
        },
      },
    ],
  },
  {
    id: 17,
    kind: "rectangle-area",
    title: "Die große Anzuchtbank",
    goal: "Miss die größere Anzuchtbank und berechne Länge mal Breite.",
    rewardKind: "sunflower",
    tools: [],
    variants: [
      {
        id: "greenhouse-large-5x3",
        name: "Die lange Anzuchtbank",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Anzuchtbank",
              column: 1,
              row: 1,
              widthCells: 5,
              heightCells: 3,
            },
          ],
        },
      },
      {
        id: "greenhouse-large-6x2",
        name: "Die schmale Anzuchtbank",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Anzuchtbank",
              column: 1,
              row: 2,
              widthCells: 6,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "greenhouse-large-4x4",
        name: "Die quadratische Anzuchtbank",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Anzuchtbank",
              column: 2,
              row: 1,
              widthCells: 4,
              heightCells: 4,
            },
          ],
        },
      },
    ],
  },
  {
    id: 18,
    kind: "rectangle-area",
    title: "Zwei Beete im Gewächshaus",
    goal: "Miss beide Beete und addiere ihre Flächen zur gesamten Anzuchtfläche.",
    rewardKind: "radish",
    tools: [],
    variants: [
      {
        id: "greenhouse-double-6-plus-4",
        name: "Kräuter und Radieschen",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Kräuterbeet",
              column: 1,
              row: 1,
              widthCells: 3,
              heightCells: 2,
            },
            {
              id: "bed-b",
              label: "Radieschenbeet",
              column: 5,
              row: 2,
              widthCells: 2,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "greenhouse-double-8-plus-6",
        name: "Salat und Jungpflanzen",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Salatbeet",
              column: 1,
              row: 1,
              widthCells: 4,
              heightCells: 2,
            },
            {
              id: "bed-b",
              label: "Jungpflanzenbeet",
              column: 5,
              row: 3,
              widthCells: 3,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "greenhouse-double-9-plus-8",
        name: "Tomaten und Blumen",
        rectangleArea: {
          beds: [
            {
              id: "bed-a",
              label: "Tomatenbeet",
              rewardKind: "tomato",
              column: 1,
              row: 1,
              widthCells: 3,
              heightCells: 3,
            },
            {
              id: "bed-b",
              label: "Blumenbeet",
              rewardKind: "flower",
              column: 5,
              row: 2,
              widthCells: 4,
              heightCells: 2,
            },
          ],
        },
      },
    ],
  },
  {
    id: 19,
    kind: "perimeter",
    title: "Das erste Beet einzäunen",
    goal: "Verlege den Zaun am ganzen Beetrand und berechne den Umfang in Metern.",
    rewardKind: "carrot",
    tools: [],
    variants: [
      {
        id: "fence-rectangle-3x2",
        name: "Das kleine Karottenbeet",
        targetCells: rectangleCells(2, 2, 3, 2),
      },
      {
        id: "fence-rectangle-4x2",
        name: "Das breite Karottenbeet",
        targetCells: rectangleCells(2, 2, 4, 2),
      },
      {
        id: "fence-rectangle-4x3",
        name: "Das große Karottenbeet",
        targetCells: rectangleCells(2, 1, 4, 3),
      },
    ],
  },
  {
    id: 20,
    kind: "perimeter",
    title: "Zaun um die Gartenecke",
    goal: "Zäune die L-Form vollständig ein und addiere alle Randlängen.",
    rewardKind: "flower",
    tools: [],
    variants: [
      {
        id: "fence-l-corner",
        name: "Die Blumen-Ecke",
        targetCells: mergeCells(
          rectangleCells(2, 1, 4, 2),
          rectangleCells(2, 3, 2, 2),
        ),
      },
      {
        id: "fence-l-wide",
        name: "Das breite Eckbeet",
        targetCells: mergeCells(
          rectangleCells(1, 1, 5, 2),
          rectangleCells(4, 3, 2, 2),
        ),
      },
      {
        id: "fence-l-tall",
        name: "Das hohe Eckbeet",
        targetCells: mergeCells(
          rectangleCells(2, 1, 2, 4),
          rectangleCells(4, 3, 3, 2),
        ),
      },
    ],
  },
  {
    id: 21,
    kind: "perimeter",
    title: "Der Zaun mit Aussparung",
    goal: "Beachte auch die inneren Ecken und bestimme den gesamten Materialbedarf.",
    rewardKind: "sunflower",
    tools: [],
    variants: [
      {
        id: "fence-u-shape",
        name: "Das U-Beet",
        targetCells: mergeCells(
          rectangleCells(2, 1, 5, 1),
          rectangleCells(2, 2, 1, 3),
          rectangleCells(6, 2, 1, 3),
        ),
      },
      {
        id: "fence-notch",
        name: "Das Beet mit Nische",
        targetCells: mergeCells(
          rectangleCells(1, 1, 6, 2),
          rectangleCells(1, 3, 2, 2),
          rectangleCells(5, 3, 2, 2),
        ),
      },
      {
        id: "fence-cross",
        name: "Das Kreuzbeet",
        targetCells: mergeCells(
          rectangleCells(3, 1, 2, 5),
          rectangleCells(1, 3, 6, 2),
        ),
      },
    ],
  },
  {
    id: 22,
    kind: "park-area",
    title: "Rasen für die Parkecke",
    goal: "Berechne die Rasenfläche: Ziehe die Fläche der Blumeninsel von der ganzen Parkfläche ab.",
    rewardKind: "flower",
    tools: [],
    variants: [
      {
        id: "park-remaining-24-minus-4",
        name: "Die kleine Blumeninsel",
        parkArea: {
          mode: "remaining",
          resultLabel: "Rasenfläche",
          parts: [
            {
              id: "park-ground",
              label: "Parkfläche",
              kind: "rectangle",
              operation: "add",
              column: 1,
              row: 1,
              widthCells: 6,
              heightCells: 4,
            },
            {
              id: "flower-island",
              label: "Blumeninsel",
              kind: "rectangle",
              operation: "subtract",
              column: 3,
              row: 2,
              widthCells: 2,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "park-remaining-28-minus-6",
        name: "Das Beet am Parkweg",
        parkArea: {
          mode: "remaining",
          resultLabel: "Rasenfläche",
          parts: [
            {
              id: "park-ground",
              label: "Parkfläche",
              kind: "rectangle",
              operation: "add",
              column: 1,
              row: 1,
              widthCells: 7,
              heightCells: 4,
            },
            {
              id: "flower-island",
              label: "Staudenbeet",
              kind: "rectangle",
              operation: "subtract",
              column: 3,
              row: 2,
              widthCells: 3,
              heightCells: 2,
            },
          ],
        },
      },
      {
        id: "park-remaining-35-minus-9",
        name: "Der Kräutergarten",
        parkArea: {
          mode: "remaining",
          resultLabel: "Rasenfläche",
          parts: [
            {
              id: "park-ground",
              label: "Parkfläche",
              kind: "rectangle",
              operation: "add",
              column: 1,
              row: 1,
              widthCells: 7,
              heightCells: 5,
            },
            {
              id: "flower-island",
              label: "Kräuterbeet",
              kind: "rectangle",
              operation: "subtract",
              column: 3,
              row: 2,
              widthCells: 3,
              heightCells: 3,
            },
          ],
        },
      },
    ],
  },
  {
    id: 23,
    kind: "park-area",
    title: "Das dreieckige Parkbeet",
    goal: "Lege ein Hilfsrechteck um das Dreieck und berechne die Hälfte seiner Rechteckfläche.",
    rewardKind: "tulip",
    tools: [],
    variants: [
      {
        id: "park-triangle-4x3",
        name: "Das Tulpendreieck",
        parkArea: {
          mode: "triangle",
          resultLabel: "Dreiecksfläche",
          parts: [
            {
              id: "triangle-bed",
              label: "Tulpenbeet",
              kind: "triangle",
              operation: "add",
              column: 2,
              row: 1,
              widthCells: 4,
              heightCells: 3,
              orientation: "top-left",
            },
          ],
        },
      },
      {
        id: "park-triangle-6x4",
        name: "Die große Wildblumenfläche",
        parkArea: {
          mode: "triangle",
          resultLabel: "Dreiecksfläche",
          parts: [
            {
              id: "triangle-bed",
              label: "Wildblumen",
              kind: "triangle",
              operation: "add",
              column: 1,
              row: 1,
              widthCells: 6,
              heightCells: 4,
              orientation: "top-right",
            },
          ],
        },
      },
      {
        id: "park-triangle-5x4",
        name: "Das Beet an der Wegkurve",
        parkArea: {
          mode: "triangle",
          resultLabel: "Dreiecksfläche",
          parts: [
            {
              id: "triangle-bed",
              label: "Wegbeet",
              kind: "triangle",
              operation: "add",
              column: 2,
              row: 1,
              widthCells: 5,
              heightCells: 4,
              orientation: "bottom-left",
            },
          ],
        },
      },
    ],
  },
  {
    id: 24,
    kind: "park-area",
    title: "Der kleine Schulgartenpark",
    goal: "Zerlege die Parkfläche in Rechteck und Dreieck und addiere beide Teilflächen.",
    rewardKind: "sunflower",
    tools: [],
    variants: [
      {
        id: "park-composite-12-plus-6",
        name: "Die Sonnenblumenbucht",
        parkArea: {
          mode: "composite",
          resultLabel: "Gesamtfläche",
          parts: [
            {
              id: "rectangle-section",
              label: "Rasenstück",
              kind: "rectangle",
              operation: "add",
              column: 1,
              row: 2,
              widthCells: 4,
              heightCells: 3,
            },
            {
              id: "triangle-section",
              label: "Blumenbucht",
              kind: "triangle",
              operation: "add",
              column: 5,
              row: 2,
              widthCells: 4,
              heightCells: 3,
              orientation: "top-left",
            },
          ],
        },
      },
      {
        id: "park-composite-12-plus-4",
        name: "Der Platz am Brunnen",
        parkArea: {
          mode: "composite",
          resultLabel: "Gesamtfläche",
          parts: [
            {
              id: "rectangle-section",
              label: "Rasenstück",
              kind: "rectangle",
              operation: "add",
              column: 1,
              row: 1,
              widthCells: 3,
              heightCells: 4,
            },
            {
              id: "triangle-section",
              label: "Brunnenplatz",
              kind: "triangle",
              operation: "add",
              column: 4,
              row: 1,
              widthCells: 4,
              heightCells: 2,
              orientation: "top-left",
            },
          ],
        },
      },
      {
        id: "park-composite-10-plus-6",
        name: "Die lange Parkwiese",
        parkArea: {
          mode: "composite",
          resultLabel: "Gesamtfläche",
          parts: [
            {
              id: "rectangle-section",
              label: "Parkwiese",
              kind: "rectangle",
              operation: "add",
              column: 1,
              row: 1,
              widthCells: 5,
              heightCells: 2,
            },
            {
              id: "triangle-section",
              label: "Blumenecke",
              kind: "triangle",
              operation: "add",
              column: 6,
              row: 1,
              widthCells: 4,
              heightCells: 3,
              orientation: "top-left",
            },
          ],
        },
      },
    ],
  },
  {
    id: 25,
    kind: "market-budget",
    title: "Genug Saatgut einkaufen",
    goal: "Stelle im Warenkorb genügend Saatgut für die geplante Beetfläche zusammen.",
    rewardKind: "flower",
    tools: [],
    variants: [
      {
        id: "market-coverage-12",
        name: "Saatgut für das Blumenfest",
        market: {
          mode: "coverage",
          requiredCoverage: 12,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 3, price: 4 },
            { id: "medium", label: "Mittlerer Sack", coverage: 5, price: 6 },
            { id: "large", label: "Großer Sack", coverage: 8, price: 9 },
          ],
        },
      },
      {
        id: "market-coverage-14",
        name: "Saatgut für das Gemüsebeet",
        market: {
          mode: "coverage",
          requiredCoverage: 14,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 4, price: 5 },
            { id: "medium", label: "Mittlerer Sack", coverage: 6, price: 7 },
            { id: "large", label: "Großer Sack", coverage: 9, price: 10 },
          ],
        },
      },
      {
        id: "market-coverage-16",
        name: "Saatgut für das Herbstbeet",
        market: {
          mode: "coverage",
          requiredCoverage: 16,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 3, price: 4 },
            { id: "medium", label: "Mittlerer Sack", coverage: 7, price: 8 },
            { id: "large", label: "Großer Sack", coverage: 10, price: 11 },
          ],
        },
      },
    ],
  },
  {
    id: 26,
    kind: "market-budget",
    title: "Das Gartenbudget einhalten",
    goal: "Kaufe genügend Saatgut, ohne das vorgegebene Budget zu überschreiten.",
    rewardKind: "carrot",
    tools: [],
    variants: [
      {
        id: "market-budget-15-for-20",
        name: "Der Einkauf fürs Kräuterbeet",
        market: {
          mode: "budget",
          requiredCoverage: 15,
          budget: 20,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 3, price: 4 },
            { id: "medium", label: "Mittlerer Sack", coverage: 5, price: 7 },
            { id: "large", label: "Großer Sack", coverage: 8, price: 10 },
          ],
        },
      },
      {
        id: "market-budget-18-for-23",
        name: "Die Bestellung fürs Gemüsebeet",
        market: {
          mode: "budget",
          requiredCoverage: 18,
          budget: 23,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 4, price: 5 },
            { id: "medium", label: "Mittlerer Sack", coverage: 7, price: 9 },
            { id: "large", label: "Großer Sack", coverage: 10, price: 12 },
          ],
        },
      },
      {
        id: "market-budget-20-for-25",
        name: "Saatgut für die große Anbaufläche",
        market: {
          mode: "budget",
          requiredCoverage: 20,
          budget: 25,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 5, price: 6 },
            { id: "medium", label: "Mittlerer Sack", coverage: 8, price: 9 },
            { id: "large", label: "Großer Sack", coverage: 12, price: 14 },
          ],
        },
      },
    ],
  },
  {
    id: 27,
    kind: "market-budget",
    title: "Die günstigste Lösung finden",
    goal: "Vergleiche alle Sackgrößen und finde den günstigsten Einkauf für die benötigte Fläche.",
    rewardKind: "radish",
    tools: [],
    variants: [
      {
        id: "market-optimum-18",
        name: "Preisvergleich am Blumenstand",
        market: {
          mode: "optimization",
          requiredCoverage: 18,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 3, price: 4 },
            { id: "medium", label: "Mittlerer Sack", coverage: 6, price: 7 },
            { id: "large", label: "Großer Sack", coverage: 10, price: 11 },
          ],
        },
      },
      {
        id: "market-optimum-20",
        name: "Der sparsame Großeinkauf",
        market: {
          mode: "optimization",
          requiredCoverage: 20,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 4, price: 5 },
            { id: "medium", label: "Mittlerer Sack", coverage: 7, price: 8 },
            { id: "large", label: "Großer Sack", coverage: 11, price: 12 },
          ],
        },
      },
      {
        id: "market-optimum-24",
        name: "Das beste Marktangebot",
        market: {
          mode: "optimization",
          requiredCoverage: 24,
          products: [
            { id: "small", label: "Kleiner Sack", coverage: 5, price: 6 },
            { id: "medium", label: "Mittlerer Sack", coverage: 8, price: 9 },
            { id: "large", label: "Großer Sack", coverage: 13, price: 14 },
          ],
        },
      },
    ],
  },
];
