import { test, expect } from './fixtures';
test('login, protected session and logout use the real API',async({page})=>{
  const errors:string[]=[]; page.on('pageerror',e=>errors.push(e.message)); page.on('console',m=>{if(m.type()==='error') errors.push(m.text());});
  await page.goto('/login'); await page.getByLabel('User ID',{exact:true}).fill('miguel'); await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login') && r.status()===200);
  await page.getByRole('button',{name:'Sign in',exact:true}).click(); const login=await (await response).json();
  await expect(page.getByRole('heading',{name:`Welcome, ${login.user.fullName}`})).toBeVisible();
  await page.getByRole('button',{name:'Sign out'}).click(); await expect(page).toHaveURL(/\/login$/); expect(errors).toEqual([]);
});

test('unauthenticated protected URLs redirect to sign in',async({page,request})=>{
  // Direct entry is intentional for the security boundary.
  await page.goto('/accounts');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading',{name:'Sign in'})).toBeVisible();
  expect((await request.get('/api/v1/portfolio/summary')).status()).toBe(401);
});

test('invalid input is rejected before an unknown user gets a backend error',async({page,expectedHttpErrors})=>{
  expectedHttpErrors.push({path:'/api/v1/auth/login',status:401});
  await page.goto('/login');
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await expect(page.getByText('User ID is required')).toBeVisible();
  await expect(page.getByText('Password is required')).toBeVisible();
  await page.getByLabel('User ID',{exact:true}).fill('unknown-e2e-user');
  await page.getByLabel('Password',{exact:true}).fill('invalid');
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/auth/login') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  expect((await response).status()).toBe(401);
  await expect(page.getByRole('alert')).toContainText('Invalid User ID or password');
  await expect(page).toHaveURL(/\/login$/);
});

test('idle warning appears and the real session expires',async({page,request})=>{
  test.setTimeout(180_000);
  await page.goto('/login');
  await page.getByLabel('User ID',{exact:true}).fill('miguel'); await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');
  const summary=page.waitForResponse(r=>r.url().endsWith('/api/v1/portfolio/summary') && r.status()===200);
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await summary; const lastServerActivity=Date.now();
  await expect(page.getByRole('heading',{name:/Welcome, Miguel/})).toBeVisible();
  const token=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('nova-session')!).token as string);
  await expect(page.getByRole('alertdialog',{name:'Still there?'})).toBeVisible({timeout:130_000});
  await expect(page.getByRole('alertdialog')).toContainText('Your session will end in');
  await expect(page).toHaveURL(/\/login$/,{timeout:40_000});
  // The portfolio request refreshes server activity after login. Let its real
  // idle window elapse rather than probing early (which would extend it).
  await page.waitForTimeout(Math.max(0,152_000-(Date.now()-lastServerActivity)));
  const response=await request.get('/api/v1/portfolio/summary',{headers:{Authorization:`Bearer ${token}`}});
  expect(response.status()).toBe(401);
});

test('mobile customer navigation remains usable',async({page},testInfo)=>{
  await page.setViewportSize({width:375,height:812});
  await page.goto('/login');
  await page.getByLabel('User ID',{exact:true}).fill('rosa'); await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await expect(page.getByRole('heading',{name:/Welcome, Rosa/})).toBeVisible();
  await page.getByRole('link',{name:'Accounts',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Accounts by type'})).toBeVisible();
  await expect(page.locator('main')).toContainText('2204');
  await page.screenshot({path:testInfo.outputPath('mobile-accounts.png'),fullPage:true});
});
