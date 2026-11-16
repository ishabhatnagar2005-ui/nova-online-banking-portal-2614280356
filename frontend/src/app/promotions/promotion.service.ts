import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
export interface Promotion { id:string; key:string; title:string; category:string; personalized:boolean; benefit:string; terms:string; eligibility:string; validUntil:string; }
export interface Catalogue { demoDate:string; promotions:Promotion[]; }
/** Access the authenticated campaign catalogue and simulated registration. */
@Injectable({providedIn:'root'}) export class PromotionService {
  constructor(private http:HttpClient) {}
  list():Observable<Catalogue> { return this.http.get<Catalogue>('/api/v1/promotions'); }
  interest(id:string):Observable<{message:string}> { return this.http.post<{message:string}>(`/api/v1/promotions/${encodeURIComponent(id)}/express-interest`,{}); }
}
