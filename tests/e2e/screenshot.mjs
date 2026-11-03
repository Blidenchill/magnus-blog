// 截图工具：本地 UI 走查用，不参与 e2e 断言
// 用法: node screenshot.mjs <输出目录> [路径...]   例: node screenshot.mjs /tmp/shots / /photos /blog
import { chromium } from '@playwright/test';

const base = process.env.BASE_URL || 'http://localhost:8080';
const outDir = process.argv[2] || '/tmp/shots';
const paths = process.argv.length > 3 ? process.argv.slice(3) : ['/'];

const browser = await chromium.launch();
for (const p of paths) {
    const name = p === '/' ? 'home' : p.replaceAll('/', '_').replace(/^_/, '');
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(base + p, { waitUntil: 'networkidle' });
    await page.screenshot({ path: `${outDir}/${name}-desktop.png`, fullPage: true });
    await page.close();

    const mp = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await mp.goto(base + p, { waitUntil: 'networkidle' });
    await mp.screenshot({ path: `${outDir}/${name}-mobile.png`, fullPage: true });
    await mp.close();
    console.log('shot:', p);
}
await browser.close();
