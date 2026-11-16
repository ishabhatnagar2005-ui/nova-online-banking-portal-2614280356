import { Component } from '@angular/core';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

@Component({ standalone: true, template: '' })
class RouteTargetComponent {}

describe('authInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  const session = { token: 'abc', tokenType: 'Bearer', expiresIn: 900, user: { id: '1', username: 'miguel', fullName: 'Miguel', role: 'CUSTOMER', lastLogin: null } };

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('nova-session', JSON.stringify(session));
    TestBed.configureTestingModule({ providers: [
      provideRouter([{ path: 'home', component: RouteTargetComponent }, { path: 'login', component: RouteTargetComponent }]),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
    ] });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService);
    await TestBed.inject(Router).navigateByUrl('/home');
  });

  afterEach(() => { requests.verify(); sessionStorage.clear(); });

  it('sends bearer credentials to API requests but not login, external URLs, or anonymous requests', () => {
    http.get('/api/v1/accounts').subscribe();
    const api = requests.expectOne('/api/v1/accounts');
    expect(api.request.headers.get('Authorization')).toBe('Bearer abc');
    api.flush({});
    http.post('/api/v1/auth/login', {}).subscribe();
    const login = requests.expectOne('/api/v1/auth/login');
    expect(login.request.headers.has('Authorization')).toBeFalse();
    login.flush({});
    http.get('https://example.org/data').subscribe();
    const external = requests.expectOne('https://example.org/data');
    expect(external.request.headers.has('Authorization')).toBeFalse();
    external.flush({});
    sessionStorage.removeItem('nova-session');
    http.get('/api/v1/accounts').subscribe();
    const anonymous = requests.expectOne('/api/v1/accounts');
    expect(anonymous.request.headers.has('Authorization')).toBeFalse();
    anonymous.flush({});
  });

  it('clears the session and routes to login on a protected 401 while propagating the error', fakeAsync(() => {
    let status = 0;
    http.get('/api/v1/accounts').subscribe({ error: e => status = e.status });
    requests.expectOne('/api/v1/accounts').flush({}, { status: 401, statusText: 'Unauthorized' });
    tick();
    expect(status).toBe(401);
    expect(sessionStorage.getItem('nova-session')).toBeNull();
    expect(TestBed.inject(AuthService).token).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
  }));

  it('preserves the session and route on a login 401 and on a protected server error', fakeAsync(() => {
    let loginStatus = 0;
    http.post('/api/v1/auth/login', {}).subscribe({ error: e => loginStatus = e.status });
    requests.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    tick();
    expect(loginStatus).toBe(401);
    expect(JSON.parse(sessionStorage.getItem('nova-session')!)).toEqual(session);
    expect(TestBed.inject(AuthService).token).toBe('abc');
    expect(TestBed.inject(Router).url).toBe('/home');

    let serverStatus = 0;
    http.get('/api/v1/accounts').subscribe({ error: e => serverStatus = e.status });
    requests.expectOne('/api/v1/accounts').flush({}, { status: 500, statusText: 'Server Error' });
    tick();
    expect(serverStatus).toBe(500);
    expect(JSON.parse(sessionStorage.getItem('nova-session')!)).toEqual(session);
    expect(TestBed.inject(AuthService).token).toBe('abc');
    expect(TestBed.inject(Router).url).toBe('/home');
  }));
});
