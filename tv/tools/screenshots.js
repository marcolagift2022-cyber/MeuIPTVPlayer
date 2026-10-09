// Tira capturas de tela 1920x1080 do app de TV usando a lista de demonstração (loja/demo.m3u).
// Usado pelo GitHub Actions (workflow "Capturas de tela da TV").
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const DEMO = 'https://raw.githubusercontent.com/marcolagift2022-cyber/MeuIPTVPlayer/main/loja/demo.m3u';
const page_url = 'file://' + path.resolve(__dirname, '../webapp/index.html');
const out = path.resolve(process.argv[2] || 'screens');
fs.mkdirSync(out, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome',
    headless: 'new',
    args: ['--no-sandbox', '--allow-file-access-from-files', '--autoplay-policy=no-user-gesture-required', '--window-size=1920,1080'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1920, height: 1080 });
  page.on('console', (m) => console.log('[app]', m.text()));
  page.on('pageerror', (e) => console.log('[erro]', e.message));

  // 1) Login (aba Lista M3U com o link de demonstração)
  await page.goto(page_url);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await sleep(800);
  await page.click('#tab-m3u');
  await page.type('#in-m3u', DEMO);
  await page.focus('#btn-login');
  await sleep(500);
  await page.screenshot({ path: path.join(out, '1-login.png') });

  // 2) Menu
  await page.click('#btn-login');
  await page.waitForSelector('#screen-home:not(.hidden)', { timeout: 30000 });
  await sleep(1000);
  await page.focus('#tile-live');
  await sleep(300);
  await page.screenshot({ path: path.join(out, '2-menu.png') });

  // 3) Filmes (categorias + grade)
  await page.click('#tile-movie');
  await page.waitForSelector('#grid .card', { timeout: 30000 });
  await sleep(1500);
  await page.focus('#grid .card');
  await sleep(400);
  await page.screenshot({ path: path.join(out, '3-filmes.png') });

  // 4) TV ao vivo
  await page.keyboard.press('Escape');
  await sleep(600);
  await page.click('#tile-live');
  await page.waitForSelector('#grid .card', { timeout: 30000 });
  await sleep(1500);
  await page.focus('#grid .card');
  await sleep(400);
  await page.screenshot({ path: path.join(out, '4-ao-vivo.png') });

  // 5) Busca
  await page.keyboard.press('Escape');
  await sleep(600);
  await page.click('#btn-search');
  await sleep(2500);
  await page.type('#search-input', 'Bunny');
  await sleep(1500);
  await page.screenshot({ path: path.join(out, '5-busca.png') });

  // 6) Player (Big Buck Bunny, com as informações na tela)
  await page.keyboard.press('Escape');
  await sleep(600);
  await page.click('#tile-movie');
  await page.waitForSelector('#grid .card', { timeout: 30000 });
  await sleep(800);
  await page.click('#grid .card');
  await sleep(9000);
  await page.keyboard.press('ArrowRight');
  await sleep(1200);
  await page.screenshot({ path: path.join(out, '6-player.png') });

  await browser.close();
  console.log('Capturas salvas em', out, fs.readdirSync(out));
})().catch((e) => { console.error(e); process.exit(1); });
