import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
export interface AuditEvent { event:string; occurredAt:string; }
export interface Message { message:string; }
/** Call the role-protected facilitator API. */
@Injectable({providedIn:'root'}) export class AdminService {
  constructor(private http:HttpClient) {}
  audit():Observable<AuditEvent[]> { return this.http.get<AuditEvent[]>('/api/v1/admin/audit'); }
  reset():Observable<Message> { return this.http.post<Message>('/api/v1/admin/reset',{}); }
  simulation():Observable<{enabled:boolean}> { return this.http.get<{enabled:boolean}>('/api/v1/admin/simulation'); }
  setSimulation(enabled:boolean):Observable<{enabled:boolean}> { return this.http.post<{enabled:boolean}>('/api/v1/admin/simulation',{enabled}); }
  probe():Observable<Message> { return this.http.post<Message>('/api/v1/admin/simulation/probe',{}); }
}
