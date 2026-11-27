# Multivo – chat in your language, read in theirs

One Android Studio project with two parts:

| Module    | What it is                                    | How to run                         |
|-----------|-----------------------------------------------|------------------------------------|
| `:server` | Ktor backend (Kotlin) + PostgreSQL            | `./gradlew :server:run`            |
| `:app`    | Android app (Kotlin, Jetpack Compose)         | Run the **app** configuration      |

## 1. Prepare the database (once)

Install PostgreSQL 14+ and create a database named `multivo` — not `mulivo_db`:

```sql
CREATE DATABASE multivo;
```

The server's default `DB_URL` is `jdbc:postgresql://localhost:5432/multivo`, so it
connects to this exact database name. If a database called `mulivo_db` exists,
delete it or leave it unused — Multivo never reads it.

Tables are created automatically on first start (Flyway migrations in
`server/src/main/resources/db/migration`).

## 2. Start the server

Set these environment variables (Android Studio: Run > Edit Configurations > Environment variables,
or in a terminal before running Gradle):

| Variable                   | Example / default                                 |
|----------------------------|---------------------------------------------------|
| `DB_URL`                   | `jdbc:postgresql://localhost:5432/multivo`        |
| `DB_USER`                  | `postgres`                                        |
| `DB_PASSWORD`              | `postgres`                                        |
| `JWT_SECRET`               | any long random text (required for real use)      |
| `GOOGLE_TRANSLATE_API_KEY` | optional – needs a Google billing account        |
| `DEEPL_API_KEY`            | optional – DeepL free key, no billing needed;     |
|                            | when set, DeepL translates instead of Google      |
| `PORT`                     | `8080`                                            |

```bash
./gradlew :server:run          # Windows: gradlew.bat :server:run
```

Check it: open http://localhost:8080/health -> `{"status":"ok","database":"connected",...}`

## 3. Run the Android app

1. Open this folder in Android Studio (File > Open) and let Gradle sync.
2. Start an emulator and press Run on **app**.
3. The app talks to the server set in `app/build.gradle.kts` (`DEFAULT_SERVER_URL`).
   The default `http://10.0.2.2:8080` is your computer as seen from the emulator.
   For a real phone, change it to `http://YOUR-COMPUTER-IP:8080` (same Wi-Fi) or your
   online server address, then run the app again. There is no server box in the app.
4. The app opens on **Sign in**; **Create an account** opens a separate page. New
   accounts read messages in the phone's language automatically (changeable there
   or later in Profile).

Every screen has `@Preview`s – open any `*Screen.kt` file and use the Split/Design view.

## 4. iOS / web version

There is an iPhone-usable version of the same app in `web/` — no Android
Studio, Mac or Xcode needed, no build step. It is a mobile web app
(`index.html` + `app.js`) that talks to the same Ktor server and the same
`multivo` database as the Android app: same sign-in, chats, contacts,
groups, notifications and profile, with live messages over WebSocket.
Open the hosted `web/` link on an iPhone, try demo mode (no server needed),
or point it at your laptop's server address, then Share → Add to Home
Screen. See `web/README.md` for the details.

## 5. Tests

```bash
./gradlew :server:test                 # 9 API tests (starts an embedded PostgreSQL automatically)
./gradlew :app:testDebugUnitTest       # logic, networking and screen tests (no emulator needed)
./gradlew :app:recordRoborazziDebug    # saves screen images to app/build/outputs/roborazzi
```

Server tests use an embedded PostgreSQL by default. To use your own instead, set
`TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` (that database is wiped on every run).

Optional end-to-end check of the app's networking against a running server:

```bash
MULTIVO_LIVE_URL=http://localhost:8080 ./gradlew :app:testDebugUnitTest --tests '*LiveServerTest*'
```

## API overview

| Method | Path | Notes |
|---|---|---|
| GET | `/health`, `/languages` | public |
| POST | `/auth/register`, `/auth/login` | returns `{ token, user }` |
| GET/PUT | `/users/me` | profile and preferred language |
| GET | `/users/search?q=` | includes relationship (contact / request_sent / ...) |
| GET/POST | `/contacts/requests`, `/contacts/requests/{id}/accept`, `/reject` | contact requests |
| GET/DELETE | `/contacts`, `/contacts/{id}` | contacts |
| GET | `/conversations`, `/conversations/{id}` | participants, last message, unread count |
| POST | `/conversations/private`, `/conversations/groups` | start chats |
| GET/POST | `/conversations/{id}/messages` | history (with *your* translation, and `readBy`) / send — text, or `audioData` / `imageData` (base64) / `stickerId` |
| POST | `/conversations/{id}/read` | clear unread |
| POST | `/conversations/{id}/clear` | clear chat for me only — hides every message so far from my view; the other people keep theirs, new messages still arrive |
| GET/POST/DELETE | `/stories/feed`, `/stories`, `/stories/{id}` | Moments — a caption and/or photo in your language, gone after 24h |
| GET/POST | `/notifications`, `/notifications/read-all` | notifications |
| WS | `/ws?token=JWT` | live `new_message`, `typing_*`, `user_online/offline`, `notification` |

All authenticated routes need `Authorization: Bearer <token>`.
