# LedgerPilot Backend (apps/backend)

**Make "where the accounting went wrong" verifiable in code** — a multi-tenant voucher engine
(BigDecimal-exact debit/credit balance, period-close locking, atomic balance accrual)
+ deterministic payroll/tax calculators + the integration endpoint for the AI parsing service
([apps/ai 的 AI 服务](../ai)). Natural language goes in (via the AI service), structured
draft vouchers come out (posted through this service) — **the human always confirms the posting**.

> **Relationship**: apps/ai 的 AI 服务 turns natural language into draft voucher JSON; this service
> validates, posts and reports on it, plus deterministic calculations. The LLM has no direct
> write access to the ledger anywhere in the chain: AI output must pass debit/credit balance,
> subject-existence and period-lock checks, and lands as a DRAFT voucher that still requires
> human approval before posting.

## Features

- **Voucher engine**: subjects/vouchers CRUD, approve (DRAFT→APPROVED), post (atomic balance
  accrual, CAS-guarded against concurrent double-posting), void with reversal, month-end P&L
  transfer, opening balances
- **Multi-tenancy (engine level)**: MyBatis-Plus row-level isolation; tenant context can only
  originate from a verified JWT
- **Deterministic calculators**: payroll (social insurance + housing fund + special deductions
  + IIT), VAT / corporate income tax / surtaxes — BigDecimal throughout, auditable and reproducible
- **AI integration**: calls apps/ai 的 AI 服务 for NL parsing; graceful degradation when the service
  is down; schema v2 string-decimal amount contract compatible
- **Real auth gate**: JWT + enforced tenant context, internal shared-secret endpoints,
  dangerous endpoints disabled by default, production JWT secret fail-fast

## Quick start

```bash
git clone <this repo> && cd apps/backend
mvn -pl ledgerpilot-admin -am package
java -jar ledgerpilot-admin/target/ledgerpilot-admin.jar --spring.profiles.active=h2 --server.port=8080
# In-memory H2 + demo data (admin/123456), no MySQL required
```

Then login and create a voucher (see the Chinese README for the full curl walkthrough; the API
is identical). Production: MySQL + env injection (`SPRING_DATASOURCE_*`, `JWT_SECRET` >= 32
chars, `LEDGERPILOT_INTERNAL_TOKEN`), see `application-local.yml.example`.

## We do not claim (honesty boundary)

1. **"Production-grade"** — only after all acceptance criteria pass (same criterion across the apps in this repo);
2. **"Complete multi-tenant SaaS"** — engine-level row isolation exists; no self-service tenant onboarding;
3. **"Internet-ready authentication"** — v0.1 auth is minimal (BCrypt user table + JWT), no rate
   limiting / password recovery / audit; deploy behind an intranet or reverse proxy;
4. **"OCR / invoice recognition"** — removed in v0.1 (was a TODO stub), planned;
5. **"Excel export"** — stub; online reports include profit statement / balance sheet / subject balances;
6. **"Standard chart of accounts"** — builtin subjects are demo samples with this repo's own
   numbering baseline (6602=selling expense / 6603=admin expense); the chart is fully replaceable;
7. Anything beyond the measured quick start;
8. **"Audited tax calculations"** — deterministic implementations, not reviewed by a licensed accountant.

## Disclaimer

1. Output is a **draft voucher** and must be reviewed by a licensed accountant before booking.
2. Subjects, rates and demo data are synthetic samples; nothing here interprets accounting
   standards or tax law.

## Security note

Historical internal versions committed production database credentials and a weak default JWT
secret in plaintext. The current version is fully env-injected (empty defaults + production
fail-fast) and published from a sanitized history. **If you ever ran an internal build: rotate
your database credentials and JWT secret now.** See SECURITY.md for reporting.

## License

Apache-2.0 — see the repository root [LICENSE](../../LICENSE).
