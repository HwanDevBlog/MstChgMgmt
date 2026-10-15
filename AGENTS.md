# Project instructions

- This is a fictional equipment catalog change-review project. Define rules independently.
- Do not inspect or import company repositories, databases, configurations, rules, or portfolio documents.
- Do not modify the existing InvMgmt project. Do not copy its business code.
- Work in small stages. Update docs/DESIGN.md and docs/HANDOFF.md once at a completed stage, using verified facts.
- Do not claim API, database, concurrency, or E2E protection based only on domain unit tests.
- Preserve old revisions. APPLIED is terminal. Approval and database application must be atomic.
- Obtain actor identity from authenticated sessions, never request bodies.
- No global Git configuration changes, remote creation, push, external publication, or deployment without explicit instruction.
- No subagents unless explicitly requested by the user.
- Use Java 21. On this Windows PC, run scripts/dev.ps1 for the documented local Gradle workaround.

## Consistency with the existing personal toy project

- Follow InvMgmt conventions: C:\toyProject\workspace, abbreviated PascalCase project names, main branch, com.hwandevblog group, and Korean descriptive commit messages.
- This project's Java base package is com.hwandevblog.mstchgmgmt; its entry point is MstChgMgmtApplication.
- Use the existing compatible Java 21/Spring Boot/Gradle Wrapper workflow. Explain necessary differences with concrete reasons.
- Continue work in this chat as requested; do not require a new chat at stage boundaries.

## 주석 작성 방식

- 기존 InvMgmt처럼 클래스의 역할과 책임은 한국어 여러 줄 Javadoc으로 설명한다.
- 처리 이유, 검증 기준, 트랜잭션·동시성 판단은 해당 코드 바로 위의 한국어 // 주석으로 설명한다.
- 주석은 간결한 평서형 문장으로 쓰고, 코드에서 바로 드러나는 동작은 반복해서 설명하지 않는다.
- 스크립트와 빌드 설정도 필요한 판단 근거를 한국어 주석으로 남긴다.

## 커밋 안내

- 커밋이 필요한 시점마다 대상 파일과 한국어 커밋 메시지를 반드시 함께 안내한다.
- Eclipse Git Staging에서 대상 파일을 확인하고 스테이징한 뒤 Commit하는 순서로 설명한다.
- 사용자 요청 없이 커밋하거나 push하지 않는다.
