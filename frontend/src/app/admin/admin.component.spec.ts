import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminComponent } from './admin.component';
describe('Facilitator controls',()=>{
  beforeEach(async()=>{sessionStorage.clear();await TestBed.configureTestingModule({imports:[AdminComponent],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting()]}).compileComponents();});
  it('requires confirmation and displays reset and audit results',()=>{
    const fixture=TestBed.createComponent(AdminComponent), http=TestBed.inject(HttpTestingController);fixture.detectChanges();
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([]);fixture.detectChanges();
    http.expectNone('/api/v1/admin/reset');
    const buttons=()=>Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>);
    buttons().find(b=>b.textContent?.trim()==='Review reset')!.click();fixture.detectChanges();buttons().find(b=>b.textContent?.trim()==='Confirm reset')!.click();
    http.expectOne('/api/v1/admin/reset').flush({message:'Restored'});http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([{event:'DEMO_RESET',occurredAt:'2026-10-14T00:00:00Z'}]);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Restored');expect(fixture.nativeElement.textContent).toContain('DEMO_RESET');
  });
  it('lets the user cancel or confirm a reset from the visible confirmation',()=>{
    const f=TestBed.createComponent(AdminComponent), http=TestBed.inject(HttpTestingController); f.detectChanges();
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false}); http.expectOne('/api/v1/admin/audit').flush([]); f.detectChanges();
    const button=(label:string):HTMLButtonElement => Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()===label)!;
    button('Review reset').click(); f.detectChanges(); expect(f.nativeElement.textContent).toContain('cannot be undone');
    http.expectNone('/api/v1/admin/reset'); button('Cancel').click(); f.detectChanges();
    expect(f.nativeElement.querySelector('.confirmation')).toBeNull(); http.expectNone('/api/v1/admin/reset');
    button('Review reset').click(); f.detectChanges(); button('Confirm reset').click(); f.detectChanges();
    expect(button('Confirm reset').disabled).toBeTrue();
    http.expectOne('/api/v1/admin/reset').flush({message:'Restored'});
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false}); http.expectOne('/api/v1/admin/audit').flush([{event:'DEMO_RESET',occurredAt:'2026-10-14T00:00:00Z'}]); f.detectChanges();
    expect(f.nativeElement.querySelector('.confirmation')).toBeNull(); expect(f.nativeElement.textContent).toContain('Restored'); expect(f.nativeElement.textContent).toContain('DEMO_RESET');
  });
  it('reports unavailable controls and reset errors',()=>{
    const fixture=TestBed.createComponent(AdminComponent),http=TestBed.inject(HttpTestingController);fixture.detectChanges();http.expectOne('/api/v1/admin/simulation').flush({},{status:403,statusText:'Forbidden'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('unavailable');
    (Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Review reset')!).click();fixture.detectChanges();
    (Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Confirm reset')!).click();http.expectOne('/api/v1/admin/reset').flush({},{status:409,statusText:'Conflict'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Reset failed');
  });
  it('toggles the isolated simulation and reports successful and simulated probes',()=>{
    const f=TestBed.createComponent(AdminComponent),http=TestBed.inject(HttpTestingController);f.detectChanges();
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([]);f.detectChanges();
    const button=(label:string)=>Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()===label)!;
    button('Run probe').click();http.expectOne('/api/v1/admin/simulation/probe').flush({message:'Demonstration probe is available.'});
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([{event:'SIMULATION_PROBE',occurredAt:'2026-10-14T00:00:00Z'}]);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Demonstration probe is available.');expect(f.nativeElement.textContent).toContain('SIMULATION_PROBE');
    button('Enable simulated error').click();const toggle=http.expectOne('/api/v1/admin/simulation');expect(toggle.request.body).toEqual({enabled:true});toggle.flush({enabled:true});
    http.expectOne('/api/v1/admin/simulation').flush({enabled:true});http.expectOne('/api/v1/admin/audit').flush([{event:'SIMULATION_ENABLED',occurredAt:'2026-10-14T00:00:00Z'}]);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Simulation is on');expect(f.nativeElement.textContent).toContain('SIMULATION_ENABLED');
    button('Run probe').click();http.expectOne('/api/v1/admin/simulation/probe').flush({},{status:503,statusText:'Unavailable'});
    http.expectOne('/api/v1/admin/simulation').flush({enabled:true});http.expectOne('/api/v1/admin/audit').flush([{event:'SIMULATED_ERROR',occurredAt:'2026-10-14T00:00:00Z'}]);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Simulated service error; no customer service was affected.');expect(f.nativeElement.textContent).toContain('SIMULATED_ERROR');
    button('Disable simulated error').click();const disable=http.expectOne('/api/v1/admin/simulation');expect(disable.request.body).toEqual({enabled:false});disable.flush({enabled:false});
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([]);f.detectChanges();expect(f.nativeElement.textContent).toContain('Simulation is off');http.verify();
  });
  it('shows unexpected toggle and probe errors and permits a retry',()=>{
    const f=TestBed.createComponent(AdminComponent),http=TestBed.inject(HttpTestingController);f.detectChanges();
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([]);f.detectChanges();
    const button=(label:string)=>Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()===label)!;
    button('Enable simulated error').click();http.expectOne('/api/v1/admin/simulation').flush({},{status:500,statusText:'Error'});f.detectChanges();expect(f.nativeElement.textContent).toContain('Unable to change simulation.');
    button('Run probe').click();http.expectOne('/api/v1/admin/simulation/probe').flush({},{status:500,statusText:'Error'});f.detectChanges();expect(f.nativeElement.textContent).toContain('Probe failed unexpectedly.');
    button('Run probe').click();http.expectOne('/api/v1/admin/simulation/probe').flush({message:'Available'});http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([]);f.detectChanges();expect(f.nativeElement.textContent).toContain('Available');http.verify();
  });
  it('shows audit loading failure and recovers the audit trail through Retry',()=>{
    const f=TestBed.createComponent(AdminComponent),http=TestBed.inject(HttpTestingController);f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Loading controls');
    http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush({}, {status:500,statusText:'Error'});f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Unable to load audit events.');
    (Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b=>b.textContent?.trim()==='Retry')!).click();f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Loading controls');http.expectOne('/api/v1/admin/simulation').flush({enabled:true});http.expectOne('/api/v1/admin/audit').flush([{event:'SIMULATION_ENABLED',occurredAt:'2026-10-14T00:00:00Z'}]);f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]')).toBeNull();expect(f.nativeElement.textContent).toContain('Simulation is on');expect(f.nativeElement.textContent).toContain('SIMULATION_ENABLED');http.verify();f.destroy();
  });
});
