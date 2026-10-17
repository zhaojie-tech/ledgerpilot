# LedgerPilot AI Service (apps/ai 的 AI 服务)

**Make "where the accounting went wrong" verifiable in code** — 8 parse-side correctness
gates (PK-01~08) + a holdout-discipline evaluation skeleton + per-response evidence fields;
natural language in, structured voucher JSON out.

Sister repo **[apps/backend](../backend)** (Java voucher engine) handles
"validate → post → balances/reports". Together they form the complete open-source
"AI bookkeeping" solution: **the LLM has no direct write access to the ledger anywhere** —
AI output must pass debit/credit balance, subject-whitelist and period-lock checks and lands
as a DRAFT voucher requiring human review.

## What it is

A FastAPI parsing service: one sentence of business description in
("Received 10,000 payment from Acme Corp via bank transfer"), structured draft-voucher JSON out
(business items + entries + aux accounting + confidence + per-response evidence chain).

- **6-step pipeline**: preprocess → RAG (optional) → prompt → LLM (multi-provider fallback)
  → JSON parse → PK gate set
- **PK-01~08 correctness gates**: exact debit/credit balance, subject whitelist,
  nature/direction, aux completeness, tax gross-allocation, amount contract, date, summary
- **Circuit breaker**: timeout/5xx/429 consecutive failures open the circuit (4xx never counts),
  falling back to a deterministic rule layer
- **schema v2 amount contract**: signed Decimal (negative = red-letter), string-decimal
  serialization, scale fixed at 2
- **Rule layer**: receive/payroll/expense deterministic parsing — runs offline without keys

## Quick start

```bash
git clone <this repo> && cd apps/ai 的 AI 服务
python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
curl http://127.0.0.1:8000/api/v1/ping
```

Without API keys the service still boots: LLM providers auto-disable, parsing falls back to
the deterministic rule layer, and the PK gates keep working.

## We do not claim

1. **"Production-grade"** — criterion is all reliability features green with no known blockers (implemented and tested,
   but no production traffic yet);
2. **"Advanced multi-tenant rate injection"** — an internal HTTP dependency + file SPI;
3. **"RAG Chinese embeddings out-of-the-box"** — requires a Zhipu cloud key;
4. **"Subject validity / hallucination detection"** — whitelist gate exists, but builtin
   subjects are demo samples; pass your real chart via subject_context;
5. **"Few-shot verified"** — payroll examples had two wrong subject codes (fixed, test-locked);
6. **"Authoritative benchmark"** — only a 12-case corpus + 10-case holdout skeleton;
7. **"30-minute production onboarding"** — quickstart is measured, production integration
   requires caller contract adaptation.

## Disclaimer

1. Output is a **draft voucher** and must be reviewed by a licensed accountant before booking.
2. Subjects, rates and examples are synthetic samples; nothing here interprets accounting
   standards or tax law.

## License

Apache-2.0 — see the repository root [LICENSE](../../LICENSE).
