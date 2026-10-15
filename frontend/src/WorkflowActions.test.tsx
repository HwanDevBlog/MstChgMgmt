import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import WorkflowActions from './WorkflowActions';
import { api, ApiError } from './api';
import type { Snapshot } from './api';

vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:{decide:vi.fn(),decision:vi.fn(),newDraft:vi.fn()}}));
const request={requestId:1,revisionId:2,requesterId:1,revisionNumber:1,state:'SUBMITTED',reason:'장비 조정',createdAt:'2026-10-15T00:00:00Z'};
const snapshot:Snapshot={...request,rulesVersion:'catalog-v1',fileId:'file-one',validationId:'validation-one',filename:'catalog.csv',originalSha256:'b'.repeat(64),contentHash:'a'.repeat(64),rows:[],submissionId:'submitted',submittedAt:'2026-10-15T00:00:00Z'};
const approver={id:2,username:'approver',roles:['APPROVER']};
beforeEach(()=>vi.resetAllMocks());

test('확인 체크 후 화면의 제출본 식별자로 승인하고 실제 처리 상태를 갱신한다',async()=>{
 const updated=vi.fn();vi.mocked(api.decide).mockResolvedValue({decisionId:'receipt',requestId:1,revisionId:2,contentHash:snapshot.contentHash,action:'APPROVE',state:'APPLIED',actorId:2,actorUsername:'approver',reason:'',processedAt:'2026-10-15T00:00:00Z',changes:[]});
 render(<WorkflowActions request={request} snapshot={snapshot} user={approver} isCurrent onUpdated={updated} onFailure={vi.fn()} />);
 expect(screen.getByRole('button',{name:'승인 및 반영'})).toBeDisabled();fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'승인 및 반영'}));
 await waitFor(()=>expect(api.decide).toHaveBeenCalledWith(snapshot,'approve',undefined));
 expect(updated).toHaveBeenCalledWith(expect.objectContaining({state:'APPLIED'}));
});
test('충돌 시 제출본을 바꾸거나 완료 상태로 전환하지 않는다',async()=>{
 const updated=vi.fn();vi.mocked(api.decide).mockRejectedValue(new ApiError(409,'CATALOG_CONFLICT'));
 render(<WorkflowActions request={request} snapshot={snapshot} user={approver} isCurrent onUpdated={updated} onFailure={vi.fn()} />);
 fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'승인 및 반영'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('새 리비전');expect(updated).not.toHaveBeenCalled();
});
test('이중 역할 소유자는 자기 승인 대신 철회만 가능하고 과거 리비전은 수정할 수 없다',()=>{
 const owner={id:1,username:'dual',roles:['REQUESTER','APPROVER']};
 const {rerender}=render(<WorkflowActions request={request} snapshot={snapshot} user={owner} isCurrent onUpdated={vi.fn()} onFailure={vi.fn()} />);
 expect(screen.queryByRole('button',{name:'승인 및 반영'})).not.toBeInTheDocument();expect(screen.getByRole('button',{name:'요청 철회'})).toBeInTheDocument();
 rerender(<WorkflowActions request={request} snapshot={snapshot} user={owner} isCurrent={false} onUpdated={vi.fn()} onFailure={vi.fn()} />);
 expect(screen.queryByRole('button',{name:'요청 철회'})).not.toBeInTheDocument();
});
