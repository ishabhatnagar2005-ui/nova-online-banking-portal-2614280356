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
    const f=TestBed.createComponent(SupportComponent), http=TestBed.inject(HttpTestingController);f.detectChanges();
    const search:HTMLInputElement=f.nativeElement.querySelector('#faq-search');search.value='credit';search.dispatchEvent(new Event('input'));f.detectChanges();expect(f.nativeElement.textContent).toContain('available credit');expect(f.nativeElement.textContent).not.toContain('How do I sign in');
    const submit:HTMLButtonElement=f.nativeElement.querySelector('button[type=submit]');submit.click();f.detectChanges();expect(f.nativeElement.textContent).toContain('Choose a topic.');expect(f.nativeElement.textContent).toContain('Enter a subject.');expect(f.nativeElement.textContent).toContain('Enter a message.');http.expectNone('/api/v1/support/inquiries');
    const topic:HTMLSelectElement=f.nativeElement.querySelector('#topic'),subject:HTMLInputElement=f.nativeElement.querySelector('#subject'),message:HTMLTextAreaElement=f.nativeElement.querySelector('#message');
    topic.value='Account';topic.dispatchEvent(new Event('change'));subject.value='Question';subject.dispatchEvent(new Event('input'));message.value='Example';message.dispatchEvent(new Event('input'));submit.click();
    const req=http.expectOne('/api/v1/support/inquiries');expect(req.request.method).toBe('POST');expect(req.request.body).toEqual({topic:'Account',subject:'Question',message:'Example'});req.flush({referenceNumber:'NB-example',status:'RECEIVED',estimatedResponse:'24h'});f.detectChanges();expect(f.nativeElement.textContent).toContain('NB-example');expect(f.nativeElement.textContent).toContain('No real agent response');f.destroy();
  });
  it('shows failure and permits retry',()=>{
    const f=TestBed.createComponent(SupportComponent),http=TestBed.inject(HttpTestingController);f.detectChanges();
    const topic:HTMLSelectElement=f.nativeElement.querySelector('#topic'),subject:HTMLInputElement=f.nativeElement.querySelector('#subject'),message:HTMLTextAreaElement=f.nativeElement.querySelector('#message');
    topic.value='Card';topic.dispatchEvent(new Event('change'));subject.value='Question';subject.dispatchEvent(new Event('input'));message.value='Example';message.dispatchEvent(new Event('input'));
    const submit:HTMLButtonElement=f.nativeElement.querySelector('button[type=submit]');submit.click();http.expectOne('/api/v1/support/inquiries').flush({}, {status:500,statusText:'Error'});f.detectChanges();expect(f.nativeElement.textContent).toContain('Unable to record');
    submit.click();const retry=http.expectOne('/api/v1/support/inquiries');expect(retry.request.body).toEqual({topic:'Card',subject:'Question',message:'Example'});retry.flush({referenceNumber:'NB-retry',status:'RECEIVED',estimatedResponse:'24h'});f.detectChanges();expect(f.nativeElement.textContent).toContain('NB-retry');expect(f.nativeElement.textContent).not.toContain('Unable to record');f.destroy();
  });
  it('opens chat, sends a topic, displays the reply and clears it on close',()=>{
    const f=TestBed.createComponent(ChatComponent), http=TestBed.inject(HttpTestingController); f.detectChanges();
    (f.nativeElement.querySelector('[aria-label="Open demonstration chat"]') as HTMLButtonElement).click(); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('scripted simulation');
    const topic=Array.from(f.nativeElement.querySelectorAll('.chips button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Speak to Agent')!;
    topic.click(); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Preparing a reply');
    const req=http.expectOne('/api/v1/chat'); expect(req.request.body).toEqual({message:'Speak to Agent'});
    req.flush({message:'No live agent was contacted',actionPath:'/contact'}); f.detectChanges();
    expect(f.nativeElement.querySelector('.conversation').textContent).toContain('You: Speak to Agent'); expect(f.nativeElement.querySelector('.conversation').textContent).toContain('No live agent was contacted');
    expect(f.nativeElement.querySelector('.conversation a').getAttribute('href')).toBe('/contact');
    (f.nativeElement.querySelector('[aria-label="Close chat"]') as HTMLButtonElement).click(); f.detectChanges();
    (f.nativeElement.querySelector('[aria-label="Open demonstration chat"]') as HTMLButtonElement).click(); f.detectChanges();
    expect(f.nativeElement.querySelector('.conversation').textContent).not.toContain('No live agent was contacted'); f.destroy();
  });
  it('renders script-bearing chat input and reply as text without creating markup or executing scripts',()=>{
    const f=TestBed.createComponent(ChatComponent), http=TestBed.inject(HttpTestingController);
    const marker=window as unknown as { __supportXss?: number };
    marker.__supportXss=0;
    const inputPayload='<script>window.__supportXss++</script><img src=x onerror="window.__supportXss++"><b>user text</b>';
    const replyPayload='<script>window.__supportXss++</script><img src=x onerror="window.__supportXss++"><b>reply text</b>';
    try {
      f.detectChanges();
      (f.nativeElement.querySelector('[aria-label="Open demonstration chat"]') as HTMLButtonElement).click(); f.detectChanges();
      const input:HTMLInputElement=f.nativeElement.querySelector('#chat-message');
      input.value=inputPayload; input.dispatchEvent(new Event('input')); f.detectChanges();
      expect(input.value).toBe(inputPayload);
      (f.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
      const req=http.expectOne('/api/v1/chat'); expect(req.request.body).toEqual({message:inputPayload});
      req.flush({message:replyPayload,actionPath:''}); f.detectChanges();
      const conversation:HTMLElement=f.nativeElement.querySelector('.conversation');
      const messages=conversation.querySelectorAll('p');
      expect(messages.length).toBe(3);
      expect(messages[1].textContent).toContain('You: '+inputPayload);
      expect(messages[2].textContent).toContain('Assistant: '+replyPayload);
      expect(conversation.querySelectorAll('script, img, b').length).toBe(0);
      expect(marker.__supportXss).toBe(0);
    } finally {
      f.destroy(); delete marker.__supportXss;
    }
  });
  it('renders inquiry validation, failure and a successful retry from the form',()=>{
    const f=TestBed.createComponent(SupportComponent), http=TestBed.inject(HttpTestingController); f.detectChanges();
    const form:HTMLFormElement=f.nativeElement.querySelector('form'); form.dispatchEvent(new Event('submit')); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Choose a topic.'); expect(f.nativeElement.textContent).toContain('Enter a subject.'); expect(f.nativeElement.textContent).toContain('Enter a message.'); http.expectNone('/api/v1/support/inquiries');
    const topic:HTMLSelectElement=f.nativeElement.querySelector('#topic'), subject:HTMLInputElement=f.nativeElement.querySelector('#subject'), message:HTMLTextAreaElement=f.nativeElement.querySelector('#message');
    topic.value='Account'; topic.dispatchEvent(new Event('change')); subject.value='Question'; subject.dispatchEvent(new Event('input')); message.value='Please help'; message.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit')); http.expectOne('/api/v1/support/inquiries').flush({}, {status:500,statusText:'Error'}); f.detectChanges(); expect(f.nativeElement.textContent).toContain('Unable to record');
    form.dispatchEvent(new Event('submit')); const req=http.expectOne('/api/v1/support/inquiries'); expect(req.request.body).toEqual({topic:'Account',subject:'Question',message:'Please help'}); req.flush({referenceNumber:'NB-123',status:'RECEIVED',estimatedResponse:'24h'}); f.detectChanges();
    expect(f.nativeElement.querySelector('[role=status]').textContent).toContain('NB-123'); expect(f.nativeElement.textContent).not.toContain('Unable to record'); f.destroy();
  });
  it('shows simulated handoff and errors in chat',()=>{
    const f=TestBed.createComponent(ChatComponent),http=TestBed.inject(HttpTestingController);f.detectChanges();
    (f.nativeElement.querySelector('[aria-label="Open demonstration chat"]') as HTMLButtonElement).click();f.detectChanges();
    const topic=Array.from(f.nativeElement.querySelectorAll('.chips button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Speak to Agent')!;topic.click();
    http.expectOne('/api/v1/chat').flush({message:'No live agent was contacted; no support ticket was opened.',actionPath:'/contact'});f.detectChanges();expect(f.nativeElement.textContent).toContain('No live agent');
    const balance=Array.from(f.nativeElement.querySelectorAll('.chips button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Account Balance')!;balance.click();
    http.expectOne('/api/v1/chat').flush({}, {status:500,statusText:'Error'});f.detectChanges();expect(f.nativeElement.textContent).toContain('Unable to reply');
    (f.nativeElement.querySelector('[aria-label="Close chat"]') as HTMLButtonElement).click();f.detectChanges();(f.nativeElement.querySelector('[aria-label="Open demonstration chat"]') as HTMLButtonElement).click();f.detectChanges();
    expect(f.nativeElement.querySelector('.conversation').textContent).not.toContain('No live agent');expect(f.nativeElement.textContent).not.toContain('Unable to reply');f.destroy();
  });
});
