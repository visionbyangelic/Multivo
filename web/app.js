/* Multivo — iPhone web version.
 *
 * Same app as the Android version, same Ktor backend, no build step.
 * Open index.html in a browser (or serve this folder statically), pick a
 * server or try demo mode, and sign in. Everything the Android client does
 * lives here too, using the same endpoints (see Routes.kt / MultivoApi.kt):
 *   GET  /health, /languages
 *   POST /auth/register, /auth/login
 *   GET /users/me, PUT /users/me, GET /users/search?q=
 *   GET /contacts, DELETE /contacts/{id}
 *   GET /contacts/requests, POST /contacts/requests,
 *   POST /contacts/requests/{id}/accept, /reject
 *   GET /conversations, POST /conversations/private, /groups,
 *   GET /conversations/{id}/messages?limit=100,
 *   POST /conversations/{id}/messages, POST /conversations/{id}/read
 *   GET /notifications, POST /notifications/read-all
 *   GET /stories/feed, POST /stories, DELETE /stories/{id}
 *   WS  /ws?token=JWT
 */
'use strict';

// ---------- tiny helpers ----------
const $ = (sel, root) => (root || document).querySelector(sel);
const main = $('#main');
const tabsEl = $('#tabs');

// iOS Safari, the keyboard and the jumping room: when the keyboard opens,
// Safari scrolls the whole page to bring the focused field into view, so
// the chat header and the messages slide off the top and the composer ends
// up floating mid-screen with a big empty gap above it. The page itself
// never scrolls any more (see index.html: html/body overflow hidden), and
// the app is sized to exactly the part of the screen the keyboard leaves
// visible — visualViewport reports that height live, so we size from it
// on every change. When the height shrinks (keyboard opening) the newest
// messages are kept in sight; when it grows back (keyboard closing) the
// message list is left exactly where it was.
function scrollChatToBottom() {
  const box = document.getElementById('chat-messages');
  if (box) box.scrollTop = box.scrollHeight;
}
let lastAppH = 0;
function fitApp() {
  const h = window.visualViewport ? Math.round(window.visualViewport.height) : window.innerHeight;
  const app = document.getElementById('app');
  if (app) app.style.height = h + 'px';
  if (h < lastAppH) scrollChatToBottom();
  lastAppH = h;
}
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', fitApp);
  window.visualViewport.addEventListener('scroll', fitApp);
}
window.addEventListener('resize', fitApp);
window.addEventListener('orientationchange', fitApp);
fitApp();

// Never inject server or user text as HTML — always escape it first.
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}
// Contact requests show a person's name, not their country —
// a request from "Kim Tae" reads right, a flag there is clutter.
function stripFlags(name) {
  return String(name == null ? '' : name)
    .replace(/[\u{1F1E6}-\u{1F1FF}]{2}/gu, '').replace(/\s{2,}/g, ' ').trim();
}
function initials(name) {
  return (name || '?').trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();
}
// A flag next to a language, for the small "original" line under a
// translated message.
const LANG_FLAG = {
  en: '🇬🇧', fr: '🇫🇷', es: '🇪🇸', pt: '🇵🇹', de: '🇩🇪', it: '🇮🇹', nl: '🇳🇱',
  ru: '🇷🇺', ar: '🇸🇦', tr: '🇹🇷', hi: '🇮🇳', zh: '🇨🇳', ja: '🇯🇵', ko: '🇰🇷',
  sw: '🇰🇪', yo: '🇳🇬', ig: '🇳🇬', ha: '🇳🇬',
};
// A person's picture when they set one, initials in a yellow circle when
// they did not. `user` is a PublicUser, Participant or UserProfile.
function avatarHtml(user, title, online) {
  const pic = user && (user.profilePicture || picFor(user));
  const inner = pic
    ? '<img src="' + esc(pic) + '" alt="">'
    : esc(initials(title || (user && user.fullName) || '?'));
  return '<span class="avatar">' + inner +
    (online ? '<span class="dot on"></span>' : '') + '</span>';
}
// The server's conversation participants do not carry a picture yet, so
// for a live chat we fetch the other person's public profile once and
// remember it. Demo mode never calls this (there is no server).
const picCache = {}; // userId -> profilePicture url ('' when they have none)
function picFor(user) {
  if (!user) return null;
  const id = user.userId || user.id;
  return (id && picCache[id]) || null;
}
async function ensurePic(user) {
  if (!user || store.demo) return false;
  const id = user.userId || user.id;
  if (!id || user.profilePicture || picCache[id] !== undefined) return false;
  picCache[id] = '';
  try {
    const pub = await api('/users/' + id);
    picCache[id] = pub.profilePicture || '';
    if (currentView === 'chats') renderChats();
    return true;
  } catch (e) { /* picture stays initials */ }
  return false;
}

// ---------- hear it, stickers, media ----------
// Hear it: the phone reads a message aloud in the language it was written
// in — the point of a language app is hearing how it sounds, not just
// reading it. The phone's own voices do the talking (speechSynthesis),
// so nothing new is needed on the server. Tap again to stop.
const SPEECH_LANG = {
  en: 'en-US', fr: 'fr-FR', es: 'es-ES', pt: 'pt-PT', de: 'de-DE', it: 'it-IT',
  nl: 'nl-NL', ru: 'ru-RU', ar: 'ar-SA', tr: 'tr-TR', hi: 'hi-IN', zh: 'zh-CN',
  ja: 'ja-JP', ko: 'ko-KR', sw: 'sw-KE', yo: 'yo-NG', ig: 'ig-NG', ha: 'ha-NG',
};
let speakingId = null;
function speak(text, lang, id) {
  if (!('speechSynthesis' in window)) { alert('This phone cannot read messages aloud.'); return; }
  if (speakingId === id) { speechSynthesis.cancel(); speakingId = null; renderMessages(); return; }
  speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text);
  const tag = SPEECH_LANG[lang] || lang || 'en-US';
  u.lang = tag;
  const voice = speechSynthesis.getVoices()
    .find((v) => v.lang && v.lang.toLowerCase().startsWith(tag.slice(0, 2).toLowerCase()));
  if (voice) u.voice = voice;
  u.onend = u.onerror = () => { if (speakingId === id) { speakingId = null; renderMessages(); } };
  speakingId = id;
  speechSynthesis.speak(u);
  renderMessages();
}

// The seven animal stickers — the same pictures as the partners' avatars,
// drawn big in the chat. stickerId is the bot key, so a sticker from
// Kim Tae is the bunny and one from Sarima is the lion cub.
const STICKERS = [
  { id: 'chinese', label: 'Mr Chu', src: 'avatars/chinese.svg' },
  { id: 'french', label: 'Monsieur Laurent', src: 'avatars/french.svg' },
  { id: 'korean', label: 'Kim Tae', src: 'avatars/korean.svg' },
  { id: 'english', label: 'Sarima', src: 'avatars/english.svg' },
  { id: 'spanish', label: 'Lucía', src: 'avatars/spanish.svg' },
  { id: 'japanese', label: 'Yuki', src: 'avatars/japanese.svg' },
  { id: 'german', label: 'Lukas', src: 'avatars/german.svg' },
];
function stickerSrc(id) {
  const s = STICKERS.find((x) => x.id === id);
  return s ? s.src : null;
}

// What the chat list shows for the last message — a voice note, a photo
// and a sticker are not text, so they get a label instead of a blank line.
function previewText(m) {
  if (!m) return 'No messages yet';
  if (m.stickerId) return 'Sticker';
  if (m.audioData) return '🎙️ Voice message';
  if (m.imageData) return '📷 Photo';
  return m.translatedText || freeTranslations[m.id] || m.originalText;
}

// Read receipts: my message is read when every other person in the chat
// has opened it since it was sent. The server works this out from each
// person's last_read_at and sends it as readBy on every message.
function isReadByAllOthers(m, conv) {
  if (!m || !conv || !m.readBy) return false;
  const others = (conv.participants || []).filter((p) => p.userId !== (state.me && state.me.id));
  return others.length > 0 && others.every((p) => m.readBy.includes(p.userId));
}

// A photo from the camera roll can be several MB — shrink it in the
// browser first (longest side 1200px, JPEG) so the upload stays small and
// the server's base64 column never has to hold a huge picture.
function fileToJpeg(file, maxSide) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      const url = canvas.toDataURL('image/jpeg', 0.8);
      URL.revokeObjectURL(img.src);
      resolve(url.split(',')[1]); // raw base64, no data: prefix — the server stores it bare
    };
    img.onerror = () => reject(new Error('Could not read that photo.'));
    img.src = URL.createObjectURL(file);
  });
}
function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(',')[1]);
    r.onerror = () => reject(new Error('Could not read the recording.'));
    r.readAsDataURL(blob);
  });
}

// Translate any short text in the browser (same free MyMemory service the
// chat uses) — story captions are translated this way too, so they do not
// need the server's translator.
async function translateText(text, from, to) {
  if (!text || !from || !to || from === to) return null;
  try {
    const res = await fetch('https://api.mymemory.translated.net/get?q=' +
      encodeURIComponent(text) + '&langpair=' + encodeURIComponent(from + '|' + to));
    const data = await res.json();
    if (data.responseStatus !== 200) return null;
    return data.responseData && data.responseData.translatedText;
  } catch (e) { return null; }
}

