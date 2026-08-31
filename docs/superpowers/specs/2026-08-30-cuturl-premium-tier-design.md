# CutURL — Premium Tier Design

**Date:** 2026-08-30
**Status:** Approved for planning
**Scope:** Accounts, entitlements, and gating of the four premium features that already exist.

## Problem

CutURL is single-tenant. `ShortUrl` has no owner, every read query is global, and
`GET /api/urls` returns every link in the database to every visitor. Nine features are
wanted behind a subscription, but none of them can be gated until the model can answer
"whose link is this?"

Of the nine features originally requested, four already exist and need only a gate (custom
short links, dashboard, detailed analytics, QR codes). Two are unbuilt but belong in this
repo (UTM builder, public API). A browser extension was requested and then dropped. The
remaining two are separate products (app integrations, priority support) — priority support
is not code at all.

This slice builds the foundation and gates the four that exist. The remaining five land
behind the same gate later without redesign.

## Tiers

Premium requires an account, so a signed-up-but-unpaid state necessarily exists. Free
accounts get what anonymous gets plus a durable link list — free to build, since
ownership already provides it, and it is the reason to sign up before paying.

| Capability | Anonymous | Free account | Premium |
|---|---|---|---|
| Shorten a URL | Yes, rate-limited | Yes | Yes |
| See click count | Via stats token | Own links list | Own links list |
| Link list across devices | No | Yes | Yes |
| Link totals (count, total clicks, top links) | No | Yes | Yes |
| Custom short links | No | No | Yes |
| Dashboard breakdowns (by date, browser, OS, referrer) | No | No | Yes |
| Detailed per-link analytics | No | No | Yes |
| QR codes | No | No | Yes |

Link totals sit in the free tier deliberately. See "No bypass paths" below: they are
arithmetic on the caller's own link list, so gating them would be theatre.

## Entitlement rule

One derived method on `User`, and every gate calls only this:

```java
boolean isPremium() {
    return plan == Plan.PREMIUM
        && (planExpiresAt == null || planExpiresAt.isAfter(LocalDateTime.now()));
}
```

When real billing lands, the Stripe webhook writes `plan` and `planExpiresAt`. No gate
changes.

## Data model

New `users` collection:

| Field | Notes |
|---|---|
| `id` | |
| `email` | Unique index, stored lowercased |
| `passwordHash` | BCrypt. Nullable, so social login needs no migration |
| `plan` | `FREE` or `PREMIUM` |
| `planExpiresAt` | Null for `FREE` |
| `createdAt` | |

`ShortUrl` gains:

| Field | Notes |
|---|---|
| `ownerId` | Indexed. Null means anonymous |
| `statsToken` | Set only when `ownerId` is null. 128-bit `SecureRandom`, Base64url |

No data migration. All three existing documents are soft-deleted, so nothing needs an
owner or a token retrofitted.

## Authorization on existing endpoints

Every current endpoint is single-tenant and needs an explicit decision, not just the four
premium ones.

| Endpoint | Today | Becomes |
|---|---|---|
| `GET /{shortCode}` | Public | Public permanently. A redirect is never gated |
| `POST /api/urls` | Public | Public. Sets `ownerId` when authenticated, else issues a `statsToken`. `customAlias` without premium returns 403 |
| `GET /api/urls` | Every link, to everyone | Owner-scoped. 401 when anonymous |
| `GET /api/urls/{id}` | Public | Owner, or a valid `X-Stats-Token` header. Otherwise 404 |
| `DELETE /api/urls/{id}` | Public | Owner, or the stats-token holder for an anonymous link |
| `GET /api/analytics/{shortCode}` | Public | Premium and owner |
| `GET /api/analytics/dashboard` | Global counts | Authenticated and owner-scoped; the `ClickEvent`-derived breakdowns in the response require premium |
| `GET /api/qr/{shortCode}` | Public | Premium and owner |

Unauthorized reads return **404, not 403**. A 403 confirms that a short code exists,
which leaks the existence of other users' links to anyone enumerating codes.

