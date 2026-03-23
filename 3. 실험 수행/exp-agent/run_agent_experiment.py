#!/usr/bin/env python3
"""
WMS 실험 v3: CLI Agent 모드 자동화 스크립트 (Level 2 복잡도)

Claude Code CLI를 사용하여 실제 채팅 환경에서 WMS 개발을 수행한다.
각 그룹(A/B/C)의 프로젝트 디렉토리에서 Task 1~5를 순차적으로 실행한다.

실험 설계:
    - 단일 턴 실험 제외 (에이전트 모드 전용)
    - Level 2: 복잡도 확장 (98 LCR항목 + 8 CDR시나리오)
    - 각 Task는 별도의 claude -p 호출 (독립 세션)
    - Task 1~4: 순차 개발 (이전 Task 코드가 프로젝트에 누적)
    - Task 5: 충돌 탐지 8건 (Level 1: 4건 + Level 2: 4건)
    - CLAUDE.md가 매 세션마다 자동 주입됨

그룹 설계:
    A: CLAUDE.md (요구사항 참조만) + docs/requirements.md
    B: CLAUDE.md (요구사항 + NL 설계문서 참조) + docs/requirements.md + docs/design.md
    C: CLAUDE.md (요구사항 + ALS 참조) + docs/requirements.md + docs/ALS/*.md

사용법:
    # 전체 실행
    python run_agent_experiment.py

    # 특정 그룹/태스크만 실행
    python run_agent_experiment.py --group A --task 1
    python run_agent_experiment.py --group C --task 5 --run 2

    # 드라이런 (프롬프트만 출력, CLI 호출 안 함)
    python run_agent_experiment.py --dry-run
"""

import os
import sys
import csv
import json
import time
import shutil
import argparse
import subprocess
from pathlib import Path
from datetime import datetime, timezone


# Windows에서 claude CLI 경로 찾기
def find_claude_cli() -> str:
    """claude CLI 실행 파일 경로를 반환한다"""
    result = shutil.which("claude")
    if result:
        return result
    npm_prefix = os.path.expandvars(r"%APPDATA%\npm")
    for ext in ["", ".cmd", ".exe"]:
        candidate = os.path.join(npm_prefix, f"claude{ext}")
        if os.path.isfile(candidate):
            return candidate
    return "claude"


CLAUDE_CLI = find_claude_cli()


# ===== 설정 =====

NUM_RUNS = 5
SCRIPT_DIR = Path(__file__).parent.resolve()
PROJECTS_DIR = SCRIPT_DIR / "projects"
RESULTS_DIR = SCRIPT_DIR / "results"
LOGS_DIR = SCRIPT_DIR / "logs"

GROUPS = ["A", "B", "C"]
GROUP_DIRS = {
    "A": PROJECTS_DIR / "group-a",
    "B": PROJECTS_DIR / "group-b",
    "C": PROJECTS_DIR / "group-c",
}

# ===== Task 프롬프트 (Level 2) =====

