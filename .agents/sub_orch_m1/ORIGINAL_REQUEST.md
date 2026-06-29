# Original User Request

## Initial Request — 2026-06-27T21:34:13+01:00

You are the Milestone 1 Sub-Orchestrator. Your working directory is C:\Users\marce\.gemini\antigravity\worktrees\supermarket-autobuy\review-application-architecture-standards\.agents\sub_orch_m1\.
Your parent is the Project Orchestrator (ID 70e6983c-3969-4f65-b68b-f75696c2fbde).

Your mission is to execute Milestone 1 (Model & Service Refactoring), which covers the following:
- R1. Interface-Compliant Credential Saving (DIP Fix) - Fixes #1: Add a default `saveCredentials(String, String, String)` method to the `CredentialProvider` interface (throwing `UnsupportedOperationException` by default) and override it in `PropertiesCredentialProvider`. Update WebApiController to call this method through the interface rather than casting.
- R2. Extract ProductService - Fixes #2: Create a new `ProductService` class in the `service/` package encapsulating all product and mapping business logic (save mapping, find mapping, delete mapping, find-or-create product). Mutating methods must be annotated with `@Transactional`.
- R3. Extract PriceHistoryService - Fixes #3: Create a new `PriceHistoryService` class in the `service/` package encapsulating price logging. The `logPrice()` method must be `@Transactional`.
- R5. PriceHistory LAZY Fetching & ObjectMapper Injection - Fixes #8: Change `PriceHistory.product` fetch type to `FetchType.LAZY`. Inject the Spring-provided `ObjectMapper` bean into `JsonShoppingListProvider` instead of instantiating it manually.
- Add new unit tests verifying the behavior of `ProductService` and `PriceHistoryService`.

Please execute the standard orchestrator procedure for this milestone scope:
1. Initialize your BRIEFING.md and progress.md.
2. Run the Explorer -> Worker -> Reviewer -> Challenger -> Auditor cycle.
3. Ensure builds/tests and spotless pass successfully.
4. When complete, update progress.md, update the status in PROJECT.md, and send a message back to the parent orchestrator (ID 70e6983c-3969-4f65-b68b-f75696c2fbde) with send_message.
Remember the anti-cheating guidelines, liveness deadliness, and security constraints. Do not run build/test commands directly; always spawn workers/reviewers/challengers/auditors to do so.
