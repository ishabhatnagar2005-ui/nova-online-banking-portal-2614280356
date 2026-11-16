import { test, expect } from '@playwright/test';
test('login, protected session and logout use the real API',async({page})=>{
  const errors:string[]=[]; page.on('pageerror',e=>errors.push(e.message)); page.on('console',m=>{if(m.type()==='error') errors.push(m.text());});
  await page.goto('/login'); await page.getByLabel('User ID').fill('miguel'); await page.getByLabel('Password',{exact:true}).fill('demo-only');
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login') && r.status()===200);
  await page.getByRole('button',{name:'Sign in',exact:true}).click(); const login=await (await response).json();
  await expect(page.getByRole('heading',{name:`Welcome, ${login.user.fullName}`})).toBeVisible();
  await page.getByRole('button',{name:'Sign out'}).click(); await expect(page).toHaveURL(/\/login$/); expect(errors).toEqual([]);
});
