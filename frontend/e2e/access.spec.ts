import { expect, test } from '@playwright/test';

test('로그인부터 본인 요청 생성과 로그아웃까지 연결된다', async ({ page }) => {
  const reason = `브라우저 연결 검증 ${Date.now()}`;
  await page.goto('/');
  await page.getByLabel('아이디').fill('tester');
  await page.getByLabel('비밀번호').fill('1');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('heading', { name: '기준정보 목록' })).toBeVisible();
  await expect(page.getByText('EQ-CAM100')).toBeVisible();
  const sessionCookie = (await page.context().cookies()).find(cookie => cookie.name === 'JSESSIONID');
  expect(sessionCookie?.httpOnly).toBe(true);
  expect(sessionCookie?.sameSite).toBe('Lax');
  await page.getByRole('button', { name: '내 변경 요청', exact: true }).click();
  await page.getByLabel('변경 사유').fill(reason);
  const submitted = page.waitForRequest(request => request.url().endsWith('/api/requests') && request.method() === 'POST');
  await page.getByRole('button', { name: '요청 임시 저장' }).click();
  expect((await submitted).postDataJSON()).toEqual({ reason });
  await expect(page.getByRole('status')).toContainText('임시 저장했습니다');
  await expect(page.getByRole('region', { name: '변경 요청 상세' })).toContainText(reason);
  await page.screenshot({ path: 'test-results/requester-desktop.png', fullPage: true });
  await page.reload();
  await expect(page.getByText('tester', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '내 변경 요청', exact: true }).click();
  await expect(page.getByRole('cell', { name: reason, exact: true })).toBeVisible();
  const ownDraft = (await (await page.request.get('/api/requests/mine')).json()).find((row: { reason: string }) => row.reason === reason);
  await page.getByRole('button', { name: '로그아웃' }).click();
  await expect(page.getByLabel('아이디')).toBeVisible();
  await page.getByLabel('아이디').fill('admin');
  await page.getByLabel('비밀번호').fill('1');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('heading', { name: '기준정보 목록', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '내 변경 요청', exact: true })).toHaveCount(0);
  expect((await page.request.get(`/api/requests/${ownDraft.requestId}`)).status()).toBe(403);
});

test('승인자는 목록을 읽되 직접 API 호출로 임시 요청을 생성할 수 없다', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('아이디').fill('admin');
  await page.getByLabel('비밀번호').fill('1');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByText('EQ-CAM100')).toBeVisible();
  await expect(page.getByRole('button', { name: '내 변경 요청' })).toHaveCount(0);
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const response = await page.request.post('/api/requests', {
    headers: { [csrf.headerName]: csrf.token }, data: { reason: '권한 우회 시도' },
  });
  expect(response.status()).toBe(403);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/approver-mobile.png', fullPage: true });
});
