from __future__ import annotations

import sys
import unittest
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1] / "src"))

from hengji_price_intelligence.contracts import EstimateRequest  # noqa: E402
from hengji_price_intelligence.estimator import estimate_price  # noqa: E402
from hengji_price_intelligence.providers import demo_quotes  # noqa: E402


class EstimatorTest(unittest.TestCase):
    def test_demo_estimate_is_visibly_non_live(self) -> None:
        request = EstimateRequest(
            currency="CNY",
            as_of=datetime(2026, 7, 15, tzinfo=timezone.utc),
            quotes=demo_quotes(),
        )

        result = estimate_price(request)

        self.assertFalse(result.live)
        self.assertIn("非实时", result.disclosure)
        self.assertIsNotNone(result.median_minor)
        self.assertEqual(4, result.sample_count)

    def test_low_confidence_hides_single_point_estimate(self) -> None:
        quote = replace(demo_quotes()[0], match_confidence=0.56)
        request = EstimateRequest(
            currency="CNY",
            as_of=datetime(2026, 7, 15, tzinfo=timezone.utc),
            quotes=(quote,),
        )

        result = estimate_price(request)

        self.assertIsNone(result.median_minor)
        self.assertIsNotNone(result.lower_quartile_minor)
        self.assertEqual("low", result.confidence_band)

    def test_removes_extreme_outlier(self) -> None:
        base = demo_quotes()
        quotes = base + (
            replace(base[0], provider_id="demo-two", price_minor=470_000),
            replace(base[0], provider_id="demo-outlier", price_minor=9_999_999),
        )
        request = EstimateRequest(
            currency="CNY",
            as_of=datetime(2026, 7, 15, tzinfo=timezone.utc),
            quotes=quotes,
        )

        result = estimate_price(request)

        self.assertEqual(5, result.sample_count)
        self.assertEqual(1, result.rejected_count)
        self.assertLess(result.upper_quartile_minor or 0, 600_000)


if __name__ == "__main__":
    unittest.main()