// ---------- themes ----------
// Designed themes, not flat colour swaps — the way the KakaoTalk and LINE
// themes Angelic sent look: a patterned chat background, a coloured header
// bar, and coloured bubbles. Each theme owns its room: chatBg is the room
// background, pattern is a repeating motif drawn over it, headBg/headText
// colour the header bar, mineBg/mineText colour my bubbles, theirsBg the
// other person's. The choice is saved on this phone (localStorage) so it
// survives closing and reopening the app, and it is applied on every start.
const PATTERNS = {
  flowers: 'url("data:image/svg+xml,%3Csvg%20xmlns%3D%27http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%27%20width%3D%27150%27%20height%3D%27150%27%3E%3Cg%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.55%27%3E%3Ccircle%20cx%3D%2738.0%27%20cy%3D%2731.9%27%20r%3D%275.5%27%2F%3E%3Ccircle%20cx%3D%2745.7%27%20cy%3D%2737.5%27%20r%3D%275.5%27%2F%3E%3Ccircle%20cx%3D%2742.7%27%20cy%3D%2746.5%27%20r%3D%275.5%27%2F%3E%3Ccircle%20cx%3D%2733.3%27%20cy%3D%2746.5%27%20r%3D%275.5%27%2F%3E%3Ccircle%20cx%3D%2730.3%27%20cy%3D%2737.5%27%20r%3D%275.5%27%2F%3E%3Ccircle%20cx%3D%2738%27%20cy%3D%2740%27%20r%3D%274.2%27%20fill%3D%27%23FFD166%27%20fill-opacity%3D%270.55%27%2F%3E%3C%2Fg%3E%3Cg%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.45%27%3E%3Ccircle%20cx%3D%27112.0%27%20cy%3D%2798.1%27%20r%3D%276.7%27%2F%3E%3Ccircle%20cx%3D%27121.4%27%20cy%3D%27104.9%27%20r%3D%276.7%27%2F%3E%3Ccircle%20cx%3D%27117.8%27%20cy%3D%27116.0%27%20r%3D%276.7%27%2F%3E%3Ccircle%20cx%3D%27106.2%27%20cy%3D%27116.0%27%20r%3D%276.7%27%2F%3E%3Ccircle%20cx%3D%27102.6%27%20cy%3D%27104.9%27%20r%3D%276.7%27%2F%3E%3Ccircle%20cx%3D%27112%27%20cy%3D%27108%27%20r%3D%275.1%27%20fill%3D%27%23FFD166%27%20fill-opacity%3D%270.45%27%2F%3E%3C%2Fg%3E%3Ccircle%20cx%3D%27120%27%20cy%3D%2722%27%20r%3D%272.5%27%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.6%27%2F%3E%3Ccircle%20cx%3D%2720%27%20cy%3D%27122%27%20r%3D%272.5%27%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.6%27%2F%3E%3C%2Fsvg%3E")',
  paws: 'url("data:image/svg+xml,%3Csvg%20xmlns%3D%27http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%27%20width%3D%27160%27%20height%3D%27160%27%3E%3Cg%20transform%3D%27translate%2842%2044%29%20rotate%28-18%29%20scale%281%29%27%20fill%3D%27%233A2A33%27%20fill-opacity%3D%270.13%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%276%27%20rx%3D%2710%27%20ry%3D%277.5%27%2F%3E%3Ccircle%20cx%3D%27-11%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%27-4%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%274%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%2711%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3C%2Fg%3E%3Cg%20transform%3D%27translate%28122%20118%29%20rotate%2822%29%20scale%281.15%29%27%20fill%3D%27%233A2A33%27%20fill-opacity%3D%270.1%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%276%27%20rx%3D%2710%27%20ry%3D%277.5%27%2F%3E%3Ccircle%20cx%3D%27-11%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%27-4%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%274%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%2711%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3C%2Fg%3E%3Cg%20transform%3D%27translate%28128%2030%29%20rotate%2810%29%20scale%280.7%29%27%20fill%3D%27%233A2A33%27%20fill-opacity%3D%270.1%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%276%27%20rx%3D%2710%27%20ry%3D%277.5%27%2F%3E%3Ccircle%20cx%3D%27-11%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%27-4%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%274%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%2711%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3C%2Fg%3E%3Cg%20transform%3D%27translate%2830%20130%29%20rotate%28-25%29%20scale%280.7%29%27%20fill%3D%27%233A2A33%27%20fill-opacity%3D%270.12%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%276%27%20rx%3D%2710%27%20ry%3D%277.5%27%2F%3E%3Ccircle%20cx%3D%27-11%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%27-4%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%274%27%20cy%3D%27-8%27%20r%3D%273.6%27%2F%3E%3Ccircle%20cx%3D%2711%27%20cy%3D%27-4%27%20r%3D%273.6%27%2F%3E%3C%2Fg%3E%3C%2Fsvg%3E")',
  leaves: 'url("data:image/svg+xml,%3Csvg%20xmlns%3D%27http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%27%20width%3D%27140%27%20height%3D%27140%27%3E%3Cg%20transform%3D%27translate%2836%2038%29%20rotate%28-30%29%20scale%281%29%27%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.38%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%270%27%20rx%3D%2714%27%20ry%3D%276%27%2F%3E%3C%2Fg%3E%3Cg%20transform%3D%27translate%28104%20102%29%20rotate%2824%29%20scale%281.2%29%27%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.3%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%270%27%20rx%3D%2714%27%20ry%3D%276%27%2F%3E%3C%2Fg%3E%3Cg%20transform%3D%27translate%28112%2026%29%20rotate%2855%29%20scale%280.7%29%27%20fill%3D%27%232E7D32%27%20fill-opacity%3D%270.18%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%270%27%20rx%3D%2714%27%20ry%3D%276%27%2F%3E%3C%2Fg%3E%3Cg%20transform%3D%27translate%2824%20116%29%20rotate%28-50%29%20scale%280.7%29%27%20fill%3D%27%232E7D32%27%20fill-opacity%3D%270.15%27%3E%3Cellipse%20cx%3D%270%27%20cy%3D%270%27%20rx%3D%2714%27%20ry%3D%276%27%2F%3E%3C%2Fg%3E%3Ccircle%20cx%3D%2770%27%20cy%3D%2770%27%20r%3D%272.5%27%20fill%3D%27%23ffffff%27%20fill-opacity%3D%270.5%27%2F%3E%3C%2Fsvg%3E")',
};
const THEMES = {
  apeach: { label: 'Apeach (Kakao Friends)', accent: '#FF8FAB', bg: '#FFF0F5', surface: '#FFFFFF',
            soft: '#FFE3EC', ink: '#4A2C3A', muted: '#A67C8C', mineText: '#FFFFFF',
            theirsBorder: '#F3D3DE', line: '#F3D3DE', tabBg: 'rgba(255,240,245,.97)', demoBg: '#FFE3EC',
            chatBg: '#FFD9E8', headBg: '#FF8FAB', headText: '#FFFFFF', mineBg: '#FF7AA2',
            theirsBg: '#FFFFFF', pattern: PATTERNS.flowers, dividerBg: 'rgba(0,0,0,.12)', dividerText: '#FFFFFF' },
  blackcat: { label: 'Black Cat (Kakao)', accent: '#2E2E2E', bg: '#FFF0F5', surface: '#FFFFFF',
            soft: '#FFE3EC', ink: '#2E2E2E', muted: '#9A7C88', mineText: '#FFFFFF',
            theirsBorder: '#EFD3DE', line: '#EFD3DE', tabBg: 'rgba(255,240,245,.97)', demoBg: '#FFE3EC',
            chatBg: '#FFC9DB', headBg: '#FFC9DB', headText: '#2E2E2E', mineBg: '#2E2E2E',
            theirsBg: '#FFFFFF', pattern: PATTERNS.paws, dividerBg: 'rgba(0,0,0,.55)', dividerText: '#FFFFFF' },
  forest: { label: 'Forest Friends (LINE)', accent: '#06C755', bg: '#F4FBF2', surface: '#FFFFFF',
            soft: '#E3F2DF', ink: '#1F3D1F', muted: '#7A9A7A', mineText: '#1F3D1F',
            theirsBorder: '#D3E8CF', line: '#D3E8CF', tabBg: 'rgba(244,251,242,.97)', demoBg: '#E3F2DF',
            chatBg: '#C9E8C0', headBg: '#FFFFFF', headText: '#1F3D1F', mineBg: '#A5D6A7',
            theirsBg: '#FFFFFF', pattern: PATTERNS.leaves, dividerBg: 'rgba(0,0,0,.12)', dividerText: '#FFFFFF' },
  kakao: { label: 'KakaoTalk Classic', accent: '#FEE500', bg: '#FFFFFF', surface: '#FFFFFF',
           soft: '#F7F7F7', ink: '#191919', muted: '#8A8A8A', mineText: '#191919',
           theirsBorder: '#E5E5E5', line: '#E5E5E5', tabBg: 'rgba(255,255,255,.97)', demoBg: '#FFFBE5',
           chatBg: '#FFFFFF', headBg: '#FEE500', headText: '#191919', mineBg: '#FEE500',
           theirsBg: '#FFFFFF', pattern: 'none', dividerBg: 'rgba(0,0,0,.12)', dividerText: '#FFFFFF' },
  line:  { label: 'LINE Classic', accent: '#06C755', bg: '#FFFFFF', surface: '#FFFFFF',
           soft: '#F7F7F7', ink: '#191919', muted: '#8A8A8A', mineText: '#1F3D1F',
           theirsBorder: '#D3E8CF', line: '#D3E8CF', tabBg: 'rgba(255,255,255,.97)', demoBg: '#EAFBF0',
           chatBg: '#A9C6E8', headBg: '#FFFFFF', headText: '#191919', mineBg: '#A5D6A7',
           theirsBg: '#FFFFFF', pattern: 'none', dividerBg: 'rgba(0,0,0,.12)', dividerText: '#FFFFFF' },
  plum:  { label: 'Plum (dark)', accent: '#8B6CFF', bg: '#150E24', surface: '#241A3D',
           soft: '#1C1430', ink: '#EEE8FF', muted: '#A99DCB', mineText: '#FFFFFF',
           theirsBorder: '#5E4F86', line: '#5E4F86', tabBg: 'rgba(21,14,36,.97)', demoBg: '#2F234F',
           chatBg: '#150E24', headBg: '#241A3D', headText: '#EEE8FF', mineBg: '#8B6CFF',
           theirsBg: '#241A3D', pattern: 'none', dividerBg: 'rgba(255,255,255,.15)', dividerText: '#EEE8FF' },
};
function currentTheme() {
  const saved = localStorage.getItem('mv_theme');
  return THEMES[saved] ? saved : 'plum';
}
function applyTheme(name) {
  const t = THEMES[name] || THEMES.plum;
  const r = document.documentElement.style;
  r.setProperty('--accent', t.accent);
  r.setProperty('--plum-950', t.bg);
  r.setProperty('--plum-900', t.soft);
  r.setProperty('--plum-800', t.surface);
  r.setProperty('--plum-700', t.soft);
  r.setProperty('--lavender', t.ink);
  r.setProperty('--lavender-muted', t.muted);
  r.setProperty('--violet-300', t.ink);
  r.setProperty('--violet-400', t.accent);
  r.setProperty('--violet-500', t.accent);
  r.setProperty('--orchid', t.accent);
  r.setProperty('--outline', t.line);
  r.setProperty('--mine-text', t.mineText);
  r.setProperty('--theirs-border', t.theirsBorder);
  r.setProperty('--tab-bg', t.tabBg);
  r.setProperty('--demo-bg', t.demoBg);
  r.setProperty('--chat-bg', t.chatBg);
  r.setProperty('--head-bg', t.headBg);
  r.setProperty('--head-text', t.headText);
  r.setProperty('--mine-bg', t.mineBg);
  r.setProperty('--theirs-bg', t.theirsBg);
  r.setProperty('--pattern', t.pattern);
  r.setProperty('--divider-bg', t.dividerBg);
  r.setProperty('--divider-text', t.dividerText);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', t.headBg);
}
function timeAgo(iso) {
  if (!iso) return '';
  const d = new Date(iso), now = Date.now(), diff = now - d.getTime();
  if (isNaN(d.getTime()) || diff < 0) return '';
  if (diff < 60000) return 'now';
  if (diff < 3600000) return Math.floor(diff / 60000) + 'm';
  if (diff < 86400000) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  return d.toLocaleDateString([], { day: 'numeric', month: 'short' });
}
function msgTime(iso) {
  const d = new Date(iso);
  return isNaN(d.getTime()) ? '' : d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// ---------- saved state (localStorage) ----------
// Server URL is EMPTY by default on purpose: the Android default
// http://10.0.2.2:8080 only works from an Android emulator, never from a
// real iPhone. The user types their own server address on first run.
const store = {
  get serverUrl() { return (localStorage.getItem('mv_server_url') || '').trim(); },
  set serverUrl(v) { localStorage.setItem('mv_server_url', (v || '').trim()); },
  get token() { return localStorage.getItem('mv_token') || ''; },
  set token(v) { v ? localStorage.setItem('mv_token', v) : localStorage.removeItem('mv_token'); },
  get demo() { return localStorage.getItem('mv_demo') === '1'; },
  set demo(v) { v ? localStorage.setItem('mv_demo', '1') : localStorage.removeItem('mv_demo'); },
};

const state = {
  me: null,           // UserProfile once signed in
  languages: null,    // [{code, name}] from GET /languages, or the built-in list
  conversations: [],
  contacts: [],
  requests: [],
  notifications: [],
  currentChat: null,  // conversation id open right now
  currentChatTitle: '',
  messages: [],
  typingUsers: {},    // conversationId -> sender name currently typing
  wsConnected: false,
};

// Same language list the server exposes at GET /languages (and the Android
// app keeps locally in data/Languages.kt), used as a fallback offline.
const FALLBACK_LANGUAGES = [
  ['en', 'English'], ['fr', 'French'], ['es', 'Spanish'], ['pt', 'Portuguese'],
  ['de', 'German'], ['it', 'Italian'], ['nl', 'Dutch'], ['ru', 'Russian'],
  ['ar', 'Arabic'], ['tr', 'Turkish'], ['hi', 'Hindi'], ['zh', 'Chinese'],
  ['ja', 'Japanese'], ['ko', 'Korean'], ['sw', 'Swahili'], ['yo', 'Yoruba'],
  ['ig', 'Igbo'], ['ha', 'Hausa'],
].map(([code, name]) => ({ code, name }));
function langName(code) {
  const hit = (state.languages || FALLBACK_LANGUAGES).find((l) => l.code === code);
  return hit ? hit.name : (code || '').toUpperCase();
}

// ---------- free translation (live mode, no Google key) ----------
// The server only translates when GOOGLE_TRANSLATE_API_KEY is set, and
// there is no key on the live server — so a live message in Chinese would
// otherwise reach the reader untranslated. Until that key exists, the app
// translates in the browser with MyMemory's free service (no sign-up,
// short chat messages only). A message the server already translated is
// never re-translated, and a failed lookup just shows the original.
const freeTranslations = {}; // message id -> translated text
async function translateFree(msg) {
  if (!msg || msg.translatedText || !msg.originalLanguage) return;
  const mine = state.me && state.me.preferredLanguage;
  if (!mine || msg.originalLanguage === mine) return;
  if (freeTranslations[msg.id] !== undefined) return; // done or in flight
  freeTranslations[msg.id] = ''; // mark in flight so we do not fetch twice
  try {
    const url = 'https://api.mymemory.translated.net/get?q=' +
      encodeURIComponent(msg.originalText) + '&langpair=' +
      encodeURIComponent(msg.originalLanguage + '|' + mine);
    const res = await fetch(url);
    const data = await res.json();
    // Only a real 200 answer counts: on a quota error MyMemory still
    // returns text, but it is a "you used all free translations" warning,
    // not a translation — never show that as the message.
    if (data.responseStatus !== 200) { delete freeTranslations[msg.id]; return; }
    const text = data && data.responseData && data.responseData.translatedText;
    if (text && text !== msg.originalText) {
      freeTranslations[msg.id] = text;
      if (state.currentChat === msg.conversationId) renderMessages();
      else if (state.conversations) renderChats();
    } else { delete freeTranslations[msg.id]; }
  } catch (e) { delete freeTranslations[msg.id]; }
}

// The language a message is written in is the language of the words,
// not the language on the sender's profile. The app used to tag every
// message with my own preferred language, so a Chinese message from me
// arrived tagged "en" — the server saw source and target as the same
// language, stored no translation at all, and the other side only ever
// "understood" it because the test partner's brain translated it on the
// fly. Tagging by script catches the languages that do not use the
// Latin alphabet; Latin text keeps my profile language, which is the
// best guess the phone can make on its own.
function detectLanguage(text) {
  const mine = (state.me && state.me.preferredLanguage) || 'en';
  if (!text) return mine;
  const counts = { ko: 0, ja: 0, zh: 0, ru: 0, ar: 0, hi: 0 };
  for (const ch of text) {
    const cp = ch.codePointAt(0);
    if (cp >= 0xAC00 && cp <= 0xD7A3) counts.ko++; // Hangul
    else if ((cp >= 0x3040 && cp <= 0x309F) || (cp >= 0x30A0 && cp <= 0x30FF)) counts.ja++; // hiragana, katakana
    else if (cp >= 0x4E00 && cp <= 0x9FFF) counts.zh++; // Han ideographs
    else if (cp >= 0x0400 && cp <= 0x04FF) counts.ru++; // Cyrillic
    else if (cp >= 0x0600 && cp <= 0x06FF) counts.ar++; // Arabic
    else if (cp >= 0x0900 && cp <= 0x097F) counts.hi++; // Devanagari
  }
  // Most letters wins; on a tie the earlier script in this list wins,
  // so kana beats kanji and Japanese wins over Chinese for mixed text.
  let best = null, bestN = 0;
  for (const lang of ['ko', 'ja', 'zh', 'ru', 'ar', 'hi']) {
    if (counts[lang] > bestN) { best = lang; bestN = counts[lang]; }
  }
  return best || mine;
}

// ---------- demo data ----------
// Copied from the Android preview data (ui/preview/SampleData.kt) so the
// demo feels like the real app. Times are relative to "now" when loaded.
function minutesAgo(m) { return new Date(Date.now() - m * 60000).toISOString(); }
const DEMO = {
  me: { id: 'me', username: 'angelic', fullName: 'Angelic', email: 'angelic@example.com',
        bio: 'Lagos · coffee · languages', profilePicture: 'https://multivo-web.vercel.app/avatars/angelic.svg',
        preferredLanguage: 'en', isOnline: true },
  users: [
    { id: 'u1', username: 'amelie', fullName: 'Amélie Laurent', profilePicture: null, preferredLanguage: 'fr', isOnline: true, relationship: 'contact' },
    { id: 'u2', username: 'diego', fullName: 'Diego Ramírez', profilePicture: null, preferredLanguage: 'es', isOnline: false, relationship: 'contact' },
    { id: 'u3', username: 'yuki', fullName: 'Yuki Tanaka', profilePicture: null, preferredLanguage: 'ja', isOnline: true, relationship: 'contact' },
    { id: 'u4', username: 'tunde', fullName: 'Tunde Bakare', profilePicture: null, preferredLanguage: 'yo', isOnline: false, relationship: 'none' },
    { id: 'u5', username: 'lena', fullName: 'Lena Fischer', profilePicture: null, preferredLanguage: 'de', isOnline: false, relationship: 'request_sent' },
    { id: 'u6', username: 'kemi', fullName: 'Kemi Adeyemi', profilePicture: null, preferredLanguage: 'en', isOnline: true, relationship: 'request_received' },
  ],
  requests: [
    { id: 'r1', sender: { id: 'u6', username: 'kemi', fullName: 'Kemi Adeyemi', profilePicture: null, preferredLanguage: 'en', isOnline: true },
      receiver: { id: 'me', username: 'angelic', fullName: 'Angelic', profilePicture: null, preferredLanguage: 'en', isOnline: true },
      status: 'pending', createdAt: minutesAgo(30) },
  ],
  conversations: [
    { id: 'c1', title: 'Amélie Laurent', isGroup: false, createdBy: 'me', createdAt: minutesAgo(600), updatedAt: minutesAgo(2),
      participants: [
        { userId: 'me', username: 'angelic', fullName: 'Angelic', preferredLanguage: 'en', isOnline: true },
        { userId: 'u1', username: 'amelie', fullName: 'Amélie Laurent', preferredLanguage: 'fr', isOnline: true },
      ],
      lastMessage: { id: 'm4', conversationId: 'c1', senderId: 'me', senderName: 'Angelic', originalText: 'See you there 🙌', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(2) },
      unreadCount: 0 },
    { id: 'c2', title: 'Diego Ramírez', isGroup: false, createdBy: 'u2', createdAt: minutesAgo(3000), updatedAt: minutesAgo(90),
      participants: [
        { userId: 'me', username: 'angelic', fullName: 'Angelic', preferredLanguage: 'en', isOnline: true },
        { userId: 'u2', username: 'diego', fullName: 'Diego Ramírez', preferredLanguage: 'es', isOnline: false },
      ],
      lastMessage: { id: 'm9', conversationId: 'c2', senderId: 'u2', senderName: 'Diego Ramírez', originalText: '¿Mañana a las diez?', originalLanguage: 'es', translatedText: 'Tomorrow at ten?', targetLanguage: 'en', createdAt: minutesAgo(90) },
      unreadCount: 2 },
    { id: 'c3', title: 'Weekend trip', isGroup: true, createdBy: 'me', createdAt: minutesAgo(5000), updatedAt: minutesAgo(1500),
      participants: [
        { userId: 'me', username: 'angelic', fullName: 'Angelic', preferredLanguage: 'en', isOnline: true },
        { userId: 'u1', username: 'amelie', fullName: 'Amélie Laurent', preferredLanguage: 'fr', isOnline: true },
        { userId: 'u3', username: 'yuki', fullName: 'Yuki Tanaka', preferredLanguage: 'ja', isOnline: true },
      ],
      lastMessage: { id: 'm10', conversationId: 'c3', senderId: 'u3', senderName: 'Yuki Tanaka', originalText: '電車は9時です', originalLanguage: 'ja', translatedText: 'The train is at 9', targetLanguage: 'en', createdAt: minutesAgo(1500) },
      unreadCount: 5 },
  ],
  messages: {
    c1: [
      { id: 'm1', conversationId: 'c1', senderId: 'u1', senderName: 'Amélie Laurent', originalText: 'Salut Angelic ! Tu es libre ce soir ?', originalLanguage: 'fr', translatedText: 'Hi Angelic! Are you free tonight?', targetLanguage: 'en', createdAt: minutesAgo(42) },
      { id: 'm2', conversationId: 'c1', senderId: 'me', senderName: 'Angelic', originalText: 'Yes! Dinner at 8?', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(40) },
      { id: 'm3', conversationId: 'c1', senderId: 'u1', senderName: 'Amélie Laurent', originalText: 'Parfait, je réserve une table près du port.', originalLanguage: 'fr', translatedText: "Perfect, I'll book a table near the harbour.", targetLanguage: 'en', createdAt: minutesAgo(38) },
      { id: 'm4', conversationId: 'c1', senderId: 'me', senderName: 'Angelic', originalText: 'See you there 🙌', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(2) },
    ],
    c2: [
      { id: 'm8', conversationId: 'c2', senderId: 'me', senderName: 'Angelic', originalText: 'Hola Diego, long time!', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(100) },
      { id: 'm9', conversationId: 'c2', senderId: 'u2', senderName: 'Diego Ramírez', originalText: '¿Mañana a las diez?', originalLanguage: 'es', translatedText: 'Tomorrow at ten?', targetLanguage: 'en', createdAt: minutesAgo(90) },
    ],
    c3: [
      { id: 'm11', conversationId: 'c3', senderId: 'u1', senderName: 'Amélie Laurent', originalText: 'Je prends les billets de train.', originalLanguage: 'fr', translatedText: "I'll get the train tickets.", targetLanguage: 'en', createdAt: minutesAgo(1600) },
      { id: 'm10', conversationId: 'c3', senderId: 'u3', senderName: 'Yuki Tanaka', originalText: '電車は9時です', originalLanguage: 'ja', translatedText: 'The train is at 9', targetLanguage: 'en', createdAt: minutesAgo(1500) },
    ],
  },
  notifications: [
    { id: 'n1', title: 'New message', body: 'Amélie Laurent sent you a message', type: 'new_message', referenceId: 'c1', isRead: false, createdAt: minutesAgo(2) },
    { id: 'n2', title: 'Contact request', body: 'Kemi Adeyemi wants to connect with you', type: 'contact_request', referenceId: 'r1', isRead: false, createdAt: minutesAgo(30) },
    { id: 'n3', title: 'Group message', body: 'Yuki Tanaka messaged Weekend trip', type: 'new_message', referenceId: 'c3', isRead: true, createdAt: minutesAgo(1500) },
  ],
  // Moments in demo mode: a picture and a caption in each person's own
  // language, translated underneath for you. Gone after 24 hours, like live.
  stories: [
    { id: 's1', userId: 'u1', fullName: 'Amélie Laurent', profilePicture: null,
      caption: 'Le marché ce matin — les fleurs sont magnifiques !', imageData: 'avatars/french.svg',
      captionLanguage: 'fr', createdAt: minutesAgo(50), expiresAt: new Date(Date.now() + 23 * 3600 * 1000).toISOString() },
    { id: 's2', userId: 'u3', fullName: 'Yuki Tanaka', profilePicture: null,
      caption: '桜がきれいです！', imageData: 'avatars/japanese.svg',
      captionLanguage: 'ja', createdAt: minutesAgo(180), expiresAt: new Date(Date.now() + 21 * 3600 * 1000).toISOString() },
    { id: 's3', userId: 'u6', fullName: 'Kemi Adeyemi', profilePicture: null,
      caption: 'Lagos sunsets never miss.', imageData: null,
      captionLanguage: 'en', createdAt: minutesAgo(300), expiresAt: new Date(Date.now() + 19 * 3600 * 1000).toISOString() },
    { id: 's4', userId: 'u2', fullName: 'Diego Ramírez', profilePicture: null,
      caption: 'Hoy cocino paella para todos.', imageData: null,
      captionLanguage: 'es', createdAt: minutesAgo(700), expiresAt: new Date(Date.now() + 12 * 3600 * 1000).toISOString() },
  ],
};

// ---------- API layer ----------
// One function for every call. In demo mode it answers from DEMO above;
// live, it talks to the Ktor server with the saved JWT.
function baseUrl() { return store.serverUrl.replace(/\/+$/, ''); }

async function api(path, opts) {
  opts = opts || {};
  if (store.demo) return demoApi(path, opts);
  const res = await fetch(baseUrl() + path, {
    method: opts.method || 'GET',
    headers: Object.assign(
      { 'Content-Type': 'application/json' },
      store.token ? { Authorization: 'Bearer ' + store.token } : {}
    ),
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  // The server's errors come back as {"error": "..."} — show that text.
  if (!res.ok) {
    let msg = 'Server error (' + res.status + ').';
    try { const data = await res.json(); if (data && data.error) msg = data.error; } catch (e) { /* keep default */ }
    if (res.status === 401) { signOut(); }
    const err = new Error(msg); err.status = res.status; throw err;
  }
  if (res.status === 204) return null;
  return res.json();
}

// Demo answers, shaped exactly like the server's JSON so the screens
// above never know the difference. A tiny delay keeps it feeling real.
function demoApi(path, opts) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      try { resolve(demoHandle(path, opts)); }
      catch (e) { reject(e); }
    }, 150);
  });
}
function demoHandle(path, opts) {
  const method = opts.method || 'GET';
  const fail = (msg) => { throw new Error(msg); };
  if (path === '/health') return { status: 'ok', database: 'demo', translation: 'demo' };
  if (path === '/languages') return FALLBACK_LANGUAGES;
  if (path === '/auth/login' && method === 'POST') {
    if (!opts.body.identifier || !opts.body.password) fail('Enter your username or email and password.');
    return { message: 'Welcome back.', token: 'demo-token', user: DEMO.me };
  }
  if (path === '/auth/register' && method === 'POST') {
    const b = opts.body;
    if (!b.username || !b.fullName || !b.email || !b.password) fail('Fill in every field.');
    if (b.password.length < 8) fail('Password must contain at least 8 characters.');
    return { message: 'Account created.', token: 'demo-token',
      user: Object.assign({}, DEMO.me, { username: b.username, fullName: b.fullName, email: b.email, preferredLanguage: b.preferredLanguage || 'en' }) };
  }
  if (path === '/users/me' && method === 'GET') return DEMO.me;
  if (path === '/users/me' && method === 'PUT') {
    Object.assign(DEMO.me, opts.body);
    return DEMO.me;
  }
  if (path.startsWith('/users/search')) {
    const q = decodeURIComponent((path.split('q=')[1] || '').split('&')[0]).toLowerCase();
    if (q.length < 2) fail('Type at least 2 characters to search.');
    return DEMO.users.filter((u) => (u.fullName + ' ' + u.username).toLowerCase().includes(q));
  }
  if (path === '/contacts' && method === 'GET')
    return DEMO.users.filter((u) => u.relationship === 'contact');
  if (path === '/contacts/requests' && method === 'GET') return DEMO.requests;
  if (path === '/contacts/requests' && method === 'POST') {
    const u = DEMO.users.find((x) => x.id === opts.body.receiverId);
    if (!u) fail('User not found.');
    u.relationship = 'request_sent';
    return { id: 'r-demo-' + Date.now(), sender: DEMO.me, receiver: u, status: 'pending', createdAt: new Date().toISOString() };
  }
  if (/^\/contacts\/requests\/[^/]+\/(accept|reject)$/.test(path) && method === 'POST') {
    const id = path.split('/')[3], accept = path.endsWith('/accept');
    const req = DEMO.requests.find((r) => r.id === id);
    if (!req) fail('Request not found.');
    DEMO.requests = DEMO.requests.filter((r) => r.id !== id);
    const sender = DEMO.users.find((u) => u.id === req.sender.id);
    if (sender) sender.relationship = accept ? 'contact' : 'none';
    return Object.assign({}, req, { status: accept ? 'accepted' : 'rejected' });
  }
  if (path.startsWith('/contacts/') && method === 'DELETE') {
    const u = DEMO.users.find((x) => x.id === path.split('/')[2]);
    if (u) u.relationship = 'none';
    return { message: 'Contact removed.' };
  }
  if (path === '/conversations' && method === 'GET') return DEMO.conversations;
  if (path === '/conversations/private' && method === 'POST') {
    const u = DEMO.users.find((x) => x.id === opts.body.userId);
    if (!u) fail('User not found.');
    let conv = DEMO.conversations.find((c) => !c.isGroup && c.participants.some((p) => p.userId === u.id));
    if (!conv) {
      conv = { id: 'c-demo-' + Date.now(), title: u.fullName, isGroup: false, createdBy: 'me',
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
        participants: [
          { userId: 'me', username: DEMO.me.username, fullName: DEMO.me.fullName, preferredLanguage: DEMO.me.preferredLanguage, isOnline: true },
          { userId: u.id, username: u.username, fullName: u.fullName, preferredLanguage: u.preferredLanguage, isOnline: u.isOnline },
        ], lastMessage: null, unreadCount: 0 };
      DEMO.conversations.unshift(conv);
      DEMO.messages[conv.id] = [];
    }
    return conv;
  }
  if (path === '/conversations/groups' && method === 'POST') {
    const parts = (opts.body.participantIds || []).map((id) => DEMO.users.find((u) => u.id === id)).filter(Boolean);
    const conv = { id: 'c-demo-' + Date.now(), title: opts.body.title || 'New group', isGroup: true, createdBy: 'me',
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      participants: [{ userId: 'me', username: DEMO.me.username, fullName: DEMO.me.fullName, preferredLanguage: DEMO.me.preferredLanguage, isOnline: true }]
        .concat(parts.map((u) => ({ userId: u.id, username: u.username, fullName: u.fullName, preferredLanguage: u.preferredLanguage, isOnline: u.isOnline }))),
      lastMessage: null, unreadCount: 0 };
    DEMO.conversations.unshift(conv);
    DEMO.messages[conv.id] = [];
    return conv;
  }
  const msgMatch = path.match(/^\/conversations\/([^/]+)\/messages(?:\?limit=(\d+))?$/);
  if (msgMatch && method === 'GET') return (DEMO.messages[msgMatch[1]] || []).slice(-(parseInt(msgMatch[2] || '100', 10)));
  if (msgMatch && method === 'POST') {
    const conv = DEMO.conversations.find((c) => c.id === msgMatch[1]);
    if (!conv) fail('Conversation not found.');
    const m = { id: 'm-demo-' + Date.now(), conversationId: conv.id, senderId: 'me', senderName: DEMO.me.fullName,
      originalText: opts.body.text || '', originalLanguage: opts.body.originalLanguage || DEMO.me.preferredLanguage,
      translatedText: null, targetLanguage: null, createdAt: new Date().toISOString(),
      audioData: opts.body.audioData || null, imageData: opts.body.imageData || null,
      stickerId: opts.body.stickerId || null, readBy: [] };
    (DEMO.messages[conv.id] = DEMO.messages[conv.id] || []).push(m);
    conv.lastMessage = m; conv.updatedAt = m.createdAt;
    return m;
  }
  if (path === '/stories/feed' && method === 'GET') return DEMO.stories.slice();
  if (path === '/stories' && method === 'POST') {
    const s = { id: 's-demo-' + Date.now(), userId: 'me', fullName: DEMO.me.fullName,
      profilePicture: DEMO.me.profilePicture || null, caption: opts.body.caption || null,
      imageData: opts.body.imageData || null, captionLanguage: opts.body.captionLanguage || DEMO.me.preferredLanguage,
      createdAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 24 * 3600 * 1000).toISOString() };
    DEMO.stories.unshift(s);
    return s;
  }
  if (path.startsWith('/stories/') && method === 'DELETE') {
    DEMO.stories = DEMO.stories.filter((s) => s.id !== path.split('/')[2]);
    return { message: 'Story deleted.' };
  }
  if (/^\/conversations\/[^/]+\/read$/.test(path) && method === 'POST') {
    const conv = DEMO.conversations.find((c) => c.id === path.split('/')[2]);
    if (conv) conv.unreadCount = 0;
    return { message: 'Marked as read.' };
  }
  if (path === '/notifications' && method === 'GET') return DEMO.notifications;
  if (path === '/notifications/read-all' && method === 'POST') {
    DEMO.notifications.forEach((n) => { n.isRead = true; });
    return { message: 'All notifications marked as read.' };
  }
  fail('Demo does not cover this yet: ' + path);
}

