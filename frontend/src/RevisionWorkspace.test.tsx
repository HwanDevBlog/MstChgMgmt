import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import RevisionWorkspace from './RevisionWorkspace';
import { api } from './api';
import type { Preview, Workspace } from './api';

vi.mock('./api', async original => ({ ...await original<typeof import('./api')>(), api: {
  workspace: vi.fn(), upload: vi.fn(), validate: vi.fn(), originalUrl: () => '/original',
} }));
const request = { requestId: 1, revisionId: 2, requesterId: 1, revisionNumber: 1, state: 'DRAFT', reason: '정비', createdAt: '2026-10-15T00:00:00Z' };
const preview: Preview = { validationId: 'validation-one', fileId: 'file-one', rulesVersion: 'catalog-v1',
  valid: true, stale: false, created: 0, changed: 1, unchanged: 0, errors: [], validatedAt: '2026-10-15T00:00:00Z', rows: [{
    recordNumber: 2, operation: 'UPDATE', equipmentCode: 'EQ-CAM100', kind: 'CHANGED', expectedVersion: '0',
    before: { name: '카메라', category: 'CAMERA', dailyFeeKrw: '15000', active: true, version: '0' },
    after: { name: '카메라', category: 'CAMERA', dailyFeeKrw: '17000', active: true, version: '0' },
  }] };
const workspace: Workspace = { fileId: 'file-one', filename: 'catalog.csv', size: 200, originalSha256: 'a'.repeat(64), preview };
beforeEach(() => { vi.resetAllMocks(); vi.mocked(api.workspace).mockResolvedValue(workspace); });

test('저장된 검증과 변경 전후 값·기대 버전을 표시한다', async () => {
  render(<RevisionWorkspace request={request} onFailure={vi.fn()} />);
  expect(await screen.findByText('검증 완료')).toBeInTheDocument();
  const comparison = screen.getByRole('table', { name: '필드별 변경 전후' });
  expect(within(comparison).getByText('15,000원')).toBeInTheDocument();
  expect(within(comparison).getByText('17,000원')).toHaveClass('changed-value');
  expect(screen.getByRole('table', { name: '변경 비교 목록' })).toHaveTextContent('0');
});

test('새 파일을 선택하면 이전 비교를 숨기고 업로드 후에만 검증할 수 있다', async () => {
  vi.mocked(api.upload).mockResolvedValue({ ...workspace, fileId: 'file-two', filename: 'new.csv', preview: null });
  vi.mocked(api.validate).mockResolvedValue({ ...preview, fileId: 'file-two' });
  render(<RevisionWorkspace request={request} onFailure={vi.fn()} />);
  await screen.findByText('검증 완료');
  const file = new File(['csv'], 'new.csv', { type: 'text/csv' });
  fireEvent.change(screen.getByLabelText('CSV 파일 선택'), { target: { files: [file] } });
  expect(screen.queryByRole('region', { name: '검증 및 비교 결과' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '검증하기' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '파일 업로드' }));
  await waitFor(() => expect(api.upload).toHaveBeenCalledWith(request, file));
  await waitFor(() => expect(screen.getByRole('button', { name: '검증하기' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: '검증하기' }));
  await waitFor(() => expect(api.validate).toHaveBeenCalledWith(request, 'file-two'));
  expect(await screen.findByText('검증 완료')).toBeInTheDocument();
});

test('최신 기준정보와 다른 미리보기에는 재검증 안내를 표시한다', async () => {
  vi.mocked(api.workspace).mockResolvedValue({ ...workspace, preview: { ...preview, stale: true } });
  render(<RevisionWorkspace request={request} onFailure={vi.fn()} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('기준정보가 변경되었습니다');
  expect(screen.getByText('재검증 필요')).toBeInTheDocument();
});

test('레코드·필드 오류와 변경 없음 결과를 구분한다', async () => {
  vi.mocked(api.workspace).mockResolvedValue({ ...workspace, preview: { ...preview, valid: false, rows: [], changed: 0,
    errors: [{ recordNumber: 2, field: 'daily_fee_krw', code: 'INVALID_FEE' }] } });
  render(<RevisionWorkspace request={request} onFailure={vi.fn()} />);
  expect(await screen.findByRole('table', { name: 'CSV 검증 오류' })).toHaveTextContent('daily_fee_krw');
  expect(screen.getByText('대여료는 0 이상의 정수로 입력하세요.')).toBeInTheDocument();
  expect(screen.queryByText('변경할 내용이 없습니다.')).not.toBeInTheDocument();
});