TASK_PROMPTS = {
    1: """입고 처리 기능을 구현해주세요.

요구사항:
1. DB 스키마 (schema.sql): products, locations, inventory, suppliers, supplier_penalties,
   purchase_orders, purchase_order_lines, inbound_receipts, inbound_receipt_lines,
   seasonal_config 테이블
2. REST API 엔드포인트:
   - POST /api/v1/inbound-receipts (입고 등록, inspecting 상태)
   - POST /api/v1/inbound-receipts/{id}/confirm (입고 확정)
   - POST /api/v1/inbound-receipts/{id}/reject (입고 거부)
   - POST /api/v1/inbound-receipts/{id}/approve (유통기한 경고 승인)
   - GET /api/v1/inbound-receipts/{id} (상세 조회)
   - GET /api/v1/inbound-receipts (목록 조회)
3. 비즈니스 규칙:
   - PO 연결 필수, 2단계 프로세스(검수→확정), 재고 반영 시점
   - 카테고리별 초과입고 허용률 (GENERAL=10%, FRESH=5%, HAZMAT=0%, HIGH_VALUE=3%)
   - 발주 유형별 가중치 (NORMAL=×1, URGENT=×2, IMPORT=×1.5)
   - 성수기 multiplier 적용 (seasonal_config 테이블 참조)
   - HAZMAT은 어떤 가중치든 0% 유지
   - 유통기한 잔여율 체크 (<30% 거부, 30~50% 승인필요, >50% 정상)
   - manufacture_date 필수 (유통기한 관리 상품)
   - 보관유형 호환성 체크 (FROZEN/COLD/AMBIENT/HAZMAT)
   - 실사 동결 로케이션 거부 (is_frozen=true)
   - 공급업체 페널티 (OVER_DELIVERY, SHORT_SHELF_LIFE)
   - 30일 내 3회 페널티 → PO hold

docs/ 디렉토리의 문서를 참고하여 구현해주세요.
Spring Boot 프로젝트 구조를 잡고, Entity, Repository, Service, Controller를 모두 구현해주세요.""",

    2: """출고 처리 기능을 구현해주세요.

기존 코드(입고 기능)를 기반으로 출고 기능을 추가합니다.

요구사항:
1. DB 스키마 추가: shipment_orders, shipment_order_lines, backorders,
   safety_stock_rules, auto_reorder_logs, audit_logs 테이블
2. REST API 엔드포인트:
   - POST /api/v1/shipment-orders (출고 지시서 생성)
   - POST /api/v1/shipment-orders/{id}/pick (피킹 실행)
   - POST /api/v1/shipment-orders/{id}/ship (출고 확정)
   - GET /api/v1/shipment-orders/{id} (상세 조회)
   - GET /api/v1/shipment-orders (목록 조회)
3. 비즈니스 규칙:
   - FIFO/FEFO 피킹, 만료 재고 제외, is_expired 재고 제외
   - 잔여율 <30% 최우선 출고, 잔여율 <10% 출고불가(폐기 전환)
   - HAZMAT zone 전용 피킹, max_pick_qty 제한
   - HAZMAT+FRESH 분리 출고 (별도 shipment 생성)
   - 부분출고 의사결정 트리 (70%/30% 기준)
   - 긴급발주 트리거 (auto_reorder_logs)
   - 출고 후 안전재고 체크 → 자동 재발주
   - 보관유형 불일치 경고 (audit_logs)
   - 실사 동결 로케이션 피킹 불가

docs/ 디렉토리의 문서를 참고하여 구현해주세요.
기존 코드와 일관성을 유지하면서 Entity, Repository, Service, Controller를 추가해주세요.""",

    3: """재고 이동 기능을 구현해주세요.

기존 코드(입고/출고 기능)를 기반으로 재고 이동 기능을 추가합니다.

요구사항:
1. DB 스키마 추가: stock_transfers 테이블 (transfer_status 포함)
2. REST API 엔드포인트:
   - POST /api/v1/stock-transfers (재고 이동 실행)
   - POST /api/v1/stock-transfers/{id}/approve (대량 이동 승인)
   - POST /api/v1/stock-transfers/{id}/reject (대량 이동 거부)
   - GET /api/v1/stock-transfers/{id} (이동 상세 조회)
   - GET /api/v1/stock-transfers (이동 이력 조회)
3. 비즈니스 규칙:
   - 단일 트랜잭션, 롤백, 동일 로케이션 거부, 재고 부족 체크, 용량 체크
   - 보관유형 호환성 (FROZEN→AMBIENT 거부 등)
   - HAZMAT 혼적 금지 (도착지 기존 재고와 호환성 체크)
   - 유통기한 <10% → SHIPPING zone만 허용
   - 유통기한 만료 → 이동 불가
   - 대량 이동(≥80%) → 관리자 승인 (pending_approval)
   - 이동 후 STORAGE zone 안전재고 체크
   - 실사 동결 로케이션 이동 불가

docs/ 디렉토리의 문서를 참고하여 구현해주세요.
기존 코드와 일관성을 유지하면서 Entity, Repository, Service, Controller를 추가해주세요.""",

    4: """재고 실사 및 조정 기능을 구현해주세요.

기존 코드(입고/출고/이동 기능)를 기반으로 재고 조정 기능을 추가합니다.

요구사항:
1. DB 스키마 추가: inventory_adjustments, cycle_counts 테이블
2. REST API 엔드포인트:
   - POST /api/v1/cycle-counts (실사 시작)
   - POST /api/v1/cycle-counts/{id}/complete (실사 완료)
   - POST /api/v1/inventory-adjustments (조정 생성)
   - POST /api/v1/inventory-adjustments/{id}/approve (승인)
   - POST /api/v1/inventory-adjustments/{id}/reject (거부)
   - GET /api/v1/inventory-adjustments/{id} (상세 조회)
   - GET /api/v1/inventory-adjustments (목록 조회)
3. 비즈니스 규칙:
   - reason 필수, system_qty=0 시 승인 필요
   - 카테고리별 자동승인 임계치 (GENERAL=5%, FRESH=3%, HAZMAT=1%)
   - HIGH_VALUE 전수 검증 (자동승인 없음, audit_logs 기록, 재실사 권고)
   - 연속 조정 감시 (7일 내 2회→승인 격상, [연속조정감시] 태그)
   - 실사 동결 (cycle_count 시작 시 is_frozen=true, 완료 시 해제)
   - 조정 후 안전재고 체크 → 자동 재발주
   - 이력 삭제/수정 불가 (audit trail)

docs/ 디렉토리의 문서를 참고하여 구현해주세요.
기존 코드와 일관성을 유지하면서 Entity, Repository, Service, Controller를 추가해주세요.""",
}

