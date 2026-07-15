# Price Intelligence

Deterministic Python service for second-hand quote aggregation. It never scrapes marketplaces. Inputs must come from a user-entered quote, a clearly labeled demonstration provider, or a separately reviewed official/contracted API adapter.

The estimator validates currency and timestamps, calculates delivered prices in minor units, removes robust outliers, applies freshness decay, and returns quartiles plus confidence. Low-confidence results do not expose a single-point estimate.

```powershell
python -m unittest discover -s tests -v
$env:PYTHONPATH = "src"
python -m hengji_price_intelligence.server
```

The bundled demo provider is non-live and uses static fictional quotes.
