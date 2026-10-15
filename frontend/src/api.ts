export type User = { id: number; username: string; roles: string[] };
export type Equipment = {
  code: string; name: string; category: string; dailyFeeKrw: string; active: boolean; version: string;
};
export type ChangeRequest = {
  requestId: number; requesterId: number; revisionId: number; revisionNumber: number;
  state: string; reason: string; createdAt: string;
};

export type Comparison = {
  recordNumber: number; operation: string; equipmentCode: string; kind: string;
  before: Values | null; after: Values; expectedVersion: string | null;
};
export type Values = { name: string; category: string; dailyFeeKrw: string; active: boolean; version: string | null };
export type Preview = {
  validationId: string; fileId: string; rulesVersion: string; valid: boolean; stale: boolean;
  created: number; changed: number; unchanged: number; rows: Comparison[];
  errors: { recordNumber: number; field: string; code: string }[]; validatedAt: string;
};
export type Workspace = { fileId: string | null; filename: string | null; size: number; originalSha256: string | null; preview: Preview | null };
const revisionPath = (request: ChangeRequest) => `/api/requests/${request.requestId}/revisions/${request.revisionId}`;

export type Snapshot = {
  requestId: number; revisionId: number; requesterId: number; revisionNumber: number;
  reason: string; rulesVersion: string; fileId: string; validationId: string; filename: string;
  originalSha256: string; contentHash: string; rows: Comparison[]; submissionId: string | null; submittedAt: string | null;
};
const errorMessages: Record<string, string> = {
  REASON_REQUIRED: '제출할 변경 사유를 입력하고 저장하세요.', VALIDATION_REQUIRED: '파일을 업로드하고 오류 없이 검증을 완료하세요.',
  STALE_CATALOG: '기준정보가 변경되었습니다. 다시 검증하고 변경 내용을 검토하세요.', NO_CHANGES: '변경할 내용이 없어 제출할 수 없습니다.',
  SUBMISSION_MISMATCH: '검토한 파일·검증 결과·사유가 달라졌습니다. 최신 내용을 다시 확인하세요.', INVALID_REASON: '변경 사유는 500자 이하이며 NUL 문자를 포함할 수 없습니다.',
};
export class ApiError extends Error {
  constructor(public status: number, public code?: string) {
    super((code && errorMessages[code]) || (status === 401 ? '로그인이 필요합니다.' : status === 403
      ? '이 작업에 대한 권한이 없습니다.' : status === 409 ? '파일·리비전이 변경되었거나 현재 상태에서 처리할 수 없습니다. 최신 상태를 확인하세요.' : status === 413 ? '파일은 1MiB 이하여야 합니다.' : '요청을 처리하지 못했습니다. 다시 확인해 주세요.'));
  }
}

async function send<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(path, { ...options, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new ApiError(response.status, error?.code);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

async function mutate<T>(path: string, body?: BodyInit, contentType?: string): Promise<T> {
  // 로그인과 로그아웃 뒤 토큰이 바뀌므로 각 변경 요청 직전에 현재 토큰을 조회한다.
  const csrf = await send<{ headerName: string; token: string }>('/api/auth/csrf');
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  if (contentType) headers['Content-Type'] = contentType;
  return send<T>(path, { method: 'POST', headers, body });
}

export const api = {
  prepare: (request: ChangeRequest) => send<Snapshot>(revisionPath(request) + '/submission-preview'),
  submission: (request: ChangeRequest) => send<Snapshot>(revisionPath(request) + '/submission'),
  saveReason: (request: ChangeRequest, reason: string) => mutate<ChangeRequest>(revisionPath(request) + '/reason', JSON.stringify({ reason }), 'application/json'),
  submit: (request: ChangeRequest, bundle: Snapshot) => mutate<Snapshot>(revisionPath(request) + '/submission', JSON.stringify({
    fileId: bundle.fileId, validationId: bundle.validationId, contentHash: bundle.contentHash,
  }), 'application/json'),
  workspace: (request: ChangeRequest) => send<Workspace>(revisionPath(request) + '/workspace'),
  upload: (request: ChangeRequest, file: File) => {
    const data = new FormData(); data.append('file', file);
    return mutate<Workspace>(revisionPath(request) + '/file', data);
  },
  validate: (request: ChangeRequest, fileId: string) => mutate<Preview>(revisionPath(request) + '/validation', JSON.stringify({ fileId }), 'application/json'),
  originalUrl: (request: ChangeRequest) => revisionPath(request) + '/file',
  me: () => send<User>('/api/auth/me'),
  login: (username: string, password: string) => mutate<void>('/api/auth/login',
    new URLSearchParams({ username, password }), 'application/x-www-form-urlencoded'),
  logout: () => mutate<void>('/api/auth/logout'),
  equipment: () => send<Equipment[]>('/api/equipment'),
  mine: () => send<ChangeRequest[]>('/api/requests/mine'),
  detail: (id: number) => send<ChangeRequest>(`/api/requests/${id}`),
  create: (reason: string) => mutate<ChangeRequest>('/api/requests', JSON.stringify({ reason }), 'application/json'),
};

export function formatFee(value: string): string {
  return BigInt(value).toLocaleString('ko-KR') + '원';
}
