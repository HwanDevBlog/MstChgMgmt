import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

const header='operation,equipment_code,name,category,daily_fee_krw,active\n';
async function login(page:Page,username:string){await page.goto('/');await page.getByLabel('아이디').fill(username);await page.getByLabel('비밀번호').fill(username === 'tester' ? '1' : 'LocalDemo!2026');await page.getByRole('button',{name:'로그인',exact:true}).click();await page.getByRole('heading',{name:'기준정보 목록',exact:true}).waitFor();}
async function submit(page:Page,csv:string){
 await page.getByRole('button',{name:'내 변경 요청',exact:true}).click();await page.getByLabel('변경 사유',{exact:true}).fill('전체 업무 흐름 검증');
 const created=page.waitForResponse(r=>r.url().endsWith('/api/requests')&&r.request().method()==='POST');await page.getByRole('button',{name:'요청 임시 저장'}).click();const request=await(await created).json();
 await page.getByLabel('CSV 파일 선택').setInputFiles({name:'workflow.csv',mimeType:'text/csv',buffer:Buffer.from(csv)});await page.getByRole('button',{name:'파일 업로드',exact:true}).click();await expect(page.getByText('workflow.csv',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'검증하기',exact:true}).click();await expect(page.getByText('검증 완료',{exact:true})).toBeVisible();await page.getByRole('button',{name:'제출 내용 확인',exact:true}).click();await page.getByRole('checkbox').check();
 await page.getByRole('button',{name:'승인 요청 제출',exact:true}).click();await expect(page.getByRole('region',{name:'고정 제출본'})).toBeVisible();return request;
}
test('요청자 제출→다른 승인자 반영→조회자 이력과 실제 버전 확인이 연결된다',async({browser})=>{
 const ownerContext=await browser.newContext();const approverContext=await browser.newContext();const viewerContext=await browser.newContext();
 const owner=await ownerContext.newPage();const approver=await approverContext.newPage();const viewer=await viewerContext.newPage();
 const code='EQ-'+Date.now().toString(36).slice(-6).toUpperCase();
 await login(owner,'tester');const request=await submit(owner,header+`CREATE,${code},브라우저 신규 장비,ACCESSORY,1000,true\n`);
 await login(approver,'approver');await approver.getByRole('button',{name:'승인 대기',exact:true}).click();await approver.getByRole('button',{name:`요청 ${request.requestId} 상세`,exact:true}).click();
 const snapshot=await(await approver.request.get(`/api/requests/${request.requestId}/revisions/${request.revisionId}/submission`)).json();
 await expect(approver.getByRole('table',{name:`${code} 제출 전후 값`})).toContainText('1,000원');await expect(approver.getByRole('button',{name:'승인 및 반영'})).toBeDisabled();
 await approver.getByRole('checkbox').check();const sent=approver.waitForRequest(r=>r.url().endsWith('/approve')&&r.method()==='POST');await approver.getByRole('button',{name:'승인 및 반영'}).click();
 expect((await sent).postDataJSON()).toEqual({revisionId:snapshot.revisionId,contentHash:snapshot.contentHash});await expect(approver.getByRole('heading',{name:'승인 및 반영 완료'})).toBeVisible();
 const receipt=await(await approver.request.get(`/api/requests/${request.requestId}/revisions/${request.revisionId}/decision`)).json();expect(receipt.changes).toHaveLength(1);expect(receipt.changes[0].after.version).toBe('0');
 await approver.screenshot({path:'test-results/approval-desktop.png',fullPage:true});
 await login(viewer,'viewer');await viewer.getByRole('button',{name:'반영 이력',exact:true}).click();await viewer.getByRole('button',{name:`요청 ${request.requestId} 상세`,exact:true}).click();
 await expect(viewer.getByRole('table',{name:'실제 기준정보 변경 이력'})).toContainText(code);await expect(viewer.getByRole('button',{name:'승인 및 반영'})).toHaveCount(0);await expect(viewer.getByRole('link',{name:'제출 원본 다운로드'})).toHaveCount(0);
 await viewer.setViewportSize({width:320,height:740});expect(await viewer.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await viewer.screenshot({path:'test-results/applied-history-mobile.png',fullPage:true});
 // 기존 장비의 증가 버전도 새 시연 장비를 대상으로 확인해 초기 기준정보를 보존한다.
 await owner.getByRole('button',{name:'기준정보 목록',exact:true}).click();const update=await submit(owner,header+`UPDATE,${code},브라우저 신규 장비,ACCESSORY,2000,true\n`);
 await approver.getByRole('button',{name:'목록 새로고침',exact:true}).click();await approver.getByRole('button',{name:`요청 ${update.requestId} 상세`,exact:true}).click();await approver.getByRole('checkbox').check();await approver.getByRole('button',{name:'승인 및 반영'}).click();
 await expect(approver.getByRole('heading',{name:'승인 및 반영 완료'})).toBeVisible();const actual=await(await owner.request.get('/api/equipment')).json();expect(actual.find((r:{code:string})=>r.code===code)).toMatchObject({dailyFeeKrw:'2000',version:'1'});
 await ownerContext.close();await approverContext.close();await viewerContext.close();
});

test('반려→새 리비전→과거 제출본 조회→재제출→철회가 연결된다',async({browser})=>{
 const ownerContext=await browser.newContext();const approverContext=await browser.newContext();const owner=await ownerContext.newPage();const approver=await approverContext.newPage();
 const code='EQ-'+(Date.now()+1234).toString(36).slice(-6).toUpperCase();await login(owner,'requester_b');const request=await submit(owner,header+`CREATE,${code},재검토 장비,AUDIO,1000,true\n`);
 const original=await(await owner.request.get(`/api/requests/${request.requestId}/revisions/${request.revisionId}/submission`)).json();
 await login(approver,'approver_b');await approver.getByRole('button',{name:'승인 대기',exact:true}).click();await approver.getByRole('button',{name:`요청 ${request.requestId} 상세`,exact:true}).click();await approver.getByRole('checkbox').check();
 await expect(approver.getByRole('button',{name:'반려',exact:true})).toBeDisabled();await approver.getByLabel('반려 사유',{exact:true}).fill('분류 재검토');await approver.getByRole('button',{name:'반려',exact:true}).click();await expect(approver.getByRole('heading',{name:'반려됨'})).toBeVisible();
 await owner.getByRole('button',{name:'목록 새로고침',exact:true}).click();await owner.getByRole('button',{name:`요청 ${request.requestId} 상세`,exact:true}).click();await owner.getByRole('button',{name:'새 리비전 작성',exact:true}).click();
 await expect(owner.getByLabel('CSV 파일 선택')).toBeVisible();await expect(owner.getByText('검증 완료',{exact:true})).toHaveCount(0);await owner.getByRole('button',{name:'리비전 1 보기',exact:true}).click();
 const preserved=await(await owner.request.get(`/api/requests/${request.requestId}/revisions/${request.revisionId}/submission`)).json();expect(preserved).toEqual(original);await expect(owner.getByRole('button',{name:'새 리비전 작성',exact:true})).toHaveCount(0);
 await owner.getByRole('button',{name:'리비전 2 보기',exact:true}).click();await owner.getByRole('button',{name:'검증하기',exact:true}).click();await expect(owner.getByText('검증 완료',{exact:true})).toBeVisible();await owner.getByRole('button',{name:'제출 내용 확인',exact:true}).click();await owner.getByRole('checkbox').check();await owner.getByRole('button',{name:'승인 요청 제출',exact:true}).click();
 await owner.getByLabel('철회 사유 (선택)').fill('변경안 조정');await owner.getByRole('button',{name:'요청 철회',exact:true}).click();await expect(owner.getByRole('heading',{name:'철회됨'})).toBeVisible();await owner.getByRole('button',{name:'새 리비전 작성',exact:true}).click();await expect(owner.getByRole('button',{name:'리비전 3 보기',exact:true})).toBeVisible();
 const equipment=await(await owner.request.get('/api/equipment')).json();expect(equipment.some((r:{code:string})=>r.code===code)).toBe(false);
 await ownerContext.close();await approverContext.close();
});

test('계정 전환 전에 시작한 반려 응답이 새 계정의 요청 화면에 남지 않는다',async({browser})=>{
 const ownerContext=await browser.newContext();const approverContext=await browser.newContext();const owner=await ownerContext.newPage();const approver=await approverContext.newPage();
 const code='EQ-'+(Date.now()+4321).toString(36).slice(-6).toUpperCase();await login(owner,'tester');const request=await submit(owner,header+`CREATE,${code},응답 지연 확인,AUDIO,1000,true\n`);
 await login(approver,'approver');await approver.getByRole('button',{name:'승인 대기',exact:true}).click();await approver.getByRole('button',{name:`요청 ${request.requestId} 상세`,exact:true}).click();
 let release!:()=>void;let done!:()=>void;const gate=new Promise<void>(resolve=>{release=resolve});const serverDone=new Promise<void>(resolve=>{done=resolve});
 await approver.route('**/reject',async route=>{const response=await route.fetch();expect(response.status()).toBe(200);done();await gate;await route.fulfill({response});});
 await approver.getByRole('checkbox').check();await approver.getByLabel('반려 사유',{exact:true}).fill('지연 응답 검증');await approver.getByRole('button',{name:'반려',exact:true}).click();await serverDone;
 await approver.getByRole('button',{name:'로그아웃',exact:true}).click();await approver.getByLabel('아이디').fill('requester_b');await approver.getByLabel('비밀번호').fill('LocalDemo!2026');await approver.getByRole('button',{name:'로그인',exact:true}).click();
 await approver.getByRole('button',{name:'내 변경 요청',exact:true}).click();await expect(approver.getByText('목록을 불러오고 있습니다…')).toHaveCount(0);
 const delivered=approver.waitForResponse(response=>response.url().endsWith('/reject'));release();await (await delivered).finished();await approver.evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));
 await expect(approver.getByRole('region',{name:'변경 요청 상세'})).toHaveCount(0);await expect(approver.getByRole('region',{name:'고정 제출본'})).toHaveCount(0);
 await ownerContext.close();await approverContext.close();
});
