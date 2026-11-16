import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
/** Keep the facilitator screen out of customer navigation; the API enforces authorization. */
export const adminGuard:CanActivateFn=()=>{
  const auth=inject(AuthService);
  return auth.token && auth.user?.role==='ADMIN' ? true : inject(Router).createUrlTree([auth.token ? '/home' : '/login']);
};
