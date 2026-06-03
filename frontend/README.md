# File Portal — Frontend

React + TypeScript + Vite + Tailwind v4 + Redux Toolkit + Axios.

## Architecture: BFF (Backend-for-Frontend)

This SPA does **not** run the OIDC flow itself. The `ApiGateway` is a confidential
OIDC client that logs the user in server-side, holds a **session cookie**, and
relays the access token to downstream services (`TokenRelay`). The frontend just
rides on that session.

| Concern   | Contract |
|-----------|----------|
| Login     | Full-page redirect to `http://localhost:8080/oauth2/authorization/gateway-client` (absolute, gateway origin) |
| Who am I  | `GET /api/auth/userinfo` → `200` UserInfo or `401` anonymous |
| CSRF      | Gateway sets `XSRF-TOKEN` cookie; Axios echoes `X-XSRF-TOKEN` |
| Logout    | Full-page nav to `http://localhost:8080/logout` (RP-initiated, goes via Keycloak) |
| APIs      | `/api/v1/{files,uploads,users,jobs}/**` (relative) |

## Running in dev

```bash
# 1. Start the backend stack (gateway, keycloak, services) from the repo root.
# 2. Start the dev server:
npm install
npm run dev
# 3. Open http://localhost:5173  (or http://localhost:8080/ui)
```

### Login flow (intended behavior)

Opening **http://localhost:5173** the first time (no session) immediately
redirects to the **Keycloak** login page, and after authenticating the gateway
lands you at **http://localhost:8080/ui**. From then on the app runs at
`:8080/ui`, same-origin with the gateway, so the session cookie and CSRF token
flow with every request.

This works because login/logout URLs are **absolute to the gateway origin**
(`http://localhost:8080`, configurable via `VITE_GATEWAY_ORIGIN`), while
userinfo and `/api/v1` calls are **relative**. At `:5173` (pre-login) the
relative userinfo probe fails → the app redirects to Keycloak. At `:8080/ui`
(post-login) it succeeds against the session. Vite uses `base: '/ui/'` so assets
resolve under the gateway's `/ui` route; HMR connects directly to `:5173`
because the servlet gateway does not proxy websockets. `base` is `'/ui'`
(no trailing slash) so the dev server answers both `/ui` and `/ui/` — the
gateway's `defaultSuccessUrl` is `/ui` and it forwards that path verbatim.

## Layout

```
src/
  config.ts                 # URLs, CSRF names, API roots (relative paths)
  api/axios.ts              # shared Axios instance (cookies, XSRF, 401 redirect)
  app/store.ts, hooks.ts    # Redux store + typed hooks
  features/auth/            # authSlice, authApi, types  (the BFF auth wiring)
  components/               # ProtectedRoute, AppLayout, FullPageSpinner
  pages/DashboardPage.tsx   # placeholder, to be replaced with real UI
```
