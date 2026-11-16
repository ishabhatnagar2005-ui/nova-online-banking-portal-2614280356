import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Catalogue, Promotion, PromotionService } from './promotion.service';
/** Display eligible offers and their simulated interest action. */
@Component({selector:'app-promotions',standalone:true,imports:[CommonModule,RouterLink,RouterLinkActive],templateUrl:'./promotions.component.html',styleUrls:['./promotions.component.css']})
export class PromotionsComponent implements OnInit, OnDestroy {
  data:Catalogue|null=null; loading=true; error=false; sending=false; interestError=false; message=''; category='ALL'; id:string|null=null;
  private request?:Subscription; private action?:Subscription; private params?:Subscription;
  readonly categories=[{value:'ALL',label:'All Offers'},{value:'SAVINGS',label:'Savings'},{value:'CARDS',label:'Cards'},{value:'LOANS',label:'Loans'}];
  constructor(private service:PromotionService,private route:ActivatedRoute,public auth:AuthService) {}
  ngOnInit():void { this.params=this.route.paramMap.subscribe(params=>{this.id=params.get('offerId');this.message='';this.interestError=false;}); this.load(); }
  get featured():Promotion|undefined { return this.data?.promotions.find(p=>p.key==='payday-ipon-boost'); }
  get selected():Promotion|undefined { return this.data?.promotions.find(p=>p.id===this.id); }
  get filtered():Promotion[] { return this.data?.promotions.filter(p=>this.category==='ALL'||p.category===this.category) ?? []; }
  /** Reload without displaying stale or ineligible campaign details. */
  load():void { this.request?.unsubscribe();this.loading=true;this.error=false;this.data=null;this.request=this.service.list().subscribe({next:data=>{this.data=data;this.loading=false;},error:()=>{this.loading=false;this.error=true;}}); }
  /** Submit a simulation and display the server's confirmation. */
  express(promotion:Promotion):void { if(this.sending)return;this.sending=true;this.interestError=false;this.message='';this.action=this.service.interest(promotion.id).subscribe({next:result=>{this.sending=false;this.message=result.message;},error:()=>{this.sending=false;this.interestError=true;}}); }
  ngOnDestroy():void { this.request?.unsubscribe();this.action?.unsubscribe();this.params?.unsubscribe(); }
}
