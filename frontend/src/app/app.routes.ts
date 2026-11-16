import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
export const routes:Routes=[{path:'login',loadComponent:()=>import('./auth/login.component').then(m=>m.LoginComponent)},{path:'home',canActivate:[authGuard],loadComponent:()=>import('./portfolio/portfolio.component').then(m=>m.PortfolioComponent)},{path:'accounts',canActivate:[authGuard],loadComponent:()=>import('./portfolio/portfolio.component').then(m=>m.PortfolioComponent)},{path:'',pathMatch:'full',redirectTo:'login'},{path:'**',redirectTo:'login'}];