**Path parameter inconsistency to resolve during implementation.** `/api/urls/{id}` is
named `id` for both verbs but the two behave differently today: `GET` passes it to
`getUrlByShortCode`, so it is really a short code, while `DELETE` passes it to `findById`,
so it is really a Mongo id. Token authorization needs to know which. Resolution: both take
the **short code**, and `deleteUrl` looks up by short code. The frontend already holds the
short code everywhere it holds the id.

`statsToken` is returned exactly once, in the `POST /api/urls` response body, and is never
retrievable afterwards. A client that loses it loses stats access to that link; the
redirect keeps working.

`getDashboardStats` currently counts every URL in the database. Per-owner scoping uses
derived queries (`countByOwnerIdAndActiveTrue` and siblings), matching the pattern
already used for the `active` flag. No hand-written queries.

`existsByShortCode` stays unscoped and unfiltered. It is the code-collision guard: if
another user's code — or a deleted one — stopped counting as taken, generation could
reissue it and links already in circulation would silently repoint.

## New endpoints

| Endpoint | Notes |
|---|---|
| `POST /api/auth/register` | Email + password |
| `POST /api/auth/login` | Session cookie |
| `POST /api/auth/logout` | Invalidates the session |
| `GET /api/me` | Current user and plan |
| `POST /api/me/upgrade` | Sets `plan=PREMIUM` with an expiry |

`POST /api/me/upgrade` is guarded by `app.billing.mock-upgrade-enabled`, defaulting to
**false**, so it cannot ship enabled. It exists to make the gate demonstrable end to end
before payments are wired.

## Authentication

`spring-boot-starter-security` with its default session cookie. The frontend is
same-origin static files, so this needs no token handling in JavaScript, and it provides
CSRF protection, `HttpOnly` cookies, and real server-side logout.

The user model is built for social login from the start (nullable `passwordHash`), but no
provider is configured in this slice. The future public API will authenticate with API
keys, not sessions.

## Security work this slice must include

Both items below are pre-existing and both are broken *by* introducing sessions, so they
are in scope rather than unrelated cleanup.

**CORS.** `WebConfig` sets `allowedOriginPatterns("*")` together with
`allowCredentials(true)`. This is harmless while there is nothing to steal. Once a session
cookie exists, any website can make credentialed calls to the API as the logged-in
visitor. Replace with an explicit origin allowlist.

**Redirect target protocol.** `CreateUrlRequest` validates with `@URL`, which accepts any
protocol `java.net.URL` recognises, including `file:`, `ftp:`, and `jar:`. Add an
`https?` pattern so a stored redirect target is always web-facing.

**Anonymous rate limiting.** New, and required: an unauthenticated shortener is a spam and
phishing rail. Per-IP fixed-window limit on anonymous `POST /api/urls` — **20 links per IP
per hour**, returning 429 past that. Authenticated requests are exempt. In-memory
`ConcurrentHashMap`, carrying a `ponytail:` comment naming the ceiling — single-instance
only, swap for Bucket4j or Redis when more than one node runs.

## No bypass paths

A premium feature is not "an endpoint requiring premium". It is **data or capability that
no reachable endpoint hands to a non-subscriber.** Gating a URL while the same information
is derivable elsewhere sells nothing. Five rules follow.

**1. The security config defaults to deny.** `anyRequest().authenticated()`, with an
explicit public allowlist: `GET /{shortCode}`, `POST /api/urls`, `/api/auth/**`, `/`, and
the static assets. Any endpoint added later is closed until someone deliberately opens it.
Gating endpoint-by-endpoint over a `permitAll` default means the next endpoint anyone adds
is public by accident.

**2. The gate is server-side only.** Locked buttons in `app.js` are cosmetic. `app.js` is
public, every endpoint it calls is callable with `curl`, and no client-side check counts as
a gate. Each premium endpoint re-checks `isPremium()` server-side regardless of what the UI
renders.

**3. Premium data must be reachable through exactly one path.** Verified for this slice:
`QrCodeService.generateQrCodePng` is called only from `QrCodeController`; `ClickEvent` is
read only by `AnalyticsService.getAnalytics` and `getDashboardStats`. Both are gated. Any
new caller of either is a new bypass and needs the same gate.

