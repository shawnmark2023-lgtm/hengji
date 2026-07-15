from __future__ import annotations

from datetime import datetime, timezone

from .contracts import MarketQuote, Provenance


def demo_quotes() -> tuple[MarketQuote, ...]:
    """Static fictional values. They are deliberately not based on a live marketplace."""
    return tuple(
        MarketQuote(
            provider_id="demo-non-live",
            title=f"演示手机 非实时样本 {index + 1}",
            model="DEMO-256",
            condition="good",
            price_minor=price_minor,
            shipping_minor=1_200 if index % 2 == 0 else 0,
            currency="CNY",
            observed_at=datetime(2026, 6, 10 + index, 2, tzinfo=timezone.utc),
            match_confidence=0.72 + index * 0.04,
            provenance=Provenance.DEMO_NON_LIVE,
            is_live=False,
            disclosure="演示行情，非实时、非平台抓取，不用于成交决策。",
        )
        for index, price_minor in enumerate((420_000, 458_000, 489_900, 510_000))
    )
