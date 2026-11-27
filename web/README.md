# Multivo — iOS / web version

The same Multivo as the Android app, as a web app for iPhone. No Android
Studio, no Mac, no Xcode, no build step, no app-store install: it is one
page (`index.html` + `app.js`) that talks to the same Ktor server and the
same PostgreSQL database (`multivo`) as the Android app.

## Open it on iPhone

1. Host this folder somewhere your phone can reach it — the simplest is
   GitHub Pages from this repo, or any static host. (Opening the file
   directly works for demo mode; live mode needs `http(s)://` because the
   page fetches the server.)
2. Open the link on your iPhone.
3. Tap Share → **Add to Home Screen**. Multivo then sits on your Home
   Screen and opens full-screen, like an app.

## Use it

- It opens straight into the app, in demo mode: sample people and
  chats (Ada, Amélie, Diego, Yuki and the Weekend trip group) so you can
  see what your friend built and tap through every screen immediately.
  It says "Demo" at the top of every screen, and nothing is sent anywhere.
  Sending a message in demo gets an answer: the other person replies in
  their own language, with the English translation shown first — that is
  the feature the preview is for.
- To use it for real, switch to a live server from Profile → Change
  server, type your server address, then **Save & check server**.
  Use your laptop's IP on the same Wi-Fi, for example
  `http://192.168.1.5:8080` — the Android emulator address `10.0.2.2`
  does not work from a real phone. The field starts empty on purpose.
- Sign in or create an account, then Chats, Contacts, Alerts and Profile
  work the same as on Android: messages are translated into your language,
  typing and online status arrive live over WebSocket.

## Files

- `index.html` — the page (inline CSS, iOS meta tags, bottom tab bar)
- `app.js` — all the logic: server settings, auth, chats, live WebSocket,
  contacts, groups, notifications, profile, demo mode
- `manifest.json`, `icon.svg` — Home Screen name, colours and icon
