# GoldStack frontend

React 19 + TypeScript + Vite. State is classic **Redux** with **redux-saga**, using a folder
structure with one folder per concern. Charts use Recharts, tests use Vitest, and
ESLint and Prettier check the code.

```bash
npm install
npm run dev          # http://localhost:5173 (proxies /api and /ws to the gateway on :8080)
npm test             # unit tests (reducers, sagas, helpers)
npm run lint         # ESLint
npm run format       # Prettier
npm run build        # type-check + production bundle
```

## Folder structure

```
src/
  index.tsx              store (createStore + saga middleware), root saga, <Provider>
  App.tsx                app shell: router, crash fallback, global overlays
  AppRouting.tsx         the route table
  App/
    Constants/           urlConstants.ts (every endpoint), RouteConstants.ts, variableConstant.ts, optionConstants.ts
    Models/              server shapes, one file per domain (Trade.ts, Price.ts, Sip.ts...)
    Services/            httpServices.ts (the only fetch) + one <Domain>Service.ts class per domain
    Redux/
      Actions/           <Domain>Constants.tsx: string action types (LOGIN_REQUEST, TRADE_QUOTE_SUCCESS...)
      Reducers/          <Domain>Reducer.tsx + reducers.tsx (combines them, exports RootState)
      Sagas/             <Domain>Saga.tsx + rootsaga.tsx (starts every saga, restarts one that crashes)
    Utils/               InfoStore.ts (saved session), errors.ts (error policy), sagaHelpers.ts,
                         format.ts, validation.ts, cx.ts, requestState.ts
    Components/          CustomUI/ (design system), CustomHooks/, AppShell/, ExceptionModal/, Toaster/...
    Views/               pages: Auth, Dashboard, Trade, Portfolio, WhatIf, Sips, Notifications
    GlobalStyles/        tokens.scss (colours, type, spacing, motion) and global.scss
```

## How a request flows

```
Component ──dispatch({ type: TRADE_QUOTE_REQUEST, payload })──► Reducer (state changes, pure)
                 │                                              ▲
                 └──────────────► Saga (TradeSaga.tsx) ─────────┘ put({ type: TRADE_QUOTE_SUCCESS })
                                     │
                                     └─► Service (TradeService.createQuote) ─► httpPost ─► API gateway
```

1. Components never call a service. They dispatch a plain action `{ type, payload }`.
2. Reducers are `switch` statements over the constants. They never call the API.
3. Sagas wait for the action, call the service through `callApi`, and `put` the result action.
4. Services only know URLs and HTTP, not Redux.

## Error handling (one rule, one place)

`Utils/sagaHelpers.ts → callApi()` wraps every service call:

| Error | Shown as |
|---|---|
| Expected (validation, not enough coins, quote expired) | inline, next to the form (`FormError`) |
| Unexpected (5xx, network, rate limit, service down) | the global `ExceptionModal` |
| 401 while logged in | logout, then the "session expired" modal |
| Render crash | the `ErrorBoundary` fallback page |

Background polls use `callApiHandled()`, which skips the modal, so a refresh loop can't spam it.
The UI reacts to error codes (`INSUFFICIENT_BALANCE`), never to message text.

## Recipe: add a feature

1. `Models/Foo.ts`: the server types.
2. `Services/FooService.ts`: `static async load() { return await httpGet<Foo>(url, true); }`
   (the URL goes in `Constants/urlConstants.ts`).
3. `Redux/Actions/FooConstants.tsx`: `export const FOO_REQUEST = "FOO_REQUEST";` and the success and failure types.
4. `Redux/Reducers/FooReducer.tsx`: a `switch` over those constants, returning `{ data, loading, error, err }`.
5. `Redux/Sagas/FooSaga.tsx`: `takeLatest(FOO_REQUEST, fooSaga)`, with a `try` that calls
   `callApi(FooService.load)` and `put`s `FOO_SUCCESS`, or `FOO_FAILURE` in the `catch`.
6. Register the reducer in `Redux/Reducers/reducers.tsx` and the saga in `Redux/Sagas/rootsaga.tsx`.
7. `Views/Foo/FooPage.tsx`: `useDispatchOnMount([{ type: FOO_REQUEST }])`, read the state with
   `useSelector((state: RootState) => state.foo)`, and build the page from `Components/CustomUI`.
8. Add the route in `AppRouting.tsx`, and tests next to the reducer and the saga.
