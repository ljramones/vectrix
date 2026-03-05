#!/usr/bin/env python3
import argparse
import csv
import json
import re
from pathlib import Path

CANONICAL_ID_RE = re.compile(r"^vectrix\.[a-z0-9]+(?:\.[a-z0-9]+){3,}$")

def parse_items(params):
    for key in ("count", "size", "vertices", "instances", "matrices", "bytes"):
        value = params.get(key)
        if value is not None:
            try:
                return int(value)
            except ValueError:
                return None
    return None


def split_benchmark_name(bench):
    parts = bench.split(".")
    cls = parts[-2] if len(parts) >= 2 else bench
    method = parts[-1]
    return cls, method


def camel_to_dot(name):
    base = re.sub(r"Benchmark$", "", name)
    tokens = re.findall(r"[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\d+", base)
    return ".".join(t.lower() for t in tokens if t)


def canonical_id(benchmark):
    cls, method = split_benchmark_name(benchmark)
    class_key = camel_to_dot(cls)
    m = method.lower()

    if "skinning" in class_key or "skin" in m:
        category = "skinning"
    elif any(k in class_key for k in ("gpu", "interop", "memory", "layout", "std")):
        category = "interop"
    elif any(k in class_key for k in ("batch", "frustum", "transform", "curve", "fft", "mesh", "reduction")):
        category = "batch"
    else:
        category = "math"

    variant = re.sub(r"_+", ".", re.sub(r"([a-z0-9])([A-Z])", r"\1.\2", method)).lower()
    cid = f"vectrix.{category}.{class_key}.{variant}"
    if not CANONICAL_ID_RE.match(cid):
        raise ValueError(f"Non-conforming canonical benchmark id: {cid}")
    return cid


def main():
    parser = argparse.ArgumentParser(description="Normalize JMH JSON into CSV with per-item metrics")
    parser.add_argument("input", help="Path to JMH JSON result")
    parser.add_argument("-o", "--output", help="Output CSV path")
    args = parser.parse_args()

    in_path = Path(args.input)
    with in_path.open("r", encoding="utf-8") as f:
        rows = json.load(f)

    out_path = Path(args.output) if args.output else in_path.with_suffix(".normalized.csv")
    out_path.parent.mkdir(parents=True, exist_ok=True)

    fields = [
        "benchmark",
        "canonical_id",
        "mode",
        "params",
        "score",
        "score_unit",
        "items",
        "ns_per_item",
        "items_per_sec",
    ]

    with out_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            benchmark = row["benchmark"]
            params = row.get("params", {})
            primary = row.get("primaryMetric", {})
            score = float(primary.get("score", 0.0))
            unit = primary.get("scoreUnit", "")
            items = parse_items(params)
            ns_per_item = ""
            items_per_sec = ""

            if items and score > 0.0:
                mode = row.get("mode", "")
                unit_l = unit.lower()
                if mode == "thrpt":
                    # score in ops/time-unit, each op processes `items`
                    ops_per_sec = score
                    if "/ns" in unit_l:
                        ops_per_sec *= 1_000_000_000.0
                    elif "/us" in unit_l:
                        ops_per_sec *= 1_000_000.0
                    elif "/ms" in unit_l:
                        ops_per_sec *= 1_000.0
                    elif "/s" in unit_l:
                        ops_per_sec *= 1.0
                    elif "/min" in unit_l:
                        ops_per_sec /= 60.0
                    elif "/hr" in unit_l:
                        ops_per_sec /= 3600.0
                    ns_per_item = (1_000_000_000.0 / (ops_per_sec * items)) if ops_per_sec > 0 else ""
                    items_per_sec = ops_per_sec * items
                elif mode == "avgt":
                    # score in time/op, each op processes `items`
                    ns = score
                    if "us/op" in unit_l:
                        ns *= 1_000.0
                    elif "ms/op" in unit_l:
                        ns *= 1_000_000.0
                    elif "s/op" in unit_l:
                        ns *= 1_000_000_000.0
                    ns_per_item = ns / items
                    items_per_sec = (items * 1_000_000_000.0 / ns) if ns > 0 else ""

            writer.writerow(
                {
                    "benchmark": benchmark,
                    "canonical_id": canonical_id(benchmark),
                    "mode": row.get("mode", ""),
                    "params": json.dumps(params, sort_keys=True),
                    "score": score,
                    "score_unit": unit,
                    "items": items if items is not None else "",
                    "ns_per_item": ns_per_item,
                    "items_per_sec": items_per_sec,
                }
            )

    print(f"Wrote normalized metrics: {out_path}")


if __name__ == "__main__":
    main()
