# Archive

`phase4-monolith/` is the single Spring Boot app from Phases 1–4, kept for reference after Phase 5 split it into
microservices (`/backend`). It is not built or run anymore. Compare, for example,
`phase4-monolith/src/main/java/com/goldstack/order/OrderExecutor.java` (one database transaction) with
`backend/order-service/.../OrderSaga.java` (a saga across services). Safe to delete once the project is in git.
