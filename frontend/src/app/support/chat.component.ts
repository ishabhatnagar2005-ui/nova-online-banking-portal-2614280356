import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { SupportService } from './support.service';

/** Provide a responsive, scripted assistant for the signed-in session. */
@Component({selector:'app-chat',standalone:true,imports:[CommonModule,ReactiveFormsModule,RouterLink],templateUrl:'./chat.component.html',styleUrls:['./chat.component.css']})
export class ChatComponent implements OnDestroy {
  open=false; sending=false; error=false; readonly input=new FormControl('',{nonNullable:true});
  readonly topics=['Account Balance','Card Help','Offers & Promos','Speak to Agent'];
  messages:{text:string;from:'you'|'assistant';actionPath?:string}[]=[]; private request?:Subscription;
  constructor(private service:SupportService) {}
  /** Send one message and display the server's scripted reply. */
  send(text:string):void { const value=text.trim(); if(!value || value.length>500 || this.sending)return; this.input.setValue('');this.messages.push({text:value,from:'you'});this.sending=true;this.error=false;this.request=this.service.chat(value).subscribe({next:r=>{this.messages.push({text:r.message,from:'assistant',actionPath:r.actionPath});this.sending=false;},error:()=>{this.error=true;this.sending=false;}}); }
  /** Close and erase the conversation. */
  close():void { this.request?.unsubscribe();this.open=false;this.sending=false;this.error=false;this.messages=[];this.input.setValue(''); }
  ngOnDestroy():void { this.request?.unsubscribe(); }
}