// ---------- demo replies ----------
// In demo mode there is no server and nobody on the other end — so when
// you send a message, the other person answers anyway, in their own
// language with the English translation underneath. That is the whole
// point of Multivo, and a preview that only shows old messages cannot
// demonstrate it. Replies cycle, one per conversation at a time.
const DEMO_REPLIES = {
  u1: [ // Amélie Laurent, French
    { originalText: 'Oui, avec plaisir !', originalLanguage: 'fr', translatedText: 'Yes, with pleasure!', targetLanguage: 'en' },
    { originalText: 'Parfait, on fait comme ça.', originalLanguage: 'fr', translatedText: "Perfect, let's do it that way.", targetLanguage: 'en' },
    { originalText: 'Tu es libre demain ?', originalLanguage: 'fr', translatedText: 'Are you free tomorrow?', targetLanguage: 'en' },
  ],
  u2: [ // Diego Ramírez, Spanish
    { originalText: '¡Claro que sí!', originalLanguage: 'es', translatedText: 'Of course!', targetLanguage: 'en' },
    { originalText: 'Me parece perfecto.', originalLanguage: 'es', translatedText: 'That sounds perfect to me.', targetLanguage: 'en' },
    { originalText: '¿Nos vemos mañana?', originalLanguage: 'es', translatedText: 'Shall we meet tomorrow?', targetLanguage: 'en' },
  ],
  u3: [ // Yuki Tanaka, Japanese
    { originalText: 'いいですね！', originalLanguage: 'ja', translatedText: 'Sounds good!', targetLanguage: 'en' },
    { originalText: '明日は大丈夫です。', originalLanguage: 'ja', translatedText: 'Tomorrow works for me.', targetLanguage: 'en' },
    { originalText: '楽しみにしています！', originalLanguage: 'ja', translatedText: "I'm looking forward to it!", targetLanguage: 'en' },
  ],
  u4: [ // Tunde Bakare, Yoruba
    { originalText: 'Ó dára!', originalLanguage: 'yo', translatedText: "That's good!", targetLanguage: 'en' },
    { originalText: 'Ẹ ṣeun!', originalLanguage: 'yo', translatedText: 'Thank you!', targetLanguage: 'en' },
  ],
  u5: [ // Lena Fischer, German
    { originalText: 'Klingt gut!', originalLanguage: 'de', translatedText: 'Sounds good!', targetLanguage: 'en' },
    { originalText: 'Bis morgen!', originalLanguage: 'de', translatedText: 'See you tomorrow!', targetLanguage: 'en' },
  ],
  u6: [ // Kemi Adeyemi, English — same language, so no translation
    { originalText: 'Sounds good to me!', originalLanguage: 'en', translatedText: null, targetLanguage: null },
    { originalText: "Let's do it!", originalLanguage: 'en', translatedText: null, targetLanguage: null },
  ],
};
const demoReplyIndex = {};   // conversationId -> next reply to use
const demoReplyPending = {}; // conversationId -> a reply is already on its way
function scheduleDemoReply(conversationId) {
  if (demoReplyPending[conversationId]) return;
  const conv = state.conversations.find((c) => c.id === conversationId);
  if (!conv) return;
  // Prefer someone online; a group answers through its first online member.
  const other = conv.participants.find((p) => p.userId !== (state.me && state.me.id) && p.isOnline)
    || conv.participants.find((p) => p.userId !== (state.me && state.me.id));
  if (!other) return;
  const replies = DEMO_REPLIES[other.userId] || DEMO_REPLIES.u6;
  const idx = demoReplyIndex[conversationId] || 0;
  const reply = replies[idx % replies.length];
  demoReplyIndex[conversationId] = idx + 1;
  demoReplyPending[conversationId] = true;
  // Typing… then the reply, delivered through the same path a live
  // message takes, so badges, previews and the chat view all update.
  setTimeout(() => {
    if (state.currentChat === conversationId) {
      state.typingUsers[conversationId] = other.fullName;
      renderTyping();
    }
  }, 1200);
  setTimeout(() => {
    delete demoReplyPending[conversationId];
    delete state.typingUsers[conversationId];
    if (state.currentChat === conversationId) renderTyping();
    // The other person read what I sent — that is what turns my single
    // tick into two. Live, the server works this out; in demo, say so here.
    (DEMO.messages[conversationId] || []).forEach((x) => {
      if (x.senderId === 'me') x.readBy = [other.userId];
    });
    if (state.currentChat === conversationId) renderMessages();
    // Sometimes the answer is a sticker, not words — the animal that
    // matches the person answering.
    const stickerFor = { u1: 'french', u2: 'spanish', u3: 'japanese', u4: 'english', u5: 'german', u6: 'english' };
    const useSticker = Math.random() < 0.25 && stickerFor[other.userId];
    const m = { id: 'm-demo-' + Date.now(), conversationId, senderId: other.userId, senderName: other.fullName,
      originalText: useSticker ? '' : reply.originalText, originalLanguage: reply.originalLanguage,
      translatedText: useSticker ? null : reply.translatedText, targetLanguage: reply.targetLanguage,
      createdAt: new Date().toISOString(), audioData: null, imageData: null,
      stickerId: useSticker ? stickerFor[other.userId] : null, readBy: [] };
    (DEMO.messages[conversationId] = DEMO.messages[conversationId] || []).push(m);
    handleSocketEvent({ type: 'new_message', message: m });
  }, 2800);
}

