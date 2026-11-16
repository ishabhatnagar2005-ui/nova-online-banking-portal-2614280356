import { test, expect } from '@playwright/test';

test('records a simulated inquiry and explains agent handoff',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.goto('/login');await page.getByLabel('User ID').fill('miguel');await page.getByLabel('Password',{exact:true}).fill('demo-only');await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.goto('/contact');await page.getByLabel('Search FAQs').fill('credit');await expect(page.getByText('How is available credit calculated?')).toBeVisible();
  await page.getByLabel('Topic').selectOption({label:'Account'});await page.getByLabel('Subject').fill('Demo inquiry');await page.getByLabel('Message',{exact:true}).fill('Please explain this demonstration.');
  const response=page.waitForResponse(r=>r.url().includes('/api/v1/support/inquiries')&&r.request().method()==='POST');await page.getByRole('button',{name:'Send Message (Simulated)'}).click();const receipt=await (await response).json();await expect(page.getByRole('status')).toContainText(receipt.referenceNumber);
  await page.getByRole('button',{name:'Open demonstration chat'}).click();await page.getByRole('button',{name:'Speak to Agent'}).click();await expect(page.getByText('Simulated handoff only:',{exact:false})).toContainText('no live agent was contacted');expect(errors).toEqual([]);
});
