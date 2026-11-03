import { test, expect } from '@playwright/test';

const ADMIN_USER = process.env.E2E_ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'test-password';

test.beforeEach(async ({ page }) => {
  await page.goto('/admin/login');
  await page.fill('input[name="username"]', ADMIN_USER);
  await page.fill('input[name="password"]', ADMIN_PASSWORD);
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/admin\/dashboard/);
});

test.describe('内容生命周期', () => {
  test('博客:创建草稿 → 公开不可见 → 发布 → 公开可见 → 撤回 → 回收站 → 恢复', async ({ page }) => {
    const title = `E2E 测试博客 ${Date.now()}`;

    // 创建草稿
    await page.goto('/admin/contents/new');
    await page.check('input[name="type"][value="BLOG"]');
    await page.click('button:has-text("创建草稿")');
    await expect(page).toHaveURL(/\/admin\/contents\/[a-f0-9-]+\/edit/);
    await page.fill('#title', title);
    await page.locator('#quill-editor .ql-editor').fill('这是 E2E 测试正文。');
    await page.click('button:has-text("保存草稿")');
    await expect(page.locator('.flash-ok')).toContainText('已保存');

    // 草稿状态:公开页 404
    const editUrl = page.url();
    const slugInput = page.locator('#slug');
    const slug = await slugInput.inputValue();
    const anon = await page.context().browser()!.newContext();
    const anonPage = await anon.newPage();
    const resp = await anonPage.goto(`/blog/${slug}`);
    expect(resp?.status()).toBe(404);

    // 发布
    await page.goto(editUrl);
    page.once('dialog', (d) => d.accept());
    await page.click('button:has-text("发布")');
    await expect(page.locator('.flash-ok')).toContainText('已发布');

    // 公开可见
    await anonPage.goto(`/blog/${slug}`);
    await expect(anonPage.locator('h1')).toContainText(title);

    // 撤回
    await page.goto(editUrl);
    page.once('dialog', (d) => d.accept());
    await page.click('button:has-text("撤回发布")');
    const resp2 = await anonPage.goto(`/blog/${slug}`);
    expect(resp2?.status()).toBe(404);

    // 回收站 → 恢复
    await page.goto(editUrl);
    page.once('dialog', (d) => d.accept());
    await page.click('button:has-text("放入回收站")');
    await page.goto('/admin/trash');
    await expect(page.locator('.admin-table')).toContainText(title);
    page.once('dialog', (d) => d.accept());
    await page.click('button:has-text("恢复为草稿")');
    await expect(page.locator('.flash-ok')).toContainText('已恢复');

    await anon.close();
  });

  test('搜索只命中已发布内容', async ({ page }) => {
    const marker = `e2esearch${Date.now()}`;
    await page.goto('/admin/contents/new');
    await page.check('input[name="type"][value="BLOG"]');
    await page.click('button:has-text("创建草稿")');
    await page.fill('#title', `搜索夹具 ${marker}`);
    await page.locator('#quill-editor .ql-editor').fill('搜索夹具正文');
    await page.click('button:has-text("保存草稿")');

    // 草稿不可被搜索到
    await page.goto(`/search?q=${marker}`);
    await expect(page.locator('body')).not.toContainText(marker);

    // 发布后可搜索
    page.once('dialog', (d) => d.accept());
    await page.click('button:has-text("发布")');
    await page.goto(`/search?q=${marker}`);
    await expect(page.locator('body')).toContainText(marker);
  });
});
