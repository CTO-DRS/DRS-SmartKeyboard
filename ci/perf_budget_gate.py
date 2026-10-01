#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DRS M0.4 — the performance budget gate.

Compares a measured benchmark result against ci/perf_budgets.json (the
single source of truth) and exits 1 when ANY measured metric exceeds its
budget. CI-grade: no third-party deps, honest exit codes, and a built-in
self-test that exercises BOTH directions (a passing run and a failing
run) before it can be trusted anywhere.

Usage:
  perf_budget_gate.py --measured results.json --budgets ci/perf_budgets.json
  perf_budget_gate.py --selftest

Measured JSON shape (the keys must match the budget names):
  { "startup_warm_ms_p50": 214.5, "startup_cold_ms_p50": 331.0,
    "frame_p90_ms": 14.2, "frame_p99_ms": 29.8 }
"""
import argparse
import json
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_BUDGETS = os.path.join(REPO_ROOT, "ci", "perf_budgets.json")


def load_budgets(path):
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    budgets = data.get("budgets")
    if not isinstance(budgets, dict) or not budgets:
        raise ValueError(f"budgets file {path} carries no 'budgets' object")
    return budgets


def evaluate(measured, budgets):
    """Returns (passed, lines) — honest per-metric report, exit-worthy flag."""
    lines = []
    passed = True
    for name, budget in budgets.items():
        if name not in measured:
            lines.append(f"MISSING  {name}: no measured value (gate fails — a budget without a measurement is a broken run)")
            passed = False
            continue
        value = measured[name]
        try:
            value = float(value)
            budget = float(budget)
        except (TypeError, ValueError):
            lines.append(f"INVALID  {name}: non-numeric value (measured={value!r}, budget={budget!r})")
            passed = False
            continue
        if value <= budget:
            lines.append(f"PASS     {name}: {value:.2f} <= budget {budget:.2f}")
        else:
            lines.append(f"FAIL     {name}: {value:.2f} > budget {budget:.2f} (exceeded by {value - budget:.2f})")
            passed = False
    # Extra measurements without a budget are reported — not failures, but
    # visible (they usually mean the budgets file lags the benchmarks).
    for name in sorted(set(measured) - set(budgets)):
        lines.append(f"UNBUDGET {name}: measured {measured[name]} has no budget entry")
    return passed, lines


def selftest():
    budgets = {
        "startup_warm_ms_p50": 220,
        "frame_p90_ms": 16.7,
    }
    # Direction 1: everything under budget → PASS.
    ok, lines = evaluate(
        {"startup_warm_ms_p50": 200.0, "frame_p90_ms": 15.0},
        budgets,
    )
    assert ok, "selftest direction 1 must pass: " + "\n".join(lines)
    # Direction 2: one exceeded budget → FAIL with the offending line.
    ok, lines = evaluate(
        {"startup_warm_ms_p50": 200.0, "frame_p90_ms": 21.0},
        budgets,
    )
    assert not ok, "selftest direction 2 must fail"
    assert any(line.startswith("FAIL") and "frame_p90_ms" in line for line in lines), lines
    # Direction 3: a missing measurement fails closed.
    ok, lines = evaluate({"startup_warm_ms_p50": 200.0}, budgets)
    assert not ok, "selftest direction 3 must fail on missing metric"
    print("SELFTEST PASS: gate passes green, fails red, and fails closed on missing data")
    return True


def main():
    parser = argparse.ArgumentParser(description="DRS performance budget gate")
    parser.add_argument("--measured", help="path to the measured results JSON")
    parser.add_argument("--budgets", default=DEFAULT_BUDGETS, help="path to the budgets JSON")
    parser.add_argument("--selftest", action="store_true", help="run the built-in both-directions test")
    args = parser.parse_args()

    if args.selftest:
        sys.exit(0 if selftest() else 1)

    if not args.measured:
        parser.error("--measured is required (or use --selftest)")
    measured = json.load(open(args.measured, "r", encoding="utf-8"))
    budgets = load_budgets(args.budgets)
    passed, lines = evaluate(measured, budgets)
    for line in lines:
        print(line)
    print("GATE: PASS" if passed else "GATE: FAIL")
    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
