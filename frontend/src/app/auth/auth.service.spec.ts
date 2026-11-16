import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService session', () => {
  const session = {token:'token-1', tokenType:'Bearer', expiresIn:900, user:{id:'1', username:'miguel', fullName:'Miguel', role:'CUSTOMER', lastLogin:null}};
  let auth: AuthService;
  let http: HttpTestingController;
  let navigate: jasmine.Spy;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({providers:[provideRouter([]), provideHttpClient(), provideHttpClientTesting()]});
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { auth.ngOnDestroy(); http.verify(); sessionStorage.clear(); });

  it('stores a successful login and exposes token and user, but not on failure', () => {
    auth = TestBed.inject(AuthService);
    let failed = false;
    auth.login('miguel', 'bad').subscribe({error: () => failed = true});
    const failure = http.expectOne('/api/v1/auth/login');
    expect(failure.request.body).toEqual({username:'miguel', password:'bad'});
    failure.flush({}, {status:401, statusText:'Unauthorized'});
    expect(failed).toBeTrue(); expect(auth.token).toBeNull();
    auth.login('miguel', 'demo').subscribe();
    http.expectOne('/api/v1/auth/login').flush(session);
    expect(auth.token).toBe('token-1'); expect(auth.user).toEqual(session.user);
    expect(JSON.parse(sessionStorage.getItem('nova-session')!)).toEqual(session);
  });

  it('treats corrupted session data as unauthenticated', () => {
    auth = TestBed.inject(AuthService);
    sessionStorage.setItem('nova-session', '{bad');
    expect(auth.token).toBeNull(); expect(auth.user).toBeNull();
  });

  it('warns at 120 seconds and expires at 150 seconds without activity', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    auth.login('miguel','demo').subscribe(); http.expectOne('/api/v1/auth/login').flush(session);
    tick(119000); expect(auth.warningSeconds).toBe(0);
    tick(1000); expect(auth.warningSeconds).toBe(30);
    tick(29000); expect(auth.warningSeconds).toBe(1); expect(auth.token).toBe('token-1');
    tick(1000); expect(auth.token).toBeNull(); expect(auth.warningSeconds).toBe(0);
    expect(navigate).toHaveBeenCalledWith(['/login']); auth.ngOnDestroy();
  }));

  it('only continues a warned session after the activity endpoint succeeds', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    auth.login('miguel','demo').subscribe(); http.expectOne('/api/v1/auth/login').flush(session);
    tick(120000); expect(auth.warningSeconds).toBe(30);
    window.dispatchEvent(new Event('click')); http.expectNone('/api/v1/auth/activity');
    auth.continueSession(); expect(auth.warningSeconds).toBe(30);
    http.expectOne('/api/v1/auth/activity').flush({});
    expect(auth.warningSeconds).toBe(0); tick(30000); expect(auth.token).toBe('token-1'); auth.ngOnDestroy();
  }));

  it('clears the session when activity fails and on logout failure', () => {
    auth = TestBed.inject(AuthService);
    sessionStorage.setItem('nova-session', JSON.stringify(session));
    auth.continueSession(); http.expectOne('/api/v1/auth/activity').flush({}, {status:401,statusText:'Unauthorized'});
    expect(auth.token).toBeNull(); expect(navigate).toHaveBeenCalledWith(['/login']);
    sessionStorage.setItem('nova-session', JSON.stringify(session));
    auth.logout(); http.expectOne('/api/v1/auth/logout').flush({}, {status:500,statusText:'Error'});
    expect(auth.token).toBeNull();
  });

  it('logs out on success and skips the network when already signed out', () => {
    auth = TestBed.inject(AuthService);
    sessionStorage.setItem('nova-session', JSON.stringify(session));
    auth.logout(); http.expectOne('/api/v1/auth/logout').flush({});
    expect(auth.token).toBeNull();
    auth.logout(); http.expectNone('/api/v1/auth/logout');
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
