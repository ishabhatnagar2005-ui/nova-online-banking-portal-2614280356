import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { AuthService } from './auth/auth.service';

describe('AppComponent idle warning', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({imports:[AppComponent],providers:[provideRouter([{path:'login',children:[]}]),provideHttpClient(),provideHttpClientTesting()]}).compileComponents();
  });
  it('shows chat only for signed-in users and exposes the warning actions', fakeAsync(() => {
    const fixture=TestBed.createComponent(AppComponent),http=TestBed.inject(HttpTestingController),auth=TestBed.inject(AuthService); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-chat')).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
    sessionStorage.setItem('nova-session',JSON.stringify({token:'token'}));tick(127000);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-chat')).not.toBeNull();
    const dialog:HTMLElement=fixture.nativeElement.querySelector('[role="alertdialog"]');
    expect(dialog.textContent).toContain('Still there?'); expect(dialog.textContent).toContain('23 seconds');
    const buttons=dialog.querySelectorAll('button');expect(buttons[0].textContent).toContain('Stay signed in');expect(buttons[1].textContent).toContain('Sign out');buttons[0].click();
    const activity=http.expectOne('/api/v1/auth/activity');expect(activity.request.method).toBe('POST');activity.flush({});fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-chat')).not.toBeNull();
    tick(120000);fixture.detectChanges();const signout=fixture.nativeElement.querySelector('[role="alertdialog"] button:nth-of-type(2)') as HTMLButtonElement;signout.click();
    const logout=http.expectOne('/api/v1/auth/logout');expect(logout.request.method).toBe('POST');logout.flush({});tick();fixture.detectChanges();expect(TestBed.inject(Router).url).toBe('/login');
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-chat')).toBeNull();
    http.verify();auth.ngOnDestroy();fixture.destroy();sessionStorage.clear();
  }));
});