// ---------- live data + WebSocket ----------
async function loadLanguages() {
  try { state.languages = await api('/languages'); }
  catch (e) { state.languages = FALLBACK_LANGUAGES; } // offline? use the built-in list
}
async function loadMe() { state.me = await api('/users/me'); }
async function refreshBadges() {
  try {
    state.conversations = await api('/conversations');
    state.notifications = await api('/notifications');
  } catch (e) { /* badges just stay as they are if the server drops */ }
  const unreadChats = state.conversations.reduce((n, c) => n + (c.unreadCount || 0), 0);
  const unreadNotifs = state.notifications.filter((n) => !n.isRead).length;
  setBadge('#badge-chats', unreadChats);
  setBadge('#badge-notifs', unreadNotifs);
}
function setBadge(sel, n) {
  const el = $(sel);
  if (!el) return;
  el.hidden = !n; el.textContent = n > 99 ? '99+' : String(n);
}

let ws = null, wsTimer = null, wsBackoff = 1000;
function wsUrl() {
  const base = baseUrl().replace(/^https:\/\//, 'wss://').replace(/^http:\/\//, 'ws://');
  return base + '/ws?token=' + encodeURIComponent(store.token);
}
function connectWS() {
  if (store.demo || !store.token || !store.serverUrl) return; // demo has no server to listen to
  disconnectWS();
  try {
    ws = new WebSocket(wsUrl());
  } catch (e) { scheduleReconnect(); return; }
  ws.onopen = () => { state.wsConnected = true; wsBackoff = 1000; };
  ws.onmessage = (ev) => {
    let data; try { data = JSON.parse(ev.data); } catch (e) { return; }
    handleSocketEvent(data);
  };
  ws.onclose = () => { state.wsConnected = false; scheduleReconnect(); };
  ws.onerror = () => { try { ws.close(); } catch (e) { /* reconnect handles it */ } };
}
function scheduleReconnect() {
  clearTimeout(wsTimer);
  wsTimer = setTimeout(connectWS, wsBackoff);
  wsBackoff = Math.min(wsBackoff * 2, 30000); // back off, at most 30s, like the Android client
}
function disconnectWS() {
  clearTimeout(wsTimer);
  if (ws) { try { ws.close(); } catch (e) { /* already closed */ } ws = null; }
  state.wsConnected = false;
}
function sendTyping(conversationId, typing) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return;
  ws.send(JSON.stringify({ type: typing ? 'typing_start' : 'typing_stop', conversationId }));
}

