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
    expect(f.nativeElement.textContent).toContain('•••• 9034'); expect(f.nativeElement.textContent).toContain('185.00'); expect(f.nativeElement.textContent).toContain('Money Out'); expect(f.nativeElement.querySelector('table')).not.toBeNull();
    const details:HTMLDetailsElement=f.nativeElement.querySelector('details');expect(details.open).toBeFalse();(details.querySelector('summary') as HTMLElement).click();expect(details.open).toBeTrue();expect(details.textContent).toContain('REF-TX-882194');f.destroy();
  });
  it('combines filters, resets and validates custom dates',fakeAsync(()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); const http=TestBed.inject(HttpTestingController); http.expectOne(r=>r.url.endsWith('/transactions')).flush(payload);
    f.detectChanges();
    const search:HTMLInputElement=f.nativeElement.querySelector('#search'),category:HTMLSelectElement=f.nativeElement.querySelector('#category'),direction:HTMLSelectElement=f.nativeElement.querySelector('#direction'),preset:HTMLSelectElement=f.nativeElement.querySelector('#preset');
    search.value='coffee';search.dispatchEvent(new Event('input'));category.value='DINING';category.dispatchEvent(new Event('change'));direction.value='DEBIT';direction.dispatchEvent(new Event('change'));preset.value='7';preset.dispatchEvent(new Event('change'));tick(200);
    const req=http.expectOne(r=>r.params.get('search')==='coffee'); expect(req.request.params.get('startDate')).toBe('2026-10-08'); expect(req.request.params.get('category')).toBe('DINING'); expect(req.request.params.get('direction')).toBe('DEBIT'); req.flush({...payload,items:[],total:0}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('No transactions found');
    preset.value='custom';preset.dispatchEvent(new Event('change'));f.detectChanges();const start:HTMLInputElement=f.nativeElement.querySelector('#start'),end:HTMLInputElement=f.nativeElement.querySelector('#end');start.value='2026-10-14';start.dispatchEvent(new Event('input'));end.value='2026-10-01';end.dispatchEvent(new Event('input'));tick(200); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Enter a valid start and end date'); http.expectNone(r=>r.url.endsWith('/transactions'));
    (Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Reset filters')!).click();tick(200); const reset=http.expectOne(r=>r.url.endsWith('/transactions')); expect(reset.request.params.has('startDate')).toBeFalse(); expect(reset.request.params.has('search')).toBeFalse(); reset.flush(payload);f.detectChanges();expect(search.value).toBe('');expect(category.value).toBe('');expect(direction.value).toBe('');expect(preset.value).toBe('30');expect(f.nativeElement.textContent).toContain('Makati Coffee Shop'); f.destroy();
  }));
  it('renders unavailable without account data and supports retry for errors',()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); const http=TestBed.inject(HttpTestingController);
    http.expectOne(r=>r.url.endsWith('/transactions')).flush({}, {status:403,statusText:'Forbidden'}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Account Not Available'); expect(f.nativeElement.textContent).not.toContain('9034');
    expect(f.nativeElement.querySelector('button')).toBeNull();f.destroy();
    const retryFixture=TestBed.createComponent(AccountDetailComponent);retryFixture.detectChanges();http.expectOne(r=>r.url.endsWith('/transactions')).flush({}, {status:500,statusText:'Error'}); retryFixture.detectChanges(); expect(retryFixture.nativeElement.textContent).toContain('Unable to load account'); retryFixture.nativeElement.querySelector('button').click(); http.expectOne(r=>r.url.endsWith('/transactions')).flush(payload);retryFixture.detectChanges();expect(retryFixture.nativeElement.textContent).toContain('Peso Savings');expect(retryFixture.nativeElement.textContent).not.toContain('Unable to load account');retryFixture.destroy();
  });
  it('downloads through the authenticated API and displays a failure',()=>{
    const f=TestBed.createComponent(AccountDetailComponent); f.detectChanges(); const http=TestBed.inject(HttpTestingController); http.expectOne(r=>r.url.endsWith('/transactions')).flush(payload); f.detectChanges();
    (Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Download statement')!).click(); const request=http.expectOne(r=>r.url.endsWith('/statement/download')); expect(request.request.responseType).toBe('blob'); request.flush(new Blob(),{status:500,statusText:'Error'}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Statement download failed'); f.destroy();
  });
});
