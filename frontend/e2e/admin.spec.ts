import { test, expect } from './fixtures';
test('facilitator resets fixtures and switches only by signing in again',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error') errors.push(m.text());});
  await page.goto('/login');await page.getByLabel('User ID',{exact:true}).fill('admin');await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Facilitator controls'})).toBeVisible();await page.getByRole('button',{name:'Review reset'}).click();
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/reset') && r.status()===200);await page.getByRole('button',{name:'Confirm reset'}).click();const result=await (await response).json();await expect(page.getByRole('status').filter({hasText:result.message})).toBeVisible();await expect(page.getByText('DEMO_RESET').first()).toBeVisible();
  await page.getByRole('button',{name:'Sign out to switch persona'}).click();await expect(page).toHaveURL(/login/);await page.getByLabel('User ID',{exact:true}).fill('miguel');await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');await page.getByRole('button',{name:'Sign in',exact:true}).click();await expect(page.getByRole('heading',{name:/Welcome, Miguel/})).toBeVisible();expect(errors).toEqual([]);
});

test('customer entering the facilitator URL is denied',async({page,request})=>{
  const login=await request.post('/api/v1/auth/login',{data:{username:'miguel',password:process.env['DEMO_PASSWORD'] || ''}});
  expect(login.status()).toBe(200); const session=await login.json();
  await page.addInitScript(value=>sessionStorage.setItem('nova-session',JSON.stringify(value)),session);
  await page.goto('/admin'); await expect(page).not.toHaveURL(/\/admin/);
  await expect(page.getByRole('heading',{name:'Facilitator controls'})).toHaveCount(0);
});

test('facilitator toggles and probes real simulation responses and audit',async({page,expectedHttpErrors})=>{
  await page.goto('/login');await page.getByLabel('User ID',{exact:true}).fill('admin');await page.getByLabel('Password',{exact:true}).fill(process.env['DEMO_PASSWORD'] || '');await page.getByRole('button',{name:'Sign in',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Facilitator controls'})).toBeVisible();
  const toggle=page.getByRole('button',{name:/^(Enable|Disable) simulated error$/});
  await expect(toggle).toBeEnabled();
  const disable=page.getByRole('button',{name:'Disable simulated error'});
  if (await disable.isVisible()) { await disable.click(); await expect(page.getByText(/Simulation is\s*off\s*\./)).toBeVisible(); }
  const success=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/simulation/probe') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Run probe'}).click(); expect((await success).status()).toBe(200);
  await expect(page.getByRole('status').filter({hasText:'Demonstration probe is available.'})).toBeVisible(); await expect(page.getByText('SIMULATION_PROBE').first()).toBeVisible();
  const enabled=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/simulation') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Enable simulated error'}).click(); expect((await enabled).status()).toBe(200);
  await expect(page.getByText(/Simulation is\s*on\s*\./)).toBeVisible();await expect(page.getByText('SIMULATION_ENABLED').first()).toBeVisible();
  expectedHttpErrors.push({path:'/api/v1/admin/simulation/probe',status:503});
  const failure=page.waitForResponse(r=>r.url().endsWith('/api/v1/admin/simulation/probe') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Run probe'}).click(); expect((await failure).status()).toBe(503);
  await expect(page.getByRole('status').filter({hasText:'Simulated service error; no customer service was affected.'})).toBeVisible();await expect(page.getByText('SIMULATED_ERROR').first()).toBeVisible();
  await page.getByRole('button',{name:'Disable simulated error'}).click();await expect(page.getByText(/Simulation is\s*off\s*\./)).toBeVisible();await expect(page.getByText('SIMULATION_DISABLED').first()).toBeVisible();
});
