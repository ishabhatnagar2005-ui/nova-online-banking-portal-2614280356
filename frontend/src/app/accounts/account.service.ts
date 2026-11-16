import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AccountDetail { id:string; name:string; type:string; maskedIdentifier:string; status:string; currency:string; availableBalance:number|null; currentBalance:number|null; pending:number; interestRate:number|null; lastStatementDate:string|null; }
export interface Transaction { postedAt:string; description:string; category:string; direction:'CREDIT'|'DEBIT'; status:string; amount:number; reference:string; }
export interface Transactions { account:AccountDetail; demoDate:string; startDate:string; endDate:string; total:number; page:number; size:number; items:Transaction[]; }
/** Query the authenticated account API and retrieve statement bytes. */
@Injectable({providedIn:'root'}) export class AccountService {
  constructor(private http:HttpClient) {}
  transactions(id:string, filters:Record<string,string>):Observable<Transactions> {
    let params=new HttpParams(); for (const [key,value] of Object.entries(filters)) if(value) params=params.set(key,value);
    return this.http.get<Transactions>(`/api/v1/accounts/${encodeURIComponent(id)}/transactions`,{params});
  }
  statement(id:string):Observable<Blob> { return this.http.get(`/api/v1/accounts/${encodeURIComponent(id)}/statement/download`,{responseType:'blob'}); }
}
