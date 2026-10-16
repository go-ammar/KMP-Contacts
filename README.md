# Sliide Users — Kotlin Multiplatform

A cross-platform user directory on top of the [GoRest](https://gorest.co.in) API. One Compose
Multiplatform UI, one set of ViewModels, one set of tests, running on Android and iOS.

---

## Quick start

### 1. Add a GoRest API token — required to add or delete users

Browsing works without one, because GoRest's read endpoints are unauthenticated. **Creating and
deleting users will fail until you add a token.**

Get one from <https://gorest.co.in/consumer/login>, then create `local.properties` in the
project root:

```properties
gorest.token=YOUR_TOKEN_HERE
```

That file is gitignored, so the token never enters the repository. `GOREST_TOKEN` as an
environment variable works too, which is what CI uses. Without either, the app still loads,
caches and browses the directory; the add and delete actions explain what is missing rather
than failing silently.

### 2. Open in Android Studio

Open the project folder and let Gradle sync. Android Studio writes the `sdk.dir` line into
`local.properties` for you. Then run the **composeApp** configuration on a device or emulator.

Requires Android Studio Ladybug (2024.2) or newer, and JDK 17.

### 3. Or build from the command line

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk   # or wherever your SDK lives

./gradlew :composeApp:assembleDebug        # Android APK
./gradlew :shared:testDebugUnitTest        # 89 unit tests
./gradlew ktlintCheck                      # formatting, enforced not described
```

`ANDROID_HOME` is only needed for a command-line build — Android Studio handles it itself. CI
(`.github/workflows/ci.yml`) runs exactly these on every push, plus the Apple-target compile.

For iOS, generate the Xcode project first (see [iOS](#ios)).

### Watching the network

Requests are logged through an `expect`/`actual` logger - `android.util.Log` on Android,
`NSLog` on iOS - because Ktor's default logger writes to stdout, which Android discards, so the
plugin would otherwise be installed and silent.

```bash
adb logcat -s SliideHttp
```

The `Authorization` header is redacted to `***` via `sanitizeHeader`, so the token never
reaches a log. Level is `LogLevel.HEADERS`; pass `LogLevel.BODY` to `createHttpClient` in
`SharedModule.kt` to see payloads too.

### Code style

`ktlint` owns formatting and runs in CI; there are currently zero violations.

```bash
./gradlew ktlintCheck    # fails the build on a violation
./gradlew ktlintFormat   # fixes most of them
```

One convention sits on top of it that no tool enforces: **an `if` in statement position always
takes braces**, however short the body.

```kotlin
if (_uiState.value.isSubmitting) {
    return
}
```

An `if` in *expression* position - one that returns a value, Kotlin's equivalent of a ternary -
deliberately stays inline:

```kotlin
colorScheme = if (useDarkTheme) DarkScheme else LightScheme
```

The distinction is the point. The readability risk braces protect against is control flow: not
being able to tell at a glance whether the next line belongs to the branch. That risk does not
exist when the `if` is producing a value, and bracing those turns a one-line selection into a
five-line block.

detekt's `BracesOnIfStatements` was trialled to enforce this and removed: it cannot tell the
two positions apart, so `always` flagged all 21 expression-position cases. A rule that cannot
express the convention is worse than the convention written down, so this is a review-time
standard rather than a build-time one.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  composeApp                                                  │
│  Compose Multiplatform UI · Android entry · iOS framework    │
│  ui/theme · ui/components · ui/users · di/UiModule           │
└───────────────────────────┬─────────────────────────────────┘
                            │ depends on
┌───────────────────────────▼─────────────────────────────────┐
│  shared                                                      │
│                                                              │
│  presentation/   ViewModels + UI state (MVVM)                │
│       ▲                                                      │
│  domain/         models · repository contract · use cases    │
│                  validation · relative-time formatting       │
│       ▲                                                      │
│  data/           Ktor remote · SQLDelight cache · mappers    │
│                  UserRepositoryImpl                          │
└─────────────────────────────────────────────────────────────┘
```

Two Gradle modules rather than one, so the boundary between UI and everything else is enforced
by the build rather than by convention. `domain` is pure Kotlin with no Ktor, SQLDelight or
Compose on its classpath; dependencies point inward only.

### Why MVVM and not MVI

MVI's single `Intent` channel buys exhaustive event logging and a trivially replayable state
machine. This screen has fewer than a dozen interactions, so that machinery would be a reducer,
an intent sealed class and a dispatcher standing between a button and the four lines of code it
runs. MVVM gives the same immutable-state-plus-unidirectional-data-flow property —
`StateFlow<UserListUiState>` is the only thing the view reads — without the ceremony:

- the view calls `viewModel.onDeleteConfirmed()`, not `viewModel.dispatch(Intent.ConfirmDelete)`
- one-shot signals (a failed delete) go through a separate `Flow<UserListEvent>`, so they are
  not replayed when the screen is recreated
- the ViewModels live in `shared`, so both platforms get identical behaviour from one test suite

### Stack

| Concern | Choice |
|---|---|
| UI | Compose Multiplatform 1.8.1, Material 3 |
| ViewModel | `androidx.lifecycle` KMP ViewModel (real `viewModelScope` on both platforms) |
| Networking | Ktor 3.1.3 — OkHttp engine on Android, Darwin on iOS |
| Persistence | SQLDelight 2.0.2 |
| DI | Koin 4.0.4 |
| Async | Coroutines + Flow; `kotlinx-datetime` for instants |
| Build | Kotlin 2.1.21, AGP 8.8.0, Gradle 8.14, JDK 17 |

### `expect`/`actual`

Kept to the two places where the platforms genuinely differ, rather than used as a general
abstraction tool:

| Declaration | Android | iOS |
|---|---|---|
| `DatabaseDriverFactory` | `AndroidSqliteDriver` (needs a `Context`) | `NativeSqliteDriver` |
| `platformHttpClientEngine()` | OkHttp | Darwin / `NSURLSession` |
| `platformModule()` | binds `Context` into Koin | no-arg factory |

Everything else — including the whole UI — is common code.

---

## Decisions worth explaining

**The relative timestamp is a local first-seen time, not a server field.**
GoRest's `/users` payload is `{id, name, email, gender, status}` — there is no `created_at`. So
the cache records when each user first arrived locally and the shared `RelativeTimeFormatter`
derives the label from that. Users created in the app carry their real creation time. The
consequence is honest but worth stating: on a first launch every row reads "just now", and ages
become meaningful from the second launch onwards. Inventing plausible-looking timestamps would
have demoed better and been a lie.

**Delete defers the API call until the undo window closes.**
The brief can be read as "`DELETE`, then offer undo", but undoing a committed delete would mean
re-`POST`ing and handing the user a different id than the one they just restored. Instead the
row is hidden locally, a pill shows the message with a ring that fills over the five-second
window, and the `DELETE` fires only when the ring closes. Undo is then genuinely free: cancel a timer, clear a flag. A
deletion abandoned by a killed process is restored on next launch (`restoreAbandonedDeletions`)
— if the request never left the device, it never happened. A `DELETE` that fails un-hides the
user rather than leaving them invisible.

**Stale data beats an error screen.**
A failed refresh only takes over the screen when the cache is empty. With anything cached, the
list stays, and a dismissible banner explains that the data is stale. Offline is a first-class
state, not an error.

**The cache merge avoids SQLite `UPSERT`.**
`ON CONFLICT ... DO UPDATE` needs SQLite 3.24; Android API 26 ships 3.18. The merge is an
`INSERT OR IGNORE` followed by an `UPDATE` of the mutable columns, inside one transaction. That
preserves `firstSeenAtEpochMillis` and the pending-delete flag on rows that already exist — a
refresh must never reset a user's age or resurrect one mid-undo. Both properties are covered by
tests against a real in-memory database.

**"Last page" comes from the pagination header.**
`GET /users?page=1` reports the page count in `x-pagination-pages`; the last page is then
fetched directly. A single-page response skips the second request.

**Icons are hand-declared vectors.**
`androidx.compose.material.icons` resolves on Android but is not published for the Apple
targets, and the multiplatform `material-icons-*` artifacts are deprecated. Five Material
Symbols path strings in `AppIcons.kt` cost less than a deprecated dependency and guarantee the
two platforms render the same glyphs.

**Shimmer is ~40 lines, not a library.**
A moving linear-gradient brush in `drawWithCache`, so it behaves identically on both platforms
and costs one draw layer.

**The token is generated into `ApiConfig`, not committed.**
A Gradle task reads `gorest.token` from `local.properties` (or `GOREST_TOKEN`) and emits a tiny
Kotlin source file. With no token the app still browses and caches; create and delete fail fast
with an explanation instead of a 401. Changing the token invalidates the configuration cache, so
a stale value cannot survive into a later build.

To be explicit about what this does and does not protect: it keeps the token out of version
control, which is the point. It does not make it a secret at runtime - it is a plaintext string
in the APK and `unzip -p app.apk 'classes*.dex' | grep -a <token>` will find it. For a real
product a write-capable credential would not live on the client at all; the app would talk to a
backend that holds it.

---

## Adaptive layout

A `BoxWithConstraints` breakpoint at 720dp, not a device-class check, so it responds to the
actual space available:

- **< 720dp** (phone portrait) — single list; tapping a user opens a detail bottom sheet
- **≥ 720dp** (phone landscape, tablet, foldable) — 400dp list pane beside a detail pane

The FAB moves with the layout rather than floating over everything: screen-level on one pane,
anchored inside the list pane on two, because a screen-level FAB sits on top of whatever detail
row happens to be beneath it. Selection is treated as a two-pane concept and is cleared when
the layout collapses - otherwise rotating a phone would promote the selected user into a modal
nobody opened, stacked on top of a half-filled add form.

### Feel and accessibility

A long press opens a destructive dialog, so it fires a haptic first: on a list of near-identical
rows, the buzz is the only confirmation that the intended row was hit.

Rows are announced as one item rather than four fragments - `"Ada Lovelace,
ada@example.com, Active, added 5 minutes ago"` - with selection exposed as state. The status dot
and a bare relative timestamp carry no meaning read aloud on their own. The undo pill is a
polite live region, because a window that closes on a timer is no use to someone who has to go
looking for it. An instrumented test pins the announcement string.

Also: a custom adaptive launcher icon (vector, with a monochrome layer for themed icons),
edge-to-edge, dark mode, `animateItem()` for insertion and removal, an extended FAB that
collapses on scroll, pull-to-refresh, and an undo pill whose progress ring fills over the undo
window. The skeleton has a 500 ms floor so a fast response does not flash it for three frames;
cached data bypasses the floor entirely, because real content should never wait. The ring is animated in the UI from the window length the ViewModel publishes, so it
cannot drift from the timer that actually commits the deletion, and the state does not
recompose once per second to drive it.

---

## Tests

```bash
./gradlew :shared:testDebugUnitTest          # 89 JVM tests
./gradlew :composeApp:connectedDebugAndroidTest   # 4 gesture tests, needs a device
```

**89 unit tests**, covering:

| Suite | What it pins down |
|---|---|
| `UserListViewModelTest` | load → content, offline with and without cache, the full delete/undo/commit lifecycle, the countdown, a second delete pre-empting the first, selection invalidation |
| `AddUserViewModelTest` | errors withheld until a field is touched, submit gating, email normalisation, 422 field errors landing on their fields, transport failures |
| `UserInputValidatorTest` | ~40 name and email cases, including the ones the regex is easy to get wrong (`ada@example..com`, `ada.@example.com`, `ada@-example.com`) |
| `UserModelTest` | `initials` for single-word, whitespace-only and non-Latin names (the `"?"` fallback is what stops an empty avatar), plus the `fromWire` fallbacks - `UserDto.gender` is nullable, so those branches run in production |
| `RelativeTimeFormatterTest` | every unit boundary, singular/plural, truncation toward zero, a clock skewed into the future |
| `UserRemoteDataSourceTest` | Ktor `MockEngine` — pagination header following, status-to-error mapping, bearer header, fail-fast with no token |
| `UserRepositoryIntegrationTest` | the repository against a real in-memory SQLite database: age preservation across refreshes, undo-window safety, failed-delete recovery |

No ViewModel writes user-facing English. They emit typed failures -
`UserListEvent.DeleteFailed(userName, error)`, `submitError: AppError?` - and the UI turns
those into words in one file, `StateViews.kt`. An earlier draft mapped errors to sentences in
three places across two layers, which is both a layering leak and the thing that would block
localisation first.

Every dependency a ViewModel takes is a single-verb use case, including
`RestoreAbandonedDeletionsUseCase`. An earlier draft injected the whole `UserRepository`
alongside the six use cases just to reach one method, which quietly handed the presentation
layer the entire data surface - the kind of thing that is invisible until someone reaches for
it.

### Why there are instrumented tests at all

Long-press is a *requirement*, not an implementation detail - it is the only route to deleting
a user. It cannot be covered by a JVM unit test, because there is no touch input, and it turned
out that synthetic `adb input` does not reproduce it either: a zero-displacement swipe and
paired `motionevent` calls both did nothing, which looked exactly like a broken feature. Three
Compose UI tests pin the gesture contract instead - long-press raises the delete request, a tap
does not, and the row renders the name, email and relative timestamp the brief asks for.

Running them on Android 16 needs Espresso 3.7.0; the 3.5.0 that Compose 1.8 pulls in calls
`InputManager.getInstance()`, which that platform removed, and every test dies in `onIdle`.

The ViewModels are testable without a dispatcher fight because the relative-time ticker is an
injected `Flow<Unit>` rather than a `while (true) { delay(…) }` in `init` — tests pass
`emptyFlow()`.

Two bugs the suite caught before the app ever shipped them.

`a second submit while one is in flight does not create the user twice` failed first time:
`isSubmitting` was being set *inside* the launched coroutine, so two taps both passed the
guard and created the user twice. It looked safe on Android only because
`Dispatchers.Main.immediate` happens to run the body eagerly - a different dispatcher would
have produced duplicate records. The flag is now claimed synchronously before the launch.

And `maps a malformed payload to a serialization error`
failed on first run. Ktor wraps deserialization failures in `ContentConvertException`, which
does not extend `SerializationException`, so bad payloads were being classified as
`AppError.Unknown` and shown as "Something went wrong" instead of the serialization branch.
That is a real bug the suite caught before the app ever ran.

---

## Previews

`composeApp/src/androidMain/.../UserListPreviews.kt` covers the states that are awkward to
reach by hand - the skeleton (up for half a second on a cold start) and the undo pill (five
seconds after a delete) - plus a row, a selected row in dark, and the detail pane. They double
as proof that the UI layer takes plain data: every one renders with no Koin, no database and
no network.

They live in `androidMain` rather than `commonMain` deliberately. Android Studio's preview
renderer only recognises `androidx.compose.ui.tooling.preview.Preview` from an Android source
set; the same file in `commonMain` with the JetBrains annotation compiles cleanly and renders
nowhere, which is how they were first written. The composables stay in `commonMain`, so
previewing from the Android side costs the shared UI nothing.

---

## iOS

The Xcode project is generated from `iosApp/project.yml` rather than committed, so the
repository does not carry an unreviewable `.pbxproj`:

```bash
brew install xcodegen
./iosApp/generate-project.sh
open iosApp/iosApp.xcodeproj
```

The target's pre-build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`, so building
from Xcode compiles the Kotlin framework first.

The Apple compile logs two `KLIB resolver: Could not find …savedstate…` warnings. Those
artifacts *are* on the compile classpath (confirmed via
`./gradlew :composeApp:dependencies --configuration iosSimulatorArm64CompileKlibraries`); the
message is an upstream klib naming mismatch in lifecycle 2.9, and compilation succeeds.

---

## How AI was used

The brief asks, so: the whole thing was built in one session with Claude as the implementer,
driven conversationally rather than by accepting completions.

What it was good for:

- **Volume with structure.** Layer scaffolding, DTOs, mappers, Koin wiring, the Material 3
  palette, 89 unit tests. This is the work that is tedious by hand and that an LLM does well once
  the shape is fixed.
- **Edge-case enumeration in tests.** The validator and formatter suites are deliberately
  exhaustive; listing every email malformation and every duration boundary is exactly the kind
  of breadth that is cheap to generate and expensive to write.
- **Being wrong usefully.** The `ContentConvertException` bug above came out of a generated test
  failing against generated production code.

What needed direction, and is where the actual engineering is:

- The first `UPSERT` was syntactically fine and would have crashed on Android API 26. Catching
  that required knowing which SQLite ships with which API level, then choosing the portable
  two-statement merge over bumping the dialect.
- The relative-timestamp problem — GoRest has no `created_at` — is a product decision, not a
  coding one. The options were to fake timestamps or to define "age" as first-seen; the second
  was chosen and documented.
- The delete-then-undo ordering was ambiguous in the brief. Deferring the `DELETE` is a
  deliberate reading, written down rather than assumed.
- The ticker in `init` was originally a `while (true)` loop that would have hung `runTest`
  forever. Making it an injected `Flow` is a testability decision that the generated code did
  not arrive at on its own.

The short version: AI removed most of the typing and a good deal of the boilerplate thinking.
The decisions this README spends its length on — compatibility floors, what a timestamp means,
when a destructive action becomes irreversible, what to do when the network fails — are the
parts that were curated, and they are the parts worth reviewing.

---

## Known gaps

- **Dark-mode containers were rebuilt once.** The first pass carried the light scheme's
  saturation across, which made a selected row a solid violet block. M3 dark containers are
  dim and low-chroma; the palette now reflects that.
- **Strings are inline, not externalised.** Compose Resources would be the move for a shipping
  app; for a one-day build it would have been setup cost without a second locale to justify it.
- **UI tests cover gestures, not layout.** The long-press contract is pinned, but there is no
  automated check that the two-pane breakpoint behaves - that was verified by hand on a device
  in both orientations.
- **Pagination is one page.** The brief asks for the last page, so that is what is fetched —
  infinite scroll would need a different cache merge strategy.
- **No retry/backoff on transport failures**, beyond the user pulling to refresh.