// One copy of each message, however it arrives. The server sends my own
// message back over the live connection as well as in the reply to my
// send — without this check it showed twice until a refresh re-fetched
// the list from the server.
function addMessage(m) {
  if (!m || !m.id) return;
  if (state.messages.some((x) => x.id === m.id)) return;
  state.messages.push(m);
}

// A live event arrives: a message, someone typing, someone coming online.
function handleSocketEvent(ev) {
  if (!ev || !ev.type) return;
  if (ev.type === 'new_message' && ev.message) {
    const m = ev.message;
    const conv = state.conversations.find((c) => c.id === m.conversationId);
    if (conv) { conv.lastMessage = m; conv.updatedAt = m.createdAt; if (m.senderId !== state.me.id && state.currentChat !== m.conversationId) conv.unreadCount = (conv.unreadCount || 0) + 1; }
    if (state.currentChat === m.conversationId) {
      // They have just sent a message, so they have read everything
      // before it: sending updates their read time past all my earlier
      // messages. Flip my ticks to double now, not on the next refresh —
      // the server already records the read, my copy of my own message
      // is just never refreshed when their reply arrives.
      if (m.senderId !== state.me.id) {
        state.messages.forEach((x) => {
          if (x.senderId === state.me.id) {
            x.readBy = x.readBy || [];
            if (!x.readBy.includes(m.senderId)) x.readBy.push(m.senderId);
          }
        });
      }
      addMessage(m);
      renderMessages();
      api('/conversations/' + m.conversationId + '/read', { method: 'POST' }).catch(() => {});
    }
    refreshBadges();
    if (currentView === 'chats') renderChats();
  } else if ((ev.type === 'typing_start' || ev.type === 'typing_stop') && ev.conversationId) {
    // The server relays typing with the sender's user id; show their name.
    const name = (state.conversations.find((c) => c.id === ev.conversationId) || { participants: [] })
      .participants.find((p) => p.userId === ev.userId);
    if (ev.type === 'typing_start' && name) state.typingUsers[ev.conversationId] = name.fullName;
    else delete state.typingUsers[ev.conversationId];
    if (state.currentChat === ev.conversationId) renderTyping();
  } else if (ev.type === 'user_online' || ev.type === 'user_offline') {
    state.conversations.forEach((c) => c.participants.forEach((p) => {
      if (p.userId === ev.userId) p.isOnline = ev.type === 'user_online';
    }));
    if (currentView === 'chats') renderChats();
    if (currentView === 'chat') renderChatHeader();
  } else if (ev.type === 'notification' && ev.notification) {
    state.notifications.unshift(ev.notification);
    refreshBadges();
    if (currentView === 'notifications') renderNotifications();
  }
}

function signOut() {
  store.token = ''; store.demo = false; state.me = null; state.currentChat = null;
  disconnectWS();
  showServer();
}

// ---------- screens ----------
let currentView = 'server';
function showView(name, withTabs) {
  currentView = name;
  main.classList.toggle('no-tabs', !withTabs);
  tabsEl.classList.toggle('hidden', !withTabs);
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === name));
}
function demoBanner() {
  return store.demo
    ? '<div class="demo-banner">Demo — not connected to a server<small>Sample data only. Nothing is sent anywhere. Switch to a live server from Profile → Change server.</small></div>'
    : '';
}
function showError(id, msg) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = msg || '';
  el.classList.toggle('show', !!msg);
}

// 1 — Server settings. This is the first screen: the iPhone needs to know
// where the Ktor server lives, and the Android emulator address will not work.
function showServer() {
  showView('server', false);
  main.innerHTML =
    '<div class="brand"><div class="logo">M</div><h1>Multivo</h1>' +
    '<p>Chat in your language, read in theirs.</p></div>' +
    '<div class="card"><h2>Where is your server?</h2>' +
    '<p style="margin:6px 0 0;color:var(--lavender-muted);font-size:14px">Multivo needs its Ktor server running. ' +
    'On your iPhone that means the server on your laptop, on the same Wi-Fi — for example ' +
    '<code>http://192.168.1.5:8080</code> using your laptop’s own IP address. ' +
    'The Android emulator address <code>10.0.2.2</code> does not work from a real phone.</p>' +
    '<label for="server-url">Server URL</label>' +
    '<input id="server-url" type="url" inputmode="url" placeholder="http://192.168.1.5:8080" value="' + esc(store.serverUrl) + '">' +
    '<button class="btn" id="btn-health">Save &amp; check server</button>' +
    '<div class="error" id="server-error"></div>' +
    '<div class="note" id="server-health" hidden></div>' +
    (store.token && store.serverUrl
      ? '<button class="btn secondary" id="btn-continue">Continue — I’m already signed in</button>' : '') +
    '<button class="btn secondary" id="btn-demo">Try demo mode — no server needed</button>' +
    '<small style="margin-top:14px">Tip: once this page is open, use Share → Add to Home Screen ' +
    'and Multivo will sit on your Home Screen like an app.</small></div>';
  $('#btn-health').onclick = async () => {
    const url = $('#server-url').value.trim();
    showError('server-error', '');
    if (!url) { showError('server-error', 'Type your server address first — for example http://192.168.1.5:8080'); return; }
    store.serverUrl = url; store.demo = false;
    const btn = $('#btn-health'); btn.disabled = true; btn.textContent = 'Checking…';
    try {
      const health = await api('/health');
      const box = $('#server-health');
      box.hidden = false;
      box.textContent = 'Server is up — status: ' + health.status + ', database: ' + health.database +
        ', translation: ' + health.translation + '. Now sign in below.';
      await loadLanguages();
      showAuth();
    } catch (e) {
      showError('server-error', 'Could not reach that server: ' + e.message +
        ' Check the address, that the server is running (./gradlew :server:run), and that your phone is on the same Wi-Fi.');
    } finally { btn.disabled = false; btn.textContent = 'Save & check server'; }
  };
  const cont = $('#btn-continue');
  if (cont) cont.onclick = async () => { await enterApp(); };
  $('#btn-demo').onclick = async () => { enterDemo(); };
}

// 2 — Auth: sign in, or create an account.
let authMode = 'login';
function showAuth() {
  showView('auth', false);
  main.innerHTML =
    '<div class="brand"><div class="logo">M</div><h1>Multivo</h1><p>Chat in your language, read in theirs.</p></div>' +
    demoBanner() +
    '<div class="card"><h2 id="auth-title">Sign in</h2>' +
    '<div class="seg"><button id="seg-login" class="active">Sign in</button>' +
    '<button id="seg-register">Create account</button></div>' +
    '<div id="auth-fields"></div>' +
    '<div class="error" id="auth-error"></div>' +
    '<button class="btn" id="btn-auth">Sign in</button>' +
    '<button class="btn ghost" id="btn-back-server">← Change server</button></div>';
  const renderFields = () => {
    $('#auth-title').textContent = authMode === 'login' ? 'Sign in' : 'Create an account';
    $('#btn-auth').textContent = authMode === 'login' ? 'Sign in' : 'Create account';
    $('#seg-login').classList.toggle('active', authMode === 'login');
    $('#seg-register').classList.toggle('active', authMode === 'register');
    $('#auth-fields').innerHTML = authMode === 'login'
      ? '<label for="f-identifier">Username or email</label><input id="f-identifier" autocomplete="username" placeholder="angelic">' +
        '<label for="f-password">Password</label><input id="f-password" type="password" autocomplete="current-password" placeholder="••••••••">'
      : '<label for="f-username">Username</label><input id="f-username" autocomplete="username" placeholder="angelic">' +
        '<label for="f-fullname">Full name</label><input id="f-fullname" autocomplete="name" placeholder="Angelic">' +
        '<label for="f-email">Email</label><input id="f-email" type="email" autocomplete="email" placeholder="angelic@example.com">' +
        '<label for="f-password">Password</label><input id="f-password" type="password" autocomplete="new-password" placeholder="At least 8 characters">' +
        '<label for="f-lang">I read messages in</label><select id="f-lang">' +
        (state.languages || FALLBACK_LANGUAGES).map((l) => '<option value="' + esc(l.code) + '">' + esc(l.name) + '</option>').join('') + '</select>' +
        '<small>New messages will be translated into this language for you.</small>';
  };
  renderFields();
  $('#seg-login').onclick = () => { authMode = 'login'; renderFields(); showError('auth-error', ''); };
  $('#seg-register').onclick = () => { authMode = 'register'; renderFields(); showError('auth-error', ''); };
  $('#btn-back-server').onclick = () => showServer();
  $('#btn-auth').onclick = async () => {
    showError('auth-error', '');
    const btn = $('#btn-auth'); btn.disabled = true;
    try {
      const pw = $('#f-password').value;
      let res;
      if (authMode === 'login') {
        res = await api('/auth/login', { method: 'POST', body: { identifier: $('#f-identifier').value.trim(), password: pw } });
      } else {
        res = await api('/auth/register', { method: 'POST', body: {
          username: $('#f-username').value.trim(), fullName: $('#f-fullname').value.trim(),
          email: $('#f-email').value.trim(), password: pw,
          preferredLanguage: $('#f-lang').value || 'en' } });
      }
      store.token = res.token; state.me = res.user;
      await enterApp();
    } catch (e) { showError('auth-error', e.message); }
    finally { btn.disabled = false; }
  };
}

