import { useEffect, useState } from 'react';
import { api } from './api';
import type { ChangeRequest, RevisionInfo } from './api';

export default function RevisionHistory({ request, currentRevisionId, onSelect }: {
  request: ChangeRequest; currentRevisionId: number; onSelect: (id: number) => void;
}) {
  const [rows, setRows] = useState<RevisionInfo[]>([]);
  const [error, setError] = useState('');
  const names: Record<string, string> = { DRAFT: '임시 저장', SUBMITTED: '승인 대기', APPLIED: '반영 완료', REJECTED: '반려', WITHDRAWN: '철회' };
  useEffect(() => {
    let current = true;
    api.history(request.requestId).then(value => { if (current) { setRows(value); setError(''); } }).catch(() => { if (current) setError('리비전 이력을 불러오지 못했습니다.'); });
    return () => { current = false; };
  }, [request.requestId, request.revisionId, request.state]);
  return <section className="card" aria-label="리비전 이력"><div className="card-heading"><h2>리비전 이력</h2><span className="count">{rows.length}건</span></div>
    {error && <p className="error">{error}</p>}<div className="table-scroll"><table><thead><tr><th>리비전</th><th>상태</th><th>사유</th><th>보기</th></tr></thead>
      <tbody>{rows.map(row => <tr key={row.revisionId}><td>{row.revisionNumber}{row.revisionId === currentRevisionId ? ' · 현재' : ''}</td><td><span className="badge">{names[row.state]}</span></td><td className="reason-cell">{row.reason || '미작성'}</td>
        <td><button className="text-button" onClick={() => onSelect(row.revisionId)} aria-label={`리비전 ${row.revisionNumber} 보기`}>보기</button></td></tr>)}</tbody></table></div>
  </section>;
}
