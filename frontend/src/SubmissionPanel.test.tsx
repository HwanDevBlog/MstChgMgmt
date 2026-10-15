import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import SubmissionPanel from './SubmissionPanel';
import { api, ApiError } from './api';
import type { Snapshot } from './api';

vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api:{prepare:vi.fn(),submit:vi.fn(),submission:vi.fn(),saveReason:vi.fn()}}));
const request={requestId:1,revisionId:2,requesterId:1,revisionNumber:1,state:'DRAFT',reason:'요금 조정',createdAt:'2026-10-15T00:00:00Z'};
const bundle:Snapshot={...request,rulesVersion:'catalog-v1',fileId:'file-one',validationId:'validation-one',filename:'catalog.csv',originalSha256:'b'.repeat(64),contentHash:'a'.repeat(64),submissionId:null,submittedAt:null,
  rows:[{recordNumber:2,operation:'UPDATE',equipmentCode:'EQ-CAM100',kind:'CHANGED',expectedVersion:'0',before:{name:'카메라',category:'CAMERA',dailyFeeKrw:'15000',active:true,version:'0'},after:{name:'카메라',category:'CAMERA',dailyFeeKrw:'17000',active:true,version:'0'}}]};
beforeEach(()=>{vi.resetAllMocks();vi.mocked(api.prepare).mockResolvedValue(bundle);});

test('표시한 묶음을 확인한 뒤에만 제출하고 자동 재조회로 식별자를 바꾸지 않는다',async()=>{
 const updated=vi.fn();vi.mocked(api.submit).mockResolvedValue({...bundle,submissionId:'submitted',submittedAt:'2026-10-15T00:00:00Z'});
 render(<SubmissionPanel request={request} onUpdated={updated} onFailure={vi.fn()} />);
 fireEvent.click(screen.getByRole('button',{name:'제출 내용 확인'}));
 expect(await screen.findByRole('table',{name:'EQ-CAM100 제출 전후 값'})).toHaveTextContent('17,000원');
 expect(screen.getByRole('button',{name:'승인 요청 제출'})).toBeDisabled();
 fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'승인 요청 제출'}));
 await waitFor(()=>expect(api.submit).toHaveBeenCalledWith(request,bundle));
 expect(api.prepare).toHaveBeenCalledTimes(1);
 expect(updated).toHaveBeenCalledWith(expect.objectContaining({state:'SUBMITTED'}));
});

test('충돌 시 검토 내용을 유지하고 다시 확인하기 전에는 재제출하지 않는다',async()=>{
 vi.mocked(api.submit).mockRejectedValue(new ApiError(409,'SUBMISSION_MISMATCH'));
 render(<SubmissionPanel request={request} onUpdated={vi.fn()} onFailure={vi.fn()} />);
 fireEvent.click(screen.getByRole('button',{name:'제출 내용 확인'}));
 await screen.findByRole('checkbox');fireEvent.click(screen.getByRole('checkbox'));fireEvent.click(screen.getByRole('button',{name:'승인 요청 제출'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('다시 확인');
 expect(screen.getByRole('table',{name:'EQ-CAM100 제출 전후 값'})).toHaveTextContent('17,000원');
 expect(screen.getByRole('button',{name:'승인 요청 제출'})).toBeDisabled();
 expect(api.prepare).toHaveBeenCalledTimes(1);
});

test('수정한 사유를 저장하기 전에는 제출 확인을 진행할 수 없다',async()=>{
 vi.mocked(api.saveReason).mockResolvedValue({...request,reason:'사유 변경'});
 const updated=vi.fn();render(<SubmissionPanel request={request} onUpdated={updated} onFailure={vi.fn()} />);
 fireEvent.change(screen.getByLabelText('제출할 변경 사유'),{target:{value:'사유 변경'}});
 expect(screen.getByRole('button',{name:'제출 내용 확인'})).toBeDisabled();
 fireEvent.click(screen.getByRole('button',{name:'사유 저장'}));
 await waitFor(()=>expect(api.saveReason).toHaveBeenCalledWith(request,'사유 변경'));
 expect(updated).toHaveBeenCalledWith(expect.objectContaining({reason:'사유 변경'}));
});