**4. Response bodies are scoped, not just endpoints.** `UrlResponse.qrCodeUrl` is dropped
for non-premium callers — it advertises a gated endpoint and appears in the free link list.

**5. `POST /api/me/upgrade` is itself a bypass** — it grants premium to whoever calls it.
`app.billing.mock-upgrade-enabled` defaults to false, and the endpoint must be absent from
the security allowlist so it also requires authentication. It must never be enabled in a
deployed environment.

### The dashboard is redefined, because the original could not be gated

`DashboardResponse` as it exists is four fields, all computable in the browser from the
free link list: `totalUrls` is its length, `totalClicks` its `clickCount` sum,
`urlsCreatedToday` a `createdAt` filter, `topUrls` a sort. Gating the endpoint paywalls
nothing.

Two ways out. Removing `clickCount` from the free list would make the numbers protectable
but contradicts the requirement that non-account users see click counts. So instead the
premium dashboard is redefined to carry `ClickEvent`-derived content, which no free
endpoint exposes:

| Dashboard content | Tier | Reason |
|---|---|---|
| `totalUrls`, `totalClicks`, `urlsCreatedToday`, `topUrls` | Free (any account) | Derivable from the caller's own link list. Not defensible as paid |
| Account-wide `clicksByDate` | Premium | Requires `ClickEvent` |
| Account-wide `browserStats`, `osStats`, `referrerStats` | Premium | Requires `ClickEvent` |
| Account-wide `recentClicks` | Premium | Requires `ClickEvent` |

The four scalars move to the free response and the gated dashboard becomes the account-wide
rollup of per-link analytics. This is a larger premium feature than the original, not a
smaller one, and it is actually enforceable.

### Reserved short codes

Adding authentication puts single-segment paths in front of `GET /{shortCode}`. A custom
alias matching one — `login`, `logout`, `register`, `api`, `me`, `dashboard`, `admin`,
`static`, `assets` — creates a link the filter chain swallows before the redirect
controller sees it, producing a silently dead link. Custom aliases are validated against a
reserved list and rejected with 400.

### Test coverage for bypasses

The authorization test table asserts, for every premium endpoint, the status returned to an
anonymous visitor, a free account, a non-owning premium account, and the owner. It
additionally asserts that no free-tier response body contains a `ClickEvent`-derived field
and that `qrCodeUrl` is absent for non-premium callers. Endpoint-by-endpoint status checks
alone would have missed the dashboard leak.

## Frontend

- Nav-bar auth state; register and login screens.
- The global link table becomes "your links", owner-scoped.
- Anonymous visitors keep `{code, statsToken}` pairs in `localStorage`. Clearing the cache
  or switching device loses stats access — not the link, which keeps redirecting. That
  friction is the upgrade prompt.
- Premium controls render **locked, not hidden**: better upsell, and less conditional
  rendering to maintain.

## Testing

The project has no test infrastructure. This slice adds some, narrowly, because
authorization has no visual symptom — "can user A read user B's link?" cannot be verified
by looking at the page.

`@WebMvcTest` with MockMvc and mocked services, table-driven over the authorization matrix
above: for each endpoint, assert the status returned to an anonymous visitor, a free
account, a non-owning premium account, and the owner. No database, no Testcontainers.

Feature behaviour beyond authorization is not tested in this slice.

## Out of scope

UTM builder, public API with keys and rate limits, app integrations, priority support, real
payments, password reset email, and social login. Each lands behind the same `isPremium()`
gate later.

A browser extension is not planned. It was in the original request and has been dropped.

## Known consequences, accepted

- Gating `GET /api/qr/{shortCode}` means the `<img>` embedding it works only for premium
  owners. Same-origin cookies make it function; every free user's QR button becomes an
  upsell.
- Free accounts can compute their own link totals from their own link list. This is
  accepted rather than prevented — see "No bypass paths".
- The custom-alias prefix in `index.html` reads `cuturl.to/` while `app.base-url` is
  `http://localhost:8080`. Cosmetic placeholder, not wired to configuration.
- The Java package remains `com.urlshortner` and the Maven artifact `url-shortner`. The
  rename to CutURL is user-facing only; renaming the package is a separate mechanical
  change.
