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
    fixture.componentInstance.reset();http.expectNone('/api/v1/admin/reset');fixture.componentInstance.confirming=true;fixture.componentInstance.reset();
    http.expectOne('/api/v1/admin/reset').flush({message:'Restored'});http.expectOne('/api/v1/admin/simulation').flush({enabled:false});http.expectOne('/api/v1/admin/audit').flush([{event:'DEMO_RESET',occurredAt:'2026-10-14T00:00:00Z'}]);fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Restored');expect(fixture.nativeElement.textContent).toContain('DEMO_RESET');
  });
  it('reports unavailable controls and reset errors',()=>{
    const fixture=TestBed.createComponent(AdminComponent),http=TestBed.inject(HttpTestingController);fixture.detectChanges();http.expectOne('/api/v1/admin/simulation').flush({},{status:403,statusText:'Forbidden'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('unavailable');
    fixture.componentInstance.confirming=true;fixture.componentInstance.reset();http.expectOne('/api/v1/admin/reset').flush({},{status:409,statusText:'Conflict'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Reset failed');
  });
});
