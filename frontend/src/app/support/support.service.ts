import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Confirmation { referenceNumber:string; status:string; estimatedResponse:string; }
export interface ChatReply { message:string; actionPath:string; }
/** Call the authenticated support simulation API. */
@Injectable({providedIn:'root'}) export class SupportService {
  constructor(private http:HttpClient) {}
  inquire(request:{topic:string;subject:string;message:string}):Observable<Confirmation> { return this.http.post<Confirmation>('/api/v1/support/inquiries',request); }
  chat(message:string):Observable<ChatReply> { return this.http.post<ChatReply>('/api/v1/chat',{message}); }
}
