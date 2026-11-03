import { test, expect } from '@playwright/test';

const ADMIN_USER = process.env.E2E_ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'test-password';

test.describe('认证与访问控制', () => {
  test('匿名访问后台重定向到登录页', async ({ page }) => {
    await page.goto('/admin/contents');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('错误密码登录失败并停留在登录页', async ({ page }) => {
    await page.goto('/admin/login');
    await page.fill('input[name="username"]', ADMIN_USER);
    await page.fill('input[name="password"]', 'wrong-password');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/admin\/login\?error/);
    await expect(page.locator('.flash-error')).toContainText('账号或密码不正确');
  });

  test('正确密码登录后进入后台,退出后失效', async ({ page }) => {
    await page.goto('/admin/login');
    await page.fill('input[name="username"]', ADMIN_USER);
    await page.fill('input[name="password"]', ADMIN_PASSWORD);
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/admin\/dashboard/);

    await page.click('button:has-text("退出登录")');
    await expect(page).toHaveURL(/\/admin\/login/);

    // 退出后再访问后台应重新要求登录
    await page.goto('/admin/contents');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('公开页面不泄露后台入口', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('a[href^="/admin"]')).toHaveCount(0);
  });
});
