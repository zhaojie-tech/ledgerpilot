# LedgerPilot Front (apps/front)

Web console for the LedgerPilot bookkeeping platform — Vue 3 + TypeScript + Element Plus + Vite.

Three repos form the full site: this console → [apps/backend](../backend)
(Java voucher engine, :8080) → [apps/ai 的 AI 服务](../ai) (Python AI parsing service, :8000).
**The LLM has no direct write access to the ledger**: parse results land as DRAFT vouchers
requiring human approval in this console.

## Dev

```bash
pnpm install
pnpm dev        # http://localhost:3000, /api and /ai-api proxied to 8080/8000
```

Login: `admin / 123456` (backend H2 demo data).

## Production build

```bash
pnpm build      # outputs dist/, SPA history routing needs the nginx fallback in deploy/
```

See `deploy/nginx.conf.template` for the same-origin reverse proxy (`/api` -> backend,
`/ai-api` -> AI service with the service token injected server-side).

## We do not claim

1. "Production-grade" (same joint criterion across the apps in this repo);
2. Full coverage of every backend endpoint;
3. OCR / invoice recognition (planned);
4. Mobile-first layout.

## Disclaimer

1. Output is a draft voucher; review by a licensed accountant is required before booking.
2. Subjects, rates and demo data are synthetic samples.

## License

Apache-2.0 — see the repository root [LICENSE](../../LICENSE).
