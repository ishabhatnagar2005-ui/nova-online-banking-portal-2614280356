import { Component } from '@angular/core';
import { AuthService } from './auth.service';
/** Show the authenticated identity and allow explicit sign out. */
@Component({standalone:true,template:`<main class="signed-in"><p class="eyebrow">NOVA BANK · DEMONSTRATION</p><h1>Welcome, {{auth.user?.fullName}}</h1><p>You are signed in to the isolated demonstration.</p><button type="button" (click)="auth.logout()">Sign out</button></main>`,styles:[`.signed-in{max-width:800px;margin:8vh auto;padding:36px}h1{font-family:Georgia,serif}button{background:#b11116;color:white;border:0;padding:14px 24px;border-radius:8px}`]})
export class HomeComponent { constructor(public auth:AuthService) {} }
