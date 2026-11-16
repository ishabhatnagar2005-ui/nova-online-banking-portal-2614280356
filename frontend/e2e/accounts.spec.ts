import { test, expect } from '@playwright/test';

test('customer filters their ledger and downloads the real statement',async({page})=>{
  const errors:string[]=[]; page.on('pageerror',e=>errors.push(e.message)); page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.goto('/login'); await page.getByLabel('User ID').fill('miguel'); await page.getByLabel('Password',{exact:true}).fill('demo-only'); await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.goto('/accounts/b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02');
  await expect(page.getByRole('heading',{name:'Peso Savings'})).toBeVisible(); await expect(page.getByLabel('Account balances')).toContainText('185.00'); await expect(page.locator('main')).toContainText('Money Out');
  await page.getByLabel('Search description or reference').fill('882194'); await page.getByLabel('Category').selectOption('DINING'); await page.getByLabel('Direction').selectOption('DEBIT');
  await expect(page.getByText('Makati Coffee Shop')).toBeVisible(); await page.getByText('Makati Coffee Shop').click(); await expect(page.getByText(/REF-TX-882194/)).toBeVisible();
  const download=page.waitForEvent('download'); await page.getByRole('button',{name:'Download statement'}).click(); expect((await download).suggestedFilename()).toBe('statement-9034-demo.txt');
  await page.getByRole('button',{name:'Reset filters'}).click(); await expect(page.getByText('Salary',{exact:true})).toBeVisible(); expect(errors).toEqual([]);
});