// After sign-in (live or demo): load the basics, open the socket, show chats.
async function enterApp() {
  try { await loadMe(); } catch (e) { /* /users/me failing means the token is stale */ signOut(); return; }
  await loadLanguages();
  await refreshBadges();
  connectWS();
  showChats();
}

// 3 — Chats list.
function showChats() {
  showView('chats', true);
  renderChats();
}
function renderChats() {
  main.innerHTML =
    '<div class="topbar"><h2>Chats</h2><span style="font-size:13px;color:var(--lavender-muted)">' +
    (store.demo ? 'Demo' : (state.wsConnected ? '● Live' : '○ Connecting…')) + '</span></div>' +
    demoBanner() +
    '<div class="list" id="chat-list"></div>';
  const list = $('#chat-list');
  if (!state.conversations.length) {
    list.innerHTML = '<div class="empty"><span class="big">💬</span>No chats yet.<br>Find someone under Contacts to start one.</div>';
    return;
  }
  list.innerHTML = state.conversations.map((c) => {
    const last = c.lastMessage;
    const text = last ? previewText(last) : 'No messages yet';
    if (last && !last.translatedText && !last.stickerId && !last.audioData && !last.imageData) translateFree(last);
    const other = c.isGroup ? null : c.participants.find((p) => p.userId !== (state.me && state.me.id));
    if (other) ensurePic(other); // fetch their picture if the server did not send one
    const online = c.isGroup ? c.participants.some((p) => p.isOnline && p.userId !== (state.me && state.me.id)) : (other && other.isOnline);
    return '<button class="row" data-id="' + esc(c.id) + '">' +
      avatarHtml(other, c.title, online) +
      '<span class="body"><span class="title">' + esc(c.title) + (c.isGroup ? '<span class="pill">Group</span>' : '') + '</span>' +
      '<span class="sub">' + esc(text) + '</span></span>' +
      '<span class="meta">' + esc(timeAgo(last ? last.createdAt : c.updatedAt)) +
      (c.unreadCount ? '<br><span class="badge">' + c.unreadCount + '</span>' : '') + '</span></button>';
  }).join('');
  list.querySelectorAll('.row').forEach((row) => { row.onclick = () => openChat(row.dataset.id); });
}

// 4 — One chat: messages, typing, send box.
async function openChat(id) {
  state.currentChat = id;
  const conv = state.conversations.find((c) => c.id === id);
  state.currentChatTitle = conv ? conv.title : 'Chat';
  showView('chat', false);
  main.classList.add('chat-view');
  main.innerHTML =
    '<div class="chat-head"><button class="back-btn" id="btn-back">←</button>' +
    '<span class="chat-head-avatar" id="chat-avatar"></span>' +
    '<div class="chat-head-name"><div id="chat-title">' + esc(state.currentChatTitle) + '</div><span id="chat-online"></span></div></div>' +
    demoBanner() +
    '<div id="chat-messages"></div>' +
    '<div class="typing" id="typing"></div>' +
    '<div class="attach-sheet" id="attach-sheet" hidden>' +
      '<button class="attach-opt" id="btn-photo">📷 Photo</button>' +
      '<button class="attach-opt" id="btn-voice">🎙️ Voice note</button>' +
      '<button class="attach-opt" id="btn-sticker">😊 Sticker</button></div>' +
    '<div class="sticker-panel" id="sticker-panel" hidden>' +
      STICKERS.map((s) => '<button class="sticker-pick" data-sticker="' + s.id + '" title="' + esc(s.label) + '"><img src="' + s.src + '" alt="' + esc(s.label) + '"></button>').join('') + '</div>' +
    '<div class="rec-bar" id="rec-bar" hidden><span class="rec-dot"></span><span id="rec-time">0:00</span>' +
      '<button class="btn small secondary" id="btn-rec-cancel">Cancel</button>' +
      '<button class="btn small" id="btn-rec-send">Send</button></div>' +
    '<div class="composer"><button class="cbtn" id="btn-attach" title="Photo, voice note or sticker">+</button>' +
    '<input id="msg-input" placeholder="Message…" autocomplete="off">' +
    '<button class="btn" id="btn-send">Send</button></div>' +
    '<input type="file" id="photo-input" accept="image/*" hidden>';
  $('#btn-back').onclick = () => { state.currentChat = null; sendTyping(id, false); refreshBadges(); main.classList.remove('chat-view'); showChats(); };
  renderChatHeader();
  try { state.messages = await api('/conversations/' + id + '/messages?limit=100'); }
  catch (e) { state.messages = []; $('#chat-messages').innerHTML = '<div class="empty">Could not load messages: ' + esc(e.message) + '</div>'; }
  renderMessages();
  api('/conversations/' + id + '/read', { method: 'POST' }).then(refreshBadges).catch(() => {});
  const input = $('#msg-input');
  let typingSent = false, typingTimer = null, sending = false;
  // Tapping the box to type: re-fit as the keyboard slides in (its
  // animation takes a moment) and keep the newest messages in sight.
  input.addEventListener('focus', () => {
    fitApp();
    setTimeout(scrollChatToBottom, 150);
    setTimeout(() => { fitApp(); scrollChatToBottom(); }, 450);
  });
  input.addEventListener('blur', () => { setTimeout(fitApp, 150); });
  input.addEventListener('input', () => {
    if (!typingSent) { sendTyping(id, true); typingSent = true; }
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => { sendTyping(id, false); typingSent = false; }, 2000);
  });
  const send = async () => {
    const text = input.value.trim();
    if (!text || sending) return; // one tap, one send — a double tap used to post twice
    sending = true; btnSend.disabled = true;
    input.value = ''; sendTyping(id, false); typingSent = false;
    try {
      const m = await api('/conversations/' + id + '/messages', {
        method: 'POST', body: { text, originalLanguage: detectLanguage(text) } });
      addMessage(m);
      renderMessages();
      const conv2 = state.conversations.find((c) => c.id === id);
      if (conv2) { conv2.lastMessage = m; conv2.updatedAt = m.createdAt; }
      if (store.demo) scheduleDemoReply(id); // demo: the other person answers
    } catch (e) {
      $('#chat-messages').innerHTML += '<div class="error show">Could not send: ' + esc(e.message) + '</div>';
    } finally {
      sending = false; btnSend.disabled = false;
    }
  };
  const btnSend = $('#btn-send');
  btnSend.onclick = send;
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });

  // A photo, a voice note or a sticker travels the same road as a text
  // message — one POST, one bubble — just with media instead of words.
  const sendMedia = async (body) => {
    if (sending) return;
    sending = true; btnSend.disabled = true;
    try {
      const m = await api('/conversations/' + id + '/messages', { method: 'POST', body });
      addMessage(m);
      renderMessages();
      const conv2 = state.conversations.find((c) => c.id === id);
      if (conv2) { conv2.lastMessage = m; conv2.updatedAt = m.createdAt; }
      if (store.demo) scheduleDemoReply(id); // demo: the other person answers
    } catch (e) {
      $('#chat-messages').innerHTML += '<div class="error show">Could not send: ' + esc(e.message) + '</div>';
    } finally {
      sending = false; btnSend.disabled = false;
    }
  };
  $('#btn-attach').onclick = () => {
    $('#sticker-panel').hidden = true;
    $('#attach-sheet').hidden = !$('#attach-sheet').hidden;
  };
  $('#btn-sticker').onclick = () => {
    $('#attach-sheet').hidden = true;
    $('#sticker-panel').hidden = !$('#sticker-panel').hidden;
  };
  $('#sticker-panel').querySelectorAll('[data-sticker]').forEach((b) => {
    b.onclick = async () => {
      $('#sticker-panel').hidden = true;
      await sendMedia({ text: '', stickerId: b.dataset.sticker, originalLanguage: state.me ? state.me.preferredLanguage : null });
    };
  });
  $('#btn-photo').onclick = () => { $('#attach-sheet').hidden = true; $('#photo-input').click(); };
  $('#photo-input').addEventListener('change', async (e) => {
    const file = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!file) return;
    try {
      const b64 = await fileToJpeg(file, 1200);
      const caption = input.value.trim();
      input.value = '';
      await sendMedia({ text: caption, imageData: b64, originalLanguage: caption ? detectLanguage(caption) : (state.me ? state.me.preferredLanguage : null) });
    } catch (err) { alert(err.message); }
  });
  // Voice notes: tap to start, tap Send to send it, Cancel to throw it
  // away. 60 seconds max; the phone's recorder makes a small webm clip,
  // which travels as base64 like a photo does.
  let rec = null, recChunks = [], recStream = null, recTimer = null, recSecs = 0, recSend = false;
  const recBar = $('#rec-bar'), recTime = $('#rec-time');
  const fmtSecs = (s) => Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  const stopRec = () => {
    clearInterval(recTimer);
    if (rec && rec.state !== 'inactive') rec.stop();
    if (recStream) { recStream.getTracks().forEach((t) => t.stop()); recStream = null; }
    recBar.hidden = true;
  };
  $('#btn-voice').onclick = async () => {
    $('#attach-sheet').hidden = true;
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { alert('This phone cannot record voice notes.'); return; }
    try {
      recStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (err) { alert('Microphone is off — allow it in the browser settings to send a voice note.'); return; }
    recChunks = [];
    rec = new MediaRecorder(recStream);
    rec.ondataavailable = (ev) => { if (ev.data && ev.data.size) recChunks.push(ev.data); };
    rec.onstop = async () => {
      if (recSend) {
        const blob = new Blob(recChunks, { type: rec.mimeType || 'audio/webm' });
        try {
          const b64 = await blobToBase64(blob);
          await sendMedia({ text: '', audioData: b64, originalLanguage: state.me ? state.me.preferredLanguage : null });
        } catch (err) { alert(err.message); }
      }
      rec = null; recChunks = [];
    };
    rec.start();
    recSecs = 0; recTime.textContent = '0:00';
    recBar.hidden = false;
    recTimer = setInterval(() => {
      recSecs += 1; recTime.textContent = fmtSecs(recSecs);
      if (recSecs >= 60) { recSend = true; stopRec(); } // 60 seconds is enough for a voice note
    }, 1000);
  };
  $('#btn-rec-cancel').onclick = () => { recSend = false; stopRec(); };
  $('#btn-rec-send').onclick = () => { recSend = true; stopRec(); };

  // One listener for the whole message list: the speaker button, a tap on
  // a photo, and the voice-note play button. renderMessages rebuilds the
  // list on every update, so per-bubble handlers would not survive it.
  $('#chat-messages').addEventListener('click', (e) => {
    const spk = e.target.closest('[data-speak]');
    if (spk) {
      const m = state.messages.find((x) => x.id === spk.dataset.speak);
      if (m && m.originalText) speak(m.originalText, m.originalLanguage, m.id);
      return;
    }
    const img = e.target.closest('.msg-img');
    if (img) { openImageViewer(img.src); return; }
    const vp = e.target.closest('.voice-play');
    if (vp) { toggleVoice(vp); return; }
  });
}
function renderChatHeader() {
  const conv = state.conversations.find((c) => c.id === state.currentChat);
  const el = $('#chat-online');
  if (!el || !conv) return;
  const others = conv.participants.filter((p) => p.userId !== (state.me && state.me.id));
  const other = others[0];
  const av = $('#chat-avatar');
  if (av && other) {
    av.innerHTML = avatarHtml(other, other.fullName);
    // The server may not have sent this person's picture — fetch their
    // public profile once and redraw the header only if it lands.
    ensurePic(other).then((changed) => { if (changed) renderChatHeader(); });
  }
  const online = conv.participants.some((p) => p.isOnline && p.userId !== (state.me && state.me.id));
  el.innerHTML = '<span class="dot' + (online ? ' on' : '') + '" style="display:inline-block;vertical-align:middle"></span> ' +
    (online ? 'Online' : 'Offline');
}
function dayLabel(iso) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  const sameDay = (a, b) => a.toDateString() === b.toDateString();
  const today = new Date(), yest = new Date();
  yest.setDate(today.getDate() - 1);
  if (sameDay(d, today)) return 'Today';
  if (sameDay(d, yest)) return 'Yesterday';
  return d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}
