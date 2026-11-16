import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
/** Attach bearer credentials and clear rejected protected sessions. */
export const authInterceptor:HttpInterceptorFn=(request,next) => {
  const auth=inject(AuthService); const login=request.url.endsWith('/auth/login'); const token=auth.token;
  const outgoing=token && !login && request.url.startsWith('/api/') ? request.clone({setHeaders:{Authorization:`Bearer ${token}`}}) : request;
  return next(outgoing).pipe(catchError((error:unknown) => { if (!login && error instanceof HttpErrorResponse && error.status===401) auth.clear(); return throwError(()=>error); }));
};
