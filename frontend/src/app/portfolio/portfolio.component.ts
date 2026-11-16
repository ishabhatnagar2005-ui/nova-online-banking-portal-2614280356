import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Account, Portfolio, PortfolioService } from './portfolio.service';

/** Display the portfolio overview or grouped accounts from the API. */
@Component({selector:'app-portfolio',standalone:true,imports:[CommonModule,RouterLink,RouterLinkActive],templateUrl:'./portfolio.component.html',styleUrls:['./portfolio.component.css']})
export class PortfolioComponent implements OnInit, OnDestroy {
  data:Portfolio|null=null; loading=true; error=false; private request?:Subscription;
  readonly groups=[{type:'DEPOSIT',label:'Deposits'},{type:'CREDIT_CARD',label:'Credit cards'},{type:'LOAN',label:'Loans'}];
  constructor(private portfolio:PortfolioService, public auth:AuthService, private router:Router) {}
  get grouped():boolean { return this.router.url.split('?')[0]==='/accounts'; }
  ngOnInit():void { this.load(); }
  /** Retry loading without showing stale account data. */
  load():void { this.request?.unsubscribe(); this.loading=true; this.error=false; this.data=null; this.request=this.portfolio.summary().subscribe({next:data=>{this.data=data;this.loading=false;},error:()=>{this.error=true;this.loading=false;}}); }
  byType(type:string):Account[] { return this.data?.accounts.filter(a=>a.type===type) ?? []; }
  ngOnDestroy():void { this.request?.unsubscribe(); }
}
