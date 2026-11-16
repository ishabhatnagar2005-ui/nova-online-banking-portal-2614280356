import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from '../auth/auth.service';
import { SupportComponent } from './support.component';
import { ChatComponent } from './chat.component';

describe('Support simulations',()=>{
  beforeEach(async()=>{await TestBed.configureTestingModule({imports:[SupportComponent,ChatComponent],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),{provide:AuthService,useValue:{token:'test'}}]}).compileComponents();});
  afterEach(()=>TestBed.inject(HttpTestingController).verify());
  it('filters FAQs, validates and displays the recorded receipt',()=>{
    const f=TestBed.createComponent(SupportComponent);f.detectChanges();f.componentInstance.search.setValue('credit');f.detectChanges();expect(f.nativeElement.textContent).toContain('available credit');expect(f.nativeElement.textContent).not.toContain('How do I sign in');
    f.componentInstance.submit();expect(f.componentInstance.form.invalid).toBeTrue();
    f.componentInstance.form.setValue({topic:'Account',subject:'Question',message:'Example'});f.componentInstance.submit();const req=TestBed.inject(HttpTestingController).expectOne('/api/v1/support/inquiries');expect(req.request.method).toBe('POST');req.flush({referenceNumber:'NB-example',status:'RECEIVED',estimatedResponse:'24h'});f.detectChanges();expect(f.nativeElement.textContent).toContain('NB-example');expect(f.nativeElement.textContent).toContain('No real agent response');f.destroy();
  });
  it('shows failure and permits retry',()=>{const f=TestBed.createComponent(SupportComponent);f.componentInstance.form.setValue({topic:'Card',subject:'Question',message:'Example'});f.componentInstance.submit();TestBed.inject(HttpTestingController).expectOne('/api/v1/support/inquiries').flush({}, {status:500,statusText:'Error'});f.detectChanges();expect(f.nativeElement.textContent).toContain('Unable to record');f.destroy();});
  it('shows simulated handoff and errors in chat',()=>{const f=TestBed.createComponent(ChatComponent);f.componentInstance.open=true;f.componentInstance.send('Speak to Agent');const http=TestBed.inject(HttpTestingController);http.expectOne('/api/v1/chat').flush({message:'No live agent was contacted; no support ticket was opened.',actionPath:'/contact'});f.detectChanges();expect(f.nativeElement.textContent).toContain('No live agent');f.componentInstance.send('balance');http.expectOne('/api/v1/chat').flush({}, {status:500,statusText:'Error'});f.detectChanges();expect(f.nativeElement.textContent).toContain('Unable to reply');f.componentInstance.close();expect(f.componentInstance.messages.length).toBe(0);f.destroy();});
});
