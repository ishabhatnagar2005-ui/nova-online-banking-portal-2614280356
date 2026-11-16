import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
export interface User { id:string; username:string; fullName:string; role:string; lastLogin:string|null; }
export interface LoginResponse { token:string; tokenType:string; expiresIn:number; user:User; }
/** Maintain a tab-scoped session and synchronize activity with the server. */
@Injectable({providedIn:'root'})
export class AuthService implements OnDestroy {
  private readonly key='nova-session'; private timer: ReturnType<typeof setInterval>; private lastActivity=Date.now(); private lastPing=0;
  warningSeconds=0; private readonly handler=():void => this.activity();
  constructor(private http:HttpClient, private router:Router, zone:NgZone) {
    for (const type of ['click','keydown','mousemove']) window.addEventListener(type,this.handler,{passive:true});
    this.timer=zone.runOutsideAngular(() => setInterval(() => zone.run(() => this.tick()),1000));
  }
  get token():string|null { try { return (JSON.parse(sessionStorage.getItem(this.key) || 'null') as LoginResponse|null)?.token ?? null; } catch { return null; } }
  get user():User|null { try { return (JSON.parse(sessionStorage.getItem(this.key) || 'null') as LoginResponse|null)?.user ?? null; } catch { return null; } }
  login(username:string,password:string):Observable<LoginResponse> { return this.http.post<LoginResponse>('/api/v1/auth/login',{username,password}).pipe(tap(result => { sessionStorage.setItem(this.key,JSON.stringify(result)); this.lastActivity=Date.now(); this.lastPing=Date.now(); this.warningSeconds=0; })); }
  logout():void { if (!this.token) { this.clear(); return; } this.http.post('/api/v1/auth/logout',{}).subscribe({next:()=>this.clear(),error:()=>this.clear()}); }
  clear():void { sessionStorage.removeItem(this.key); this.warningSeconds=0; void this.router.navigate(['/login']); }
  /** Continue the session only when the server confirms activity. */
  continueSession():void { this.ping(); }
  private activity():void { if (!this.token || this.warningSeconds>0) return; this.lastActivity=Date.now(); if (Date.now()-this.lastPing>30000) this.ping(); }
  private ping():void { this.lastPing=Date.now(); this.http.post('/api/v1/auth/activity',{}).subscribe({next:()=>{this.lastActivity=Date.now();this.warningSeconds=0;},error:()=>this.clear()}); }
  private tick():void { if (!this.token) return; const elapsed=Math.floor((Date.now()-this.lastActivity)/1000); if (elapsed>=150) { this.clear(); return; } this.warningSeconds=elapsed>=120 ? 150-elapsed : 0; }
  ngOnDestroy():void { clearInterval(this.timer); for (const type of ['click','keydown','mousemove']) window.removeEventListener(type,this.handler); }
}
