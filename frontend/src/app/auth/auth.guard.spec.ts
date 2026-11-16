import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const auth: {token:string|null} = {token:null};
  beforeEach(() => { auth.token = null; TestBed.configureTestingModule({providers:[provideRouter([]), {provide:AuthService,useValue:auth}]}); });
  it('allows a session and redirects an anonymous visitor to login', () => {
    const run = () => TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    expect(TestBed.inject(Router).serializeUrl(run() as import('@angular/router').UrlTree)).toBe('/login');
    auth.token = 'valid'; expect(run()).toBeTrue();
  });
});
