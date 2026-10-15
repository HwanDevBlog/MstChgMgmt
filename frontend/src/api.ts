export type User = { id: number; username: string; roles: string[] };
export type Equipment = {
  code: string; name: string; category: string; dailyFeeKrw: string; active: boolean; version: string;
};
export type ChangeRequest = {
  requestId: number; requesterId: number; revisionId: number; revisionNumber: number;
  state: string; reason: string; createdAt: string;
};

export class ApiError extends Error {
  constructor(public status: number) {
    super(status === 401 ? '로그인이 필요합니다.' : status === 403
      ? '이 작업에 대한 권한이 없습니다.' : '요청을 처리하지 못했습니다. 다시 확인해 주세요.');
  }
}

async function send<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(path, { ...options, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) throw new ApiError(response.status);
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