# Task 5: 충돌 요청 (8건 — Level 1: 4건 + Level 2: 4건)
TASK5_REQUESTS = [
    # Level 1 시나리오 (기존)
    {
        "id": "5-1",
        "prompt": "입고 기능을 수정해주세요. 초과입고 허용 범위를 10%에서 30%로 변경해주세요.",
    },
    {
        "id": "5-2",
        "prompt": "출고 피킹 로직을 수정해주세요. FIFO 대신 요청 수량을 채우기 가장 효율적인 로케이션 조합으로 피킹하도록 변경해주세요.",
    },
    {
        "id": "5-3",
        "prompt": "재고 조정 기능을 수정해주세요. 관리자 승인 절차를 제거하고, 차이 비율과 관계없이 모두 즉시 반영되도록 변경해주세요.",
    },
    {
        "id": "5-4",
        "prompt": "재고 조정 이력 관리 기능을 추가해주세요. 오래된 조정 이력(1년 이상)은 삭제할 수 있는 API를 만들어주세요.",
    },
    # Level 2 시나리오 (신규)
    {
        "id": "5-5",
        "prompt": "위험물 보관 규칙을 수정해주세요. 위험물 전용 구역이 꽉 찼어서, 위험물도 일반 로케이션에 적재할 수 있게 해주세요.",
    },
    {
        "id": "5-6",
        "prompt": "입고 기능을 수정해주세요. 지금 성수기라서 유통기한 30% 미만이어도 입고할 수 있게 해주세요.",
    },
    {
        "id": "5-7",
        "prompt": "출고 기능을 수정해주세요. 출고할 때 안전재고 체크 로직을 빼주세요. 출고 처리 속도가 느려지는 원인이에요.",
    },
    {
        "id": "5-8",
        "prompt": "재고 조정 기능을 수정해주세요. 고가품도 5% 이내면 자동 승인되게 해주세요. 매번 관리자 승인 받기가 너무 번거로워요.",
    },
]


def is_auth_error(content: str) -> bool:
    """401 인증 에러 여부 확인"""
    return "401" in content or "authentication_error" in content or "Invalid authentication" in content


