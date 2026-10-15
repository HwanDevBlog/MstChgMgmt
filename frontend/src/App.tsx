import { useEffect, useState } from 'react';
import RevisionWorkspace from './RevisionWorkspace';
import SubmissionPanel from './SubmissionPanel';
import type { FormEvent } from 'react';
import { api, ApiError, formatFee } from './api';
import type { ChangeRequest, Equipment, User } from './api';

const roleNames: Record<string, string> = { REQUESTER: '요청자', APPROVER: '승인자', VIEWER: '조회자' };
const categories: Record<string, string> = { CAMERA: '카메라', AUDIO: '음향', LIGHTING: '조명', ACCESSORY: '액세서리' };
const states: Record<string, string> = { DRAFT: '임시 저장', SUBMITTED: '승인 대기', APPLIED: '반영 완료', REJECTED: '반려', WITHDRAWN: '철회' };

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [page, setPage] = useState<'catalog' | 'requests'>('catalog');
  const [equipment, setEquipment] = useState<Equipment[]>([]);
  const [requests, setRequests] = useState<ChangeRequest[]>([]);
  const [selected, setSelected] = useState<ChangeRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  function navigate(next: 'catalog' | 'requests') {
    if (next === page) return;
    // 목록 조회 전에 입력 폼이 잠깐 표시되었다가 다시 생성되는 것을 막는다.
    setLoading(true); setSelected(null); setNotice(''); setPage(next);
  }

  function report(cause: unknown) {
    if (cause instanceof ApiError && cause.status === 401) {
      setUser(null); setSelected(null); setRequests([]); setEquipment([]); setNotice('');
    }
    setError(cause instanceof Error ? cause.message : '서버에 연결하지 못했습니다.');
  }

  useEffect(() => {
    api.me().then(setUser).catch(cause => {
      if (!(cause instanceof ApiError && cause.status === 401)) report(cause);
    }).finally(() => setInitializing(false));
  }, []);

  useEffect(() => {
    if (!user) return;
    let current = true;
    setLoading(true);
    setError('');
    const work = page === 'catalog' ? api.equipment().then(rows => { if (current) setEquipment(rows); })
      : api.mine().then(rows => { if (current) setRequests(rows); });
    work.catch(cause => { if (current) report(cause); }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [user, page]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true); setError('');
    try {
      await api.login(String(data.get('username')), String(data.get('password')));
      setRequests([]); setEquipment([]); setSelected(null); setNotice('');
      setPage('catalog'); setUser(await api.me());
    } catch (cause) { report(cause); }
    finally { setBusy(false); }
  }

  async function logout() {
    setBusy(true); setError('');
    try {
      await api.logout(); setUser(null); setSelected(null); setRequests([]); setEquipment([]); setNotice('');
    }
    catch (cause) { report(cause); }
    finally { setBusy(false); }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const reason = String(new FormData(form).get('reason') ?? '');
    setBusy(true); setError(''); setNotice('');
    try {
      const draft = await api.create(reason);
      setRequests(await api.mine()); setSelected(draft);
      setNotice(`요청 #${draft.requestId}을 임시 저장했습니다.`); form.reset();
    } catch (cause) { report(cause); }
    finally { setBusy(false); }
  }

  function updatedRequest(value: ChangeRequest) {
    setNotice(value.state === 'SUBMITTED' ? `요청 #${value.requestId}을 승인 요청으로 제출했습니다.` : `요청 #${value.requestId}의 변경 사유를 저장했습니다.`);
    setSelected(value); setRequests(rows => rows.map(row => row.requestId === value.requestId ? value : row));
  }

  async function detail(id: number) {
    setBusy(true); setError('');
    try { setSelected(await api.detail(id)); }
    catch (cause) { report(cause); }
    finally { setBusy(false); }
  }

  if (initializing) return <main className="loading">세션을 확인하고 있습니다…</main>;
  if (!user) return <main className="login-layout">
    <header className="login-header"><span className="wordmark"><span className="brand-symbol" aria-hidden="true">▤</span>MstChgMgmt</span>
      <span className="muted">기준정보 변경 관리</span></header>
    <section className="login-body"><div className="login-panel">
      <h1>업무를 시작하세요</h1><p className="muted">장비 기준정보와 변경 요청을 관리합니다.</p>
      <form onSubmit={login}>
        <label>아이디<input name="username" autoComplete="username" placeholder="아이디를 입력하세요" required disabled={busy} /></label>
        <label>비밀번호<input name="password" type="password" autoComplete="current-password" placeholder="비밀번호를 입력하세요" required disabled={busy} /></label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
      </form><p className="login-help">사전에 등록된 계정으로 로그인할 수 있습니다.</p>
    </div></section>
  </main>;

  return <div className="app-layout">
    <aside className="sidebar"><span className="wordmark"><span className="brand-symbol" aria-hidden="true">▤</span>MstChgMgmt</span><p className="sidebar-caption">기준정보 변경 관리</p><p className="nav-caption">워크스페이스</p>
      <nav aria-label="주 메뉴"><button className={page === 'catalog' ? 'nav active' : 'nav'} onClick={() => navigate('catalog')}>기준정보 목록</button>
        {user.roles.includes('REQUESTER') && <button className={page === 'requests' ? 'nav active' : 'nav'} onClick={() => navigate('requests')}>내 변경 요청</button>}
      </nav><div className="sidebar-bottom">장비 카탈로그<br /><span>가상 장비 대여 조직</span></div>
    </aside>
    <div className="workspace"><header className="topbar"><span>카탈로그 관리</span><div><span className="avatar" aria-hidden="true">{user.username.slice(0, 1).toUpperCase()}</span><strong>{user.username}</strong>
      <span className="roles">{user.roles.map(role => roleNames[role] ?? role).join(' · ')}</span>
      <button className="quiet" onClick={logout} disabled={busy}>로그아웃</button></div></header>
      <main className="content"><div className="page-heading"><div><p className="eyebrow">{page === 'catalog' ? 'CURRENT CATALOG' : 'MY CHANGE REQUESTS'}</p>
        <h1>{page === 'catalog' ? '기준정보 목록' : '내 변경 요청'}</h1>
        <p className="muted">{page === 'catalog' ? '현재 등록된 장비와 대여 기준을 확인합니다.' : '내가 작성한 요청과 현재 리비전을 확인합니다.'}</p></div>
          {page === 'catalog' && user.roles.includes('REQUESTER') && <button className="primary" onClick={() => navigate('requests')}>변경 요청 작성</button>}
        </div>
        {error && <p className="error" role="alert">{error}</p>}
        {notice && page === 'requests' && <p className="notice" role="status">{notice}</p>}
        {loading ? <p role="status">목록을 불러오고 있습니다…</p> : page === 'catalog' ? <section className="card">
          <div className="card-heading"><h2>장비 카탈로그</h2><span className="count">{equipment.length}개 장비</span></div>
          <div className="table-scroll"><table><thead><tr><th>장비 코드</th><th>장비명</th><th>분류</th><th className="numeric">일 대여료</th><th>상태</th><th className="numeric">버전</th></tr></thead>
            <tbody>{equipment.map(row => <tr key={row.code}><td className="code">{row.code}</td><td>{row.name}</td><td>{categories[row.category] ?? row.category}</td>
              <td className="numeric">{formatFee(row.dailyFeeKrw)}</td><td><span className={row.active ? 'badge enabled' : 'badge'}>{row.active ? '사용' : '비활성'}</span></td><td className="numeric">{row.version}</td></tr>)}</tbody></table></div>
          {!equipment.length && <p className="empty">등록된 장비가 없습니다.</p>}
        </section> : <>
          <section className="card"><div className="card-heading"><h2>새 요청 작성</h2><span className="badge">임시 저장</span></div>
            <form className="draft-form" onSubmit={create}><label>변경 사유<textarea name="reason" rows={3} placeholder="변경하려는 이유를 작성하세요." disabled={busy} /></label>
              <div className="form-footer"><span className="muted">변경 내용을 검토하기 전에 요청과 사유를 임시 저장합니다.</span><button className="primary" disabled={busy}>요청 임시 저장</button></div></form>
          </section>
          <section className="card"><div className="card-heading"><h2>내 요청 목록</h2><span className="count">{requests.length}건</span></div>
            <div className="table-scroll"><table><thead><tr><th>요청 번호</th><th>리비전</th><th>상태</th><th>변경 사유</th><th>작성일</th><th>상세</th></tr></thead><tbody>{requests.map(row => <tr key={row.requestId}>
              <td className="code">#{row.requestId}</td><td>{row.revisionNumber}</td><td><span className="badge">{states[row.state] ?? row.state}</span></td><td className="reason-cell">{row.reason || '미작성'}</td>
              <td>{new Date(row.createdAt).toLocaleDateString('ko-KR')}</td><td><button className="text-button" aria-label={`요청 ${row.requestId} 상세`} onClick={() => detail(row.requestId)} disabled={busy}>보기</button></td></tr>)}</tbody></table></div>
            {!requests.length && <p className="empty">아직 작성한 요청이 없습니다.</p>}
          </section>
          {selected && <><section className="card" aria-label="변경 요청 상세"><div className="card-heading"><h2>요청 #{selected.requestId} 상세</h2><span className="badge">{states[selected.state] ?? selected.state}</span></div>
            <dl className="detail-grid"><dt>현재 리비전</dt><dd>{selected.revisionNumber}</dd><dt>변경 사유</dt><dd>{selected.reason || '미작성'}</dd>
              <dt>작성일</dt><dd>{new Date(selected.createdAt).toLocaleString('ko-KR')}</dd></dl></section>
            {selected.state === 'DRAFT' && selected.requesterId === user.id && <RevisionWorkspace
              key={selected.revisionId} request={selected} onFailure={report} onUpdated={updatedRequest} />}
            {selected.state === 'SUBMITTED' && <SubmissionPanel key={`submitted-${selected.revisionId}`} request={selected} onUpdated={updatedRequest} onFailure={report} />}
          </>}
        </>}
      </main></div>
  </div>;
}
