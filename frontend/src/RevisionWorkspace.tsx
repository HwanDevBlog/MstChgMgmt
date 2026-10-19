import { useEffect, useState, useRef } from 'react';
import SubmissionPanel from './SubmissionPanel';
import type { ChangeRequest, Comparison, Preview, Workspace } from './api';
import { api, ApiError, formatFee } from './api';

const fields = ['name', 'category', 'dailyFeeKrw', 'active'] as const;
const fieldNames = { name: '장비명', category: '분류', dailyFeeKrw: '일 대여료', active: '사용 여부' };
const categories: Record<string, string> = { CAMERA: '카메라', AUDIO: '음향', LIGHTING: '조명', ACCESSORY: '액세서리' };
const kinds: Record<string, string> = { CREATE: '신규', CHANGED: '변경', UNCHANGED: '변경 없음' };
const errorNames: Record<string, string> = {
  FILE_TOO_LARGE: '파일은 1MiB 이하여야 합니다.', INVALID_UTF8: 'UTF-8로 저장한 파일이 필요합니다.',
  HEADER_REQUIRED: '헤더가 필요합니다.', HEADER_MISMATCH: 'CSV 헤더가 지정된 양식과 다릅니다.',
  TOO_MANY_ROWS: '데이터는 200레코드 이하여야 합니다.', COLUMN_COUNT: '열 개수가 다릅니다.',
  INVALID_OPERATION: 'CREATE 또는 UPDATE를 입력하세요.', INVALID_CODE: '장비 코드 형식을 확인하세요.',
  DUPLICATE_CODE: '파일 안에 같은 장비 코드가 중복되어 있습니다.', INVALID_NAME: '장비명 길이·공백·제어문자를 확인하세요.',
  INVALID_CATEGORY: '허용된 분류를 입력하세요.', INVALID_FEE: '대여료는 0 이상의 정수로 입력하세요.',
  FEE_OUT_OF_RANGE: '대여료가 허용 범위를 초과합니다.', INVALID_ACTIVE: 'true 또는 false를 입력하세요.',
  DATA_REQUIRED: '데이터 레코드가 필요합니다.', MALFORMED_CSV: 'CSV 따옴표와 문법을 확인하세요.',
  CREATE_EXISTS: '이미 존재하는 장비입니다. UPDATE를 사용하세요.', UPDATE_MISSING: '수정할 장비가 존재하지 않습니다.',
};

function display(row: Comparison['after'] | null, field: typeof fields[number]) {
  if (!row) return '—';
  if (field === 'dailyFeeKrw') return formatFee(row.dailyFeeKrw);
  if (field === 'active') return row.active ? '사용' : '비활성';
  return field === 'category' ? categories[row.category] ?? row.category : row.name;
}

