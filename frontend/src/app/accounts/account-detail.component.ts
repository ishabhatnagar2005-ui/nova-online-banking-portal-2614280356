import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, debounceTime } from 'rxjs';
import { AccountService, Transactions } from './account.service';

/** Display an owner-scoped ledger with combined filters and downloads. */
@Component({selector:'app-account-detail',standalone:true,imports:[CommonModule,ReactiveFormsModule,RouterLink],templateUrl:'./account-detail.component.html',styleUrls:['./account-detail.component.css']})
export class AccountDetailComponent implements OnInit, OnDestroy {
  readonly form=new FormGroup({search:new FormControl('',{nonNullable:true}),category:new FormControl('',{nonNullable:true}),direction:new FormControl('',{nonNullable:true}),preset:new FormControl('30',{nonNullable:true}),startDate:new FormControl('',{nonNullable:true}),endDate:new FormControl('',{nonNullable:true})});
  data:Transactions|null=null; loading=true; error=false; unavailable=false; downloadError=false; invalidRange=false; page=0; readonly size=20; private id=''; private request?:Subscription; private changes?:Subscription; private download?:Subscription;
  constructor(private route:ActivatedRoute,private service:AccountService) {}
  ngOnInit():void {
    this.id=this.route.snapshot.paramMap.get('accountId') ?? '';
    this.load();
    this.changes=this.form.valueChanges.pipe(debounceTime(200)).subscribe(()=>{this.page=0;this.load();});
  }
  private dateDays(date:string,days:number):string { const d=new Date(`${date}T00:00:00Z`); d.setUTCDate(d.getUTCDate()-days); return d.toISOString().slice(0,10); }
  /** Reload the page while retaining the server's explicitly labelled demo date. */
  load():void {
    const f=this.form.getRawValue(); const demo=this.data?.demoDate;
    const params:Record<string,string>={search:f.search,category:f.category,direction:f.direction,page:String(this.page),size:String(this.size)};
    this.invalidRange=f.preset==='custom' && (!f.startDate || !f.endDate || f.startDate>f.endDate);
    if(this.invalidRange) { this.request?.unsubscribe(); this.loading=false; return; }
    if(f.preset==='custom') { params['startDate']=f.startDate; params['endDate']=f.endDate; }
    else if(demo && f.preset!=='30') { params['startDate']=this.dateDays(demo,Number(f.preset)-1); params['endDate']=demo; }
    this.request?.unsubscribe(); this.loading=true; this.error=false; this.unavailable=false;
    this.request=this.service.transactions(this.id,params).subscribe({next:data=>{this.data=data;this.loading=false;},error:(err:unknown)=>{this.data=null;this.loading=false;this.unavailable=err instanceof HttpErrorResponse && err.status===403;this.error=!this.unavailable;}});
  }
  reset():void { this.page=0; this.form.reset({search:'',category:'',direction:'',preset:'30',startDate:'',endDate:''}); }
  changePage(delta:number):void { this.page+=delta; this.load(); }
  /** Download the server-generated attachment without navigating away. */
  downloadStatement():void {
    this.downloadError=false;
    this.download=this.service.statement(this.id).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`statement-${this.data?.account.maskedIdentifier.slice(-4)}-demo.txt`;a.click();URL.revokeObjectURL(url);},error:()=>{this.downloadError=true;}});
  }
  ngOnDestroy():void { this.request?.unsubscribe();this.changes?.unsubscribe();this.download?.unsubscribe(); }
}
