import { useEffect, useState } from 'react';
import { api, ApiError, formatFee } from './api';
import type { ChangeRequest, DecisionReceipt, Snapshot, User } from './api';

/**
 * 화면에 표시한 제출본의 리비전과 해시로만 결정 명령을 보낸다.
 * 처리 후 영수증과 실제 변경 이력을 표시하고 과거 리비전의 수정을 막는다.
 */
export default function WorkflowActions({ request, snapshot, user, isCurrent, onUpdated, onFailure }: {
  request: ChangeRequest; snapshot: Snapshot; user: User; isCurrent: boolean;
  onUpdated: (request: ChangeRequest) => void; onFailure: (cause: unknown) => void;
}) {
  const [receipt, setReceipt] = useState<DecisionReceipt | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const owner = user.id === request.requesterId && user.roles.includes('REQUESTER');
  const approver = user.id !== request.requesterId && user.roles.includes('APPROVER');
  function failed(cause: unknown) {
    setError(cause instanceof Error ? cause.message : '처리하지 못했습니다.');
    if (cause instanceof ApiError && cause.status === 401) onFailure(cause);
  }
  useEffect(() => {
    if (request.state === 'SUBMITTED' || receipt) return;
    let current = true;
    api.decision(request).then(value => { if (current) setReceipt(value); }).catch(cause => { if (current) failed(cause); });
    return () => { current = false; };
  }, [request.revisionId, request.state]);

  async function decide(action: 'approve' | 'reject' | 'withdraw') {
    setBusy(true); setError('');
    try {
      const value = await api.decide(snapshot, action, action === 'approve' ? undefined : reason);
      setReceipt(value); setConfirmed(false); onUpdated({ ...request, state: value.state });
    } catch (cause) { failed(cause); }
    finally { setBusy(false); }
  }
  async function newDraft() {
    setBusy(true); setError('');
    try { onUpdated(await api.newDraft(snapshot)); }
    catch (cause) { failed(cause); }
    finally { setBusy(false); }
  }
  return <div className="workflow-actions">
    {!isCurrent && <p className="notice">과거 리비전입니다. 원래 제출 내용과 처리 결과를 조회합니다.</p>}
    {error && <p className="error" role="alert">{error}</p>}
    {request.state === 'SUBMITTED' && isCurrent && <>
      {approver && <><label className="submission-check"><input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)} />위 제출본의 변경 내용과 사유를 확인했습니다.</label>
        <label>반려 사유<textarea value={reason} disabled={busy} rows={2} placeholder="반려할 경우 사유를 입력하세요." onChange={event => setReason(event.target.value)} /></label>
        <div className="workspace-actions reason-actions"><button className="quiet" disabled={busy || !confirmed || !reason.trim()} onClick={() => decide('reject')}>반려</button>
          <button className="primary" disabled={busy || !confirmed} onClick={() => decide('approve')}>승인 및 반영</button></div></>}
      {owner && <><label>철회 사유 (선택)<textarea value={reason} disabled={busy} rows={2} onChange={event => setReason(event.target.value)} /></label>
        <div className="reason-actions"><button className="quiet" disabled={busy} onClick={() => decide('withdraw')}>요청 철회</button></div></>}
    </>}
    {receipt && <div className="decision-receipt" aria-label="처리 결과"><h3>{receipt.state === 'APPLIED' ? '승인 및 반영 완료' : receipt.state === 'REJECTED' ? '반려됨' : '철회됨'}</h3>
      <dl className="detail-grid"><dt>처리자</dt><dd>{receipt.actorUsername}</dd><dt>처리 시각</dt><dd>{new Date(receipt.processedAt).toLocaleString('ko-KR')}</dd>
        {receipt.reason && <><dt>처리 사유</dt><dd>{receipt.reason}</dd></>}<dt>처리 ID</dt><dd className="hash-value">{receipt.decisionId}</dd></dl>
      {!!receipt.changes.length && <div className="table-scroll"><table className="audit-table" aria-label="실제 기준정보 변경 이력"><colgroup><col style={{ width: '20%' }} /><col style={{ width: '12%' }} /><col style={{ width: '20%' }} /><col style={{ width: '20%' }} /><col style={{ width: '14%' }} /><col style={{ width: '14%' }} /></colgroup><thead><tr><th>장비 코드</th><th>작업</th><th className="numeric">이전 대여료</th><th className="numeric">반영 대여료</th><th>이전 버전</th><th>반영 버전</th></tr></thead>
        <tbody>{receipt.changes.map(row => <tr key={row.equipmentCode}><td className="code">{row.equipmentCode}</td><td>{row.operation}</td><td className="numeric">{row.before ? formatFee(row.before.dailyFeeKrw) : '—'}</td><td className="numeric">{formatFee(row.after.dailyFeeKrw)}</td><td>{row.before?.version ?? '—'}</td><td>{row.after.version}</td></tr>)}</tbody></table></div>}
      <p className="muted">{receipt.state === 'APPLIED' ? '변경 없음 행은 버전과 변경 이력을 증가시키지 않습니다.' : '기준정보는 변경되지 않았습니다. 새 리비전에서 다시 작성할 수 있습니다.'}</p>
    </div>}
    {owner && isCurrent && ['REJECTED', 'WITHDRAWN'].includes(request.state) && <button className="primary" disabled={busy} onClick={newDraft}>새 리비전 작성</button>}
  </div>;
}
