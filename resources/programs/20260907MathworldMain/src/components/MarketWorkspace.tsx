import type { MarketQuantities, MarketTotals } from "../logic/market";
import type { MarketConfig, RewardKind } from "../types";
import { ParametricSackSvg } from "./ParametricSack";

type MarketWorkspaceProps = {
  isComplete: boolean;
  market: MarketConfig;
  quantities: MarketQuantities;
  rewardKind: RewardKind;
  totals: MarketTotals;
};

const stallPositions = [
  { height: 118, width: 82, x: 105, y: 178 },
  { height: 132, width: 92, x: 332, y: 164 },
  { height: 148, width: 104, x: 548, y: 148 },
];

export function MarketWorkspace({
  isComplete,
  market,
  quantities,
  rewardKind,
  totals,
}: MarketWorkspaceProps) {
  const basketItems = market.products.flatMap((product, productIndex) =>
    Array.from({ length: quantities[product.id] ?? 0 }, (_, itemIndex) => ({
      id: `${product.id}-${itemIndex}`,
      productIndex,
    })),
  );
  const visibleBasketItems = basketItems.slice(0, 12);
  const hiddenBasketItemCount = Math.max(0, basketItems.length - visibleBasketItems.length);

  return (
    <section className="board-panel board-panel--market" aria-label="Gartenmarkt-Arbeitsfläche">
      <div className="market-board">
        <svg
          aria-label="Gartenmarkt mit drei Saatgutsäcken und einem Warenkorb"
          role="img"
          viewBox="0 0 760 520"
        >
          <defs>
            <linearGradient id="market-ground" x1="0" x2="1" y1="0" y2="1">
              <stop offset="0" stopColor="#f3e8bb" />
              <stop offset=".62" stopColor="#d9c98e" />
              <stop offset="1" stopColor="#bcae7e" />
            </linearGradient>
            <pattern height="34" id="market-ground-speckles" patternUnits="userSpaceOnUse" width="40">
              <circle cx="8" cy="11" fill="#8f8157" opacity=".18" r="1.5" />
              <circle cx="30" cy="25" fill="#fff8d6" opacity=".45" r="1.8" />
            </pattern>
          </defs>

          <rect className="market-board__sky" height="520" rx="16" width="760" />
          <path className="market-board__ground" d="M 0 128 H 760 V 520 H 0 Z" />
          <path className="market-board__ground-texture" d="M 0 128 H 760 V 520 H 0 Z" />

          <g className="market-stall">
            <path className="market-stall__roof" d="M 44 86 H 716 L 686 132 H 73 Z" />
            {Array.from({ length: 10 }, (_, stripeIndex) => (
              <path
                className={stripeIndex % 2 === 0 ? "market-stall__stripe" : "market-stall__stripe market-stall__stripe--light"}
                d={`M ${56 + stripeIndex * 66} 87 H ${118 + stripeIndex * 66} L ${110 + stripeIndex * 66} 131 H ${83 + stripeIndex * 66} Z`}
                key={stripeIndex}
              />
            ))}
            <path className="market-stall__counter" d="M 67 304 H 693 V 334 H 67 Z" />
            <path className="market-stall__legs" d="M 92 334 V 430 M 668 334 V 430" />
          </g>

          {market.products.map((product, productIndex) => {
            const position = stallPositions[productIndex];
            const quantity = quantities[product.id] ?? 0;

            return (
              <g className="market-offer" key={product.id}>
                <ParametricSackSvg
                  ariaHidden
                  height={position.height}
                  kind="seed"
                  plantKind={rewardKind}
                  title={product.label}
                  width={position.width}
                  x={position.x}
                  y={position.y}
                />
                <g
                  className="market-offer__sign"
                  transform={`translate(${position.x + position.width / 2} 321)`}
                >
                  <rect height="54" rx="7" width="144" x="-72" y="-27" />
                  <text className="market-offer__name" textAnchor="middle" y="-6">
                    {product.label}
                  </text>
                  <text className="market-offer__facts" textAnchor="middle" y="15">
                    {product.coverage} m² · {product.price} €
                  </text>
                </g>
                {quantity > 0 ? (
                  <g
                    className="market-offer__quantity"
                    transform={`translate(${position.x + position.width - 2} ${position.y + 10})`}
                  >
                    <circle r="18" />
                    <text dominantBaseline="middle" textAnchor="middle" y="1">
                      {quantity}
                    </text>
                  </g>
                ) : null}
              </g>
            );
          })}

          <g className="market-basket" transform="translate(380 444)">
            <path className="market-basket__handle" d="M -92 -26 Q 0 -96 92 -26" />
            <path className="market-basket__body" d="M -112 -34 H 112 L 88 42 H -88 Z" />
            <path className="market-basket__weave" d="M -74 -30 L -61 37 M -31 -30 L -24 40 M 31 -30 L 24 40 M 74 -30 L 61 37 M -98 -6 H 98 M -92 18 H 92" />
            {visibleBasketItems.map((item, itemIndex) => (
              <rect
                className={`market-basket__item market-basket__item--${item.productIndex + 1}`}
                height="22"
                key={item.id}
                rx="4"
                transform={`rotate(${(itemIndex % 3 - 1) * 5})`}
                width="30"
                x={-82 + (itemIndex % 6) * 30}
                y={-45 - Math.floor(itemIndex / 6) * 22}
              />
            ))}
            {hiddenBasketItemCount > 0 ? (
              <g className="market-basket__more" transform="translate(100 -58)">
                <circle r="17" />
                <text dominantBaseline="middle" textAnchor="middle" y="1">
                  +{hiddenBasketItemCount}
                </text>
              </g>
            ) : null}
          </g>

          {isComplete ? (
            <g className="market-success-stamp" transform="translate(650 430) rotate(-8)">
              <circle r="48" />
              <path d="M -20 1 L -7 15 L 22 -18" />
              <text textAnchor="middle" y="68">
                EINKAUF PASST
              </text>
            </g>
          ) : null}
        </svg>
        <div
          className={
            isComplete
              ? "market-board__status market-board__status--complete"
              : "market-board__status"
          }
        >
          <span aria-hidden="true" className="market-basket-icon" />
          {totals.coverage} von {market.requiredCoverage} m² · {totals.cost} €
          {market.mode === "budget" ? ` von ${market.budget} €` : ""}
        </div>
      </div>
    </section>
  );
}
