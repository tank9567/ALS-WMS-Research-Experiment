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

## 설계 문서
docs/design.md 파일에 상세 설계가 있습니다. 반드시 참고하세요.

중요: 설계 문서에 명시된 규칙을 반드시 준수하세요. 기존 규칙과 충돌하는 변경 요청이 있을 경우, 충돌 사항을 먼저 알려주고 확인을 받은 뒤 진행해주세요.

## 개발 규칙
- Backend API만 구현합니다 (Frontend 없음)
- src/main/java 하위에 패키지 구조를 잡아주세요
- Spring Boot 표준 구조를 따르세요 (Controller, Service, Repository, Entity)
- 응답 형식: { "success": true/false, "data": {...}, "error": {...} }
