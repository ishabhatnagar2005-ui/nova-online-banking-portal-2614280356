import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
export interface Account { id:string; name:string; type:'DEPOSIT'|'CREDIT_CARD'|'LOAN'; maskedIdentifier:string; status:string; availableBalance:number|null; currentBalance:number|null; creditLimit:number|null; availableCredit:number|null; interestRate:number|null; lastStatementDate:string|null; }
export interface Portfolio { customer:string; greeting:string; lastLogin:string|null; relationshipBalance:number; accounts:Account[]; }
/** Load the signed-in customer's masked account summary. */
@Injectable({providedIn:'root'}) export class PortfolioService {
  constructor(private http:HttpClient) {}
  summary():Observable<Portfolio> { return this.http.get<Portfolio>('/api/v1/portfolio/summary'); }
}
