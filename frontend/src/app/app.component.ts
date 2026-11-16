import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './auth/auth.service';
/** Host routing and the shared idle-session warning. */
@Component({selector:'app-root',standalone:true,imports:[RouterOutlet,CommonModule],template:`<router-outlet/><section *ngIf="auth.warningSeconds" class="idle" role="alertdialog" aria-labelledby="idle-title" aria-describedby="idle-desc"><h2 id="idle-title">Still there?</h2><p id="idle-desc">Your session will end in {{auth.warningSeconds}} seconds.</p><button (click)="auth.continueSession()">Stay signed in</button><button (click)="auth.logout()">Sign out</button></section>`,styles:[`.idle{position:fixed;bottom:24px;right:24px;max-width:320px;padding:24px;background:white;border:2px solid #b11116;border-radius:12px;box-shadow:0 10px 40px #0003;z-index:2}.idle button{margin-right:10px;padding:10px}`]})
export class AppComponent { constructor(public auth:AuthService) {} }
