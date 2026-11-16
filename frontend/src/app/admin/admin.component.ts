import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { AdminService, AuditEvent } from './admin.service';

/** Provide scoped facilitator controls and a privacy-preserving audit view. */
@Component({selector:'app-admin',standalone:true,imports:[CommonModule],templateUrl:'./admin.component.html',styleUrls:['./admin.component.css']})
export class AdminComponent implements OnInit {
  events:AuditEvent[]=[]; loading=true; busy=false; enabled=false; confirming=false; error=''; message='';
  constructor(private admin:AdminService,public auth:AuthService) {}
  ngOnInit():void { this.load(); }
  /** Refresh the control state and audit trail. */
  load():void {
    this.loading=true; this.error='';
    this.admin.simulation().subscribe({next:state=>{this.enabled=state.enabled;this.admin.audit().subscribe({next:events=>{this.events=events;this.loading=false;},error:()=>{this.error='Unable to load audit events.';this.loading=false;}});},error:()=>{this.error='Facilitator controls are unavailable. Check that demonstration mode is enabled.';this.loading=false;}});
  }
  /** Require an explicit confirmation before restoring fixtures. */
  reset():void {
    if (!this.confirming || this.busy) return;
    this.busy=true;this.error='';this.message='';
    this.admin.reset().subscribe({next:result=>{this.busy=false;this.confirming=false;this.message=result.message;this.load();},error:()=>{this.busy=false;this.error='Reset failed; no changes were applied. Check demo mode and fixture conflicts.';}});
  }
  /** Change only the dedicated simulation probe. */
  toggle():void {
    if (this.busy) return; this.busy=true;this.error='';this.message='';
    this.admin.setSimulation(!this.enabled).subscribe({next:state=>{this.busy=false;this.enabled=state.enabled;this.load();},error:()=>{this.busy=false;this.error='Unable to change simulation.';}});
  }
  /** Exercise the isolated simulation endpoint. */
  probe():void {
    this.busy=true;this.error='';this.message='';
    this.admin.probe().subscribe({next:r=>{this.busy=false;this.message=r.message;this.load();},error:(err:unknown)=>{this.busy=false;if(err instanceof HttpErrorResponse && err.status===503) {this.message='Simulated service error; no customer service was affected.';this.load();} else this.error='Probe failed unexpectedly.';}});
  }
}
