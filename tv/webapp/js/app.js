/*
 * X BR TOP CINE — versão para TV (LG webOS / Samsung Tizen)
 * Reprodutor genérico: o usuário informa o próprio servidor Xtream Codes ou lista M3U.
 * Escrito em JavaScript "antigo" (ES5) para funcionar também em TVs de 2016+.
 */
(function () {
  'use strict';

  // ======================================================================
  // Utilidades
  // ======================================================================
  function $(id) { return document.getElementById(id); }
  function show(el) { el.className = el.className.replace(/\s*hidden/g, ''); }
  function hide(el) { if (!/\bhidden\b/.test(el.className)) el.className += ' hidden'; }
  function hasClass(el, c) { return new RegExp('\\b' + c + '\\b').test(el.className); }
  function addClass(el, c) { if (!hasClass(el, c)) el.className += ' ' + c; }
  function removeClass(el, c) { el.className = el.className.replace(new RegExp('\\s*\\b' + c + '\\b', 'g'), ''); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  function str(v) { return v == null ? '' : String(v); }
  function enc(s) { return encodeURIComponent(s); }

  var toastTimer = null;
  function toast(msg) {
    var t = $('toast');
    t.textContent = msg;
    show(t);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { hide(t); }, 3200);
  }

  function store(key, value) {
    try {
      if (value === undefined) return JSON.parse(localStorage.getItem(key));
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) { return null; }
  }

  // ======================================================================
  // Rede
  // ======================================================================
  function httpGet(url, cb) {
    var xhr = new XMLHttpRequest();
    var done = false;
    xhr.open('GET', url, true);
    xhr.timeout = 30000;
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4 || done) return;
      done = true;
      if (xhr.status >= 200 && xhr.status < 300) cb(null, xhr.responseText);
      else if (xhr.status === 0) cb('Não foi possível conectar ao servidor. Confira o endereço e a internet.');
      else cb('O servidor respondeu com erro ' + xhr.status + '.');
    };
    xhr.ontimeout = function () { if (!done) { done = true; cb('O servidor demorou demais para responder.'); } };
    xhr.send();
  }

  function httpJSON(url, cb) {
    httpGet(url, function (err, text) {
      if (err) return cb(err);
      var data;
      try { data = JSON.parse(text); } catch (e) { return cb('O servidor respondeu em um formato inesperado.'); }
      cb(null, data);
    });
  }

  function asArray(d) { return Object.prototype.toString.call(d) === '[object Array]' ? d : []; }

  // ======================================================================
  // Conta e dados (Xtream Codes / M3U)
  // ======================================================================
  var account = store('xbr_account');
  var cache = {};
  var m3uItems = null;

  function normalizeUrl(s) {
    s = (s || '').replace(/^\s+|\s+$/g, '');
    if (!s) return s;
    if (!/^https?:\/\//i.test(s)) s = 'http://' + s;
    return s.replace(/\/+$/, '');
  }

  function api(action, extra) {
    var url = account.server + '/player_api.php?username=' + enc(account.user) + '&password=' + enc(account.pass);
    if (action) url += '&action=' + action + (extra || '');
    return url;
  }

  function xtreamLogin(acc, cb) {
    account = acc;
    httpJSON(api(), function (err, data) {
      if (err) return cb(err);
      var info = data && data.user_info;
      if (!info || String(info.auth) !== '1') return cb('Usuário ou senha incorretos.');
      if (info.status && String(info.status).toLowerCase() !== 'active') return cb('Sua conta está com status: ' + info.status);
      acc.exp = parseInt(info.exp_date, 10) || 0;
      // Formato dos canais ao vivo: HLS (.m3u8) quando o painel permite; senão MPEG-TS
      var fmts = asArray(info.allowed_output_formats);
      acc.fmt = !fmts.length || fmts.indexOf('m3u8') >= 0 ? 'm3u8' : 'ts';
      cb(null);
    });
  }

  function loadM3u(cb) {
    if (m3uItems) return cb(null, m3uItems);
    httpGet(account.m3u, function (err, text) {
      if (err) return cb(err);
      m3uItems = parseM3u(text);
      cb(null, m3uItems);
    });
  }

  function parseM3u(text) {
    var lines = text.split(/\r?\n/), out = [], name = null, logo = null, group = 'Sem categoria';
    for (var i = 0; i < lines.length; i++) {
      var l = lines[i].replace(/^\s+|\s+$/g, '');
      if (/^#EXTINF/i.test(l)) {
        var m = /tvg-logo="([^"]*)"/i.exec(l); logo = m && m[1] ? m[1] : null;
        m = /group-title="([^"]*)"/i.exec(l); group = m && m[1] ? m[1] : 'Sem categoria';
        name = titleOf(l);
        if (!name) { m = /tvg-name="([^"]*)"/i.exec(l); name = m && m[1] ? m[1] : 'Sem nome'; }
      } else if (l && l.charAt(0) !== '#' && name !== null) {
        out.push({ id: l, name: name, icon: logo, kind: kindOf(l), url: l, cat: group });
        name = null;
      }
    }
    return out;
  }

  function titleOf(line) {
    var q = false;
    for (var i = 0; i < line.length; i++) {
      var c = line.charAt(i);
      if (c === '"') q = !q;
      else if (c === ',' && !q) return line.substring(i + 1).replace(/^\s+|\s+$/g, '');
    }
    return '';
  }

  function kindOf(url) {
    var u = url.toLowerCase();
    if (u.indexOf('/movie/') >= 0 || /\.(mp4|mkv|avi)$/.test(u)) return 'movie';
    if (u.indexOf('/series/') >= 0) return 'series';
    return 'live';
  }

  function getCategories(kind, cb) {
    var key = 'cat|' + kind;
    if (cache[key]) return cb(null, cache[key]);
    var finish = function (cats) {
      cats.unshift({ id: 'all', name: 'Todos' });
      cache[key] = cats;
      cb(null, cats);
    };
    if (account.type === 'm3u') {
      return loadM3u(function (err, items) {
        if (err) return cb(err);
        var seen = {}, cats = [];
        for (var i = 0; i < items.length; i++) {
          if (items[i].kind === kind && !seen[items[i].cat]) { seen[items[i].cat] = 1; cats.push({ id: items[i].cat, name: items[i].cat }); }
        }
        finish(cats);
      });
    }
    var action = kind === 'live' ? 'get_live_categories' : kind === 'movie' ? 'get_vod_categories' : 'get_series_categories';
    httpJSON(api(action), function (err, data) {
      if (err) return cb(err);
      var arr = asArray(data), cats = [];
      for (var i = 0; i < arr.length; i++) cats.push({ id: str(arr[i].category_id), name: str(arr[i].category_name) });
      finish(cats);
    });
  }

  function getItems(kind, catId, cb) {
    var key = 'it|' + kind + '|' + catId;
    if (cache[key]) return cb(null, cache[key]);
    if (account.type === 'm3u') {
      return loadM3u(function (err, items) {
        if (err) return cb(err);
        var out = [];
        for (var i = 0; i < items.length; i++) {
          if (items[i].kind === kind && (catId === 'all' || items[i].cat === catId)) out.push(items[i]);
        }
        cache[key] = out;
        cb(null, out);
      });
    }
    var action = kind === 'live' ? 'get_live_streams' : kind === 'movie' ? 'get_vod_streams' : 'get_series';
    var extra = catId === 'all' ? '' : '&category_id=' + enc(catId);
    httpJSON(api(action, extra), function (err, data) {
      if (err) return cb(err);
      var arr = asArray(data), out = [], base = account.server, u = account.user, p = account.pass;
      for (var i = 0; i < arr.length; i++) {
        var o = arr[i];
        if (kind === 'live') {
          out.push({ id: str(o.stream_id), name: str(o.name), icon: o.stream_icon || null, kind: 'live', cat: str(o.category_id),
            url: base + '/live/' + u + '/' + p + '/' + o.stream_id + '.' + (account.fmt || 'm3u8') });
        } else if (kind === 'movie') {
          out.push({ id: str(o.stream_id), name: str(o.name), icon: o.stream_icon || null, kind: 'movie', cat: str(o.category_id),
            url: base + '/movie/' + u + '/' + p + '/' + o.stream_id + '.' + (o.container_extension || 'mp4') });
        } else {
          out.push({ id: str(o.series_id), name: str(o.name), icon: o.cover || null, kind: 'series', cat: str(o.category_id), url: null });
        }
      }
      cache[key] = out;
      cb(null, out);
    });
  }

  function getEpisodes(seriesId, cb) {
    httpJSON(api('get_series_info', '&series_id=' + enc(seriesId)), function (err, data) {
      if (err) return cb(err);
      var eps = data && data.episodes, seasons = [], out = [];
      if (eps && typeof eps === 'object') {
        for (var k in eps) if (eps.hasOwnProperty(k)) seasons.push({ key: k, list: asArray(eps[k]) });
      }
      seasons.sort(function (a, b) { return (parseInt(a.key, 10) || 0) - (parseInt(b.key, 10) || 0); });
      for (var s = 0; s < seasons.length; s++) {
        for (var i = 0; i < seasons[s].list.length; i++) {
          var e = seasons[s].list[i], num = str(e.episode_num) || String(i + 1);
          out.push({
            id: str(e.id), kind: 'movie', icon: e.info && e.info.movie_image || null,
            name: 'T' + (str(e.season) || seasons[s].key) + ' · E' + num + ' — ' + (str(e.title) || 'Episódio ' + num),
            url: account.server + '/series/' + account.user + '/' + account.pass + '/' + e.id + '.' + (e.container_extension || 'mp4')
          });
        }
      }
      cb(null, out);
    });
  }

  function decodeB64(s) {
    try { return decodeURIComponent(escape(atob(s))); } catch (e) { return s; }
  }

  function nowNext(item, cb) {
    if (account.type !== 'xtream' || item.kind !== 'live') return cb('');
    httpJSON(api('get_short_epg', '&stream_id=' + enc(item.id) + '&limit=2'), function (err, data) {
      if (err || !data || !data.epg_listings) return cb('');
      var l = asArray(data.epg_listings), lines = [];
      for (var i = 0; i < l.length; i++) {
        var start = str(l[i].start).substring(11, 16);
        lines.push((i === 0 ? 'Agora  ' : 'Depois  ') + start + '  ' + decodeB64(str(l[i].title)));
      }
      cb(lines.join('\n'));
    });
  }

  // ======================================================================
  // Favoritos
  // ======================================================================
  function favorites() { return store('xbr_favs') || []; }
  function favKey(it) { return it.kind + ':' + it.id; }
  function isFav(it) {
    var f = favorites();
    for (var i = 0; i < f.length; i++) if (favKey(f[i]) === favKey(it)) return true;
    return false;
  }
  function toggleFav(it) {
    var f = favorites(), out = [], removed = false;
    for (var i = 0; i < f.length; i++) { if (favKey(f[i]) === favKey(it)) removed = true; else out.push(f[i]); }
    if (!removed) out.unshift(it);
    store('xbr_favs', out);
    toast(removed ? 'Removido dos favoritos' : 'Adicionado aos favoritos');
    return !removed;
  }

  // ======================================================================
  // Navegação por controle remoto (setas)
  // ======================================================================
  function visible(el) { return el.offsetParent !== null || el.getClientRects().length > 0; }

  function focusables() {
    var screen = $('screen-' + current);
    var all = screen.querySelectorAll('.focusable'), out = [];
    for (var i = 0; i < all.length; i++) if (visible(all[i])) out.push(all[i]);
    return out;
  }

  function focusEl(el) {
    if (!el) return;
    el.focus();
    ensureVisible(el);
  }

  /** Rola a lista para o item focado ficar visível. */
  function ensureVisible(el) {
    var p = el.parentNode;
    while (p && p !== document.body) {
      if (hasClass(p, 'scroll') || hasClass(p, 'cat-list') || hasClass(p, 'ep-list')) {
        var top = el.offsetTop - p.offsetTop, bottom = top + el.offsetHeight;
        if (top < p.scrollTop + 10) p.scrollTop = Math.max(0, top - 20);
        else if (bottom > p.scrollTop + p.clientHeight - 10) p.scrollTop = bottom - p.clientHeight + 30;
        return;
      }
      p = p.parentNode;
    }
  }

  function moveFocus(dir) {
    var items = focusables();
    if (!items.length) return;
    var cur = document.activeElement;
    if (items.indexOf(cur) < 0) { focusEl(items[0]); return; }
    var r = cur.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    var best = null, bestScore = Infinity;
    for (var i = 0; i < items.length; i++) {
      var el = items[i];
      if (el === cur) continue;
      var b = el.getBoundingClientRect(), x = b.left + b.width / 2, y = b.top + b.height / 2;
      var dx = x - cx, dy = y - cy, primary, secondary;
      if (dir === 'left') { if (b.right > r.left + 4) continue; primary = -dx; secondary = Math.abs(dy); }
      else if (dir === 'right') { if (b.left < r.right - 4) continue; primary = dx; secondary = Math.abs(dy); }
      else if (dir === 'up') { if (b.bottom > r.top + 4) continue; primary = -dy; secondary = Math.abs(dx); }
      else { if (b.top < r.bottom - 4) continue; primary = dy; secondary = Math.abs(dx); }
      var score = primary + secondary * 2.5;
      if (score < bestScore) { bestScore = score; best = el; }
    }
    if (best) focusEl(best);
  }

  // ======================================================================
  // Telas
  // ======================================================================
  var current = 'login';
  var history = [];
  var screenFocus = {};

  function showScreen(name, push) {
    if (push && current !== name) {
      screenFocus[current] = document.activeElement;
      history.push(current);
    }
    var all = ['login', 'home', 'list', 'search', 'episodes', 'player'];
    for (var i = 0; i < all.length; i++) {
      if (all[i] === name) show($('screen-' + all[i])); else hide($('screen-' + all[i]));
    }
    $('bg').style.display = name === 'player' ? 'none' : 'block';
    current = name;
  }

  function goBack() {
    if (current === 'player') stopPlayer();
    if (!history.length) {
      if (current === 'home' || current === 'login') exitApp();
      return;
    }
    var prev = history.pop();
    showScreen(prev, false);
    if (prev === 'list' && listKind === 'fav') selectCategory(currentCat, true);
    else if (prev === 'list' || prev === 'search') refreshStars();
    var f = screenFocus[prev];
    if (f && visible(f)) focusEl(f); else focusEl(focusables()[0]);
  }

  function exitApp() {
    try { if (window.tizen) { tizen.application.getCurrentApplication().exit(); return; } } catch (e) {}
    try { if (window.webOS && webOS.platformBack) { webOS.platformBack(); return; } } catch (e) {}
    try { window.close(); } catch (e) {}
  }

  // ---------------------------------------------------------------- Login
  var loginType = 'xtream';

  function setLoginType(t) {
    loginType = t;
    if (t === 'xtream') { addClass($('tab-xtream'), 'active'); removeClass($('tab-m3u'), 'active'); show($('xtream-fields')); hide($('m3u-fields')); }
    else { addClass($('tab-m3u'), 'active'); removeClass($('tab-xtream'), 'active'); hide($('xtream-fields')); show($('m3u-fields')); }
    $('login-error').textContent = '';
  }

  function doLogin() {
    var err = $('login-error');
    err.textContent = '';
    var acc;
    if (loginType === 'xtream') {
      acc = { type: 'xtream', server: normalizeUrl($('in-server').value), user: $('in-user').value.replace(/^\s+|\s+$/g, ''), pass: $('in-pass').value.replace(/^\s+|\s+$/g, '') };
      if (!acc.server || !acc.user || !acc.pass) { err.textContent = 'Preencha servidor, usuário e senha.'; return; }
    } else {
      acc = { type: 'm3u', m3u: normalizeUrl($('in-m3u').value) };
      if (!acc.m3u) { err.textContent = 'Cole o link da lista M3U.'; return; }
    }
    show($('login-loading'));
    var done = function (e) {
      hide($('login-loading'));
      if (e) { err.textContent = e; account = store('xbr_account'); return; }
      account = acc;
      store('xbr_account', acc);
      cache = {};
      openHome();
    };
    if (acc.type === 'xtream') xtreamLogin(acc, done);
    else {
      account = acc; m3uItems = null;
      loadM3u(function (e, items) { done(e || (items.length ? null : 'A lista está vazia ou não é uma lista M3U.')); });
    }
  }

  // ---------------------------------------------------------------- Menu
  function openHome() {
    history = [];
    showScreen('home', false);
    $('home-user').textContent = account.type === 'xtream' ? 'Usuário: ' + account.user : 'Lista M3U';
    var exp = $('home-exp');
    removeClass(exp, 'warn');
    exp.textContent = '';
    if (account.exp) {
      var d = new Date(account.exp * 1000), days = Math.floor((d.getTime() - Date.now()) / 86400000);
      var txt = pad2(d.getDate()) + '/' + pad2(d.getMonth() + 1) + '/' + d.getFullYear();
      exp.textContent = days < 0 ? 'Assinatura vencida em ' + txt : 'Vencimento: ' + txt + ' (' + days + ' dias)';
      if (days <= 5) addClass(exp, 'warn');
    }
    tickClock();
    focusEl($('tile-live'));
  }

  function tickClock() {
    var d = new Date();
    $('clock').textContent = pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + '  ·  ' + pad2(d.getDate()) + '/' + pad2(d.getMonth() + 1) + '/' + d.getFullYear();
  }

  var logoutArmed = 0;
  function logout() {
    // Confirmação: apertar "Sair" duas vezes em até 4 segundos
    if (Date.now() - logoutArmed > 4000) {
      logoutArmed = Date.now();
      toast('Aperte "Sair" de novo para confirmar.');
      return;
    }
    logoutArmed = 0;
    try { localStorage.removeItem('xbr_account'); } catch (e) {}
    account = null; cache = {}; m3uItems = null;
    history = [];
    showScreen('login', false);
    focusEl($('in-server'));
  }

  // ---------------------------------------------------------------- Grade de cards
  var BATCH = 60;

  /** Desenha os cards aos poucos (60 por vez) para não pesar em TV fraca. */
  function Grid(container, onOpen) {
    this.el = container;
    this.items = [];
    this.rendered = 0;
    this.onOpen = onOpen;
  }
  Grid.prototype.set = function (items) {
    this.items = items;
    this.rendered = 0;
    this.el.innerHTML = '';
    this.el.scrollTop = 0;
    this.more();
  };
  Grid.prototype.more = function () {
    var self = this, end = Math.min(this.items.length, this.rendered + BATCH);
    var frag = document.createDocumentFragment();
    for (var i = this.rendered; i < end; i++) frag.appendChild(this.card(this.items[i], i));
    this.el.appendChild(frag);
    this.rendered = end;
  };
  Grid.prototype.card = function (it, i) {
    var self = this, b = document.createElement('button');
    b.className = 'focusable card' + (it.kind !== 'live' ? ' poster' : '');
    b.setAttribute('data-i', i);
    b.innerHTML = '<div class="thumb"' + (it.icon ? ' style="background-image:url(\'' + esc(it.icon).replace(/\(/g, '%28').replace(/\)/g, '%29') + '\')"' : '') + '></div>' +
      '<div class="name">' + esc(it.name) + '</div>' + (isFav(it) ? '<span class="fav">★</span>' : '');
    b.onclick = function () { self.onOpen(self.items[i], i, self.items); };
    b.onfocus = function () { if (i >= self.rendered - 12 && self.rendered < self.items.length) self.more(); };
    b.favItem = it;
    return b;
  };

  function refreshStars() {
    var cards = document.querySelectorAll('.card');
    for (var i = 0; i < cards.length; i++) {
      var c = cards[i], star = c.querySelector('.fav'), fav = c.favItem && isFav(c.favItem);
      if (fav && !star) { var s = document.createElement('span'); s.className = 'fav'; s.textContent = '★'; c.appendChild(s); }
      else if (!fav && star) c.removeChild(star);
    }
  }

  function openItem(it, i, list) {
    if (!it.url) { openEpisodes(it); return; }
    if (it.kind === 'live') {
      var channels = [], idx = 0;
      for (var k = 0; k < list.length; k++) if (list[k].url) { if (list[k] === it) idx = channels.length; channels.push(list[k]); }
      playQueue(channels, idx);
    } else {
      playQueue([it], 0);
    }
  }

  // ---------------------------------------------------------------- Lista
  var listKind = 'live', categories = [], currentCat = 0, listItems = [];
  var listGrid = new Grid($('grid'), openItem);

  function openList(kind) {
    listKind = kind;
    $('list-title').textContent = { live: 'TV ao vivo', movie: 'Filmes', series: 'Séries', fav: 'Favoritos' }[kind];
    $('list-search').value = '';
    showScreen('list', true);
    $('cat-list').innerHTML = '';
    listGrid.set([]);
    hide($('list-msg'));
    if (kind === 'fav') {
      categories = [{ id: 'live', name: 'TV ao vivo' }, { id: 'movie', name: 'Filmes' }, { id: 'series', name: 'Séries' }];
      renderCategories();
      selectCategory(0, false, true);
      return;
    }
    show($('list-loading'));
    getCategories(kind, function (err, cats) {
      hide($('list-loading'));
      if (err) { listMsg(err); return; }
      categories = cats;
      renderCategories();
      selectCategory(cats.length > 1 ? 1 : 0, false, true);
    });
  }

  function renderCategories() {
    var box = $('cat-list'), frag = document.createDocumentFragment();
    box.innerHTML = '';
    for (var i = 0; i < categories.length; i++) {
      (function (i) {
        var b = document.createElement('button');
        b.className = 'focusable cat';
        b.textContent = categories[i].name;
        b.onclick = function () { selectCategory(i, false, false); };
        frag.appendChild(b);
      })(i);
    }
    box.appendChild(frag);
  }

  function selectCategory(i, keepFocus, focusCat) {
    currentCat = i;
    var cats = $('cat-list').children;
    for (var k = 0; k < cats.length; k++) { if (k === i) addClass(cats[k], 'selected'); else removeClass(cats[k], 'selected'); }
    if (focusCat && cats[i]) focusEl(cats[i]);
    hide($('list-msg'));
    if (listKind === 'fav') {
      var f = favorites(), out = [];
      for (var j = 0; j < f.length; j++) if (f[j].kind === categories[i].id) out.push(f[j]);
      setListItems(out);
      return;
    }
    show($('list-loading'));
    listGrid.set([]);
    var catId = categories[i].id;
    getItems(listKind, catId, function (err, items) {
      if (currentCat !== i) return; // o usuário já trocou de categoria
      hide($('list-loading'));
      if (err) { listMsg(err); return; }
      setListItems(items);
    });
  }

  function setListItems(items) {
    listItems = items;
    applyListFilter();
  }

  function applyListFilter() {
    var q = $('list-search').value.toLowerCase().replace(/^\s+|\s+$/g, ''), out = listItems;
    if (q) {
      out = [];
      for (var i = 0; i < listItems.length; i++) if (listItems[i].name.toLowerCase().indexOf(q) >= 0) out.push(listItems[i]);
    }
    listGrid.set(out);
    if (!out.length) listMsg(listKind === 'fav' && !q ? 'Nenhum favorito aqui ainda.\nAperte o botão vermelho em um item para favoritar.' : 'Nada encontrado.');
    else hide($('list-msg'));
  }

  function listMsg(text) { var m = $('list-msg'); m.textContent = text; show(m); }

  // ---------------------------------------------------------------- Busca geral
  var searchKinds = [['live', 'Canais'], ['movie', 'Filmes'], ['series', 'Séries']];
  var searchCatalog = null, searchResults = null, searchTab = 0, searchTimer = null;
  var searchGrid = new Grid($('search-grid'), openItem);

  function openSearch() {
    showScreen('search', true);
    renderSearchTabs();
    focusEl($('search-input'));
    if (searchCatalog) return;
    show($('search-loading'));
    $('search-msg').textContent = 'Carregando o catálogo...';
    var pending = searchKinds.length, cat = {};
    for (var i = 0; i < searchKinds.length; i++) {
      (function (kind) {
        getItems(kind, 'all', function (err, items) {
          cat[kind] = err ? [] : items;
          if (--pending === 0) {
            searchCatalog = cat;
            hide($('search-loading'));
            runSearch();
          }
        });
      })(searchKinds[i][0]);
    }
  }

  function runSearch() {
    if (!searchCatalog) return;
    var q = $('search-input').value.toLowerCase().replace(/^\s+|\s+$/g, '');
    var msg = $('search-msg');
    if (q.length < 2) {
      searchResults = null;
      renderSearchTabs();
      searchGrid.set([]);
      msg.textContent = 'Digite pelo menos 2 letras para buscar.';
      show(msg);
      return;
    }
    searchResults = {};
    for (var i = 0; i < searchKinds.length; i++) {
      var k = searchKinds[i][0], all = searchCatalog[k] || [], found = [];
      for (var j = 0; j < all.length && found.length < 300; j++) if (all[j].name.toLowerCase().indexOf(q) >= 0) found.push(all[j]);
      searchResults[k] = found;
    }
    if (!searchResults[searchKinds[searchTab][0]].length) {
      for (var t = 0; t < searchKinds.length; t++) if (searchResults[searchKinds[t][0]].length) { searchTab = t; break; }
    }
    renderSearchTabs();
    showSearchTab();
  }

  function renderSearchTabs() {
    var box = $('search-tabs');
    box.innerHTML = '';
    for (var i = 0; i < searchKinds.length; i++) {
      (function (i) {
        var b = document.createElement('button');
        var n = searchResults ? ' (' + searchResults[searchKinds[i][0]].length + ')' : '';
        b.className = 'focusable tab' + (i === searchTab ? ' active' : '');
        b.textContent = searchKinds[i][1] + n;
        b.onclick = function () { searchTab = i; renderSearchTabs(); showSearchTab(); focusEl($('search-tabs').children[i]); };
        box.appendChild(b);
      })(i);
    }
  }

  function showSearchTab() {
    var list = searchResults ? searchResults[searchKinds[searchTab][0]] : [];
    searchGrid.set(list);
    var msg = $('search-msg');
    if (searchResults && !list.length) { msg.textContent = 'Nada encontrado em ' + searchKinds[searchTab][1].toLowerCase() + '.'; show(msg); }
    else hide(msg);
  }

  // ---------------------------------------------------------------- Episódios
  function openEpisodes(series) {
    if (account.type !== 'xtream') return;
    $('ep-title').textContent = series.name;
    $('ep-list').innerHTML = '';
    hide($('ep-msg'));
    show($('ep-loading'));
    showScreen('episodes', true);
    getEpisodes(series.id, function (err, eps) {
      hide($('ep-loading'));
      if (err || !eps.length) { $('ep-msg').textContent = err || 'Nenhum episódio encontrado.'; show($('ep-msg')); return; }
      var box = $('ep-list'), frag = document.createDocumentFragment();
      for (var i = 0; i < eps.length; i++) {
        (function (i) {
          var b = document.createElement('button');
          b.className = 'focusable ep';
          b.textContent = eps[i].name;
          b.onclick = function () { playQueue(eps, i); };
          frag.appendChild(b);
        })(i);
      }
      box.appendChild(frag);
      focusEl(box.children[0]);
    });
  }

  // ======================================================================
  // Player
  // ======================================================================
  var video = $('video'), hls = null, queue = [], qIndex = 0, retries = 0, osdTimer = null, epgSeq = 0;

  function playQueue(list, index) {
    queue = list;
    qIndex = index;
    showScreen('player', true);
    playCurrent();
  }

  function playCurrent() {
    var it = queue[qIndex];
    if (!it) return;
    retries = 0;
    startStream(it.url);
    showOsd(true);
  }

  function startStream(url) {
    destroyHls();
    show($('player-loading'));
    var isHls = /\.m3u8(\?|$)/i.test(url);
    var native = video.canPlayType('application/vnd.apple.mpegurl') || video.canPlayType('application/x-mpegURL');
    if (isHls && !native && window.Hls && Hls.isSupported()) {
      hls = new Hls({ maxBufferLength: 30, enableWorker: true });
      hls.loadSource(url);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, function () { tryPlay(); });
      hls.on(Hls.Events.ERROR, function (e, data) { if (data && data.fatal) onVideoError(); });
    } else {
      video.src = url;
      video.load();
      tryPlay();
    }
  }

  function tryPlay() {
    var p = video.play();
    if (p && p.catch) p.catch(function () {});
  }

  function destroyHls() {
    if (hls) { try { hls.destroy(); } catch (e) {} hls = null; }
  }

  function stopPlayer() {
    destroyHls();
    try { video.pause(); video.removeAttribute('src'); video.load(); } catch (e) {}
    hide($('osd')); hide($('osd-bar')); hide($('player-loading'));
    clearTimeout(osdTimer);
  }

  function isLive() { var it = queue[qIndex]; return it && it.kind === 'live'; }

  function onVideoError() {
    if (current !== 'player') return;
    if (retries < 3) {
      retries++;
      toast('Reconectando... (' + retries + '/3)');
      setTimeout(function () { if (current === 'player') startStream(queue[qIndex].url); }, 2000);
    } else {
      hide($('player-loading'));
      toast('Não foi possível reproduzir este conteúdo.');
    }
  }

  video.addEventListener('playing', function () { hide($('player-loading')); retries = 0; });
  video.addEventListener('waiting', function () { show($('player-loading')); });
  video.addEventListener('error', function () { if (!hls) onVideoError(); });
  video.addEventListener('ended', function () {
    if (!isLive() && qIndex < queue.length - 1) { qIndex++; playCurrent(); }
    else if (!isLive()) goBack();
  });
  video.addEventListener('timeupdate', function () {
    if (isLive() || hasClass($('osd-bar'), 'hidden')) return;
    updateBar();
  });

  function fmtTime(s) {
    s = Math.floor(s || 0);
    var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return (h ? h + ':' + pad2(m) : m) + ':' + pad2(sec);
  }

  function updateBar() {
    var d = video.duration || 0, t = video.currentTime || 0;
    $('osd-time').textContent = fmtTime(t);
    $('osd-dur').textContent = isFinite(d) ? fmtTime(d) : '--:--';
    $('osd-progress').style.width = (d > 0 && isFinite(d) ? Math.min(100, (t / d) * 100) : 0) + '%';
  }

  function showOsd(loadEpg) {
    var it = queue[qIndex];
    if (!it) return;
    var live = it.kind === 'live';
    $('osd-name').textContent = (live && queue.length > 1 ? (qIndex + 1) + '. ' : '') + it.name;
    $('osd-hint').textContent = live ? '▲▼ / CH+ CH−: trocar canal   ·   OK: pausar   ·   Voltar: sair'
      : '◀▶: voltar/avançar 10s   ·   OK: pausar   ·   Voltar: sair';
    if (loadEpg) {
      $('osd-epg').textContent = '';
      var seq = ++epgSeq;
      nowNext(it, function (text) { if (seq === epgSeq) $('osd-epg').textContent = text; });
    }
    show($('osd'));
    if (live) hide($('osd-bar')); else { show($('osd-bar')); updateBar(); }
    clearTimeout(osdTimer);
    osdTimer = setTimeout(function () { hide($('osd')); hide($('osd-bar')); }, 6000);
  }

  function zap(dir) {
    if (queue.length < 2) return;
    qIndex = (qIndex + dir + queue.length) % queue.length;
    playCurrent();
  }

  function seek(sec) {
    try { video.currentTime = Math.max(0, (video.currentTime || 0) + sec); } catch (e) {}
    showOsd(false);
  }

  function togglePause() {
    if (video.paused) tryPlay(); else video.pause();
    showOsd(false);
  }

  // ======================================================================
  // Teclas do controle
  // ======================================================================
  var KEY = {
    LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13,
    BACK_WEBOS: 461, BACK_TIZEN: 10009, ESC: 27, BACKSPACE: 8,
    RED: 403, CH_UP: 427, CH_DOWN: 428, PAGE_UP: 33, PAGE_DOWN: 34,
    PLAY: 415, PAUSE: 19, PLAY_PAUSE: 10252, STOP: 413, FF: 417, RW: 412
  };

  // Samsung: libera as teclas extras do controle
  try {
    if (window.tizen && tizen.tvinputdevice) {
      var keys = ['ColorF0Red', 'ChannelUp', 'ChannelDown', 'MediaPlay', 'MediaPause', 'MediaPlayPause', 'MediaStop', 'MediaFastForward', 'MediaRewind'];
      for (var k = 0; k < keys.length; k++) { try { tizen.tvinputdevice.registerKey(keys[k]); } catch (e) {} }
    }
  } catch (e) {}

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode, active = document.activeElement;
    var inInput = active && active.tagName === 'INPUT';

    // Voltar
    if (code === KEY.BACK_WEBOS || code === KEY.BACK_TIZEN || code === KEY.ESC || (code === KEY.BACKSPACE && !inInput)) {
      e.preventDefault();
      if (inInput) { active.blur(); focusEl(focusables()[0]); return; }
      goBack();
      return;
    }

    // Player
    if (current === 'player') {
      e.preventDefault();
      var live = isLive();
      if (code === KEY.ENTER || code === KEY.PLAY_PAUSE) togglePause();
      else if (code === KEY.PLAY) { tryPlay(); showOsd(false); }
      else if (code === KEY.PAUSE) { video.pause(); showOsd(false); }
      else if (code === KEY.STOP) goBack();
      else if (live && (code === KEY.UP || code === KEY.CH_UP || code === KEY.PAGE_UP)) zap(1);
      else if (live && (code === KEY.DOWN || code === KEY.CH_DOWN || code === KEY.PAGE_DOWN)) zap(-1);
      else if (!live && (code === KEY.RIGHT || code === KEY.FF)) seek(10);
      else if (!live && (code === KEY.LEFT || code === KEY.RW)) seek(-10);
      else showOsd(false);
      return;
    }

    // Favoritar (botão vermelho)
    if (code === KEY.RED && active && active.favItem) {
      e.preventDefault();
      toggleFav(active.favItem);
      if (current === 'list' && listKind === 'fav') selectCategory(currentCat, true); else refreshStars();
      return;
    }

    // Setas
    var dir = code === KEY.LEFT ? 'left' : code === KEY.RIGHT ? 'right' : code === KEY.UP ? 'up' : code === KEY.DOWN ? 'down' : null;
    if (dir) {
      if (inInput && (dir === 'left' || dir === 'right')) {
        var v = active.value || '', pos = active.selectionStart;
        var atEdge = dir === 'left' ? pos === 0 : pos === v.length;
        if (!atEdge) return; // deixa mover o cursor dentro do texto
      }
      e.preventDefault();
      moveFocus(dir);
      return;
    }

    if (code === KEY.ENTER && active && !inInput && active.click) {
      e.preventDefault();
      active.click();
    }
  });

  // ======================================================================
  // Ligações dos botões
  // ======================================================================
  $('tab-xtream').onclick = function () { setLoginType('xtream'); };
  $('tab-m3u').onclick = function () { setLoginType('m3u'); };
  $('btn-login').onclick = doLogin;
  $('tile-live').onclick = function () { openList('live'); };
  $('tile-movie').onclick = function () { openList('movie'); };
  $('tile-series').onclick = function () { openList('series'); };
  $('tile-fav').onclick = function () { openList('fav'); };
  $('btn-search').onclick = openSearch;
  $('btn-refresh').onclick = function () { cache = {}; m3uItems = null; searchCatalog = null; toast('Lista atualizada! Os conteúdos novos já vão aparecer.'); };
  $('btn-logout').onclick = logout;

  var filterTimer = null;
  $('list-search').oninput = function () { clearTimeout(filterTimer); filterTimer = setTimeout(applyListFilter, 300); };
  $('search-input').oninput = function () { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 350); };

  setInterval(function () { if (current === 'home') tickClock(); }, 15000);

  // ======================================================================
  // Início
  // ======================================================================
  if (account && (account.server || account.m3u)) {
    openHome();
    // Confere o login em segundo plano (atualiza vencimento)
    if (account.type === 'xtream') {
      var saved = account;
      xtreamLogin(saved, function (err) {
        account = saved;
        if (!err) { store('xbr_account', saved); if (current === 'home') openHome(); }
      });
    }
  } else {
    showScreen('login', false);
    focusEl($('in-server'));
  }
})();
