import { test, expect } from '@playwright/test';
test('facilitator resets fixtures and switches only by signing in again',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error') errors.push(m.text());});
  await page.goto('/login');await page.getByLabel('User ID').fill('admin');await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Facilitator controls'})).toBeVisible();await page.getByRole('button',{name:'Review reset'}).click();
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/reset') && r.status()===200);await page.getByRole('button',{name:'Confirm reset'}).click();const result=await (await response).json();await expect(page.getByRole('status').filter({hasText:result.message})).toBeVisible();await expect(page.getByText('DEMO_RESET')).toBeVisible();
  await page.getByRole('button',{name:'Sign out to switch persona'}).click();await expect(page).toHaveURL(/login/);await page.getByLabel('User ID').fill('miguel');await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByRole('button',{name:'Sign in',exact:true}).click();await expect(page.getByRole('heading',{name:/Welcome, Miguel/})).toBeVisible();await page.goto('/admin');await expect(page).not.toHaveURL(/\/admin/);expect(errors).toEqual([]);
});
