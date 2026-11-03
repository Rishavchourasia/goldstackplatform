# GoldStack 🪙

GoldStack is a digital gold investment platform, similar to Jar, Paytm Gold and PhonePe Gold. Users buy and sell gold in grams with **virtual coins** (1 coin = ₹1, not real money). It also has live prices, SIPs (auto-invest) and a "what if I had invested…" calculator.

> 🚧 **Work in progress.** It is built in phases. See [docs/SPEC.md](docs/SPEC.md) for the full plan.

## Features (planned)
- Signup/login with JWT, starting coin bonus, mock top-up
- Live gold price over WebSocket
- Buy/sell with a 5-minute locked quote
- Portfolio: grams, invested, current value, P&L, return %, transaction history
- What-if calculator (lump sum + SIP) with charts
- SIP auto-debit (create / pause / resume / cancel)

## Tech stack
Java 21 · Spring Boot 3 · PostgreSQL · Flyway · Redis · Kafka · Spring Cloud Gateway · React + TypeScript + Vite · Recharts · Docker · Kubernetes · Prometheus · Grafana · Zipkin · GitHub Actions

## Repository layout
| Folder      | What's inside                                  |
|-------------|------------------------------------------------|
| `backend/`  | 8 Spring Boot services + 2 shared libraries (multi-module Maven) |
| `frontend/` | React + TypeScript + Vite web app              |
| `infra/`    | docker-compose, Kubernetes, monitoring config  |
| `docs/`     | Spec, architecture and flow diagrams           |
| `archive/`  | The Phase 1–4 monolith, kept for comparison    |

## Progress
- [x] Phase 0: Setup
- [x] Phase 1: Modular monolith (backend logic)
- [x] Phase 2: React frontend
- [x] Phase 3: Docker
- [x] Phase 4: Redis
- [x] Phase 5: Microservices + Kafka
- [ ] Phase 6: Observability + integration tests
- [ ] Phase 7: Kubernetes
- [ ] Phase 8: CI/CD + polish

## Getting started

