from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from typing import cast


class Provenance(StrEnum):
    MANUAL = "manual"
    DEMO_NON_LIVE = "demo_non_live"
    OFFICIAL_OR_CONTRACTED_API = "official_or_contracted_api"


@dataclass(frozen=True, slots=True)
class MarketQuote:
    provider_id: str
    title: str
    model: str | None
    condition: str
    price_minor: int
    shipping_minor: int
    currency: str
    observed_at: datetime
    match_confidence: float
    provenance: Provenance
    is_live: bool
    disclosure: str

    def __post_init__(self) -> None:
        if not self.provider_id or not self.title:
            raise ValueError("provider_id and title are required")
        if self.price_minor < 0 or self.shipping_minor < 0:
            raise ValueError("prices must use non-negative minor units")
        if len(self.currency) != 3 or not self.currency.isupper() or not self.currency.isalpha():
            raise ValueError("currency must be a three-letter uppercase code")
        if self.observed_at.tzinfo is None:
            raise ValueError("observed_at must include a timezone")
        if not 0.0 <= self.match_confidence <= 1.0:
            raise ValueError("match_confidence must be between zero and one")
        if self.provenance is Provenance.DEMO_NON_LIVE:
            if self.is_live:
                raise ValueError("demonstration quotes cannot be live")
            if "非实时" not in self.disclosure and "non-live" not in self.disclosure.lower():
                raise ValueError("demonstration disclosure must state that data is non-live")
        if self.provenance is Provenance.MANUAL and self.is_live:
            raise ValueError("manual quotes cannot claim live status")

    @property
    def delivered_price_minor(self) -> int:
        return self.price_minor + self.shipping_minor

    @classmethod
    def from_dict(cls, value: dict[str, object]) -> MarketQuote:
        return cls(
            provider_id=_string(value, "providerId", 64),
            title=_string(value, "title", 256),
            model=_optional_string(value, "model", 128),
            condition=_string(value, "condition", 32),
            price_minor=_integer(value, "priceMinor"),
            shipping_minor=_integer(value, "shippingMinor"),
            currency=_string(value, "currency", 3),
            observed_at=_timestamp(value, "observedAt"),
            match_confidence=_number(value, "matchConfidence"),
            provenance=Provenance(_string(value, "provenance", 64)),
            is_live=_boolean(value, "isLive"),
            disclosure=_string(value, "disclosure", 512),
        )


@dataclass(frozen=True, slots=True)
class EstimateRequest:
    currency: str
    as_of: datetime
    quotes: tuple[MarketQuote, ...]
    minimum_match_confidence: float = 0.55
    freshness_half_life_days: int = 30

    def __post_init__(self) -> None:
        if len(self.currency) != 3 or not self.currency.isupper() or not self.currency.isalpha():
            raise ValueError("currency must be a three-letter uppercase code")
        if self.as_of.tzinfo is None:
            raise ValueError("as_of must include a timezone")
        if len(self.quotes) > 100:
            raise ValueError("at most 100 quotes are accepted")
        if not 0.0 <= self.minimum_match_confidence <= 1.0:
            raise ValueError("minimum_match_confidence must be between zero and one")
        if not 1 <= self.freshness_half_life_days <= 365:
            raise ValueError("freshness_half_life_days must be between 1 and 365")

    @classmethod
    def from_dict(cls, value: dict[str, object]) -> EstimateRequest:
        raw_quotes_value = value.get("quotes")
        if not isinstance(raw_quotes_value, list):
            raise ValueError("quotes must be an array")
        raw_quotes = cast(list[object], raw_quotes_value)
        quotes: list[MarketQuote] = []
        for item in raw_quotes:
            if not isinstance(item, dict):
                raise ValueError("each quote must be an object")
            untyped_quote = cast(dict[object, object], item)
            if not all(isinstance(key, str) for key in untyped_quote):
                raise ValueError("each quote key must be a string")
            quotes.append(MarketQuote.from_dict(cast(dict[str, object], untyped_quote)))
        return cls(
            currency=_string(value, "currency", 3),
            as_of=_timestamp(value, "asOf"),
            quotes=tuple(quotes),
            minimum_match_confidence=_optional_number(value, "minimumMatchConfidence", 0.55),
            freshness_half_life_days=_optional_integer(value, "freshnessHalfLifeDays", 30),
        )


@dataclass(frozen=True, slots=True)
class PriceEstimate:
    currency: str
    sample_count: int
    rejected_count: int
    median_minor: int | None
    lower_quartile_minor: int | None
    upper_quartile_minor: int | None
    confidence: float
    confidence_band: str
    live: bool
    disclosure: str
    calculated_at: datetime

    def to_dict(self) -> dict[str, object]:
        return {
            "currency": self.currency,
            "sampleCount": self.sample_count,
            "rejectedCount": self.rejected_count,
            "medianMinor": self.median_minor,
            "lowerQuartileMinor": self.lower_quartile_minor,
            "upperQuartileMinor": self.upper_quartile_minor,
            "confidence": round(self.confidence, 4),
            "confidenceBand": self.confidence_band,
            "live": self.live,
            "disclosure": self.disclosure,
            "calculatedAt": self.calculated_at.isoformat(),
        }


def _string(value: dict[str, object], field: str, maximum: int) -> str:
    result = value.get(field)
    if not isinstance(result, str) or not result or len(result) > maximum:
        raise ValueError(f"{field} must be a non-empty string no longer than {maximum} characters")
    return result


def _optional_string(value: dict[str, object], field: str, maximum: int) -> str | None:
    result = value.get(field)
    if result is None:
        return None
    if not isinstance(result, str) or not result or len(result) > maximum:
        raise ValueError(f"{field} must be null or a non-empty string no longer than {maximum} characters")
    return result


def _integer(value: dict[str, object], field: str) -> int:
    result = value.get(field)
    if not isinstance(result, int) or isinstance(result, bool):
        raise ValueError(f"{field} must be an integer")
    return result


def _optional_integer(value: dict[str, object], field: str, default: int) -> int:
    if field not in value:
        return default
    return _integer(value, field)


def _number(value: dict[str, object], field: str) -> float:
    result = value.get(field)
    if not isinstance(result, (int, float)) or isinstance(result, bool):
        raise ValueError(f"{field} must be a number")
    return float(result)


def _optional_number(value: dict[str, object], field: str, default: float) -> float:
    if field not in value:
        return default
    return _number(value, field)


def _boolean(value: dict[str, object], field: str) -> bool:
    result = value.get(field)
    if not isinstance(result, bool):
        raise ValueError(f"{field} must be a boolean")
    return result


def _timestamp(value: dict[str, object], field: str) -> datetime:
    raw = _string(value, field, 64)
    try:
        parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{field} must be an ISO-8601 timestamp") from error
    if parsed.tzinfo is None:
        raise ValueError(f"{field} must include a timezone")
    return parsed
