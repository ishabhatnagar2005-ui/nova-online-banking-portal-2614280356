import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountDetailComponent } from './account-detail.component';

describe('AccountDetailComponent',()=>{
  const payload={account:{id:'savings',name:'Peso Savings',type:'DEPOSIT',maskedIdentifier:'•••• 9034',status:'ACTIVE',currency:'PHP',availableBalance:116620.10,currentBalance:116620.10,pending:185,interestRate:3.25,lastStatementDate:'2026-09-30'},demoDate:'2026-10-14',startDate:'2026-09-15',endDate:'2026-10-14',total:1,page:0,size:20,items:[{postedAt:'2026-10-09T16:45:00Z',description:'Makati Coffee Shop',category:'DINING',direction:'DEBIT',status:'PENDING',amount:185,reference:'REF-TX-882194'}]};
  beforeEach(async()=>{await TestBed.configureTestingModule({imports:[AccountDetailComponent],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),{provide:ActivatedRoute,useValue:{snapshot:{paramMap:{get:()=> 'savings'}}}}]}).compileComponents();});
  afterEach(()=>TestBed.inject(HttpTestingController).verify());
  it('renders loading, masked balances, debit labels and expandable reference',()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Loading account details');
    TestBed.inject(HttpTestingController).expectOne(r=>r.url.endsWith('/savings/transactions')).flush(payload); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('•••• 9034'); expect(f.nativeElement.textContent).toContain('185.00'); expect(f.nativeElement.textContent).toContain('Money Out'); expect(f.nativeElement.textContent).toContain('REF-TX-882194'); expect(f.nativeElement.querySelector('table')).not.toBeNull(); f.destroy();
  });
  it('combines filters, resets and validates custom dates',fakeAsync(()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); const http=TestBed.inject(HttpTestingController); http.expectOne(r=>r.url.endsWith('/transactions')).flush(payload);
    f.componentInstance.form.patchValue({search:'coffee',category:'DINING',direction:'DEBIT',preset:'7'}); tick(200);
    const req=http.expectOne(r=>r.params.get('search')==='coffee'); expect(req.request.params.get('startDate')).toBe('2026-10-08'); expect(req.request.params.get('category')).toBe('DINING'); expect(req.request.params.get('direction')).toBe('DEBIT'); req.flush({...payload,items:[],total:0}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('No transactions found');
    f.componentInstance.form.patchValue({preset:'custom',startDate:'2026-10-14',endDate:'2026-10-01'}); tick(200); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Enter a valid start and end date'); http.expectNone(r=>r.url.endsWith('/transactions'));
    f.componentInstance.reset(); tick(200); const reset=http.expectOne(r=>r.url.endsWith('/transactions')); expect(reset.request.params.has('startDate')).toBeFalse(); expect(reset.request.params.has('search')).toBeFalse(); reset.flush(payload); f.destroy();
  }));
  it('renders unavailable without account data and supports retry for errors',()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); const http=TestBed.inject(HttpTestingController);
    http.expectOne(r=>r.url.endsWith('/transactions')).flush({}, {status:403,statusText:'Forbidden'}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Account Not Available'); expect(f.nativeElement.textContent).not.toContain('9034');
    f.componentInstance.load(); http.expectOne(r=>r.url.endsWith('/transactions')).flush({}, {status:500,statusText:'Error'}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Unable to load account'); f.nativeElement.querySelector('button').click(); http.expectOne(r=>r.url.endsWith('/transactions')).flush(payload); f.destroy();
  });
  it('downloads through the authenticated API and displays a failure',()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); const http=TestBed.inject(HttpTestingController); http.expectOne(r=>r.url.endsWith('/transactions')).flush(payload); f.detectChanges();
    f.componentInstance.downloadStatement(); const request=http.expectOne(r=>r.url.endsWith('/statement/download')); expect(request.request.responseType).toBe('blob'); request.flush(new Blob(),{status:500,statusText:'Error'}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Statement download failed'); f.destroy();
  });
});
