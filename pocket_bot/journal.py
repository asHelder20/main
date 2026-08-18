"""Registro (CSV) de todas as entradas e resultados, para auditoria e
para permitir analisar depois o desempenho real da estratégia."""
import csv
from datetime import datetime, timezone
from pathlib import Path

FIELDS = [
    "timestamp",
    "pair",
    "action",
    "amount",
    "expiry_seconds",
    "trade_id",
    "score",
    "reason",
    "result",
    "profit",
]


class Journal:
    def __init__(self, path: Path):
        self.path = path
        if not self.path.exists():
            with self.path.open("w", newline="", encoding="utf-8") as f:
                csv.DictWriter(f, fieldnames=FIELDS).writeheader()

    def record(self, **kwargs):
        row = {field: kwargs.get(field, "") for field in FIELDS}
        row["timestamp"] = kwargs.get("timestamp") or datetime.now(timezone.utc).isoformat()
        with self.path.open("a", newline="", encoding="utf-8") as f:
            csv.DictWriter(f, fieldnames=FIELDS).writerow(row)
