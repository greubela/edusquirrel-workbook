import { useId, useMemo, type CSSProperties } from "react";
import "../../parametrische-svg-saecke-v3/sack-generator-v3.js";
import type { RewardKind } from "../types";

type ParametricSackProps = {
  ariaHidden?: boolean;
  className?: string;
  height: number;
  kind: "seed" | "fertilizer";
  plantKind?: RewardKind;
  style?: CSSProperties;
  title: string;
  width: number;
};

type ParametricSackSvgProps = ParametricSackProps & {
  x: number;
  y: number;
};

const MIN_SOURCE_WIDTH = 140;
const MIN_SOURCE_HEIGHT = 180;
const MAX_SOURCE_WIDTH = 1000;
const MAX_SOURCE_HEIGHT = 1200;

function clamp(value: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(maximum, value));
}

function getSourceDimensions(width: number, height: number) {
  const safeWidth = Math.max(width, 1);
  const safeHeight = Math.max(height, 1);
  const minimumScale = Math.max(
    MIN_SOURCE_WIDTH / safeWidth,
    MIN_SOURCE_HEIGHT / safeHeight,
    1,
  );
  const maximumScale = Math.min(
    MAX_SOURCE_WIDTH / safeWidth,
    MAX_SOURCE_HEIGHT / safeHeight,
  );
  const sourceScale = Math.min(minimumScale, maximumScale);

  return {
    height: clamp(safeHeight * sourceScale, MIN_SOURCE_HEIGHT, MAX_SOURCE_HEIGHT),
    width: clamp(safeWidth * sourceScale, MIN_SOURCE_WIDTH, MAX_SOURCE_WIDTH),
  };
}

export function ParametricSack({
  ariaHidden = false,
  className = "",
  height,
  kind,
  plantKind,
  style,
  title,
  width,
}: ParametricSackProps) {
  const reactId = useId();
  const sourceDimensions = useMemo(
    () => getSourceDimensions(width, height),
    [height, width],
  );
  const markup = useMemo(
    () =>
      window.SackGraphics.markup({
        height: sourceDimensions.height,
        idPrefix: `sack-${reactId}`,
        kind,
        plantKind,
        title,
        width: sourceDimensions.width,
      }),
    [kind, plantKind, reactId, sourceDimensions.height, sourceDimensions.width, title],
  );

  return (
    <span
      aria-hidden={ariaHidden || undefined}
      className={`parametric-sack ${className}`.trim()}
      dangerouslySetInnerHTML={{ __html: markup }}
      style={style}
    />
  );
}

export function ParametricSackSvg({
  className = "",
  height,
  kind,
  plantKind,
  title,
  width,
  x,
  y,
}: ParametricSackSvgProps) {
  const reactId = useId();
  const sourceDimensions = useMemo(
    () => getSourceDimensions(width, height),
    [height, width],
  );
  const markup = useMemo(
    () =>
      window.SackGraphics.markup({
        height: sourceDimensions.height,
        idPrefix: `workspace-${reactId}`,
        kind,
        plantKind,
        title,
        width: sourceDimensions.width,
      }),
    [kind, plantKind, reactId, sourceDimensions.height, sourceDimensions.width, title],
  );
  const displayScale = Math.min(
    width / sourceDimensions.width,
    height / sourceDimensions.height,
  );
  const offsetX = (width - sourceDimensions.width * displayScale) / 2;
  const offsetY = (height - sourceDimensions.height * displayScale) / 2;

  return (
    <g
      className={`parametric-sack-svg ${className}`.trim()}
      dangerouslySetInnerHTML={{ __html: markup }}
      transform={`translate(${x + offsetX} ${y + offsetY}) scale(${displayScale})`}
    />
  );
}
