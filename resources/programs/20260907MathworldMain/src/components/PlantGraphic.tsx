import type { RewardKind } from "../types";

type PlantGraphicProps = {
  rewardKind: RewardKind;
};

export function PlantGraphic({ rewardKind }: PlantGraphicProps) {
  if (rewardKind === "radish") {
    return (
      <>
        <ellipse className="reward-plant__ground" cx="20" cy="36" rx="11" ry="3.5" />
        <ellipse className="reward-plant__radish-bulb" cx="20" cy="27" rx="7.2" ry="8.4" />
        <path className="reward-plant__radish-highlight" d="M 17 23 C 15 27, 16 31, 19 33" />
        <path className="reward-plant__radish-root" d="M 20 34 L 18 38 M 20 34 L 22 38" />
        <ellipse className="reward-plant__leaf" cx="15" cy="17" rx="6.8" ry="3.2" transform="rotate(-38 15 17)" />
        <ellipse className="reward-plant__leaf reward-plant__leaf--light" cx="21" cy="14" rx="6.4" ry="3" transform="rotate(-82 21 14)" />
        <ellipse className="reward-plant__leaf" cx="25" cy="18" rx="6.8" ry="3.2" transform="rotate(35 25 18)" />
        <path className="reward-plant__leaf-vein" d="M 15 17 C 17 16, 19 15, 21 14 M 21 14 C 22 16, 24 17, 25 18" />
      </>
    );
  }

  if (rewardKind === "carrot") {
    return (
      <>
        <ellipse className="reward-plant__ground" cx="20" cy="36" rx="10" ry="3.2" />
        <polygon className="reward-plant__carrot-root" points="14,17 26,17 20,36" />
        <path className="reward-plant__carrot-ridge" d="M 17 23 L 22 22 M 17 29 L 21 28" />
        <ellipse className="reward-plant__leaf" cx="15" cy="13" rx="6.5" ry="3" transform="rotate(-35 15 13)" />
        <ellipse className="reward-plant__leaf reward-plant__leaf--light" cx="20" cy="11" rx="7" ry="3.2" transform="rotate(-90 20 11)" />
        <ellipse className="reward-plant__leaf" cx="25" cy="13" rx="6.5" ry="3" transform="rotate(35 25 13)" />
      </>
    );
  }

  if (rewardKind === "tomato") {
    return (
      <>
        <ellipse className="reward-plant__ground" cx="20" cy="36" rx="10.5" ry="3.2" />
        <path className="reward-plant__stem" d="M 20 35 C 19 29, 21 22, 20 14" />
        <path className="reward-plant__stem reward-plant__stem--side" d="M 20 24 C 16 22, 14 20, 12 18 M 20 27 C 24 25, 27 23, 29 20" />
        <ellipse className="reward-plant__leaf" cx="15" cy="16" rx="6.2" ry="2.8" transform="rotate(-28 15 16)" />
        <ellipse className="reward-plant__leaf reward-plant__leaf--light" cx="25" cy="18" rx="6.2" ry="2.8" transform="rotate(30 25 18)" />
        <circle className="reward-plant__tomato-fruit" cx="13" cy="24" r="5.3" />
        <circle className="reward-plant__tomato-fruit reward-plant__tomato-fruit--light" cx="27" cy="26" r="5.1" />
        <circle className="reward-plant__tomato-fruit" cx="20" cy="31" r="5.6" />
        <path className="reward-plant__tomato-calyx" d="M 10 20 L 13 23 L 16 20 M 24 22 L 27 25 L 30 22 M 17 27 L 20 30 L 23 27" />
        <path className="reward-plant__tomato-highlight" d="M 11 23 Q 12 21 14 21 M 25 25 Q 26 23 28 23 M 18 30 Q 19 28 21 28" />
      </>
    );
  }

  if (rewardKind === "sunflower") {
    return (
      <>
        <ellipse className="reward-plant__ground" cx="20" cy="36" rx="10.5" ry="3.2" />
        <path className="reward-plant__stem" d="M 20 34 C 19 27, 21 21, 20 15" />
        <ellipse className="reward-plant__leaf" cx="15" cy="26" rx="6.3" ry="3" transform="rotate(-35 15 26)" />
        <ellipse className="reward-plant__leaf reward-plant__leaf--light" cx="25" cy="23" rx="6.3" ry="3" transform="rotate(35 25 23)" />
        <g className="reward-plant__sunflower" transform="translate(20 12)">
          {Array.from({ length: 8 }, (_, petalIndex) => (
            <ellipse
              className="reward-plant__sunflower-petal"
              cx="0"
              cy="-6"
              key={petalIndex}
              rx="2.7"
              ry="5"
              transform={`rotate(${petalIndex * 45})`}
            />
          ))}
          <circle className="reward-plant__sunflower-center" cx="0" cy="0" r="4.4" />
        </g>
      </>
    );
  }

  if (rewardKind === "tulip") {
    return (
      <>
        <ellipse className="reward-plant__ground" cx="20" cy="36" rx="10.5" ry="3.2" />
        <path className="reward-plant__stem" d="M 20 34 C 19 27, 21 21, 20 15" />
        <path className="reward-plant__tulip-leaf" d="M 19 29 C 9 26, 10 18, 19 24 Z" />
        <path className="reward-plant__tulip-leaf reward-plant__tulip-leaf--right" d="M 21 27 C 31 23, 30 17, 21 22 Z" />
        <path className="reward-plant__tulip-bloom" d="M 13 13 L 14 6 L 20 10 L 26 5 L 27 13 C 26 20, 14 20, 13 13 Z" />
      </>
    );
  }

  return (
    <>
      <ellipse className="reward-plant__ground" cx="20" cy="36" rx="10.5" ry="3.2" />
      <path className="reward-plant__stem" d="M 20 33 C 19 27, 21 22, 20 17" />
      <path className="reward-plant__stem reward-plant__stem--side" d="M 18 32 C 16 28, 15 24, 13 21" />
      <path className="reward-plant__stem reward-plant__stem--side" d="M 22 32 C 24 28, 25 25, 27 22" />
      <ellipse className="reward-plant__leaf" cx="15" cy="24" rx="6.3" ry="3" transform="rotate(-33 15 24)" />
      <ellipse className="reward-plant__leaf reward-plant__leaf--light" cx="25" cy="23" rx="6.3" ry="3" transform="rotate(35 25 23)" />
      <circle className="reward-plant__tiny-bloom" cx="13" cy="20" r="2.1" />
      <circle className="reward-plant__tiny-bloom reward-plant__tiny-bloom--blue" cx="27" cy="21" r="1.9" />
      <g className="reward-plant__flower" transform="translate(20 14)">
        <ellipse className="reward-plant__petal" cx="0" cy="-5" rx="3.1" ry="4.4" />
        <ellipse className="reward-plant__petal" cx="4.7" cy="-1.4" rx="3.1" ry="4.4" transform="rotate(72 4.7 -1.4)" />
        <ellipse className="reward-plant__petal" cx="2.8" cy="4.2" rx="3.1" ry="4.4" transform="rotate(144 2.8 4.2)" />
        <ellipse className="reward-plant__petal" cx="-2.8" cy="4.2" rx="3.1" ry="4.4" transform="rotate(-144 -2.8 4.2)" />
        <ellipse className="reward-plant__petal" cx="-4.7" cy="-1.4" rx="3.1" ry="4.4" transform="rotate(-72 -4.7 -1.4)" />
        <circle className="reward-plant__bloom" cx="0" cy="0" r="3.2" />
      </g>
    </>
  );
}

export function PlantIcon({
  className = "",
  decorative = false,
  rewardKind,
  title,
}: PlantGraphicProps & {
  className?: string;
  decorative?: boolean;
  title: string;
}) {
  return (
    <svg
      aria-hidden={decorative || undefined}
      aria-label={decorative ? undefined : title}
      className={`plant-icon ${className}`.trim()}
      role={decorative ? undefined : "img"}
      viewBox="0 0 40 40"
    >
      {decorative ? null : <title>{title}</title>}
      <g className="reward-plant">
        <PlantGraphic rewardKind={rewardKind} />
      </g>
    </svg>
  );
}
