import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import App from './App';
import { api } from './api';
import type { DecisionReceipt } from './api';

vi.mock('./api',async original=>{
 const real=await original<typeof import('./api')>();
 return {...real,api:{...real.api,me:vi.fn(),login:vi.fn(),logout:vi.fn(),equipment:vi.fn(),pending:vi.fn(),mine:vi.fn(),detail:vi.fn(),submission:vi.fn(),decide:vi.fn(),history:vi.fn()}};
});
beforeEach(()=>vi.resetAllMocks());

test('계정 전환 후 도착한 반려 응답으로 타인 요청 상세를 표시하지 않는다',async()=>{
 const privateRequest={requestId:7,revisionId:8,requesterId:1,revisionNumber:1,state:'SUBMITTED',reason:'다른 계정의 비공개 변경 사유',createdAt:'2026-10-15T00:00:00Z'};
 vi.mocked(api.me).mockResolvedValueOnce({id:2,username:'approver',roles:['APPROVER']}).mockResolvedValueOnce({id:3,username:'requester_b',roles:['REQUESTER']});
 vi.mocked(api.equipment).mockResolvedValue([]);vi.mocked(api.pending).mockResolvedValue([privateRequest]);vi.mocked(api.mine).mockResolvedValue([]);vi.mocked(api.history).mockResolvedValue([]);
 vi.mocked(api.detail).mockResolvedValue(privateRequest);vi.mocked(api.login).mockResolvedValue(undefined);vi.mocked(api.logout).mockResolvedValue(undefined);
 vi.mocked(api.submission).mockResolvedValue({...privateRequest,rulesVersion:'catalog-v1',fileId:'file',validationId:'validation',filename:'private.csv',originalSha256:'b'.repeat(64),contentHash:'a'.repeat(64),rows:[],submissionId:'submission',submittedAt:'2026-10-15T00:00:00Z'});
 let complete!:(value:DecisionReceipt)=>void;
 vi.mocked(api.decide).mockImplementation(()=>new Promise(resolve=>{complete=resolve;}));
 render(<App/>);fireEvent.click(await screen.findByRole('button',{name:'승인 대기'}));fireEvent.click(await screen.findByRole('button',{name:'요청 7 상세'}));
 fireEvent.click(await screen.findByRole('checkbox'));fireEvent.change(screen.getByLabelText('반려 사유'),{target:{value:'재검토'}});fireEvent.click(screen.getByRole('button',{name:'반려'}));
 await waitFor(()=>expect(api.decide).toHaveBeenCalled());fireEvent.click(screen.getByRole('button',{name:'로그아웃'}));
 fireEvent.change(await screen.findByLabelText('아이디'),{target:{value:'requester_b'}});fireEvent.change(screen.getByLabelText('비밀번호'),{target:{value:'demo'}});fireEvent.click(screen.getByRole('button',{name:'로그인'}));
 fireEvent.click(await screen.findByRole('button',{name:'내 변경 요청'}));await screen.findByText('아직 작성한 요청이 없습니다.');
 await act(async()=>complete({decisionId:'decision',requestId:7,revisionId:8,contentHash:'a'.repeat(64),action:'REJECT',state:'REJECTED',actorId:2,actorUsername:'approver',reason:'재검토',processedAt:'2026-10-15T00:00:00Z',changes:[]}));
 expect(screen.queryByRole('region',{name:'변경 요청 상세'})).not.toBeInTheDocument();expect(screen.queryByText(privateRequest.reason)).not.toBeInTheDocument();
});
