# Redis in GoldStack (Phase 4)

Redis is an **in-memory key → value store**. Reads and writes take well under a millisecond, and every key can have a
**TTL** (time to live): when it runs out, Redis deletes the key by itself.

GoldStack uses Redis for data that is **short-lived, shared between app instances, or both**. Postgres stays the
**source of truth**: if Redis is wiped, nothing is lost and no rule is broken. Things just get slower for a moment.
That's why the Redis container runs with persistence off (`--save ""`).

## The five keys

| Key | What's in it | TTL | Code |
|---|---|---|---|
| `gold:price:latest` | Current price JSON | none (overwritten every 5 s) | `price/PriceService` |
| `quote:{quoteId}` | Locked price + coins + grams + owner | 5 min | `order/QuoteStore` |
| `idem:{userId}:{key}` | `PENDING` lock, then the finished order | 30 s, then 24 h | `order/IdempotencyStore` |
| `ratelimit:{userId}` / `ratelimit:ip:{addr}` | Request count this minute | 60 s | `common/security/RateLimitFilter` |
| `whatif:{amount}:{date}` / `whatif:sip:{amount}:{freq}:{start}` | Calculator result | 1 h | `portfolio/PortfolioService` |

### 1. `gold:price:latest`: a shared cache
- **Problem:** in Phase 1 the latest price was a Java variable, so it existed in only ONE app instance. From Phase 5
  several instances run, and they must all quote the same price.
- **How:** every tick runs `SET gold:price:latest '{"pricePerGram":"12048.87",...}'`, and every reader runs `GET`.
- **If Redis is down:** we fall back to the latest row of `price_history` in Postgres. That's slower, but it works.

### 2. `quote:{quoteId}`: expiring, single-use data
- **Problem:** a quote must disappear after 5 minutes and must be used at most once, even if two requests try at
  the same instant.
- **How:**
  - Expiry: `SET quote:… <json> EX 300`. Redis deletes it at 5:00. No cleanup job, no `expires_at` checks in SQL.
  - Single use: confirming runs `GET` (check owner, side, expiry) then `DEL`. Redis runs commands one at a time, so if
    two requests race, only one `DEL` returns 1. The other gets **410 Gone**.
  - Compensation: if the purchase then fails (e.g. not enough coins), no money moved, so the quote is **put back** for
    its remaining lifetime. The user can top up and confirm at the same price.
- The old `quotes` table was dropped (`V7__quotes_moved_to_redis.sql`).

### 3. `idem:{userId}:{key}`: idempotency results and an in-progress lock
- **Problem:** a retried "Buy" must never buy twice. Phase 1 already guaranteed that with a Postgres UNIQUE
  constraint, but every retry still hit the database, and duplicates arriving at the *same moment* raced each other.
- **How:**
  1. `GET idem:…`. If it's `COMPLETED`, return the stored answer (no database work).
  2. `SET idem:… PENDING NX EX 30`. `NX` means "only if it doesn't exist", so exactly one request wins the key.
     Duplicates arriving meanwhile **wait** and then return the winner's answer.
  3. Execute the purchase in Postgres, then `SET idem:… COMPLETED EX 86400` (24 h).
  4. If the purchase failed, `DEL` the key, so the client may retry with the same key.
- **The user id is part of the key** (the spec says `idem:{key}`). That way two users who happen to send the same key
  can never see each other's orders.
- **Postgres is still the last line of defence.** If Redis is wiped, `UNIQUE(user_id, idempotency_key)` still blocks
  a double purchase, and the answer is rebuilt from the `orders` table (see `RedisFeaturesTest`).

### 4. `ratelimit:{userId}`: a counter with a window
- **Problem:** one script hammering the API shouldn't slow it down for everyone.
- **How:** a "fixed window". Each request runs this tiny Lua script *inside* Redis:
  ```lua
  local count = redis.call('INCR', KEYS[1])
  if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
  ```
  Over the limit (120/min per user, 60/min per IP when logged out) the API returns **429 Too Many Requests** with a
  `Retry-After` header. Responses carry `X-RateLimit-Limit` and `X-RateLimit-Remaining`.
- **Why Lua?** With two separate commands, a crash between `INCR` and `EXPIRE` would leave a counter with no TTL,
  and that user would be blocked forever. A Lua script runs as one atomic step.
- **If Redis is down:** requests are allowed (fail-open). Blocking everyone would be worse.
- In Phase 5 this check moves into the API gateway.

### 5. `whatif:{amount}:{date}`: cache-aside
- **Problem:** a SIP what-if over 7 daily years walks ~2,500 dates, and popular questions repeat.
- **How:** "cache-aside": look in Redis; on a miss, compute, then store with a 1 h TTL. The amount is normalised first,
  so `10000` and `10000.00` share one key.
- **Trade-off:** "value today" inside a cached answer can be up to 1 hour old. That's fine for an estimate, but not for
  a real balance, so the portfolio summary is never cached.

## Inspecting Redis with `redis-cli`

Open a shell inside the Redis container. The compose file sets `REDISCLI_AUTH`, so you're logged in automatically:
```bash
docker compose exec redis redis-cli
```

From your Mac with a locally installed `redis-cli` (port **6380**, password from `.env`):
```bash
redis-cli -p 6380 -a "$(grep ^REDIS_PASSWORD .env | cut -d= -f2)"
```

Useful commands (type them at the `127.0.0.1:6379>` prompt):

| Command | What it does |
|---|---|
| `SCAN 0 MATCH quote:* COUNT 100` | List keys matching a pattern, a batch at a time (safe on big databases) |
| `KEYS *` | List ALL keys. Fine locally, **never in production** (it blocks Redis while it scans everything) |
| `GET gold:price:latest` | Read a value |
| `TTL quote:<id>` | Seconds left. `-1` = no expiry, `-2` = key doesn't exist |
| `TYPE <key>` | Data type (all ours are `string`) |
| `EXISTS <key>` | 1 if present |
| `DEL <key>` | Delete. Try deleting `gold:price:latest`: the next request reloads it from Postgres |
| `DBSIZE` | Number of keys |
| `MONITOR` | Live stream of **every** command the app sends. Great for learning. `Ctrl+C` to stop |
| `INFO memory` | Memory usage |
| `FLUSHALL` | Wipe everything (local only!). The app recovers, and `RedisFeaturesTest` proves it |

### Try it: watch a purchase happen
1. In one terminal: `docker compose exec redis redis-cli MONITOR`
2. In the app, get a quote. You'll see `SET "quote:…" … "EX" "300"`.
3. Confirm it. You'll see `GET "idem:…"`, `SET "idem:…" … NX`, `GET "quote:…"`, `DEL "quote:…"`, and finally
   `SET "idem:…" {"state":"COMPLETED",…} EX 86400`.
4. Every 5 seconds: `SET "gold:price:latest" …`.