### Quick start: everything in Docker (one command)
Needs only Docker (Docker Desktop or Colima).
```bash
cp .env.example .env      # then set DB_PASSWORD, and JWT_SECRET from: openssl rand -base64 32
docker compose up --build -d
```
| URL | What |
|---|---|
| http://localhost:3000 | The app (Nginx serves React and proxies `/api` + `/ws` to the gateway) |
| http://localhost:8080 | API gateway: the single public API (routes: `/actuator/gateway/routes`) |
| http://localhost:8089 | **Kafka UI**: topics, messages, consumer groups, lag |
| `http://localhost:8081`…`8087` `/swagger-ui.html` | Swagger UI of user, wallet, price, order, sip, portfolio, notification |
| `localhost:5433` | PostgreSQL (see [Inspecting the database](#inspecting-the-database-pgadmin)) |
| `localhost:6380` | Redis (see [docs/REDIS.md](docs/REDIS.md)) |

Useful commands:
```bash
docker compose ps                 # status + health of each container
docker compose logs -f backend    # follow backend logs
docker compose up --build -d      # rebuild after code changes
docker compose down               # stop (data kept in the postgres-data volume)
docker compose down -v            # stop AND wipe the database
```

How the containers fit together (details: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**, flows: **[docs/FLOWS.md](docs/FLOWS.md)**):
```
browser ─► :3000 Nginx ─► :8080 api-gateway ─► user | wallet | price | order | sip | portfolio | notification
                                                  │ each service: its own Postgres database (one server, 7 DBs)
                                                  └ events over Kafka (outbox → topic → idempotent consumer)
```
13 containers in total. They need about 4 GB of RAM inside Docker/Colima. Compose starts them in order using health
checks: Postgres, Redis and Kafka are ready, then the services, then the gateway, then the frontend.

Watch a saga: open Kafka UI → *Topics* → `order.placed`, buy gold in the app, and see `order.placed →
wallet.settled → order.completed` appear. To see a **compensation** (refund) instead, run this first:
```bash
curl -X POST localhost:8080/api/dev/saga/fail-next-gold-credit -H "Authorization: Bearer <token>"
```

### Development mode
Prerequisites: Java 21 (`export JAVA_HOME=/opt/homebrew/opt/openjdk@21` on macOS/Homebrew), Maven 3.9+, Node 22+.

Frontend with hot reload, against the Dockerised backend:
```bash
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api and /ws to the gateway on :8080
```
Work on ONE service locally: stop its container, then run it from Maven. It finds Postgres, Redis and Kafka on
the published ports (5433 / 6380 / 9094) via `.env`:
```bash
docker compose stop wallet-service
cd backend && mvn -q -pl wallet-service -am spring-boot:run
```
(Locally the services use ports 8081–8087. The Docker gateway still points at the containers, so call a locally
running service directly, e.g. `localhost:8082/swagger-ui.html`.)

| Page | What it shows |
|---|---|
| Dashboard | Live price + sparkline (WebSocket), coin balance, gold, value, P&L, recent activity, top-up |
| Buy / Sell | Quote with a 5-minute countdown → confirm. One idempotency key per attempt; safe retry; expired-quote and low-balance handling |
| Portfolio | Summary, value-vs-invested chart, paginated transactions |
| What if? | Lump-sum and SIP calculators with charts and table views |
| SIPs | Create / pause / resume / cancel, installment history, and a dev-only "run SIP job now" button (`npm run dev` only) |
| Notifications | Alerts for purchases, SIP successes and failures |

Money note: the API sends coins, grams and prices as **strings** (`"500.00"`), because JavaScript numbers are
doubles. The frontend only formats them for display and never does arithmetic on money.

Frontend architecture (Redux Toolkit + redux-saga, design system, error handling, how to add a feature):
**[frontend/README.md](frontend/README.md)**.

### Inspecting the database (pgAdmin)
Postgres is published on your Mac at port **5433** (not the default 5432, so it can't clash with a
locally installed Postgres), and any database tool can connect.
In **pgAdmin**: right-click **Servers → Register → Server…**
- **General** tab → Name: `GoldStack (local)`
- **Connection** tab → Host: `127.0.0.1` · Port: `5433` · Maintenance database: `goldstack` · Username: `goldstack` · Password: the `DB_PASSWORD` value from your `.env`

Then open **Databases → goldstack → Schemas → public → Tables**, or use **Tools → Query Tool**. Some queries to try:
```sql
-- Every coin movement, newest first (each one has a DEBIT + CREDIT pair)
SELECT transaction_id, direction, amount, entry_type, created_at
FROM coin_ledger_entries ORDER BY created_at DESC LIMIT 20;

-- The double-entry promise: both of these must be exactly 0
SELECT SUM(balance) FROM wallets;
SELECT SUM(grams)   FROM gold_accounts;
```
No pgAdmin? `docker compose exec postgres psql -U goldstack goldstack` opens a SQL prompt inside the container.

### Inspecting Redis (`redis-cli`)
```bash
docker compose exec redis redis-cli        # already authenticated
```
Then try `SCAN 0 COUNT 100`, `GET gold:price:latest`, `TTL quote:<id>` or `MONITOR`.
What each key is for, and more commands: **[docs/REDIS.md](docs/REDIS.md)**.

### Running the tests
Integration tests start a real PostgreSQL and Redis with Testcontainers, so Docker must be running.
With **Colima**, Testcontainers needs to be told where Docker is:
```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
cd backend && mvn test                         # all modules
cd backend && mvn -pl order-service -am test   # one service
```

| Test (module) | What it proves |
|---|---|
| `MoneyTest` (common) | Rounding rules: grams DOWN, payouts DOWN, why not `double` |
| `CoinLedgerTest` (wallet) | Every transfer writes one DEBIT + one equal CREDIT; user wallets can't go negative |
| `WalletSagaStepTest` (wallet, Postgres) | Two simultaneous settlements can't overspend; a redelivered event is processed once; a refund (compensation) pays exactly once |
| `QuoteTest` (order) | Quotes expire after 5 minutes; a BUY quote can't sell |
| `OrderSagaTest` (order, Postgres + Redis) | Buy saga to COMPLETED; same key ×5 at once = one order; gold-credit failure asks for a refund; failed sell payout returns the gold; duplicate replies ignored |
| `WhatIfCalculatorTest`, `AverageCostTest` (portfolio) | What-if maths and average-cost P&L against hand-calculated numbers |

Full end-to-end saga tests with a real Kafka come in Phase 6.

## API routes

All routes except the public ones need `Authorization: Bearer <token>`. Errors always use one JSON shape:
`{timestamp, status, error, message, path, fieldErrors}`.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/signup` | public | Create an account (+10,000 bonus coins) and get a JWT |
| POST | `/api/auth/login` | public | Log in and get a JWT |
| GET | `/api/users/me` | ✅ | My profile |
| GET | `/api/wallet` | ✅ | Coin balance |
| POST | `/api/wallet/topup` | ✅ | Mock top-up `{amount}` (max 100,000 per call) |
| GET | `/api/wallet/ledger?page&size` | ✅ | My coin ledger entries |
| GET | `/api/prices/latest` | public | Current live price per gram |
| GET | `/api/prices/daily?from&to` | public | Daily closing prices |
| GET | `/api/prices/ticks?limit` | public | Recent live ticks |
| WS | `/ws/price` | public | Live price push |
| POST | `/api/orders/quotes` | ✅ | Lock a price for 5 min: `{side:"BUY", coins}` or `{side:"SELL", grams}` |
| POST | `/api/orders/buy` | ✅ | `{quoteId}` + header `Idempotency-Key`. 201 done, 200 replay, **202 still processing**, 422 failed |
| POST | `/api/orders/sell` | ✅ | `{quoteId}` + header `Idempotency-Key` |
| GET | `/api/orders?page&size` | ✅ | My orders |
| GET | `/api/orders/{id}` | ✅ | One order (poll after a 202: `PENDING` → `COMPLETED` / `FAILED`) |
| GET | `/api/gold` | ✅ | My gold balance (grams) |
| GET | `/api/gold/ledger?page&size` | ✅ | My gold ledger entries |
| GET | `/api/portfolio` | ✅ | Grams, invested, value, P&L, return %, coins |
| GET | `/api/portfolio/transactions?page&size` | ✅ | Transaction history |
| GET | `/api/portfolio/history` | ✅ | Daily value vs invested (chart data) |
| GET | `/api/what-if/lump-sum?amount&date` | public | "If I had invested X on date D…" |
| GET | `/api/what-if/sip?amount&frequency&startDate` | public | SIP what-if with a time series |
| POST | `/api/sips` | ✅ | Create SIP `{amount, frequency, startDate}` |
| GET | `/api/sips` | ✅ | My SIPs |
| GET | `/api/sips/{id}` | ✅ | One SIP |
| POST | `/api/sips/{id}/pause` · `/resume` · `/cancel` | ✅ | Change SIP status |
| GET | `/api/sips/{id}/executions` | ✅ | Installment history (success/failed) |
| GET | `/api/notifications?page&size` | ✅ | My notifications |
| GET | `/api/notifications/unread-count` | ✅ | Unread count |
| POST | `/api/notifications/{id}/read` · `/read-all` | ✅ | Mark read |
| POST | `/api/dev/sips/run?date` | ✅ dev only | Request today's SIP installments now (results arrive via Kafka) |
| GET | `/api/dev/ledger/coins` · `/api/dev/ledger/gold` | ✅ dev only | Check each ledger balances to 0 |
| POST | `/api/dev/saga/fail-next-gold-credit` | ✅ dev only | Make the next buy fail after the debit, to watch the refund compensation |

| Status | Meaning |
|---|---|
| 400 | Validation failed / bad input |
| 401 | Missing or invalid token |
| 404 | Not found (or not yours) |
| 409 | Conflict (email taken, idempotency key reused with a different quote, identical request still in progress, invalid SIP state) |
| 410 | Quote expired or already used |
| 422 | Insufficient coins or gold |
| 429 | Rate limit exceeded (see `Retry-After` header) |
| 503 | Redis or another service unavailable (`SERVICE_UNAVAILABLE` / `DEPENDENCY_UNAVAILABLE`) |

## Architecture
Eight services, each with its own database, connected by an API gateway, OpenFeign and Kafka. Buys and sells run as an
orchestrated **saga** with compensation, events are never lost thanks to the **transactional outbox**, and consumers
are **idempotent**. Everything is explained in **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**, with sequence
diagrams of every success and failure path in **[docs/FLOWS.md](docs/FLOWS.md)**.

## Design decisions
_Coming in Phase 8._