function renderTyping() {
  const el = $('#typing');
  if (!el) return;
  const name = state.typingUsers[state.currentChat];
  el.textContent = name ? name + ' is typing…' : '';
}
// One audio player for all voice notes — only one plays at a time.
let voiceAudio = null, voiceBtn = null;
function fmtClock(s) {
  if (!isFinite(s) || s == null) return '0:00';
  return Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0');
}
function toggleVoice(btn) {
  const src = btn.dataset.audio;
  if (!src) return;
  if (voiceAudio && voiceBtn === btn && !voiceAudio.paused) { voiceAudio.pause(); btn.textContent = '▶'; return; }
  if (voiceAudio) { voiceAudio.pause(); if (voiceBtn) voiceBtn.textContent = '▶'; }
  const row = btn.closest('.voice');
  const fill = row ? row.querySelector('.voice-fill') : null;
  const timeEl = row ? row.querySelector('.voice-time') : null;
  voiceAudio = new Audio(src);
  voiceBtn = btn;
  btn.textContent = '⏸';
  voiceAudio.addEventListener('timeupdate', () => {
    if (fill && voiceAudio.duration) fill.style.width = (voiceAudio.currentTime / voiceAudio.duration * 100) + '%';
    if (timeEl) timeEl.textContent = fmtClock(voiceAudio.currentTime);
  });
  voiceAudio.addEventListener('ended', () => {
    btn.textContent = '▶';
    if (fill) fill.style.width = '0%';
    if (timeEl) timeEl.textContent = '0:00';
  });
  voiceAudio.play().catch(() => { btn.textContent = '▶'; });
}
// Tap a photo to see it full screen; tap anywhere to close it.
function openImageViewer(src) {
  closeImageViewer();
  const v = document.createElement('div');
  v.className = 'img-viewer'; v.id = 'img-viewer';
  v.innerHTML = '<img src="' + esc(src) + '" alt=""><button class="img-viewer-close">✕</button>';
  v.onclick = () => closeImageViewer();
  document.body.appendChild(v);
}
function closeImageViewer() {
  const v = document.getElementById('img-viewer');
  if (v) v.remove();
}
// A picture's src: the server stores raw base64, the demo stores a file
// path for its sample stories — both need to become something an <img>
// can show.
function imgSrc(data) {
  if (!data) return '';
  if (data.startsWith('data:') || data.startsWith('http') || data.startsWith('/') || data.startsWith('avatars/')) return data;
  return 'data:image/jpeg;base64,' + data;
}
function renderMessages() {
  const box = $('#chat-messages');
  if (!box) return;
  renderTyping();
  const conv = state.conversations.find((c) => c.id === state.currentChat);
  let lastDay = '';
  box.innerHTML = state.messages.map((m) => {
    const mine = state.me && m.senderId === state.me.id;
    // A date divider when the day changes, the way KakaoTalk shows it.
    const day = dayLabel(m.createdAt);
    const div = day && day !== lastDay ? '<div class="date-div">' + esc(day) + '</div>' : '';
    lastDay = day || lastDay;
    // What I read is the message itself; the original is one small grey
    // line underneath it — a chat message first, a translation second.
    const shown = m.translatedText || freeTranslations[m.id] || '';
    const main_text = shown || m.originalText;
    const orig = shown
      ? '<div class="orig">' + (LANG_FLAG[m.originalLanguage] || '') + ' ' + esc(m.originalText) + '</div>' : '';
    if (!shown && m.originalText) translateFree(m);
    const sender = !mine && conv ? conv.participants.find((p) => p.userId === m.senderId) : null;
    const av = !mine ? '<span class="msg-avatar">' + avatarHtml(sender || { fullName: m.senderName }, m.senderName) + '</span>' : '';
    // A sticker is a picture, not a bubble — it floats on its own.
    if (m.stickerId && stickerSrc(m.stickerId)) {
      return div + '<div class="msg-row ' + (mine ? 'mine' : 'theirs') + '">' + av +
        '<img class="sticker-img" src="' + stickerSrc(m.stickerId) + '" alt="sticker">' +
        '<div class="time">' + esc(msgTime(m.createdAt)) + ticksHtml(m, mine, conv) + '</div></div>';
    }
    // Media inside the bubble: a photo on top, a voice note as a player,
    // then the words (a caption, or the message itself).
    const img = m.imageData
      ? '<img class="msg-img" src="' + imgSrc(m.imageData) + '" alt="photo">' : '';
    const voice = m.audioData
      ? '<div class="voice"><button class="voice-play" data-audio="data:audio/webm;base64,' + esc(m.audioData) + '">▶</button>' +
        '<span class="voice-time">0:00</span><div class="voice-bar"><div class="voice-fill"></div></div></div>' : '';
    const textHtml = (m.originalText || main_text)
      ? '<div>' + esc(main_text) + '</div>' + orig : '';
    const speakBtn = m.originalText
      ? '<button class="speak-btn" data-speak="' + esc(m.id) + '" title="Hear it">' +
        (speakingId === m.id ? '🔊' : '🔈') + '</button>' : '';
    return div + '<div class="msg-row ' + (mine ? 'mine' : 'theirs') + '">' + av +
      '<div class="msg ' + (mine ? 'mine' : 'theirs') + '">' +
      (mine ? '' : '<div class="who">' + esc(m.senderName) + '</div>') +
      img + voice + textHtml +
      '<div class="time">' + esc(msgTime(m.createdAt)) + ticksHtml(m, mine, conv) + speakBtn + '</div></div></div>';
  }).join('');
  box.scrollTop = box.scrollHeight;
}
// Read receipts, two ticks: one tick sent, two ticks when everyone else
// in the chat has opened it. The server sends readBy on every message.
function ticksHtml(m, mine, conv) {
  if (!mine) return '';
  const read = isReadByAllOthers(m, conv);
  return ' <span class="ticks' + (read ? ' read' : '') + '">' + (read ? '✓✓' : '✓') + '</span>';
}

// 5 — Contacts: search, requests, list, new group.
async function showContacts() {
  showView('contacts', true);
  main.innerHTML =
    '<div class="topbar"><h2>Contacts</h2><button class="icon-btn" id="btn-new-group">+ New group</button></div>' +
    demoBanner() +
    '<div id="group-form"></div>' +
    '<label for="search">Find people</label>' +
    '<input id="search" type="search" placeholder="Type at least 2 characters…" autocomplete="off">' +
    '<div class="list" id="search-results"></div>' +
    '<h3 style="margin-top:20px">Requests</h3><div class="list" id="req-list"></div>' +
    '<h3 style="margin-top:20px">My contacts</h3><div class="list" id="contact-list"></div>';
  $('#btn-new-group').onclick = () => renderGroupForm();
  let searchTimer = null;
  $('#search').addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    const q = e.target.value.trim();
    const box = $('#search-results');
    if (q.length < 2) { box.innerHTML = ''; return; }
    searchTimer = setTimeout(async () => {
      try {
        const results = await api('/users/search?q=' + encodeURIComponent(q));
        box.innerHTML = results.length ? results.map((u) => searchRow(u)).join('')
          : '<div class="empty">No one found for “' + esc(q) + '”.</div>';
        wireSearchRows(box);
      } catch (err) { box.innerHTML = '<div class="error show">' + esc(err.message) + '</div>'; }
    }, 400);
  });
  await reloadContacts();
}
function searchRow(u) {
  // relationship comes from the server: contact | request_sent |
  // request_received | none. The button matches what makes sense next.
  const action = u.relationship === 'contact'
    ? '<button class="btn small secondary" data-act="message" data-id="' + esc(u.id) + '">Message</button>'
    : u.relationship === 'request_sent'
      ? '<span class="badge grey">Request sent</span>'
      : '<button class="btn small" data-act="add" data-id="' + esc(u.id) + '">Add</button>';
  return '<div class="row">' + avatarHtml(u, u.fullName, u.isOnline) +
    '<span class="body"><span class="title">' + esc(u.fullName) + '</span>' +
    '<span class="sub">@' + esc(u.username) + ' · ' + esc(langName(u.preferredLanguage)) + '</span></span>' + action + '</div>';
}
function wireSearchRows(box) {
  box.querySelectorAll('[data-act]').forEach((btn) => {
    btn.onclick = async () => {
      const id = btn.dataset.id;
      btn.disabled = true;
      try {
        if (btn.dataset.act === 'add') {
          await api('/contacts/requests', { method: 'POST', body: { receiverId: id } });
          btn.outerHTML = '<span class="badge grey">Request sent</span>';
        } else {
          const conv = await api('/conversations/private', { method: 'POST', body: { userId: id } });
          await refreshBadges(); openChat(conv.id);
        }
      } catch (e) { btn.disabled = false; alert(e.message); }
    };
  });
}
async function reloadContacts() {
  try {
    state.contacts = await api('/contacts');
    state.requests = await api('/contacts/requests');
  } catch (e) {
    $('#contact-list').innerHTML = '<div class="error show">' + esc(e.message) + '</div>';
    return;
  }
  const reqBox = $('#req-list');
  reqBox.innerHTML = state.requests.length ? state.requests.map((r) =>
    '<div class="row">' + avatarHtml(r.sender, r.sender.fullName, false) +
    '<span class="body"><span class="title">' + esc(stripFlags(r.sender.fullName)) + '</span>' +
    '<span class="sub">wants to connect · ' + esc(timeAgo(r.createdAt)) + '</span></span>' +
    '<button class="btn small" data-accept="' + esc(r.id) + '">Accept</button> ' +
    '<button class="btn small secondary" data-reject="' + esc(r.id) + '">Reject</button></div>').join('')
    : '<div class="empty">No pending requests.</div>';
  reqBox.querySelectorAll('[data-accept],[data-reject]').forEach((btn) => {
    btn.onclick = async () => {
      const id = btn.dataset.accept || btn.dataset.reject;
      const accept = !!btn.dataset.accept;
      try {
        await api('/contacts/requests/' + id + (accept ? '/accept' : '/reject'), { method: 'POST' });
        await reloadContacts();
      } catch (e) { alert(e.message); }
    };
  });
  const box = $('#contact-list');
  box.innerHTML = state.contacts.length ? state.contacts.map((u) =>
    '<div class="row">' + avatarHtml(u, u.fullName, u.isOnline) +
    '<span class="body"><span class="title">' + esc(u.fullName) + '</span>' +
    '<span class="sub">@' + esc(u.username) + ' · ' + esc(langName(u.preferredLanguage)) + '</span></span>' +
    '<button class="btn small" data-msg="' + esc(u.id) + '">Message</button> ' +
    '<button class="btn small secondary" data-del="' + esc(u.id) + '">Remove</button></div>').join('')
    : '<div class="empty">No contacts yet — search above to find people.</div>';
  box.querySelectorAll('[data-msg]').forEach((btn) => {
    btn.onclick = async () => {
      try {
        const conv = await api('/conversations/private', { method: 'POST', body: { userId: btn.dataset.msg } });
        await refreshBadges(); openChat(conv.id);
      } catch (e) { alert(e.message); }
    };
  });
  box.querySelectorAll('[data-del]').forEach((btn) => {
    btn.onclick = async () => {
      if (!confirm('Remove this contact?')) return;
      try { await api('/contacts/' + btn.dataset.del, { method: 'DELETE' }); await reloadContacts(); }
      catch (e) { alert(e.message); }
    };
  });
}
function renderGroupForm() {
  const form = $('#group-form');
  if (!form) return;
  form.innerHTML = '<div class="card"><h2>New group</h2>' +
    '<label for="group-title">Group name</label>' +
    '<input id="group-title" placeholder="Weekend trip">' +
    '<label>Members (pick from your contacts)</label>' +
    (state.contacts.length ? state.contacts.map((u) =>
      '<label class="check-row"><input type="checkbox" value="' + esc(u.id) + '"> ' + esc(u.fullName) + ' (@' + esc(u.username) + ')</label>').join('')
      : '<small>You have no contacts yet — add some first.</small>') +
    '<div class="error" id="group-error"></div>' +
    '<button class="btn" id="btn-create-group">Create group</button></div>';
  $('#btn-create-group').onclick = async () => {
    const title = $('#group-title').value.trim();
    const ids = Array.from(form.querySelectorAll('input[type=checkbox]:checked')).map((c) => c.value);
    if (!title) { showError('group-error', 'Give the group a name.'); return; }
    if (!ids.length) { showError('group-error', 'Pick at least one member.'); return; }
    try {
      const conv = await api('/conversations/groups', { method: 'POST', body: { title, participantIds: ids } });
      await refreshBadges(); openChat(conv.id);
    } catch (e) { showError('group-error', e.message); }
  };
}

