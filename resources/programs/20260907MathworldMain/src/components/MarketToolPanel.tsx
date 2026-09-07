import { getMinimumMarketCost, type MarketQuantities, type MarketTotals } from "../logic/market";
import type { MarketConfig } from "../types";

type MarketToolPanelProps = {
  canAdvance: boolean;
  hasNextMission: boolean;
  isComplete: boolean;
  market: MarketConfig;
  onChangeQuantity: (productId: string, delta: number) => void;
  onClearBasket: () => void;
  onGoToNextMission: () => void;
  onGoToNextMissionInDevMode: () => void;
  onGoToPreviousMissionInDevMode: () => void;
  primaryActionLabel?: string;
  quantities: MarketQuantities;
  showDevControls: boolean;
  totals: MarketTotals;
};

export function MarketToolPanel({
  canAdvance,
  hasNextMission,
  isComplete,
  market,
  onChangeQuantity,
  onClearBasket,
  onGoToNextMission,
  onGoToNextMissionInDevMode,
  onGoToPreviousMissionInDevMode,
  primaryActionLabel = "Nächstes Level",
  quantities,
  showDevControls,
  totals,
}: MarketToolPanelProps) {
  const missingCoverage = Math.max(0, market.requiredCoverage - totals.coverage);
  const minimumCost =
    market.mode === "optimization" ? getMinimumMarketCost(market) : null;
  const isOverBudget =
    market.mode === "budget" &&
    market.budget !== undefined &&
    totals.cost > market.budget;

  let basketHint = `Es fehlen noch ${missingCoverage} m².`;

  if (totals.coverage >= market.requiredCoverage) {
    if (isOverBudget) {
      basketHint = `Das Budget ist um ${totals.cost - (market.budget ?? 0)} € überschritten.`;
    } else if (
      market.mode === "optimization" &&
      minimumCost !== null &&
      totals.cost > minimumCost
    ) {
      basketHint = "Es gibt noch einen günstigeren ausreichenden Einkauf.";
    } else if (isComplete) {
      basketHint = "Der Warenkorb erfüllt alle Bedingungen.";
    } else {
      basketHint = "Die benötigte Fläche ist abgedeckt.";
    }
  }

  return (
    <aside className="tool-panel" aria-label="Marktstände und Warenkorb">
      <section className="tool-panel__content">
        <p className="eyebrow">Saatgut auswählen</p>
        <div className="market-product-list">
          {market.products.map((product, productIndex) => {
            const quantity = quantities[product.id] ?? 0;

            return (
              <div className="market-product-control" key={product.id}>
                <span
                  aria-hidden="true"
                  className={`market-product-icon market-product-icon--${productIndex + 1}`}
                />
                <span className="market-product-control__copy">
                  <strong>{product.label}</strong>
                  <small>
                    {product.coverage} m² · {product.price} €
                  </small>
                </span>
                <span className="market-quantity-stepper">
                  <button
                    aria-label={`${product.label} aus dem Warenkorb entfernen`}
                    disabled={quantity === 0}
                    onClick={() => onChangeQuantity(product.id, -1)}
                    title="Einen Sack entfernen"
                    type="button"
                  >
                    −
                  </button>
                  <output aria-label={`${quantity} ${product.label}`}>{quantity}</output>
                  <button
                    aria-label={`${product.label} in den Warenkorb legen`}
                    disabled={quantity >= 9}
                    onClick={() => onChangeQuantity(product.id, 1)}
                    title="Einen Sack hinzufügen"
                    type="button"
                  >
                    +
                  </button>
                </span>
              </div>
            );
          })}
        </div>

        <div className="market-basket-summary">
          <div>
            <span>Reichweite</span>
            <strong>
              {totals.coverage} / {market.requiredCoverage} m²
            </strong>
          </div>
          <div>
            <span>Kosten</span>
            <strong>
              {totals.cost} €
              {market.mode === "budget" ? ` / ${market.budget} €` : ""}
            </strong>
          </div>
          <p className={isComplete ? "market-basket-hint market-basket-hint--success" : "market-basket-hint"}>
            {basketHint}
          </p>
        </div>

        <div className="action-row">
          <button
            className="secondary-button"
            disabled={totals.itemCount === 0}
            onClick={onClearBasket}
            type="button"
          >
            Warenkorb leeren
          </button>
          <button
            className="primary-button"
            disabled={!canAdvance || !hasNextMission}
            onClick={onGoToNextMission}
            type="button"
          >
            {primaryActionLabel}
          </button>
        </div>
        {showDevControls ? (
          <div className="dev-navigation">
            <button
              className="dev-button dev-button--previous"
              onClick={onGoToPreviousMissionInDevMode}
              type="button"
            >
              Dev: Voriges Level
            </button>
            <button
              className="dev-button dev-button--next"
              onClick={onGoToNextMissionInDevMode}
              type="button"
            >
              Dev: Nächstes Level
            </button>
          </div>
        ) : null}
      </section>
    </aside>
  );
}
