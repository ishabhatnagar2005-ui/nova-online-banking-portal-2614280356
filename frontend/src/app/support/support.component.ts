import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Confirmation, SupportService } from './support.service';

/** Provide synthetic directory, searchable help and a simulated inquiry form. */
@Component({selector:'app-support',standalone:true,imports:[CommonModule,ReactiveFormsModule,RouterLink],templateUrl:'./support.component.html',styleUrls:['./support.component.css']})
export class SupportComponent implements OnDestroy {
  readonly search=new FormControl('',{nonNullable:true});
  readonly form=new FormGroup({topic:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(80)]}),subject:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(160)]}),message:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(4000)]})});
  readonly faqs=[
    {question:'How do I sign in to the demo?',answer:'Use a demonstration user ID and password shown on the login page. Never use real banking credentials.'},
    {question:'How do I filter transactions and export a statement?',answer:'Open an account, combine date, category, direction and search filters, then use the statement download to export a demonstration .txt file.'},
    {question:'What is the Peso Savings rate?',answer:'The sample Peso Savings rate is 3.25% p.a. An illustrative promotion offers an additional 0.50% p.a. only when eligible and valid on the demonstration date; it is not a live offer.'},
    {question:'How is available credit calculated?',answer:'The example limit PHP 50,000.00 minus PHP 8,450.00 due equals PHP 41,550.00 available credit.'}
  ];
  sending=false; error=false; confirmation:Confirmation|null=null; private request?:Subscription;
  constructor(public auth:AuthService,private service:SupportService) {}
  get filtered() { const q=this.search.value.trim().toLowerCase(); return this.faqs.filter(f=>(f.question+' '+f.answer).toLowerCase().includes(q)); }
  /** Submit valid content and show only the simulated receipt. */
  submit():void {
    this.form.markAllAsTouched();
    const value=this.form.getRawValue();
    if(this.form.invalid || !value.topic.trim() || !value.subject.trim() || !value.message.trim() || this.sending) return;
    this.sending=true;this.error=false;this.confirmation=null;
    this.request=this.service.inquire(value).subscribe({next:r=>{this.confirmation=r;this.sending=false;this.form.reset();},error:()=>{this.error=true;this.sending=false;}});
  }
  ngOnDestroy():void { this.request?.unsubscribe(); }
}