export default function RevisionWorkspace({ request, onFailure, onUpdated = () => {} }: {
  request: ChangeRequest; onFailure: (cause: unknown) => void; onUpdated?: (request: ChangeRequest) => void;
}) {
  const [workspace, setWorkspace] = useState<Workspace | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const resultRef = useRef<HTMLElement>(null);
  const revealResult = useRef(false);
  const [operation, setOperation] = useState<'upload' | 'validate' | 'refresh' | ''>('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [selectedCode, setSelectedCode] = useState('');
  const preview = file ? null : workspace?.preview;
  const selected = preview?.rows.find(row => row.equipmentCode === selectedCode) ?? preview?.rows[0];

  // 검증을 직접 실행했을 때만 결과로 이동하며 저장된 결과 조회는 화면을 강제로 옮기지 않는다.
  useEffect(() => {
    if (!revealResult.current || !preview) return;
    revealResult.current = false;
    resultRef.current?.scrollIntoView?.({ block: 'start' });
    resultRef.current?.focus({ preventScroll: true });
  }, [preview?.validationId]);

  function failed(cause: unknown) {
    setError(cause instanceof Error ? cause.message : '요청을 처리하지 못했습니다.');
    if (cause instanceof ApiError && cause.status === 401) onFailure(cause);
  }
  useEffect(() => {
    let current = true;
    api.workspace(request).then(value => { if (current) setWorkspace(value); })
      .catch(cause => { if (current) failed(cause); }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [request.requestId, request.revisionId]);

  async function refresh() {
    setOperation('refresh'); setBusy(true); setError('');
    try { setWorkspace(await api.workspace(request)); setNotice('저장한 파일과 검증 결과를 다시 확인했습니다.'); }
    catch (cause) { failed(cause); }
    finally { setBusy(false); setOperation(''); }
  }
  async function upload() {
    if (!file) return;
    if (file.size > 1048576) { setError('파일은 1MiB 이하여야 합니다.'); return; }
    setOperation('upload'); setBusy(true); setError(''); setNotice('');
    try {
      setWorkspace(await api.upload(request, file)); setFile(null); setSelectedCode('');
      setNotice('파일을 업로드했습니다. 이전 검증 결과는 폐기되었습니다.');
    } catch (cause) { failed(cause); }
    finally { setBusy(false); setOperation(''); }
  }
  async function validate() {
    if (!workspace?.fileId || file) return;
    setOperation('validate'); setBusy(true); setError(''); setNotice('');
    try {
      const result: Preview = await api.validate(request, workspace.fileId);
      revealResult.current = true;
      setWorkspace({ ...workspace, preview: result }); setSelectedCode('');
      setNotice(result.valid ? '검증을 완료했습니다. 변경 전후 값을 확인한 뒤 아래에서 제출을 진행하세요.' : `검증 오류 ${result.errors.length}건이 있습니다. 아래 오류 내용을 확인하고 수정한 파일을 다시 업로드하세요.`);
    } catch (cause) { failed(cause); }
    finally { setBusy(false); setOperation(''); }
  }

  return <div className="revision-workspace" aria-label="CSV 작업 영역">
    <section className="card"><div className="card-heading"><h2>CSV 업로드</h2><span className="badge">리비전 {request.revisionNumber}</span></div>
      <div className="upload-body">
        <p className="muted">UTF-8 · 최대 1MiB · 데이터 200레코드. UPDATE는 모든 필드 값을 입력하세요.</p>
        <details className="csv-help"><summary>CSV 작성 양식</summary>
          <code>operation,equipment_code,name,category,daily_fee_krw,active</code>
          <p className="muted">작업: CREATE / UPDATE · 장비 코드: EQ- 뒤 영문 대문자·숫자 6자리<br />분류: CAMERA / AUDIO / LIGHTING / ACCESSORY · 사용 여부: true / false</p>
        </details>
        {loading ? <p>파일 정보를 불러오고 있습니다…</p> : <>
          {workspace?.fileId && <div className="file-summary"><span className="badge enabled">업로드 완료</span><strong>{workspace.filename}</strong><span className="muted">{workspace.size.toLocaleString()} 바이트</span>
            <a href={api.originalUrl(request)}>원본 다운로드</a></div>}
          <div className="upload-picker"><label>CSV 파일 선택<input key={workspace?.fileId ?? 'empty'} type="file" accept=".csv,text/csv" disabled={busy} onChange={event => {
            setFile(event.target.files?.[0] ?? null); setError(''); setNotice('');
          }} /></label><button className="quiet" onClick={upload} disabled={busy || !file}>{operation === 'upload' ? '업로드 중…' : '파일 업로드'}</button></div>
          <p className="upload-guide" aria-live="polite">{file ? `${file.name} 선택 완료 · 파일 업로드 버튼을 눌러 저장하세요.` : workspace?.fileId ? preview ? '검증 결과가 아래에 표시됩니다. 파일을 바꿀 때만 새 파일을 선택하세요.' : '업로드 완료 · 검증하기 버튼을 눌러 파일 내용을 확인하세요.' : '1. 파일 선택 → 2. 파일 업로드 → 3. 검증하기 순서로 진행하세요.'}</p>
          <div className="form-footer"><span className="muted">파일을 교체하면 이전 검증·비교 결과가 폐기됩니다.</span>
            <div className="workspace-actions"><button className="quiet" disabled={busy || !!file} onClick={refresh}>최신 상태 확인</button>
              <button className="primary" disabled={busy || !workspace?.fileId || !!file} onClick={validate}>{operation === 'validate' ? '검증 중…' : '검증하기'}</button></div></div>
        </>}
        {error && <p role="alert" className="error">{error}</p>}
        {notice && <p className="notice" aria-live="polite">{notice}</p>}
      </div></section>
    {preview && <section className="card validation-result" aria-label="검증 및 비교 결과" ref={resultRef} tabIndex={-1}><div className="card-heading"><h2>검증·비교 결과</h2>
      <span className={preview.valid && !preview.stale ? 'badge enabled' : 'badge'}>{preview.stale ? '재검증 필요' : preview.valid ? '검증 완료' : '검증 오류'}</span></div>
      <div className="validation-summary">
        {preview.stale && <p role="alert" className="error">검증 이후 기준정보가 변경되었습니다. 아래 값은 이전 검증 시점의 결과입니다. 다시 검증하세요.</p>}
        {!preview.valid && <p role="alert" className="error">오류 {preview.errors.length}건을 수정한 파일을 다시 업로드하세요. 전체 파일의 검증이 완료되지 않았습니다.</p>}
        <div className="validation-counts"><span>신규 <strong>{preview.created}</strong></span><span>변경 <strong>{preview.changed}</strong></span><span>변경 없음 <strong>{preview.unchanged}</strong></span>
          <span className="muted">검증 시각 {new Date(preview.validatedAt).toLocaleString('ko-KR')}</span></div>
        {preview.valid && !preview.created && !preview.changed && <p className="notice">변경할 내용이 없습니다.</p>}
      </div>
      {!!preview.errors.length && <div className="table-scroll"><table className="error-table" aria-label="CSV 검증 오류"><colgroup><col style={{ width: '12%' }} /><col style={{ width: '26%' }} /><col style={{ width: '62%' }} /></colgroup><thead><tr><th>레코드</th><th>필드</th><th>오류 내용</th></tr></thead>
        <tbody>{preview.errors.map((item, index) => <tr key={index}><td>{item.recordNumber === 0 ? '파일' : item.recordNumber}</td><td className="code">{item.field}</td><td>{errorNames[item.code] ?? item.code}</td></tr>)}</tbody></table></div>}
      {!!preview.rows.length && <><div className="table-scroll"><table className="comparison-table" aria-label="변경 비교 목록"><colgroup>
        <col style={{ width: '7%' }} /><col style={{ width: '10%' }} /><col style={{ width: '17%' }} />
        <col style={{ width: '30%' }} /><col style={{ width: '12%' }} /><col style={{ width: '14%' }} /><col style={{ width: '10%' }} />
      </colgroup><thead><tr><th>레코드</th><th>작업</th><th>장비 코드</th><th>장비명</th><th>구분</th><th>기대 버전</th><th>비교</th></tr></thead>
        <tbody>{preview.rows.map(row => <tr key={row.equipmentCode} className={selected?.equipmentCode === row.equipmentCode ? 'selected-row' : ''}>
          <td>{row.recordNumber}</td><td className="code">{row.operation}</td><td className="code">{row.equipmentCode}</td><td>{row.after.name}</td>
          <td><span className={row.kind === 'UNCHANGED' ? 'badge' : 'badge enabled'}>{kinds[row.kind]}</span></td><td>{row.expectedVersion ?? '신규'}</td>
          <td><button className="text-button" aria-label={`${row.equipmentCode} 전후 비교`} onClick={() => setSelectedCode(row.equipmentCode)}>보기</button></td></tr>)}</tbody></table></div>
        {selected && <div className="comparison-detail"><h3>{selected.equipmentCode} · 변경 전후 값</h3><div className="table-scroll"><table className="value-table" aria-label="필드별 변경 전후"><colgroup><col style={{ width: '22%' }} /><col style={{ width: '39%' }} /><col style={{ width: '39%' }} /></colgroup><thead><tr><th>필드</th><th>변경 전</th><th>변경 후</th></tr></thead>
          <tbody>{fields.map(field => <tr key={field}><th>{fieldNames[field]}</th><td>{display(selected.before, field)}</td>
            <td className={!selected.before || selected.before[field] !== selected.after[field] ? 'changed-value' : ''}>{display(selected.after, field)}</td></tr>)}</tbody></table></div></div>}
      </>}
      <div className="validation-footer"><p className="muted">원본 해시는 파일 식별용입니다. 검증은 실제 기준정보를 변경하지 않습니다.</p>
        <details><summary>검증 식별 정보</summary><dl className="detail-grid"><dt>규칙 버전</dt><dd>{preview.rulesVersion}</dd><dt>파일 해시</dt><dd className="hash-value">{workspace?.originalSha256}</dd>
          <dt>검증 ID</dt><dd className="hash-value">{preview.validationId}</dd></dl></details></div>
    </section>}
    {preview?.valid && !preview.stale && <SubmissionPanel key={`${preview.fileId}-${preview.validationId}`} request={request} onUpdated={onUpdated} onFailure={onFailure} />}
  </div>;
}
