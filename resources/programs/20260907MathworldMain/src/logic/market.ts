import type { MarketConfig } from "../types";

export type MarketQuantities = Record<string, number>;

export type MarketTotals = {
  cost: number;
  coverage: number;
  itemCount: number;
};

export function getMarketTotals(
  market: MarketConfig,
  quantities: MarketQuantities,
): MarketTotals {
  return market.products.reduce<MarketTotals>(
    (totals, product) => {
      const quantity = quantities[product.id] ?? 0;

      return {
        cost: totals.cost + quantity * product.price,
        coverage: totals.coverage + quantity * product.coverage,
        itemCount: totals.itemCount + quantity,
      };
    },
    { cost: 0, coverage: 0, itemCount: 0 },
  );
}

export function getMinimumMarketCost(market: MarketConfig) {
  const costs = Array.from(
    { length: market.requiredCoverage + 1 },
    () => Number.POSITIVE_INFINITY,
  );
  costs[0] = 0;

  for (let coverage = 0; coverage < market.requiredCoverage; coverage += 1) {
    if (!Number.isFinite(costs[coverage])) {
      continue;
    }

    for (const product of market.products) {
      const nextCoverage = Math.min(
        market.requiredCoverage,
        coverage + product.coverage,
      );
      costs[nextCoverage] = Math.min(
        costs[nextCoverage],
        costs[coverage] + product.price,
      );
    }
  }

  return costs[market.requiredCoverage];
}

export function isMarketSelectionComplete(
  market: MarketConfig,
  totals: MarketTotals,
) {
  if (totals.coverage < market.requiredCoverage) {
    return false;
  }

  if (market.mode === "budget") {
    return market.budget !== undefined && totals.cost <= market.budget;
  }

  if (market.mode === "optimization") {
    return totals.cost === getMinimumMarketCost(market);
  }

  return true;
}
