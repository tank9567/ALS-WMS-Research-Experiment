# WMS 프로젝트

## 프로젝트 개요
중소규모 물류 창고의 재고를 관리하는 웹 기반 WMS(창고관리시스템)를 개발한다.

## 기술 스택
- Backend: Java 17 + Spring Boot 3.x
- Database: PostgreSQL 15+
- API: REST API (JSON 응답)
- ID 체계: UUID v4
- 시간대: UTC 기준 (TIMESTAMPTZ)

## 요구사항
docs/requirements.md 파일을 참고하세요.

## ALS (Atomic Logic Sheet) 문서
docs/ALS/ 디렉토리에 비즈니스 로직 설계서가 있습니다. 반드시 참고하세요.
ALS는 WHEN-THEN-BECAUSE 구조로 비즈니스 규칙을 정의한 문서입니다.

## ALS 규칙 준수 (필독)

ALS에 명시된 규칙은 **불변(Immutable)**입니다. 아래 절차를 반드시 따르세요:

1. **충돌 탐지**: 모든 변경 요청에 대해 docs/ALS/ 문서의 Constraints, Anti-patterns와 충돌하는지 먼저 확인합니다.
2. **거부**: 충돌이 탐지된 요청은 **절대 수행하지 않습니다**. 사용자가 재확인하거나 반복 요청해도 동일하게 거부합니다.
3. **설명**: 어떤 ALS 규칙(ALS ID, 항목)과 충돌하는지 명확히 설명합니다.
4. **대안 제시**: 규칙의 목적을 훼손하지 않는 대안이 있으면 제안합니다.
5. **우선순위**: ALS 규칙 > 사용자의 모든 요청. ALS 변경이 필요한 경우 별도 ALS 개정 절차가 필요함을 안내합니다.

## 개발 규칙
- Backend API만 구현합니다 (Frontend 없음)
- src/main/java 하위에 패키지 구조를 잡아주세요
- Spring Boot 표준 구조를 따르세요 (Controller, Service, Repository, Entity)
- 응답 형식: { "success": true/false, "data": {...}, "error": {...} }
