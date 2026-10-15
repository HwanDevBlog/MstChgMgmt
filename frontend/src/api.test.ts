import { afterEach, expect, test, vi } from 'vitest';
import { api, formatFee } from './api';

afterEach(() => vi.unstubAllGlobals());

test('변경 요청마다 현재 CSRF 토큰을 보내고 요청자 ID는 전송하지 않는다', async () => {
  const fetcher = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'masked-one' })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ requestId: 1 }), { status: 201 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'masked-two' })))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetcher);
  await api.create('카탈로그 정비');
  expect(fetcher.mock.calls[1][1]).toMatchObject({ method: 'POST', credentials: 'same-origin',
    headers: { 'X-CSRF-TOKEN': 'masked-one' }, body: JSON.stringify({ reason: '카탈로그 정비' }) });
  await api.logout();
  expect(fetcher.mock.calls[3][1].headers['X-CSRF-TOKEN']).toBe('masked-two');
});

test('인증 실패를 구분해 로그인 화면으로 복귀할 수 있다', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
  await expect(api.me()).rejects.toMatchObject({ status: 401 });
});

test('bigint 상한 금액을 반올림 없이 표시한다', () => {
  expect(formatFee('9223372036854775807')).toBe('9,223,372,036,854,775,807원');
});
