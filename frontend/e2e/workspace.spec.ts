import { expect, test } from '@playwright/test';

const header = 'operation,equipment_code,name,category,daily_fee_krw,active\n';
test('오류 수정부터 전후 비교와 저장 결과 복원까지 연결된다', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('아이디').fill('requester_a');
  await page.getByLabel('비밀번호').fill('LocalDemo!2026');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.getByRole('button', { name: '변경 요청 작성', exact: true }).click();
  await page.getByLabel('변경 사유').fill(`CSV 검증 확인 ${Date.now()}`);
  const created = page.waitForResponse(response => response.url().endsWith('/api/requests') && response.request().method() === 'POST');
  await page.getByRole('button', { name: '요청 임시 저장' }).click();
  const draft = await (await created).json();
  const base = `/api/requests/${draft.requestId}/revisions/${draft.revisionId}`;
  await page.getByLabel('CSV 파일 선택').setInputFiles({ name: 'invalid.csv', mimeType: 'text/csv',
    buffer: Buffer.from(header + 'UPDATE,EQ-CAM100,여행용 카메라,CAMERA,-17000,true\n') });
  await page.getByRole('button', { name: '파일 업로드', exact: true }).click();
  await expect(page.getByText('invalid.csv', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '검증하기', exact: true }).click();
  await expect(page.getByRole('table', { name: 'CSV 검증 오류' })).toContainText('daily_fee_krw');
  const original = header + 'UPDATE,EQ-CAM100,여행용 카메라,CAMERA,17000,true\n'
    + 'CREATE,EQ-TRI200,삼각대,ACCESSORY,3000,true\n'
    + 'UPDATE,EQ-LGT100,휴대용 조명,LIGHTING,5000,false\n';
  await page.getByLabel('CSV 파일 선택').setInputFiles({ name: 'corrected.csv', mimeType: 'text/csv', buffer: Buffer.from(original) });
  await expect(page.getByRole('region', { name: '검증 및 비교 결과' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '검증하기', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '파일 업로드', exact: true }).click();
  await expect(page.getByText('corrected.csv', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '검증하기', exact: true }).click();
  await expect(page.getByText('검증 완료', { exact: true })).toBeVisible();
  const detail = page.getByRole('table', { name: '필드별 변경 전후' });
  await expect(detail).toContainText('15,000원');
  await expect(detail).toContainText('17,000원');
  const stored = await (await page.request.get(base + '/workspace')).json();
  expect(stored.preview).toMatchObject({ valid: true, stale: false, created: 1, changed: 1, unchanged: 1 });
  const download = await page.request.get(base + '/file');
  expect(await download.body()).toEqual(Buffer.from(original));
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const tooLarge = await page.request.post(base + '/file', { headers: { [csrf.headerName]: csrf.token },
    multipart: { file: { name: 'large.csv', mimeType: 'text/csv', buffer: Buffer.alloc(1048577, 65) } } });
  expect(tooLarge.status()).toBe(413);
  const afterRejected = await (await page.request.get(base + '/workspace')).json();
  expect(afterRejected.fileId).toBe(stored.fileId);
  expect(afterRejected.preview.validationId).toBe(stored.preview.validationId);
  const equipment = await (await page.request.get('/api/equipment')).json();
  expect(equipment.find((row: {code: string}) => row.code === 'EQ-CAM100').dailyFeeKrw).toBe('15000');
  await page.screenshot({ path: 'test-results/workspace-desktop.png', fullPage: true });
  await page.reload();
  await page.getByRole('button', { name: '내 변경 요청', exact: true }).click();
  await page.getByRole('button', { name: `요청 ${draft.requestId} 상세`, exact: true }).click();
  await expect(page.getByText('corrected.csv', { exact: true })).toBeVisible();
  await expect(page.getByText('검증 완료', { exact: true })).toBeVisible();
  const reloaded = await (await page.request.get(base + '/workspace')).json();
  expect(reloaded.preview.validationId).toBe(stored.preview.validationId);
  await page.setViewportSize({ width: 320, height: 740 });
  await expect(page.getByRole('table', { name: '필드별 변경 전후' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/workspace-mobile.png', fullPage: true });
});
