from __future__ import annotations

import math
from datetime import datetime
from statistics import fmean, median

from .contracts import EstimateRequest, MarketQuote, PriceEstimate, Provenance


def estimate_price(request: EstimateRequest) -> PriceEstimate:
    eligible = [
        quote
        for quote in request.quotes
        if quote.currency == request.currency
        and quote.match_confidence >= request.minimum_match_confidence
        and quote.observed_at <= request.as_of
    ]
    filtered = _remove_outliers(eligible)
    rejected_count = len(request.quotes) - len(filtered)
    confidence = _confidence(filtered, request.as_of, request.freshness_half_life_days)
    confidence_band = "high" if confidence >= 0.72 else "medium" if confidence >= 0.45 else "low"
    can_show_point = len(filtered) >= 3 and confidence_band != "low"
    prices = sorted(quote.delivered_price_minor for quote in filtered)
    includes_demo = any(quote.provenance is Provenance.DEMO_NON_LIVE for quote in filtered)
    is_live = bool(filtered) and all(
        quote.is_live and quote.provenance is Provenance.OFFICIAL_OR_CONTRACTED_API for quote in filtered
    )
    disclosure = (
        "包含演示行情，非实时；仅用于体验估值流程。"
        if includes_demo
        else "手工/历史报价，不代表实时成交价。"
        if not is_live
        else "来自已配置的官方或签约 API；报价仍可能延迟。"
    )
    return PriceEstimate(
        currency=request.currency,
        sample_count=len(filtered),
        rejected_count=rejected_count,
        median_minor=_quantile(prices, 0.5) if can_show_point else None,
        lower_quartile_minor=_quantile(prices, 0.25) if prices else None,
        upper_quartile_minor=_quantile(prices, 0.75) if prices else None,
        confidence=confidence,
        confidence_band=confidence_band,
        live=is_live,
        disclosure=disclosure,
        calculated_at=request.as_of,
    )


def _remove_outliers(quotes: list[MarketQuote]) -> list[MarketQuote]:
    if len(quotes) < 5:
        return quotes
    values = [quote.delivered_price_minor for quote in quotes]
    center = median(values)
    absolute_deviations = [abs(value - center) for value in values]
    mad = median(absolute_deviations)
    if mad == 0:
        return [quote for quote in quotes if quote.delivered_price_minor == center]
    threshold = 3.5 * 1.4826 * mad
    return [quote for quote in quotes if abs(quote.delivered_price_minor - center) <= threshold]


def _confidence(quotes: list[MarketQuote], as_of: datetime, half_life_days: int) -> float:
    if not quotes:
        return 0.0
    freshness_scores: list[float] = []
    for quote in quotes:
        age_days = max(0.0, (as_of - quote.observed_at).total_seconds() / 86_400)
        freshness_scores.append(math.exp(-math.log(2) * age_days / half_life_days))
    match_score = fmean(quote.match_confidence for quote in quotes)
    freshness_score = fmean(freshness_scores)
    sample_score = min(1.0, len(quotes) / 8)
    provider_score = min(1.0, len({quote.provider_id for quote in quotes}) / 3)
    return max(0.0, min(1.0, 0.4 * match_score + 0.3 * freshness_score + 0.2 * sample_score + 0.1 * provider_score))


def _quantile(sorted_values: list[int], probability: float) -> int:
    if not sorted_values:
        raise ValueError("cannot calculate a quantile of an empty list")
    if len(sorted_values) == 1:
        return sorted_values[0]
    position = (len(sorted_values) - 1) * probability
    lower_index = math.floor(position)
    upper_index = math.ceil(position)
    if lower_index == upper_index:
        return sorted_values[lower_index]
    lower = sorted_values[lower_index]
    upper = sorted_values[upper_index]
    return round(lower + (upper - lower) * (position - lower_index))
