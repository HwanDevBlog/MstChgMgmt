import { useEffect, useState } from 'react';
import { api, ApiError, formatFee } from './api';
import type { ChangeRequest, Snapshot, Values } from './api';

const categories: Record<string, string> = { CAMERA: '카메라', AUDIO: '음향', LIGHTING: '조명', ACCESSORY: '액세서리' };
const labels = { name: '장비명', category: '분류', dailyFeeKrw: '일 대여료', active: '사용 여부' };
const fields = ['name', 'category', 'dailyFeeKrw', 'active'] as const;
function value(row: Values | null, field: typeof fields[number]) {
  if (!row) return '—';
  return field === 'dailyFeeKrw' ? formatFee(row.dailyFeeKrw) : field === 'active' ? row.active ? '사용' : '비활성' : field === 'category' ? categories[row.category] ?? row.category : row.name;
}

/**
 * 화면에 표시한 제출 묶음의 식별자와 해시를 그대로 전송한다.
 * 실패 후 확인 내용을 자동 교체하지 않고 사용자가 다시 검토하도록 한다.
 */
export default function SubmissionPanel({ request, onUpdated, onFailure }: {
  request: ChangeRequest; onUpdated: (request: ChangeRequest) => void; onFailure: (cause: unknown) => void;
}) {
  const [bundle, setBundle] = useState<Snapshot | null>(null);
  const [reason, setReason] = useState(request.reason);
  const [busy, setBusy] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState('');
  const readOnly = request.state !== 'DRAFT';
  function failed(cause: unknown) {
    setConfirmed(false);
    setError(cause instanceof Error ? cause.message : '요청을 처리하지 못했습니다.');
    if (cause instanceof ApiError && cause.status === 401) onFailure(cause);
  }
  useEffect(() => {
    if (!readOnly) return;
    let current = true;
    api.submission(request).then(result => { if (current) setBundle(result); }).catch(cause => { if (current) failed(cause); });
    return () => { current = false; };
  }, [request.requestId, request.revisionId, readOnly]);
  async function saveReason() {
    setBusy(true); setError(''); setBundle(null); setConfirmed(false);
    try { onUpdated(await api.saveReason(request, reason)); }
    catch (cause) { failed(cause); }
    finally { setBusy(false); }
  }
  async function prepare() {
    setBusy(true); setError(''); setConfirmed(false);
    try { setBundle(await api.prepare(request)); }
    catch (cause) { failed(cause); }
    finally { setBusy(false); }
  }
  async function submit() {
    if (!bundle || !confirmed) return;
    setBusy(true); setError('');
    try {
      const frozen = await api.submit(request, bundle);
      setBundle(frozen); onUpdated({ ...request, state: 'SUBMITTED', reason: frozen.reason });
    } catch (cause) { failed(cause); }
    finally { setBusy(false); }
  }
  return <section className="card" aria-label={readOnly ? '고정 제출본' : '제출 확인'}>
    <div className="card-heading"><h2>{readOnly ? '고정 제출본' : '제출 확인'}</h2>
      {readOnly && <span className="badge enabled">승인 대기</span>}</div>
    <div className="submission-body">
      {!readOnly && <>
        <p className="muted">제출하면 변경 사유·파일·전후 값이 고정되어 수정할 수 없습니다. 실제 기준정보는 승인 단계에서 반영됩니다.</p>
        {!bundle && <><label>제출할 변경 사유<textarea value={reason} rows={3} disabled={busy} onChange={event => setReason(event.target.value)} /></label>
          <div className="workspace-actions reason-actions"><button className="quiet" disabled={busy || reason === request.reason} onClick={saveReason}>사유 저장</button>
            <button className="primary" disabled={busy || reason !== request.reason} onClick={prepare}>제출 내용 확인</button></div>
          {reason !== request.reason && <p className="muted">변경한 사유를 먼저 저장하세요.</p>}</>}
      </>}
      {error && <p className="error" role="alert">{error}</p>}
      {bundle && <>
        {readOnly && <p className="notice">제출이 완료되었습니다. 아래 내용은 제출 시점에 고정된 값입니다.</p>}
        {readOnly && <p><a className="text-button" href={api.originalUrl(request)}>제출 원본 다운로드</a></p>}
        <dl className="detail-grid"><dt>요청 / 리비전</dt><dd>#{bundle.requestId} / {bundle.revisionNumber}</dd>
          <dt>변경 사유</dt><dd>{bundle.reason}</dd><dt>파일</dt><dd>{bundle.filename}</dd>
          {bundle.submittedAt && <><dt>제출 시각</dt><dd>{new Date(bundle.submittedAt).toLocaleString('ko-KR')}</dd></>}</dl>
        {bundle.rows.map(row => <div key={row.equipmentCode} className="submitted-row"><h3>{row.equipmentCode} · {row.operation === 'CREATE' ? '신규' : row.kind === 'UNCHANGED' ? '변경 없음' : '변경'}
          <span className="muted">기대 버전 {row.expectedVersion ?? '신규'}</span></h3>
          <div className="table-scroll"><table aria-label={`${row.equipmentCode} 제출 전후 값`}><thead><tr><th>필드</th><th>변경 전</th><th>변경 후</th></tr></thead>
            <tbody>{fields.map(field => <tr key={field}><th>{labels[field]}</th><td>{value(row.before, field)}</td>
              <td className={!row.before || row.before[field] !== row.after[field] ? 'changed-value' : ''}>{value(row.after, field)}</td></tr>)}</tbody></table></div>
        </div>)}
        <details className="submission-identifiers"><summary>제출 식별 정보</summary><dl className="detail-grid">
          <dt>내용 해시</dt><dd className="hash-value">{bundle.contentHash}</dd><dt>원본 해시</dt><dd className="hash-value">{bundle.originalSha256}</dd>
          <dt>검증 ID</dt><dd className="hash-value">{bundle.validationId}</dd>{bundle.submissionId && <><dt>제출 ID</dt><dd className="hash-value">{bundle.submissionId}</dd></>}</dl></details>
        {!readOnly && <><label className="submission-check"><input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)} />위 변경 내용과 사유를 확인했습니다.</label>
          <div className="workspace-actions"><button className="quiet" disabled={busy} onClick={() => { setBundle(null); setConfirmed(false); }}>검토로 돌아가기</button>
            <button className="primary" disabled={busy || !confirmed} onClick={submit}>{busy ? '제출 중…' : '승인 요청 제출'}</button></div></>}
      </>}
      {readOnly && !bundle && !error && <p className="muted">제출본을 불러오고 있습니다…</p>}
    </div>
  </section>;
}
