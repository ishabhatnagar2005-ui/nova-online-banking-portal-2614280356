import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from './auth.service';
/** Present an accessible sign-in form for the isolated demonstration. */
@Component({selector:'app-login',standalone:true,imports:[CommonModule,ReactiveFormsModule],templateUrl:'./login.component.html',styleUrls:['./login.component.css']})
export class LoginComponent {
  showPassword=false; loading=false; error=''; language='en';
  private readonly fb=inject(FormBuilder);
  form=this.fb.nonNullable.group({username:[localStorage.getItem('nova-remembered-user') ?? '',[Validators.required,Validators.maxLength(64)]],password:['',[Validators.required,Validators.maxLength(72)]],remember:[!!localStorage.getItem('nova-remembered-user')]});
  constructor(private auth:AuthService,private router:Router) {}
  submit():void {
    this.form.markAllAsTouched(); if (this.form.invalid || this.loading) return;
    const {username,password,remember}=this.form.getRawValue(); this.loading=true; this.error='';
    this.auth.login(username,password).subscribe({next:result=>{ if (remember) localStorage.setItem('nova-remembered-user',username); else localStorage.removeItem('nova-remembered-user'); this.form.controls.password.reset(); this.loading=false; void this.router.navigate([result.user.role==='ADMIN' ? '/admin' : '/home']); },error:(err:unknown)=>{this.loading=false;this.error=err instanceof HttpErrorResponse && err.status===423 ? 'Account locked due to multiple failed login attempts. Use the Facilitator Reset to unlock.' : err instanceof HttpErrorResponse && err.status===401 ? 'Invalid User ID or password. Please try again.' : 'Unable to sign in right now. Please try again.';}});
  }
}
