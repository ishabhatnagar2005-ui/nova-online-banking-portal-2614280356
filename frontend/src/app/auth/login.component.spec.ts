import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { LoginComponent } from './login.component';

@Component({ standalone: true, template: '' })
class RouteTargetComponent {}

describe('LoginComponent', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([
          { path: 'login', component: RouteTargetComponent },
          { path: 'home', component: RouteTargetComponent },
          { path: 'admin', component: RouteTargetComponent },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    await TestBed.inject(Router).navigateByUrl('/login');
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows required field messages and sends no request for empty input', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('User ID is required');
    expect(fixture.nativeElement.textContent).toContain('Password is required');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(sessionStorage.getItem('nova-session')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone('/api/v1/auth/login');
  });

  it('displays demo-only credentials for both customer personas and links to contact', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const fixtures: HTMLElement = fixture.nativeElement.querySelector('.fixtures');
    expect(fixtures.textContent).toContain('miguel / demo-only');
    expect(fixtures.textContent).toContain('rosa / demo-only');
    expect(fixtures.textContent).toContain('isolated environments only');
    const contact: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/contact"]');
    expect(contact).not.toBeNull();
    expect(contact.textContent).toContain('Contact Us');
  });

  it('submits a successful customer sign-in, remembers only the ID and navigates home', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const username: HTMLInputElement = fixture.nativeElement.querySelector('#username');
    const password: HTMLInputElement = fixture.nativeElement.querySelector('#password');
    const remember: HTMLInputElement = fixture.nativeElement.querySelector('input[type=checkbox]');
    username.value = 'miguel'; username.dispatchEvent(new Event('input'));
    password.value = 'demo-only'; password.dispatchEvent(new Event('input'));
    remember.click();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeTrue();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ username: 'miguel', password: 'demo-only' });
    req.flush({ token: 'abc', tokenType: 'Bearer', expiresIn: 900, user: { id: '1', username: 'miguel', fullName: 'Miguel', role: 'CUSTOMER', lastLogin: null } });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/home');
    expect(localStorage.getItem('nova-remembered-user')).toBe('miguel');
    expect(localStorage.getItem('password')).toBeNull();
    expect(password.value).toBe('');
    expect(JSON.parse(sessionStorage.getItem('nova-session')!)).toEqual(jasmine.objectContaining({ token: 'abc', user: jasmine.objectContaining({ username: 'miguel', role: 'CUSTOMER' }) }));
  });

  it('shows a locked-account error, then routes a successful administrator sign-in to admin', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const username: HTMLInputElement = fixture.nativeElement.querySelector('#username');
    const password: HTMLInputElement = fixture.nativeElement.querySelector('#password');
    username.value = 'admin'; username.dispatchEvent(new Event('input'));
    password.value = 'demo-only'; password.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('button[type=submit]').click();
    http.expectOne('/api/v1/auth/login').flush({}, { status: 423, statusText: 'Locked' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Account locked');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(sessionStorage.getItem('nova-session')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[type=submit]').disabled).toBeFalse();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    http.expectOne('/api/v1/auth/login').flush({ token: 'a', tokenType: 'Bearer', expiresIn: 900, user: { id: '2', username: 'admin', fullName: 'Admin', role: 'ADMIN', lastLogin: null } });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/admin');
    expect(localStorage.getItem('nova-remembered-user')).toBeNull();
    expect(password.value).toBe('');
    expect(JSON.parse(sessionStorage.getItem('nova-session')!).user.role).toBe('ADMIN');
    expect(fixture.nativeElement.textContent).not.toContain('Account locked');
  });

  it('shows generic credentials error without saving a session or the password', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const username: HTMLInputElement = fixture.nativeElement.querySelector('#username');
    const password: HTMLInputElement = fixture.nativeElement.querySelector('#password');
    username.value = 'miguel'; username.dispatchEvent(new Event('input'));
    password.value = 'wrong'; password.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('input[type=checkbox]').click();
    fixture.nativeElement.querySelector('button[type=submit]').click();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login').flush({ message: 'invalid' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Invalid User ID or password');
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(sessionStorage.getItem('nova-session')).toBeNull();
    expect(localStorage.getItem('nova-remembered-user')).toBeNull();
    expect(localStorage.getItem('password')).toBeNull();
  });
});
