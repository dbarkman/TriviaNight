package com.redmesastudios.trivianight

// The page a phone gets from the QR code: join with a name and a color, then answer each
// question with one tap. It asks the TV what to show about twice a second, and remembers its
// player id so a reload or a locked phone rejoins the same seat. Plain HTML and JS, so any
// phone's browser works without installing anything. (No "$" in the script: this is a
// Kotlin string.)
fun phonePage(): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="theme-color" content="#151831">
<title>Trivia Night</title>
<style>
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  html, body { margin: 0; height: 100%; background: #151831; color: #F5F3FF; font: 600 17px/1.4 -apple-system, system-ui, sans-serif; }
  main { min-height: 100%; display: flex; flex-direction: column; gap: 18px; padding: max(28px, env(safe-area-inset-top)) 20px max(24px, env(safe-area-inset-bottom)); max-width: 480px; margin: 0 auto; }
  .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 20px; }
  .brand svg { width: 34px; height: 34px; }
  h1 { margin: 0; font-size: 34px; line-height: 1.1; letter-spacing: -0.5px; font-weight: 800; }
  .soft { color: #B7B9E0; }
  label { font-size: 15px; font-weight: 700; color: #B7B9E0; display: block; margin-bottom: 8px; }
  input { width: 100%; height: 58px; padding: 0 16px; border-radius: 16px; border: 2px solid #3A4078; background: #1F2346; color: #F5F3FF; font: 700 22px -apple-system, system-ui, sans-serif; outline: none; }
  input:focus { border-color: #FFD25E; }
  .colors { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
  .colors button { height: 60px; border-radius: 18px; border: 0; }
  .colors button.on { box-shadow: 0 0 0 4px #151831, 0 0 0 7px #F5F3FF; }
  .go { margin-top: auto; padding: 18px; border: 0; border-radius: 999px; background: #FFD25E; color: #151831; font: 800 24px -apple-system, system-ui, sans-serif; }
  .go:disabled { opacity: 0.5; }
  .top { display: flex; justify-content: space-between; align-items: center; }
  .me { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 19px; }
  .dot { width: 38px; height: 38px; border-radius: 50%; color: #151831; display: flex; align-items: center; justify-content: center; font-weight: 800; }
  .score { font-weight: 800; font-size: 24px; color: #FFD25E; }
  .answers { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 14px; min-height: 420px; }
  .answers.two { grid-template-columns: 1fr; }
  .answers button { border: 0; border-radius: 26px; color: #151831; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 12px; font: 800 20px/1.2 -apple-system, system-ui, sans-serif; }
  .answers button:active { transform: scale(0.97); }
  .answers svg { width: 52px; height: 52px; }
  .big { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; text-align: center; }
  .big .shape { width: 120px; height: 120px; border-radius: 32px; display: flex; align-items: center; justify-content: center; }
  .big .shape svg { width: 64px; height: 64px; }
  .points { font-size: 56px; font-weight: 800; color: #FFD25E; }
  .note { font-size: 15px; }
  .picks { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .pick { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 18px; border: 2px solid #3A4078; background: #1F2346; color: #F5F3FF; font: 800 19px -apple-system, system-ui, sans-serif; text-align: left; }
  .pick:active { border-color: #FFD25E; }
  .pick:disabled { opacity: 0.45; }
  .pick small { font-weight: 600; font-size: 13px; color: #B7B9E0; }
  .or { margin: 4px 0 -6px; }
  .problem { margin: 0; color: #FF7A66; }
</style>
</head>
<body>
<main id="app"></main>
<script>
var COLORS = ['#4DB5FF', '#FF7A66', '#4FE0B0', '#FFD25E', '#B79BFF', '#FF8FC7', '#B8E55C', '#FFA44F'];
var ANSWER_COLORS = ['#FF7A66', '#4DB5FF', '#FFD25E', '#4FE0B0'];
var SHAPES = ['M32 6 L60 56 L4 56 Z', 'M32 2 L62 32 L32 62 L2 32 Z', 'M32 4 a28 28 0 1 0 0.01 0 Z', 'M6 6 H58 V58 H6 Z'];
var params = new URLSearchParams(location.search);
var pin = params.get('pin') || '';
var idKey = 'trivia-id-' + pin;
var id = localStorage.getItem(idKey);
var color = parseInt(localStorage.getItem('trivia-color') || '0', 10);
var shown = '';
var app = document.getElementById('app');

function esc(text) { var d = document.createElement('div'); d.textContent = text; return d.innerHTML; }
function shape(i, fill) { return '<svg viewBox="0 0 64 64" aria-hidden="true"><path d="' + SHAPES[i] + '" fill="' + (fill || '#151831') + '"/></svg>'; }
function brand() { return '<div class="brand"><svg viewBox="0 0 84 84" aria-hidden="true"><rect x="4" y="4" width="76" height="76" rx="22" fill="#FFD25E"/><path d="M30 34c0-7 5-12 12-12s12 5 12 11c0 9-12 9-12 17" fill="none" stroke="#151831" stroke-width="7" stroke-linecap="round"/><circle cx="42" cy="61" r="4.5" fill="#151831"/></svg>Trivia Night</div>'; }
function ordinal(n) { var s = ['th', 'st', 'nd', 'rd'], v = n % 100; return n + (s[(v - 20) % 10] || s[v] || s[0]); }
function header(v) {
  return '<div class="top"><div class="me"><span class="dot" style="background:' + COLORS[v.color] + '">' + esc(v.name.charAt(0).toUpperCase()) + '</span>' + esc(v.name) + '</div><span class="score">' + v.score.toLocaleString() + '</span></div>';
}

function showCode() {
  app.innerHTML = brand() + '<h1>Enter the code on the TV</h1><div><label for="code">Code</label><input id="code" inputmode="numeric" maxlength="4" autocomplete="off"></div><button class="go" id="go">Next</button>';
  document.getElementById('go').onclick = function () {
    location.search = '?pin=' + encodeURIComponent(document.getElementById('code').value.trim());
  };
}

function showJoin() {
  app.innerHTML = brand() + '<p class="soft">Loading…</p>';
  fetch('/api/players?pin=' + encodeURIComponent(pin), { cache: 'no-store' })
    .then(function (r) { return r.json(); })
    .then(function (r) { joinForm(r.players || [], ''); })
    .catch(function () { joinForm([], ''); });
}

// Saved players first (tap your name), then a form for someone new.
function joinForm(players, problem) {
  var name = localStorage.getItem('trivia-name') || '';
  var mine = localStorage.getItem('trivia-profile') || '';
  players.sort(function (a, b) { return (b.id === mine) - (a.id === mine); });
  var swatches = COLORS.map(function (c, i) {
    return '<button type="button" aria-label="Color ' + (i + 1) + '" data-i="' + i + '" class="' + (i === color ? 'on' : '') + '" style="background:' + c + '"></button>';
  }).join('');
  var saved = players.map(function (p) {
    return '<button type="button" class="pick" data-id="' + esc(p.id) + '"' + (p.playing ? ' disabled' : '') + '>' +
      '<span class="dot" style="background:' + COLORS[p.color] + '">' + esc(p.name.charAt(0).toUpperCase()) + '</span>' +
      '<span>' + esc(p.name) + (p.playing ? '<small> · already in</small>' : '') + '</span></button>';
  }).join('');
  app.innerHTML = brand() + '<h1>' + (players.length ? 'Who\'s playing?' : 'Join the game on the TV') + '</h1>' +
    (problem ? '<p class="problem">' + esc(problem) + '</p>' : '') +
    (players.length ? '<div class="picks">' + saved + '</div><p class="soft or">Someone new?</p>' : '') +
    '<div><label for="name">First name</label><input id="name" maxlength="14" autocomplete="given-name" value="' + (players.length ? '' : esc(name)) + '"></div>' +
    '<div><label>Favorite color</label><div class="colors">' + swatches + '</div></div>' +
    '<button class="go" id="join">Join</button>';
  Array.prototype.forEach.call(document.querySelectorAll('.colors button'), function (b) {
    b.onclick = function () {
      color = parseInt(b.getAttribute('data-i'), 10);
      Array.prototype.forEach.call(document.querySelectorAll('.colors button'), function (o) { o.className = o === b ? 'on' : ''; });
    };
  });
  Array.prototype.forEach.call(document.querySelectorAll('.pick'), function (b) {
    b.onclick = function () { join({ profile: b.getAttribute('data-id') }); };
  });
  document.getElementById('join').onclick = function () {
    var picked = document.getElementById('name').value.trim();
    if (!picked) { document.getElementById('name').focus(); return; }
    localStorage.setItem('trivia-name', picked);
    localStorage.setItem('trivia-color', String(color));
    join({ name: picked, color: color });
  };
}

function join(body) {
  Array.prototype.forEach.call(document.querySelectorAll('button'), function (b) { b.disabled = true; });
  body.id = id || '';
  post('/api/join', body).then(function (r) {
    if (!r.id) {
      return fetch('/api/players?pin=' + encodeURIComponent(pin), { cache: 'no-store' }).then(function (x) { return x.json(); })
        .then(function (x) { joinForm(x.players || [], 'Someone else is playing as that name. Pick another.'); });
    }
    id = r.id;
    localStorage.setItem(idKey, id);
    localStorage.setItem('trivia-profile', r.profile);
    shown = '';
    poll();
  });
}

function render(v) {
  var key = JSON.stringify(v);
  if (key === shown) return;
  // The join form stays put while someone types.
  if (v.phase === 'join' && shown.indexOf('"join"') >= 0) return;
  shown = key;
  if (v.phase === 'join') return showJoin();
  var html = header(v);
  if (v.phase === 'lobby') {
    html += '<div class="big"><h1>You\'re in!</h1><p class="soft">Watch the TV. The game starts when everyone has joined.</p><p class="soft note">' + v.players + (v.players === 1 ? ' player' : ' players') + ' so far</p></div>';
  } else if (v.phase === 'question') {
    html += '<p class="soft note">Question ' + v.round + ' of ' + v.total + ' · read it on the TV</p><div class="answers' + (v.choices.length === 2 ? ' two' : '') + '">' +
      v.choices.map(function (c, i) {
        return '<button data-i="' + i + '" style="background:' + ANSWER_COLORS[i] + '">' + shape(i) + '<span>' + esc(c) + '</span></button>';
      }).join('') + '</div>';
  } else if (v.phase === 'answered') {
    html += '<div class="big"><div class="shape" style="background:' + ANSWER_COLORS[v.mine] + '">' + shape(v.mine) + '</div><h1>Locked in</h1><p class="soft">' + esc(v.choices[v.mine]) + '</p><p class="soft note">Waiting for everyone else…</p></div>';
  } else if (v.phase === 'reveal') {
    var right = v.mine === v.correct;
    html += '<div class="big">' + (right
      ? '<h1>Right!</h1><div class="points">+' + v.points.toLocaleString() + '</div>'
      : '<h1>' + (v.mine < 0 ? 'Out of time' : 'Not this time') + '</h1><p class="soft">The answer was</p><div class="shape" style="background:' + ANSWER_COLORS[v.correct] + '">' + shape(v.correct) + '</div><p>' + esc(v.choices[v.correct]) + '</p>') +
      '<p class="soft note">You\'re ' + ordinal(v.place) + ' of ' + v.players + '</p></div>';
  } else if (v.phase === 'final') {
    html += '<div class="big"><p class="soft">Final scores are on the TV</p><h1>You came ' + ordinal(v.place) + '!</h1><div class="points">' + v.score.toLocaleString() + '</div><p class="soft">' + v.right + ' of ' + v.total + ' right</p></div>';
  }
  app.innerHTML = html;
  Array.prototype.forEach.call(document.querySelectorAll('.answers button'), function (b) {
    b.onclick = function () {
      var choice = parseInt(b.getAttribute('data-i'), 10);
      post('/api/answer', { id: id, round: v.round, choice: choice });
      render(Object.assign({}, v, { phase: 'answered', mine: choice }));
    };
  });
}

function post(path, body) {
  body.pin = pin;
  return fetch(path, { method: 'POST', body: JSON.stringify(body) }).then(function (r) { return r.json(); });
}

var timer = null;
function poll() {
  clearTimeout(timer);
  fetch('/api/state?pin=' + encodeURIComponent(pin) + '&id=' + encodeURIComponent(id || ''), { cache: 'no-store' })
    .then(function (r) { if (r.status === 403) { showCode(); throw new Error('code'); } return r.json(); })
    .then(function (v) { render(v); timer = setTimeout(poll, 600); })
    .catch(function (e) { if (e.message !== 'code') timer = setTimeout(poll, 2000); });
}

if (!pin) showCode(); else poll();
</script>
</body>
</html>
"""