// 6 — Notifications.
async function showNotifications() {
  showView('notifications', true);
  main.innerHTML = '<div class="topbar"><h2>Alerts</h2>' +
    '<button class="icon-btn" id="btn-read-all">Mark all read</button></div>' + demoBanner() +
    '<div class="list" id="notif-list"></div>';
  $('#btn-read-all').onclick = async () => {
    try { await api('/notifications/read-all', { method: 'POST' }); await refreshBadges(); showNotifications(); }
    catch (e) { alert(e.message); }
  };
  renderNotifications();
}
function renderNotifications() {
  const box = $('#notif-list');
  if (!box) return;
  box.innerHTML = state.notifications.length ? state.notifications.map((n) =>
    '<div class="row notif' + (n.isRead ? '' : ' unread') + '">' +
    '<span class="avatar">🔔</span>' +
    '<span class="body"><span class="title">' + esc(n.title) + '</span>' +
    '<span class="sub">' + esc(n.body) + '</span></span>' +
    '<span class="meta">' + esc(timeAgo(n.createdAt)) + '</span></div>').join('')
    : '<div class="empty"><span class="big">🔔</span>Nothing here yet.</div>';
}

// 6b — Moments: a feed, not WhatsApp circles. A picture and a caption in
// your own language, translated underneath for everyone else — stories
// are the translation idea in its purest form. Gone after 24 hours.
async function showMoments() {
  showView('moments', true);
  main.innerHTML =
    '<div class="topbar"><h2>Moments</h2><button class="icon-btn" id="btn-new-moment">+ New moment</button></div>' +
    demoBanner() +
    '<div id="moment-form"></div>' +
    '<div class="list" id="moment-list"><div class="empty">Loading moments…</div></div>';
  $('#btn-new-moment').onclick = () => renderMomentForm();
  try {
    state.stories = await api('/stories/feed');
  } catch (e) {
    $('#moment-list').innerHTML = '<div class="error show">' + esc(e.message) + '</div>';
    return;
  }
  renderMoments();
}
function renderMomentForm() {
  const form = $('#moment-form');
  if (!form) return;
  form.innerHTML = '<div class="card"><h2>New moment</h2>' +
    '<label for="moment-caption">What is happening?</label>' +
    '<input id="moment-caption" placeholder="A line in your language…" autocomplete="off">' +
    '<div class="moment-photo-row"><button class="btn secondary" id="btn-moment-photo">📷 Add a photo</button>' +
    '<span id="moment-photo-name" style="font-size:13px;color:var(--lavender-muted)"></span></div>' +
    '<input type="file" id="moment-photo-input" accept="image/*" hidden>' +
    '<div class="error" id="moment-error"></div>' +
    '<button class="btn" id="btn-post-moment">Post moment</button></div>';
  let photoB64 = null;
  $('#btn-moment-photo').onclick = () => $('#moment-photo-input').click();
  $('#moment-photo-input').addEventListener('change', async (e) => {
    const file = e.target.files && e.target.files[0];
    e.target.value = '';
    if (!file) return;
    try {
      photoB64 = await fileToJpeg(file, 1200);
      $('#moment-photo-name').textContent = 'Photo attached ✓';
    } catch (err) { showError('moment-error', err.message); }
  });
  $('#btn-post-moment').onclick = async () => {
    const caption = $('#moment-caption').value.trim();
    if (!caption && !photoB64) { showError('moment-error', 'Write a line or add a photo.'); return; }
    try {
      await api('/stories', { method: 'POST', body: {
        caption: caption || null, imageData: photoB64,
        captionLanguage: state.me ? state.me.preferredLanguage : null } });
      await showMoments();
    } catch (e) { showError('moment-error', e.message); }
  };
}
function renderMoments() {
  const box = $('#moment-list');
  if (!box) return;
  const stories = state.stories || [];
  if (!stories.length) {
    box.innerHTML = '<div class="empty"><span class="big">✨</span>No moments yet.<br>Post the first one — it disappears in 24 hours.</div>';
    return;
  }
  box.innerHTML = stories.map((s) => {
    const mine = state.me && s.userId === state.me.id;
    return '<div class="story-card">' +
      '<div class="story-head">' + avatarHtml({ fullName: s.fullName, profilePicture: s.profilePicture }, s.fullName) +
      '<span class="body"><span class="title">' + esc(s.fullName) + '</span>' +
      '<span class="sub">' + esc(timeAgo(s.createdAt)) + ' · ' + esc(langName(s.captionLanguage)) + '</span></span>' +
      (mine ? '<button class="icon-btn" data-del-story="' + esc(s.id) + '">Delete</button>' : '') + '</div>' +
      (s.imageData ? '<img class="story-img" src="' + imgSrc(s.imageData) + '" alt="">' : '') +
      (s.caption ? '<div class="story-cap">' + esc(s.caption) + '</div>' +
        '<div class="story-trans" data-story-trans="' + esc(s.id) + '"></div>' : '') +
      '</div>';
  }).join('');
  box.querySelectorAll('[data-del-story]').forEach((b) => {
    b.onclick = async () => {
      if (!confirm('Delete this moment?')) return;
      try { await api('/stories/' + b.dataset.delStory, { method: 'DELETE' }); await showMoments(); }
      catch (e) { alert(e.message); }
    };
  });
  // The caption is in the poster's language; the translation underneath
  // is for me, fetched in the browser like the chat's free translation.
  const mine = state.me ? state.me.preferredLanguage : 'en';
  stories.forEach(async (s) => {
    if (!s.caption || !s.captionLanguage || s.captionLanguage === mine) return;
    const el = box.querySelector('[data-story-trans="' + s.id + '"]');
    if (!el) return;
    const t = await translateText(s.caption, s.captionLanguage, mine);
    if (t && el.isConnected) el.textContent = t;
  });
}

// 7 — Profile: view and edit, language picker, sign out.
async function showProfile() {
  showView('profile', true);
  if (!state.me) { try { await loadMe(); } catch (e) { signOut(); return; } }
  await loadLanguages();
  const me = state.me;
  main.innerHTML =
    '<div class="topbar"><h2>Profile</h2></div>' + demoBanner() +
    '<div class="card" style="text-align:center">' +
    (me.profilePicture
      ? '<img src="' + esc(me.profilePicture) + '" alt="" style="width:84px;height:84px;border-radius:50%;object-fit:cover;margin:0 auto;display:block">'
      : '<div class="avatar" style="width:84px;height:84px;font-size:32px;margin:0 auto">' + esc(initials(me.fullName)) + '</div>') +
    '<h2 style="margin-top:10px">' + esc(me.fullName) + '</h2>' +
    '<p style="color:var(--lavender-muted);margin:4px 0">@' + esc(me.username) + ' · ' + esc(me.email) + '<br>' +
    (me.isOnline ? '● Online' : '○ Offline') + ' · reads ' + esc(langName(me.preferredLanguage)) + '</p></div>' +
    '<div class="card"><h2>Edit profile</h2>' +
    '<label for="p-fullname">Full name</label><input id="p-fullname" value="' + esc(me.fullName) + '">' +
    '<label for="p-bio">Bio</label><input id="p-bio" value="' + esc(me.bio || '') + '" placeholder="A line about you">' +
    '<label for="p-pic">Profile picture (image link)</label><input id="p-pic" type="url" value="' + esc(me.profilePicture || '') + '" placeholder="https://…">' +
    '<label for="p-lang">I read messages in</label><select id="p-lang">' +
    (state.languages || FALLBACK_LANGUAGES).map((l) =>
      '<option value="' + esc(l.code) + '"' + (l.code === me.preferredLanguage ? ' selected' : '') + '>' + esc(l.name) + '</option>').join('') +
    '</select><div class="error" id="profile-error"></div>' +
    '<button class="btn" id="btn-save-profile">Save profile</button></div>' +
    '<div class="card"><h2>Theme</h2>' +
    '<label for="p-theme">App theme</label><select id="p-theme">' +
    Object.keys(THEMES).map((k) => '<option value="' + k + '"' + (k === currentTheme() ? ' selected' : '') + '>' + esc(THEMES[k].label) + '</option>').join('') +
    '</select><small>Plum dark is the default. Your choice is saved on this phone.</small></div>' +
    '<div class="card"><h2>Server</h2>' +
    '<p style="color:var(--lavender-muted);font-size:14px;margin:6px 0">' + (store.demo ? 'Demo mode — no server.' : 'Connected to<br><code>' + esc(store.serverUrl) + '</code>') + '</p>' +
    '<button class="btn secondary" id="btn-change-server">Change server</button>' +
    '<button class="btn ghost" id="btn-signout">Sign out</button></div>';
  $('#btn-save-profile').onclick = async () => {
    showError('profile-error', '');
    try {
      state.me = await api('/users/me', { method: 'PUT', body: {
        fullName: $('#p-fullname').value.trim(),
        bio: $('#p-bio').value.trim() || null,
        profilePicture: $('#p-pic').value.trim() || null,
        preferredLanguage: $('#p-lang').value } });
      showProfile();
    } catch (e) { showError('profile-error', e.message); }
  };
  $('#btn-change-server').onclick = () => { store.token = ''; disconnectWS(); showServer(); };
  $('#btn-signout').onclick = () => signOut();
  $('#p-theme').onchange = (e) => {
    localStorage.setItem('mv_theme', e.target.value);
    applyTheme(e.target.value);
  };
}

// ---------- tabs + start ----------
// Tabs are wired once; each one loads its own data when tapped.
document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', async () => {
    const name = tab.dataset.tab;
    if (name === 'chats') { await refreshBadges(); showChats(); }
    if (name === 'contacts') showContacts();
    if (name === 'moments') showMoments();
    if (name === 'notifications') { await refreshBadges(); showNotifications(); }
    if (name === 'profile') showProfile();
  });
});

// Demo mode: open the app itself, with your friend's sample chats,
// so the first thing anyone sees is the app, not a setup screen.
// The server screen is still reachable from Profile → Change server.
function enterDemo() {
  store.demo = true; store.token = 'demo-token';
  state.me = DEMO.me; state.languages = FALLBACK_LANGUAGES;
  state.conversations = DEMO.conversations; state.notifications = DEMO.notifications;
  showChats();
}

// First screen: the app itself. A saved live session resumes into the
// real server; a saved demo resumes into the demo; everyone else opens
// straight into the demo chats — no server setup screen first.
(async function init() {
  applyTheme(currentTheme());
  if (store.demo) { enterDemo(); return; }
  if (store.serverUrl && store.token) { await enterApp(); return; }
  enterDemo();
})();
