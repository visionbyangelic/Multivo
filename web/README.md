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
- The look is KakaoTalk-style: light screens, yellow `#FEE500` for your
  own messages and buttons, white bubbles for everyone else. A translated
  message reads like a normal chat message — the translation is the
  message, and the original sits underneath it as one small grey line
  with its flag.
- To use it for real, switch to a live server from Profile → Change
  server, type your server address, then **Save & check server**.
  The live server is `https://multivo.onrender.com` (Render, free tier;
  it sleeps after 15 minutes idle, so open it a minute before presenting).
  Use your laptop's IP on the same Wi-Fi, for example
  `http://192.168.1.5:8080` — the Android emulator address `10.0.2.2`
  does not work from a real phone. The field starts empty on purpose.
- Sign in or create an account, then Chats, Contacts, Alerts and Profile
  work the same as on Android: messages are translated into your language,
  typing and online status arrive live over WebSocket.

## Live translation, honestly

The server only translates when `GOOGLE_TRANSLATE_API_KEY` is set, and
there is no key on the live server — so a live message in Chinese would
otherwise arrive untranslated. Until that key exists, the app translates
in the browser with MyMemory's free service (no sign-up, short chat
messages only). A message the server already translated is never
re-translated, and if the free service is out of its daily quota the
message just shows in its original language. The proper Google key is
still the follow-up for the real product.

## AI test partners (live server)

Three test accounts live on the server so the app can be tried out with
nobody else online. Each is named by its language, with its flag, a cute
picture, and a bio saying it is an AI test partner — they are stand-ins,
not real people. They answer in their own language (Chinese, French,
Korean) and the app translates for you.

- `Chinese 🇨🇳` (`@chinese`, panda picture, reads Chinese)
- `French 🇫🇷` (`@french`, cat in a beret, reads French)
- `Korean 🇰🇷` (`@korean`, bunny picture, reads Korean)

Their pictures live in `avatars/` (`chinese.svg`, `french.svg`,
`korean.svg`) — generated images, shrunk and wrapped in SVG so they
can be pushed to GitHub as text files. The originals are kept outside
the repo.

## Files

- `index.html` — the page (inline CSS in KakaoTalk style, iOS meta tags, bottom tab bar)
- `app.js` — all the logic: server settings, auth, chats, live WebSocket,
  contacts, groups, notifications, profile, demo mode, free in-browser
  translation fallback
- `manifest.json`, `icon.svg` — Home Screen name, colours and icon
- `avatars/` — cute pictures for the AI test partners (`chinese.svg`,
  `french.svg`, `korean.svg`)
