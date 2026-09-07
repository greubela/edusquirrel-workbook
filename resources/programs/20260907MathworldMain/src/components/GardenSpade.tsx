type GardenSpadeIconProps = {
  className?: string;
};

export function GardenSpadeArtwork() {
  return (
    <g className="garden-spade-artwork">
      <path
        className="garden-spade-artwork__grip"
        d="M22 10 C22 3 58 3 58 10 L58 22 C58 27 52 30 47 27 L47 22 C47 19 44 17 40 17 C36 17 33 19 33 22 L33 27 C28 30 22 27 22 22 Z"
      />
      <path
        className="garden-spade-artwork__grip-highlight"
        d="M29 11 C34 8 46 7 53 10"
      />
      <path
        className="garden-spade-artwork__shaft-shadow"
        d="M37 24 L45 24 L50 84 L35 84 Z"
      />
      <path
        className="garden-spade-artwork__shaft"
        d="M36 24 L43 24 L46 84 L34 84 Z"
      />
      <path
        className="garden-spade-artwork__shaft-highlight"
        d="M38.5 28 L39.5 78"
      />
      <path
        className="garden-spade-artwork__collar"
        d="M31 78 L49 78 L52 90 L28 90 Z"
      />
      <path
        className="garden-spade-artwork__blade"
        d="M18 86 Q40 78 62 86 L58 105 Q54 117 40 122 Q26 117 22 105 Z"
      />
      <path
        className="garden-spade-artwork__blade-shine"
        d="M27 91 Q38 87 50 90 L47 108 Q44 113 38 115"
      />
      <path
        className="garden-spade-artwork__blade-edge"
        d="M23 105 Q40 116 57 105"
      />
    </g>
  );
}

export function GardenSpadeIcon({ className = "" }: GardenSpadeIconProps) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      focusable="false"
      viewBox="0 0 80 126"
    >
      <GardenSpadeArtwork />
    </svg>
  );
}
