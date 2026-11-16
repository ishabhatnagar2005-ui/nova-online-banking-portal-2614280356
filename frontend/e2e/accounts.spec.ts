import { test, expect } from './fixtures';

test('customer filters their ledger and downloads the real statement',async({page})=>{
  const errors:string[]=[]; page.on('pageerror',e=>errors.push(e.message)); page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.goto('/login'); await page.getByLabel('User ID',{exact:true}).fill('miguel'); await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || ''); await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await page.getByRole('article').filter({hasText:'Peso Savings'}).getByRole('link',{name:/Account details/}).click();
  await expect(page.getByRole('heading',{name:'Peso Savings'})).toBeVisible(); await expect(page.getByLabel('Account balances')).toContainText('185.00');
  await page.getByRole('link',{name:'Back to accounts'}).click();
  await page.getByRole('link',{name:'Accounts',exact:true}).click();
  await page.getByRole('article').filter({hasText:'Peso Savings'}).getByRole('link',{name:/Account details/}).click();
  await expect(page.getByRole('heading',{name:'Peso Savings'})).toBeVisible(); await expect(page.getByLabel('Account balances')).toContainText('185.00'); await expect(page.locator('main')).toContainText('Money Out');
  await page.getByLabel('Search description or reference').fill('882194'); await page.getByLabel('Category').selectOption('DINING'); await page.getByLabel('Direction').selectOption('DEBIT');
  await expect(page.getByText('Makati Coffee Shop')).toBeVisible(); await page.getByText('Makati Coffee Shop').click(); await expect(page.getByText(/REF-TX-882194/)).toBeVisible();
  const download=page.waitForEvent('download'); await page.getByRole('button',{name:'Download statement'}).click(); expect((await download).suggestedFilename()).toBe('statement-9034-demo.txt');
  await page.getByLabel('Search description or reference').fill('unfindable-transaction-9273');
  await expect(page.getByText('No transactions found in this period.',{exact:false})).toBeVisible();
  await page.getByRole('button',{name:'Reset filters'}).click(); await expect(page.getByText('Salary',{exact:true})).toBeVisible();
  await page.getByLabel('Date range').selectOption('custom');
  await page.getByLabel('From').fill('2026-10-14'); await page.getByLabel('To',{exact:true}).fill('2026-10-01');
  await expect(page.getByRole('alert')).toContainText('Enter a valid start and end date.');
  expect(errors).toEqual([]);
});

test('a customer cannot view another owner’s account',async({page,request,expectedHttpErrors})=>{
  const login=await request.post('/api/v1/auth/login',{data:{username:'miguel',password:process.env['DEMO_PASSWORD'] || ''}});
  expect(login.status()).toBe(200);
  const session=await login.json(); const token=session.token as string;
  await page.addInitScript(value=>sessionStorage.setItem('nova-session',JSON.stringify(value)),session);
  const id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a05';
  const denied=await request.get(`/api/v1/accounts/${id}/transactions`,{headers:{Authorization:`Bearer ${token}`}});
  expect(denied.status()).toBe(403);
  const statement=await request.get(`/api/v1/accounts/${id}/statement/download`,{headers:{Authorization:`Bearer ${token}`}});
  expect(statement.status()).toBe(403);
  // Direct navigation is intentional: this tests a protected URL boundary.
  expectedHttpErrors.push({path:`/api/v1/accounts/${id}/transactions`,status:403});
  await page.goto(`/accounts/${id}`);
  await expect(page.getByRole('heading',{name:'Account Not Available'})).toBeVisible();
  await expect(page.locator('main')).not.toContainText('54,300.00');
});
