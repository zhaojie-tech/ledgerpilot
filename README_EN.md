# LedgerPilot — Self-hostable AI bookkeeping platform

> Chinese name: 账航

Type one line of business language ("Received 10,000 payment from Acme via bank transfer"),
the AI service parses it into a structured draft voucher, which passes debit/credit balance,
subject-whitelist and period-lock gates before a human approves posting in the console —
**the LLM never writes to the ledger directly**.

```text
front (Vue3 :3000) ──► backend (Spring Boot :8080) ──► ai (FastAPI :8000)
```

Monorepo (`apps/`), atomic cross-stack contract commits; PK-01~08 accounting gates,
circuit breakers, schema v2 amounts (signed Decimal strings).

## Quick start

One-command demo (all-in-one image, no API keys needed — NL parsing runs on the
deterministic rule layer):

```bash
docker build -f deploy/all-in-one/Dockerfile -t ledgerpilot/all-in-one .
docker run -p 3000:3000 ledgerpilot/all-in-one
# open http://localhost:3000 (admin / 123456)
```

Production (compose, per-component containers, MySQL):

```bash
cp deploy/.env.example deploy/.env   # set JWT_SECRET etc.
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

Per-stack development: see `apps/*/README.md`.

## We do not claim

"Production-grade" (requires real production traffic validation), OCR/invoice recognition
(planned), Excel export (stub), mobile-first, authoritative benchmarks, standard chart
numbers (builtin subjects are demo samples). Full lists in each app's README.

## Disclaimer

Output is a draft voucher; review by a licensed accountant is required before booking.
Subjects/rates/demo data are synthetic. IIT uses monthly tax-bracket conversion (not
cumulative withholding).

## License

Apache-2.0 — see [LICENSE](LICENSE).
