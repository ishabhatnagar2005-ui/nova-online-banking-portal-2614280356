import { test, expect } from '@playwright/test';
for (const persona of [{user:'miguel',name:'Miguel De La Cruz',total:'142,860.25',own:'9034',other:'2204'},{user:'rosa',name:'Rosa Santos',total:'54,300.00',own:'2204',other:'9034'}]) {
  test(`${persona.user} sees only their portfolio`,async({page})=>{
    const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
    await page.goto('/login');await page.getByLabel('User ID').fill(persona.user);await page.getByLabel('Password',{exact:true}).fill('demo-only');
    const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/portfolio/summary') && r.status()===200);
    await page.getByRole('button',{name:'Sign in',exact:true}).click();const summary=await (await response).json();
    expect(summary.customer).toBe(persona.name);await expect(page.getByRole('heading',{name:`Welcome, ${persona.name}`})).toBeVisible();
    await expect(page.getByLabel('Relationship balance')).toContainText(persona.total);await expect(page.locator('main')).toContainText(persona.own);await expect(page.locator('main')).not.toContainText(persona.other);
    await page.getByRole('link',{name:'Accounts',exact:true}).click();await expect(page.getByRole('heading',{name:'Accounts by type'})).toBeVisible();await expect(page.locator('main')).toContainText(persona.own);
    await page.getByRole('button',{name:'Sign out'}).click();await expect(page).toHaveURL(/\/login$/);expect(errors).toEqual([]);
  });
}
