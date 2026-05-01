# Tasks

## Task 1: Add `addUser` to `UserStore` interface and update `InMemoryUserStore`

- [x] 1.1 Add `suspend fun addUser(user: User)` to the `UserStore` interface in `shared/src/commonMain/kotlin/com/assistant/rbac/UserStore.kt`
- [x] 1.2 Change `addUser` in `InMemoryUserStore.kt` from a standalone method to `override suspend fun addUser(user: User)` to implement the new interface method

## Task 2: Seed default users in `CoreModule.kt`

- [x] 2.1 Update the `UserStore` singleton in `CoreModule.kt` to seed default users (`admin` with ADMINISTRATOR role, `user` with READER role) using `runBlocking` to call the suspend `addUser()` method during DI initialization
- [x] 2.2 Add necessary imports (`kotlinx.coroutines.runBlocking`, `com.assistant.auth.UserRole`) to `CoreModule.kt`

## Task 3: Register user on successful authentication in `AuthServiceImpl`

- [x] 3.1 Add `UserStore` as a constructor dependency to `AuthServiceImpl`
- [x] 3.2 In `authenticate()`, after creating `AuthenticatedUser` and before returning `AuthResult.Success`, convert to a `User` and call `userStore.addUser()` to register the user in the store (idempotent — map put overwrites existing)
- [x] 3.3 Update the `AuthService` Koin binding in `CoreModule.kt` to pass `UserStore` via `get()` to `AuthServiceImpl`

## Task 4: Verify the fix

- [x] 4.1 Compile affected modules (`shared`, `server`) to verify no compilation errors
- [x] 4.2 Verify `GET /api/users` returns the seeded users after server startup
