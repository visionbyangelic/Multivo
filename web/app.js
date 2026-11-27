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
 *   WS  /ws?token=JWT
 */
'use strict';

// ---------- tiny helpers ----------
const $ = (sel, root) => (root || document).querySelector(sel);
const main = $('#main');
const tabsEl = $('#tabs');

// Never inject server or user text as HTML — always escape it first.
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}
function initials(name) {
  return (name || '?').trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();
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

// ---------- demo data ----------
// Copied from the Android preview data (ui/preview/SampleData.kt) so the
// demo feels like the real app. Times are relative to "now" when loaded.
function minutesAgo(m) { return new Date(Date.now() - m * 60000).toISOString(); }
const DEMO = {
  me: { id: 'me', username: 'ada', fullName: 'Ada Obi', email: 'ada@example.com',
        bio: 'Lagos · coffee · languages', preferredLanguage: 'en', isOnline: true },
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
      receiver: { id: 'me', username: 'ada', fullName: 'Ada Obi', profilePicture: null, preferredLanguage: 'en', isOnline: true },
      status: 'pending', createdAt: minutesAgo(30) },
  ],
  conversations: [
    { id: 'c1', title: 'Amélie Laurent', isGroup: false, createdBy: 'me', createdAt: minutesAgo(600), updatedAt: minutesAgo(2),
      participants: [
        { userId: 'me', username: 'ada', fullName: 'Ada Obi', preferredLanguage: 'en', isOnline: true },
        { userId: 'u1', username: 'amelie', fullName: 'Amélie Laurent', preferredLanguage: 'fr', isOnline: true },
      ],
      lastMessage: { id: 'm4', conversationId: 'c1', senderId: 'me', senderName: 'Ada Obi', originalText: 'See you there 🙌', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(2) },
      unreadCount: 0 },
    { id: 'c2', title: 'Diego Ramírez', isGroup: false, createdBy: 'u2', createdAt: minutesAgo(3000), updatedAt: minutesAgo(90),
      participants: [
        { userId: 'me', username: 'ada', fullName: 'Ada Obi', preferredLanguage: 'en', isOnline: true },
        { userId: 'u2', username: 'diego', fullName: 'Diego Ramírez', preferredLanguage: 'es', isOnline: false },
      ],
      lastMessage: { id: 'm9', conversationId: 'c2', senderId: 'u2', senderName: 'Diego Ramírez', originalText: '¿Mañana a las diez?', originalLanguage: 'es', translatedText: 'Tomorrow at ten?', targetLanguage: 'en', createdAt: minutesAgo(90) },
      unreadCount: 2 },
    { id: 'c3', title: 'Weekend trip', isGroup: true, createdBy: 'me', createdAt: minutesAgo(5000), updatedAt: minutesAgo(1500),
      participants: [
        { userId: 'me', username: 'ada', fullName: 'Ada Obi', preferredLanguage: 'en', isOnline: true },
        { userId: 'u1', username: 'amelie', fullName: 'Amélie Laurent', preferredLanguage: 'fr', isOnline: true },
        { userId: 'u3', username: 'yuki', fullName: 'Yuki Tanaka', preferredLanguage: 'ja', isOnline: true },
      ],
      lastMessage: { id: 'm10', conversationId: 'c3', senderId: 'u3', senderName: 'Yuki Tanaka', originalText: '電車は9時です', originalLanguage: 'ja', translatedText: 'The train is at 9', targetLanguage: 'en', createdAt: minutesAgo(1500) },
      unreadCount: 5 },
  ],
  messages: {
    c1: [
      { id: 'm1', conversationId: 'c1', senderId: 'u1', senderName: 'Amélie Laurent', originalText: 'Salut Ada ! Tu es libre ce soir ?', originalLanguage: 'fr', translatedText: 'Hi Ada! Are you free tonight?', targetLanguage: 'en', createdAt: minutesAgo(42) },
      { id: 'm2', conversationId: 'c1', senderId: 'me', senderName: 'Ada Obi', originalText: 'Yes! Dinner at 8?', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(40) },
      { id: 'm3', conversationId: 'c1', senderId: 'u1', senderName: 'Amélie Laurent', originalText: 'Parfait, je réserve une table près du port.', originalLanguage: 'fr', translatedText: "Perfect, I'll book a table near the harbour.", targetLanguage: 'en', createdAt: minutesAgo(38) },
      { id: 'm4', conversationId: 'c1', senderId: 'me', senderName: 'Ada Obi', originalText: 'See you there 🙌', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(2) },
    ],
    c2: [
      { id: 'm8', conversationId: 'c2', senderId: 'me', senderName: 'Ada Obi', originalText: 'Hola Diego, long time!', originalLanguage: 'en', translatedText: null, targetLanguage: null, createdAt: minutesAgo(100) },
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
      originalText: opts.body.text, originalLanguage: opts.body.originalLanguage || DEMO.me.preferredLanguage,
      translatedText: null, targetLanguage: null, createdAt: new Date().toISOString() };
    (DEMO.messages[conv.id] = DEMO.messages[conv.id] || []).push(m);
    conv.lastMessage = m; conv.updatedAt = m.createdAt;
    return m;
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

// A live event arrives: a message, someone typing, someone coming online.
function handleSocketEvent(ev) {
  if (!ev || !ev.type) return;
  if (ev.type === 'new_message' && ev.message) {
    const m = ev.message;
    const conv = state.conversations.find((c) => c.id === m.conversationId);
    if (conv) { conv.lastMessage = m; conv.updatedAt = m.createdAt; if (m.senderId !== state.me.id && state.currentChat !== m.conversationId) conv.unreadCount = (conv.unreadCount || 0) + 1; }
    if (state.currentChat === m.conversationId) {
      state.messages.push(m);
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
      ? '<label for="f-identifier">Username or email</label><input id="f-identifier" autocomplete="username" placeholder="ada">' +
        '<label for="f-password">Password</label><input id="f-password" type="password" autocomplete="current-password" placeholder="••••••••">'
      : '<label for="f-username">Username</label><input id="f-username" autocomplete="username" placeholder="ada">' +
        '<label for="f-fullname">Full name</label><input id="f-fullname" autocomplete="name" placeholder="Ada Obi">' +
        '<label for="f-email">Email</label><input id="f-email" type="email" autocomplete="email" placeholder="ada@example.com">' +
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
    const text = last ? (last.translatedText || last.originalText) : 'No messages yet';
    const other = c.isGroup ? null : c.participants.find((p) => p.userId !== (state.me && state.me.id));
    const online = c.isGroup ? c.participants.some((p) => p.isOnline && p.userId !== (state.me && state.me.id)) : (other && other.isOnline);
    return '<button class="row" data-id="' + esc(c.id) + '">' +
      '<span class="avatar">' + esc(initials(c.title)) + (online ? '<span class="dot on"></span>' : '') + '</span>' +
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
  main.innerHTML =
    '<button class="back-btn" id="btn-back">← Chats</button>' +
    '<div class="topbar"><h2 id="chat-title">' + esc(state.currentChatTitle) + '</h2><span id="chat-online"></span></div>' +
    demoBanner() +
    '<div id="chat-messages"></div>' +
    '<div class="typing" id="typing"></div>' +
    '<div class="composer"><input id="msg-input" placeholder="Message…" autocomplete="off">' +
    '<button class="btn" id="btn-send">Send</button></div>';
  $('#btn-back').onclick = () => { state.currentChat = null; sendTyping(id, false); refreshBadges(); showChats(); };
  renderChatHeader();
  try { state.messages = await api('/conversations/' + id + '/messages?limit=100'); }
  catch (e) { state.messages = []; $('#chat-messages').innerHTML = '<div class="empty">Could not load messages: ' + esc(e.message) + '</div>'; }
  renderMessages();
  api('/conversations/' + id + '/read', { method: 'POST' }).then(refreshBadges).catch(() => {});
  const input = $('#msg-input');
  let typingSent = false, typingTimer = null;
  input.addEventListener('input', () => {
    if (!typingSent) { sendTyping(id, true); typingSent = true; }
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => { sendTyping(id, false); typingSent = false; }, 2000);
  });
  const send = async () => {
    const text = input.value.trim();
    if (!text) return;
    input.value = ''; sendTyping(id, false); typingSent = false;
    try {
      const m = await api('/conversations/' + id + '/messages', {
        method: 'POST', body: { text, originalLanguage: state.me ? state.me.preferredLanguage : null } });
      state.messages.push(m);
      renderMessages();
      const conv2 = state.conversations.find((c) => c.id === id);
      if (conv2) { conv2.lastMessage = m; conv2.updatedAt = m.createdAt; }
    } catch (e) {
      $('#chat-messages').innerHTML += '<div class="error show">Could not send: ' + esc(e.message) + '</div>';
    }
  };
  $('#btn-send').onclick = send;
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });
}
function renderChatHeader() {
  const conv = state.conversations.find((c) => c.id === state.currentChat);
  const el = $('#chat-online');
  if (!el || !conv) return;
  const online = conv.participants.some((p) => p.isOnline && p.userId !== (state.me && state.me.id));
  el.innerHTML = '<span class="dot' + (online ? ' on' : '') + '" style="display:inline-block;vertical-align:middle"></span> ' +
    '<span style="font-size:13px;color:var(--lavender-muted)">' + (online ? 'Online' : 'Offline') + '</span>';
}
function renderTyping() {
  const el = $('#typing');
  if (!el) return;
  const name = state.typingUsers[state.currentChat];
  el.textContent = name ? name + ' is typing…' : '';
}
function renderMessages() {
  const box = $('#chat-messages');
  if (!box) return;
  renderTyping();
  box.innerHTML = state.messages.map((m) => {
    const mine = state.me && m.senderId === state.me.id;
    // Show the translation (what I read) first, original underneath — the
    // whole point of Multivo.
    const main_text = m.translatedText || m.originalText;
    const orig = m.translatedText
      ? '<div class="orig">Original (' + esc(langName(m.originalLanguage)) + '): ' + esc(m.originalText) + '</div>' : '';
    return '<div class="msg ' + (mine ? 'mine' : 'theirs') + '">' +
      (mine ? '' : '<div class="who">' + esc(m.senderName) + '</div>') +
      '<div>' + esc(main_text) + '</div>' + orig +
      '<div class="time">' + esc(msgTime(m.createdAt)) + '</div></div>';
  }).join('');
  box.scrollTop = box.scrollHeight;
  window.scrollTo(0, document.body.scrollHeight);
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
  return '<div class="row"><span class="avatar">' + esc(initials(u.fullName)) +
    (u.isOnline ? '<span class="dot on"></span>' : '') + '</span>' +
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
    '<div class="row"><span class="avatar">' + esc(initials(r.sender.fullName)) + '</span>' +
    '<span class="body"><span class="title">' + esc(r.sender.fullName) + '</span>' +
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
    '<div class="row"><span class="avatar">' + esc(initials(u.fullName)) +
    (u.isOnline ? '<span class="dot on"></span>' : '') + '</span>' +
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

// 7 — Profile: view and edit, language picker, sign out.
async function showProfile() {
  showView('profile', true);
  if (!state.me) { try { await loadMe(); } catch (e) { signOut(); return; } }
  await loadLanguages();
  const me = state.me;
  main.innerHTML =
    '<div class="topbar"><h2>Profile</h2></div>' + demoBanner() +
    '<div class="card" style="text-align:center"><div class="avatar" style="width:84px;height:84px;font-size:32px;margin:0 auto">' +
    esc(initials(me.fullName)) + '</div>' +
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
}

// ---------- tabs + start ----------
// Tabs are wired once; each one loads its own data when tapped.
document.querySelectorAll('.tab').forEach((tab) => {
  tab.addEventListener('click', async () => {
    const name = tab.dataset.tab;
    if (name === 'chats') { await refreshBadges(); showChats(); }
    if (name === 'contacts') showContacts();
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
  if (store.demo) { enterDemo(); return; }
  if (store.serverUrl && store.token) { await enterApp(); return; }
  enterDemo();
})();
