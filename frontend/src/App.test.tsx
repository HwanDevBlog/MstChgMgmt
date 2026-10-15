import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import App from './App';
import { api, ApiError } from './api';

vi.mock('./api', async importOriginal => {
  const original = await importOriginal<typeof import('./api')>();
  return { ...original, api: { me: vi.fn(), login: vi.fn(), logout: vi.fn(), equipment: vi.fn(), mine: vi.fn(), create: vi.fn(), detail: vi.fn(), workspace: vi.fn(), history: vi.fn() } };
});

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.history).mockResolvedValue([]);
  vi.mocked(api.workspace).mockResolvedValue({ fileId: null, filename: null, size: 0, originalSha256: null, preview: null });
  vi.mocked(api.equipment).mockResolvedValue([]);
  vi.mocked(api.mine).mockResolvedValue([]);
});

test('로그인하지 않은 사용자는 로그인 양식을 보고 입력 계정으로 인증을 요청한다', async () => {
  vi.mocked(api.me).mockRejectedValueOnce(new ApiError(401));
  vi.mocked(api.login).mockResolvedValue(undefined);
  vi.mocked(api.me).mockResolvedValueOnce({ id: 1, username: 'requester_a', roles: ['REQUESTER'] });
  render(<App />);
  fireEvent.change(await screen.findByLabelText('아이디'), { target: { value: 'requester_a' } });
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'demo' } });
  fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  expect(await screen.findByText('requester_a')).toBeInTheDocument();
  expect(api.login).toHaveBeenCalledWith('requester_a', 'demo');
});

test('조회자 화면에는 내 요청 생성 메뉴가 나타나지 않는다', async () => {
  vi.mocked(api.me).mockResolvedValue({ id: 5, username: 'viewer', roles: ['VIEWER'] });
  render(<App />);
  expect(await screen.findByText('viewer')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '내 변경 요청' })).not.toBeInTheDocument();
});

test('요청자는 빈 목록을 확인하고 자신의 임시 요청을 만들 수 있다', async () => {
  vi.mocked(api.me).mockResolvedValue({ id: 1, username: 'requester_a', roles: ['REQUESTER'] });
  const draft = { requestId: 7, requesterId: 1, revisionId: 8, revisionNumber: 1,
    state: 'DRAFT', reason: '카탈로그 정비', createdAt: '2026-10-15T00:00:00Z' };
  vi.mocked(api.create).mockResolvedValue(draft);
  render(<App />);
  fireEvent.click(await screen.findByRole('button', { name: '내 변경 요청' }));
  expect(await screen.findByText('아직 작성한 요청이 없습니다.')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('변경 사유'), { target: { value: '카탈로그 정비' } });
  fireEvent.click(screen.getByRole('button', { name: '요청 임시 저장' }));
  expect(await screen.findByRole('status')).toHaveTextContent('요청 #7을 임시 저장했습니다.');
  expect(api.create).toHaveBeenCalledWith('카탈로그 정비');
});
