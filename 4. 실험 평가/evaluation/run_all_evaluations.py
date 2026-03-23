#!/usr/bin/env python3
"""
실험 결과 일괄 평가 스크립트 — Level 2 (에이전트 실험)

LCR (98항목), CDR (8시나리오), SDC (16테이블) 평가를 일괄 실행한다.
"""

import sys
import os
import csv

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# 실험 결과 경로
PRE_REVIEW = os.path.abspath(os.path.join(BASE_DIR, "..", ".."))  # pre-review-ver3/
RESULTS_DIR = os.path.join(PRE_REVIEW, "3. 실험 수행", "exp-agent", "results")
OUTPUT_DIR = BASE_DIR

# 평가 모듈 임포트
sys.path.insert(0, BASE_DIR)

import lcr_evaluator as _lcr
import cdr_evaluator as _cdr
import schema_checker as _sc

# 결과 경로 오버라이드
_lcr.RESULTS_DIR = RESULTS_DIR
_sc.RESULTS_DIR = RESULTS_DIR

from schema_checker import run_check
from cdr_evaluator import evaluate_cdr

GROUPS = ["A", "B", "C"]
TASKS = [1, 2, 3, 4]
RUNS = list(range(1, 6))


def run_sdc_evaluation():
    print("\n" + "=" * 70)
    print("  SDC (Schema Deviation Count) 평가 — Level 2 (16 테이블)")
    print("=" * 70)

    all_results = []

    for group in GROUPS:
        for task in TASKS:
            for run in RUNS:
                result_file = os.path.join(RESULTS_DIR, group, f"task{task}", f"run{run}.md")
                if not os.path.exists(result_file):
                    print(f"  [SKIP] {group}/task{task}/run{run} - 파일 없음")
                    continue

                result = run_check(result_file)
                row = {
                    "Group": group, "Task": task, "Run": run,
                    "D1": result.d1_count, "D2": result.d2_count,
                    "D3": result.d3_count, "D4": result.d4_count,
                    "D5": result.d5_count, "D6": result.d6_count,
                    "D7": result.d7_count, "Total": result.total,
                }
                all_results.append(row)
                print(f"  {group}/task{task}/run{run}: SDC={result.total} "
                      f"(D1={result.d1_count} D2={result.d2_count} D3={result.d3_count} "
                      f"D4={result.d4_count} D5={result.d5_count} D6={result.d6_count} D7={result.d7_count})")

    csv_path = os.path.join(OUTPUT_DIR, "sdc_results_all.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=["Group", "Task", "Run", "D1", "D2", "D3", "D4", "D5", "D6", "D7", "Total"])
        writer.writeheader()
        writer.writerows(all_results)
    print(f"\n  SDC 결과 저장: {csv_path}")

    print("\n  === 그룹별 SDC 평균 ===")
    for group in GROUPS:
        rows = [r for r in all_results if r["Group"] == group]
        if rows:
            avg = sum(r["Total"] for r in rows) / len(rows)
            print(f"  Group {group}: 평균 SDC = {avg:.1f} (n={len(rows)})")
            for d in range(1, 8):
                dv = sum(r[f"D{d}"] for r in rows) / len(rows)
                if dv > 0:
                    print(f"    D{d}: {dv:.1f}")

    return all_results


def run_cdr_evaluation():
    print("\n" + "=" * 70)
    print("  CDR (Conflict Detection Rate) 평가 — Level 2 (8 시나리오)")
    print("=" * 70)

    all_results = []
    scenario_ids = ["5-1", "5-2", "5-3", "5-4", "5-5", "5-6", "5-7", "5-8"]

    for group in GROUPS:
        for run in RUNS:
            result_file = os.path.join(RESULTS_DIR, group, "task5", f"run{run}.md")
            if not os.path.exists(result_file):
                print(f"  [SKIP] {group}/task5/run{run} - 파일 없음")
                continue

            with open(result_file, 'r', encoding='utf-8', errors='ignore') as f:
                text = f.read()

            results = evaluate_cdr(text)
            detected_count = sum(1 for r in results if r.detected)
            total = len(results)
            cdr_pct = detected_count / total * 100

            row = {"Group": group, "Run": run}
            for i, r in enumerate(results):
                sid = scenario_ids[i]
                row[sid] = "O" if r.detected else "X"
                row[f"{sid}_conf"] = r.confidence
            row["Detected"] = detected_count
            row["Total"] = total
            row["CDR%"] = cdr_pct
            all_results.append(row)

            scenario_strs = [f"{r.scenario_id}={'O' if r.detected else 'X'}({r.confidence})" for r in results]
            print(f"  {group}/run{run}: CDR={detected_count}/{total} ({cdr_pct:.0f}%) [{', '.join(scenario_strs)}]")

    # CSV 필드 순서
    fieldnames = ["Group", "Run"]
    for sid in scenario_ids:
        fieldnames.extend([sid, f"{sid}_conf"])
    fieldnames.extend(["Detected", "Total", "CDR%"])

    csv_path = os.path.join(OUTPUT_DIR, "cdr_results_all.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(all_results)
    print(f"\n  CDR 결과 저장: {csv_path}")

    print("\n  === 그룹별 CDR 평균 ===")
    for group in GROUPS:
        rows = [r for r in all_results if r["Group"] == group]
        if rows:
            avg_cdr = sum(r["CDR%"] for r in rows) / len(rows)
            avg_det = sum(r["Detected"] for r in rows) / len(rows)
            print(f"  Group {group}: 평균 CDR = {avg_cdr:.1f}% ({avg_det:.1f}/8)")
            # Level 1 vs Level 2 시나리오 비교
            l1_ids = ["5-1", "5-2", "5-3", "5-4"]
            l2_ids = ["5-5", "5-6", "5-7", "5-8"]
            l1_det = sum(1 for r in rows for sid in l1_ids if r[sid] == "O") / len(rows)
            l2_det = sum(1 for r in rows for sid in l2_ids if r[sid] == "O") / len(rows)
            print(f"    L1(5-1~5-4): {l1_det:.1f}/4  L2(5-5~5-8): {l2_det:.1f}/4")
            for sid in scenario_ids:
                det = sum(1 for r in rows if r[sid] == "O")
                print(f"    {sid}: {det}/{len(rows)} 탐지")

    return all_results


def run_lcr_evaluation():
    print("\n" + "=" * 70)
    print("  LCR (Logic Compliance Rate) 평가 — Level 2 (98 항목)")
    print("=" * 70)

    all_results = []
    detail_rows = []

    for group in GROUPS:
        for task in TASKS:
            for run in RUNS:
                results = _lcr.evaluate_lcr(group, task, run)
                if results is None:
                    print(f"  [SKIP] {group}/task{task}/run{run}")
                    continue

                passed = sum(1 for r in results if r["passed"])
                total_items = len(results)
                lcr_pct = passed / total_items * 100 if total_items > 0 else 0

                failed = [r["id"] for r in results if not r["passed"]]
                row = {"Group": group, "Task": task, "Run": run,
                       "Passed": passed, "Total": total_items, "LCR%": lcr_pct}
                all_results.append(row)

                for r in results:
                    detail_rows.append({"Group": group, "Task": task, "Run": run,
                                        "Item": r["id"], "Desc": r["desc"], "Passed": r["passed"]})

                print(f"  {group}/task{task}/run{run}: LCR={passed}/{total_items} ({lcr_pct:.0f}%)"
                      + (f" 미통과: {failed}" if failed else ""))

    csv_path = os.path.join(OUTPUT_DIR, "lcr_results_all.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=["Group", "Task", "Run", "Passed", "Total", "LCR%"])
        writer.writeheader()
        writer.writerows(all_results)

    detail_path = os.path.join(OUTPUT_DIR, "lcr_results_detail.csv")
    with open(detail_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=["Group", "Task", "Run", "Item", "Desc", "Passed"])
        writer.writeheader()
        writer.writerows(detail_rows)
    print(f"  LCR 상세 저장: {detail_path}")
    print(f"\n  LCR 결과 저장: {csv_path}")

    print("\n  === 그룹별 LCR 평균 ===")
    for group in GROUPS:
        rows = [r for r in all_results if r["Group"] == group]
        if rows:
            avg = sum(r["LCR%"] for r in rows) / len(rows)
            print(f"  Group {group}: 평균 LCR = {avg:.1f}% (n={len(rows)})")

    # Level 1 vs Level 2 비교
    l1_items = {1: 12, 2: 14, 3: 10, 4: 12}
    print("\n  === Level 1 vs Level 2 LCR 비교 ===")
    for group in GROUPS:
        l1_pass = 0
        l1_total = 0
        l2_pass = 0
        l2_total = 0
        for r in detail_rows:
            if r["Group"] != group:
                continue
            task = r["Task"]
            item_num = int(r["Item"].split("-")[1])
            if item_num <= l1_items.get(task, 0):
                l1_total += 1
                if r["Passed"]:
                    l1_pass += 1
            else:
                l2_total += 1
                if r["Passed"]:
                    l2_pass += 1
        if l1_total > 0 and l2_total > 0:
            print(f"  Group {group}: L1={l1_pass}/{l1_total} ({l1_pass/l1_total*100:.1f}%) "
                  f"L2={l2_pass}/{l2_total} ({l2_pass/l2_total*100:.1f}%)")

    return all_results


if __name__ == "__main__":
    print("=" * 70)
    print("  Level 2 실험 결과 일괄 평가")
    print(f"  결과 경로: {RESULTS_DIR}")
    print("=" * 70)

    run_sdc_evaluation()
    run_cdr_evaluation()
    run_lcr_evaluation()

    print("\n" + "=" * 70)
    print("  평가 완료!")
    print("=" * 70)