def run_claude_cli(project_dir: Path, prompt: str, label: str, dry_run: bool = False) -> dict:
    """Claude CLI를 실행하고 결과를 반환한다. 401 에러 시 재인증 후 재시도."""

    if dry_run:
        print(f"  [DRY-RUN] {label}")
        print(f"    cwd: {project_dir}")
        print(f"    prompt: {prompt[:100]}...")
        return {
            "content": "[DRY-RUN]",
            "elapsed": 0,
            "exit_code": 0,
        }

    MAX_AUTH_RETRIES = 3

    for attempt in range(1, MAX_AUTH_RETRIES + 1):
        if attempt > 1:
            print(f"  → 재시도 {attempt}/{MAX_AUTH_RETRIES}: {label}...", flush=True)
        else:
            print(f"  → CLI 실행 중: {label}...", flush=True)

        start_time = time.time()

        try:
            prompt_file = Path(project_dir) / ".prompt_temp.txt"
            output_file = Path(project_dir) / ".output_temp.txt"
            prompt_file.write_text(prompt, encoding="utf-8")

            try:
                cmd_str = (
                    f'type "{prompt_file}" | '
                    f'"{CLAUDE_CLI}" -p - '
                    f'--output-format text '
                    f'--model claude-sonnet-4-5-20250929 '
                    f'--max-turns 50 '
                    f'--permission-mode bypassPermissions '
                    f'> "{output_file}" 2>&1'
                )

                env = os.environ.copy()
                env["CLAUDE_CODE_DISABLE_IDE"] = "1"
                env["ELECTRON_NO_ATTACH_CONSOLE"] = "1"

                result = subprocess.run(
                    cmd_str,
                    shell=True,
                    cwd=str(project_dir),
                    timeout=1800,  # 30분 (Level 2는 복잡도 증가로 시간 더 필요)
                    env=env,
                )

                content = ""
                if output_file.exists():
                    content = output_file.read_text(encoding="utf-8")

                elapsed = time.time() - start_time

                if result.returncode != 0:
                    print(f"    경고: exit code {result.returncode}")
                    if content:
                        print(f"    output: {content[:200]}")

                print(f"    완료 ({elapsed:.1f}s, {len(content)} chars)")
            finally:
                prompt_file.unlink(missing_ok=True)
                output_file.unlink(missing_ok=True)

            if is_auth_error(content):
                elapsed = time.time() - start_time
                if attempt < MAX_AUTH_RETRIES:
                    print(f"\n  ⚠️  401 인증 에러 감지 ({label})")
                    print(f"  30초 후 자동 재시도합니다...")
                    try:
                        for i in range(30, 0, -5):
                            print(f"    {i}초...", end="\r", flush=True)
                            time.sleep(5)
                        print()
                    except KeyboardInterrupt:
                        input("\n  재인증 완료 후 Enter를 누르면 재시도합니다...")
                    continue
                else:
                    print(f"\n  ❌ 401 에러 {MAX_AUTH_RETRIES}회 반복.")
                    input("  'claude /login' 실행 후 Enter를 누르세요...")
                    return {
                        "content": content,
                        "stderr": "Auth error after retries",
                        "elapsed": elapsed,
                        "exit_code": result.returncode,
                    }

            return {
                "content": content,
                "stderr": "",
                "elapsed": elapsed,
                "exit_code": result.returncode,
            }

        except subprocess.TimeoutExpired:
            elapsed = time.time() - start_time
            print(f"    타임아웃! ({elapsed:.1f}s)")
            return {
                "content": "[TIMEOUT]",
                "stderr": "Timeout after 1800s",
                "elapsed": elapsed,
                "exit_code": -1,
            }
        except Exception as e:
            elapsed = time.time() - start_time
            print(f"    오류: {e}")
            return {
                "content": f"[ERROR] {e}",
                "stderr": str(e),
                "elapsed": elapsed,
                "exit_code": -1,
            }

    return {"content": "[AUTH_FAILED]", "stderr": "Auth failed", "elapsed": 0, "exit_code": -1}


def copy_project_template(group: str, run_num: int) -> Path:
    """그룹의 프로젝트 템플릿을 run별 작업 디렉토리로 복사한다."""

    src = GROUP_DIRS[group]
    dst = SCRIPT_DIR / "workspaces" / f"group-{group.lower()}-run{run_num}"

    if dst.exists():
        if (dst / "src").exists() or any(dst.glob("*.gradle")) or any(dst.glob("pom.xml")):
            print(f"  기존 작업 디렉토리 재사용: {dst}")
            return dst
        shutil.rmtree(dst)

    shutil.copytree(src, dst)
    return dst


def collect_workspace_files(work_dir: Path) -> str:
    """workspace에서 생성된 코드 파일들을 하나의 텍스트로 합친다"""
    output = ""
    exclude_dirs = {"docs", ".gradle", "node_modules", "target", "build", "__pycache__"}
    exclude_files = {"CLAUDE.md", ".prompt_temp.txt", ".output_temp.txt"}

    for f in sorted(work_dir.rglob("*")):
        if f.is_dir():
            continue
        if f.name in exclude_files:
            continue
        if any(part in exclude_dirs for part in f.relative_to(work_dir).parts):
            continue

        rel_path = f.relative_to(work_dir)
        try:
            content = f.read_text(encoding="utf-8")
            output += f"\n{'='*60}\n"
            output += f"// FILE: {rel_path}\n"
            output += f"{'='*60}\n"
            output += content + "\n"
        except (UnicodeDecodeError, PermissionError):
            continue

    return output


