import { test, expect } from './fixtures';
import { queryDatabase } from './database';

test('records a simulated inquiry and explains agent handoff',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.goto('/login');await page.getByLabel('User ID',{exact:true}).fill('miguel');await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.getByRole('link',{name:'Support',exact:true}).click();await page.getByRole('link',{name:'Contact',exact:true}).click();
  await page.getByLabel('Search FAQs').fill('credit');await expect(page.getByText('How is available credit calculated?')).toBeVisible();
  await page.getByLabel('Search FAQs').fill('unfindable-faq-9273');await expect(page.getByText('No matching questions.')).toBeVisible();
  await page.getByRole('button',{name:'Send Message (Simulated)'}).click();
  await expect(page.getByText('Choose a topic.')).toBeVisible();await expect(page.getByText('Enter a subject.')).toBeVisible();await expect(page.getByText('Enter a message.')).toBeVisible();
  await page.getByLabel('Topic').selectOption({label:'Account'});await page.getByLabel('Subject').fill('Demo inquiry');await page.getByLabel('Message',{exact:true}).fill('Please explain this demonstration.');
  const response=page.waitForResponse(r=>r.url().includes('/api/v1/support/inquiries')&&r.request().method()==='POST');await page.getByRole('button',{name:'Send Message (Simulated)'}).click();const posted=await response;expect(posted.status()).toBe(201);const receipt=await posted.json();await expect(page.getByRole('status')).toContainText(receipt.referenceNumber);
  // There is no inquiry GET endpoint or receipt history; re-read the real
  // database after a browser reload.
  expect(receipt.referenceNumber).toMatch(/^NB-[0-9A-F-]{36}$/);
  const sql=`SELECT count(*) FROM support_inquiries i JOIN bank_users u ON u.id=i.customer_id WHERE i.reference_number='${receipt.referenceNumber}' AND u.username='miguel' AND i.topic='Account' AND i.subject='Demo inquiry' AND i.message='Please explain this demonstration.'`;
  expect(queryDatabase(sql)).toBe('1');
  await page.reload(); await expect(page.getByLabel('Search FAQs')).toBeVisible();
  expect(queryDatabase(sql)).toBe('1');
  await page.getByRole('button',{name:'Open demonstration chat'}).click();await page.getByRole('button',{name:'Speak to Agent'}).click();await expect(page.getByText('Simulated handoff only:',{exact:false})).toContainText('no live agent was contacted');expect(errors).toEqual([]);
});
