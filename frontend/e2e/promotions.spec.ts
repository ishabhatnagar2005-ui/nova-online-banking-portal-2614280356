import { test, expect } from '@playwright/test';

test('customer discovers eligible offers and records simulated interest',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.goto('/login');await page.getByLabel('User ID').fill('miguel');await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.getByRole('link',{name:'Offers',exact:true}).click();
  await expect(page.getByText('Demonstration clock:',{exact:false})).toContainText('2026-10-14');
  await expect(page.getByRole('heading',{name:'Payday Ipon Boost'}).first()).toBeVisible();
  await page.getByRole('button',{name:'Cards',exact:true}).click();await expect(page.getByRole('heading',{name:'Cashback Week'})).toBeVisible();await expect(page.getByRole('heading',{name:'5% Dining Cashback'})).toBeVisible();
  await page.getByRole('article').filter({hasText:'Cashback Week'}).getByRole('link',{name:'View details'}).click();
  await expect(page.getByRole('heading',{name:'Terms'})).toBeVisible();await expect(page.getByText('Illustrative cashback capped',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Express Interest (Simulated)'}).click();await expect(page.getByRole('status')).toContainText('No real application or credit check');expect(errors).toEqual([]);
});