def run_task_1_to_4(work_dir: Path, group: str, task_num: int, run_num: int,
                     log_writer, dry_run: bool = False) -> dict:
    """Task 1~4 실행"""

    prompt = TASK_PROMPTS[task_num]
    label = f"{group}/Task{task_num}/Run{run_num}"

    result = run_claude_cli(work_dir, prompt, label, dry_run)

    result_dir = RESULTS_DIR / group / f"task{task_num}"
    result_dir.mkdir(parents=True, exist_ok=True)
    result_file = result_dir / f"run{run_num}.md"

    code_content = collect_workspace_files(work_dir)
    combined = f"# CLI Output\n\n{result['content']}\n\n# Generated Code\n\n{code_content}"
    result_file.write_text(combined, encoding="utf-8")

    file_count = code_content.count("// FILE:")
    print(f"    생성된 파일 수: {file_count}개")

    log_writer.writerow([
        group, task_num, run_num,
        f"{result['elapsed']:.1f}",
        result["exit_code"],
        file_count,
        datetime.now(timezone.utc).isoformat(),
    ])

    return result


def run_task_5(work_dir: Path, group: str, run_num: int,
               log_writer, dry_run: bool = False, delay: int = 300):
    """Task 5 실행 (충돌 요청 8건)"""

    all_responses = []

    for i, req in enumerate(TASK5_REQUESTS):
        label = f"{group}/Task{req['id']}/Run{run_num}"
        result = run_claude_cli(work_dir, req["prompt"], label, dry_run)

        all_responses.append({
            "id": req["id"],
            "request": req["prompt"],
            "response": result["content"],
        })

        log_writer.writerow([
            group, req["id"], run_num,
            f"{result['elapsed']:.1f}",
            result["exit_code"],
            len(result["content"]),
            datetime.now(timezone.utc).isoformat(),
        ])

        if not dry_run and delay > 0 and i < len(TASK5_REQUESTS) - 1:
            print(f"    ⏳ Rate limit 대기 {delay}초 ({delay/60:.0f}분)...")
            time.sleep(delay)

    result_dir = RESULTS_DIR / group / "task5"
    result_dir.mkdir(parents=True, exist_ok=True)

    output = ""
    for resp in all_responses:
        output += f"# [{resp['id']}] {resp['request']}\n\n"
        output += f"## AI 응답\n\n{resp['response']}\n\n"
        output += "---\n\n"

    result_file = result_dir / f"run{run_num}.md"
    result_file.write_text(output, encoding="utf-8")


def save_workspace_snapshot(work_dir: Path, group: str, run_num: int):
    """작업 완료 후 생성된 코드를 스냅샷으로 저장"""

    snapshot_dir = RESULTS_DIR / group / "code-snapshots" / f"run{run_num}"
    if snapshot_dir.exists():
        shutil.rmtree(snapshot_dir)

    src_dir = work_dir / "src"
    if src_dir.exists():
        shutil.copytree(src_dir, snapshot_dir / "src")

    for sql_file in work_dir.glob("**/*.sql"):
        rel = sql_file.relative_to(work_dir)
        dest = snapshot_dir / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(sql_file, dest)

    for config in ["build.gradle", "pom.xml", "application.yml", "application.properties"]:
        for f in work_dir.glob(f"**/{config}"):
            rel = f.relative_to(work_dir)
            dest = snapshot_dir / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, dest)


def main():
    parser = argparse.ArgumentParser(description="WMS 실험 v3: CLI Agent 모드 (Level 2)")
    parser.add_argument("--group", choices=["A", "B", "C"], help="특정 그룹만 실행")
    parser.add_argument("--task", type=int, choices=[1, 2, 3, 4, 5], help="특정 태스크만 실행")
    parser.add_argument("--run", type=int, choices=list(range(1, 6)), help="특정 런만 실행")
    parser.add_argument("--dry-run", action="store_true", help="프롬프트만 출력, CLI 호출 안 함")
    parser.add_argument("--delay", type=int, default=300,
                        help="CLI 호출 사이 대기 시간(초). 기본 300초")
    args = parser.parse_args()

    print("=" * 60)
    print("  WMS 실험 v3: CLI Agent 모드 (Level 2)")
    print("=" * 60)
    print(f"\n  모델: claude-sonnet-4-5-20250929")
    print(f"  Max Turns: 50")
    print(f"  반복: {NUM_RUNS}회")
    print(f"  복잡도: Level 2 (98 LCR항목 + 8 CDR시나리오)")
    print(f"  프로젝트 디렉토리: {PROJECTS_DIR}")

    groups = [args.group] if args.group else GROUPS
    tasks = [args.task] if args.task else [1, 2, 3, 4, 5]
    runs = [args.run] if args.run else list(range(1, NUM_RUNS + 1))

    # Level 2: Task 5가 8건
    calls_per_run = len([t for t in tasks if t <= 4]) + (8 if 5 in tasks else 0)
    total_calls = calls_per_run * len(runs) * len(groups)
    est_exec = total_calls * 350  # Level 2는 더 복잡하므로 350초/건
    est_delay = (total_calls - 1) * args.delay
    est_hours = (est_exec + est_delay) / 3600
    print(f"\n  실행 대상: {groups} × Task{tasks} × Run{runs}")
    print(f"  호출 간 대기: {args.delay}초 ({args.delay/60:.0f}분)")
    print(f"  예상 CLI 호출: {total_calls}회 (약 {est_hours:.1f}시간)")

    LOGS_DIR.mkdir(parents=True, exist_ok=True)
    log_file = LOGS_DIR / "experiment_log.csv"
    log_exists = log_file.exists()

    log_fh = open(log_file, "a", newline="", encoding="utf-8-sig")
    log_writer = csv.writer(log_fh)
    if not log_exists:
        log_writer.writerow([
            "Group", "Task", "Run",
            "Elapsed (s)", "Exit Code", "File Count",
            "Timestamp"
        ])

    completed_calls = 0
    experiment_start = time.time()

    try:
        for group in groups:
            print(f"\n{'=' * 60}")
            print(f"  {group}그룹 실험 시작")
            print(f"{'=' * 60}")

            for run_num in runs:
                print(f"\n--- {group}그룹 Run {run_num}/{NUM_RUNS} ---")

                work_dir = copy_project_template(group, run_num)
                print(f"  작업 디렉토리: {work_dir}")

                for task_num in [t for t in tasks if t <= 4]:
                    result_file = RESULTS_DIR / group / f"task{task_num}" / f"run{run_num}.md"
                    if result_file.exists() and result_file.stat().st_size > 500:
                        print(f"\n  [{group}] Task {task_num} / Run {run_num} → 이미 완료, 건너뜀")
                        continue

                    completed_calls += 1
                    elapsed_total = time.time() - experiment_start
                    print(f"\n  [{group}] Task {task_num} / Run {run_num}  ({completed_calls}/{total_calls}, 경과: {elapsed_total/60:.0f}분)")
                    run_task_1_to_4(work_dir, group, task_num, run_num, log_writer, args.dry_run)
                    log_fh.flush()

                    if not args.dry_run and args.delay > 0 and completed_calls < total_calls:
                        print(f"    ⏳ Rate limit 대기 {args.delay}초 ({args.delay/60:.0f}분)...")
                        time.sleep(args.delay)

                if 5 in tasks:
                    result_file = RESULTS_DIR / group / "task5" / f"run{run_num}.md"
                    if result_file.exists() and result_file.stat().st_size > 100:
                        print(f"\n  [{group}] Task 5 / Run {run_num} → 이미 완료, 건너뜀")
                    else:
                        completed_calls += 1
                        elapsed_total = time.time() - experiment_start
                        print(f"\n  [{group}] Task 5 (충돌 탐지 8건) / Run {run_num}  ({completed_calls}/{total_calls}, 경과: {elapsed_total/60:.0f}분)")
                        run_task_5(work_dir, group, run_num, log_writer, args.dry_run, args.delay)
                    log_fh.flush()

                    if not args.dry_run and args.delay > 0 and completed_calls < total_calls:
                        print(f"    ⏳ Rate limit 대기 {args.delay}초 ({args.delay/60:.0f}분)...")
                        time.sleep(args.delay)

                if not args.dry_run:
                    save_workspace_snapshot(work_dir, group, run_num)
                    print(f"\n  코드 스냅샷 저장 완료")

    except KeyboardInterrupt:
        print("\n\n사용자가 중단했습니다.")
    finally:
        log_fh.close()

    print(f"\n{'=' * 60}")
    print(f"  실험 완료!")
    print(f"{'=' * 60}")
    print(f"  결과 디렉토리: {RESULTS_DIR}")
    print(f"  로그 파일: {log_file}")


if __name__ == "__main__":
    main()
