import { execFileSync, spawn } from 'node:child_process';
import { once } from 'node:events';
import { createInterface } from 'node:readline';

// An external server must be paired explicitly with libpq settings for its
// actual disposable database. Compose uses its own named project database.
function psqlCommand(): { command: string; args: string[] } {
  if (process.env['E2E_REUSE_SERVER'] === '1') {
    if (!process.env['PGHOST'] || !process.env['PGDATABASE'] || process.env['E2E_PG_MATCHES_SERVER'] !== '1') {
      throw new Error('External server database checks require PGHOST, PGDATABASE and E2E_PG_MATCHES_SERVER=1 for the same disposable database.');
    }
    return { command:'psql', args:['-X','-t','-A','-v','ON_ERROR_STOP=1'] };
  }
  return { command:'docker', args:['compose','-f','../docker-compose.yml','exec','-T','db','psql','-U',process.env['DB_USERNAME'] || 'nova','-d',process.env['DB_NAME'] || 'nova','-X','-t','-A','-v','ON_ERROR_STOP=1'] };
}

export function queryDatabase(sql: string): string {
  const { command, args } = psqlCommand();
  return execFileSync(command, [...args, '-c', sql], { encoding:'utf8', cwd:__dirname+'/..' }).trim();
}

// Hold a real PostgreSQL lock until the browser has observed the pending state.
export async function lockPromotions(): Promise<() => Promise<void>> {
  const { command, args } = psqlCommand();
  const child = spawn(command, args, { cwd:__dirname+'/..', stdio:['pipe','pipe','pipe'] });
  let stderr = '';
  child.stderr.setEncoding('utf8'); child.stderr.on('data', chunk => { stderr += chunk; });
  const exit = once(child, 'exit');
  const lines = createInterface({ input:child.stdout });
  const locked = new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timed out acquiring PostgreSQL lock: ${stderr}`)), 15_000);
    lines.on('line', line => { if (line.trim() === 'E2E_LOCK_ACQUIRED') { clearTimeout(timer); resolve(); } });
    child.once('error', error => { clearTimeout(timer); reject(error); });
    child.once('exit', () => { clearTimeout(timer); reject(new Error(`PostgreSQL lock process exited: ${stderr}`)); });
  });
  child.stdin.write('BEGIN; LOCK TABLE promotions IN ACCESS EXCLUSIVE MODE;\n\\echo E2E_LOCK_ACQUIRED\n');
  try { await locked; } catch (error) { child.kill(); await exit.catch(() => undefined); throw error; }
  return async () => {
    child.stdin.end('COMMIT;\n\\q\n');
    const [code] = await exit;
    lines.close();
    if (code !== 0) throw new Error(`PostgreSQL lock release failed (${code}): ${stderr}`);
  };
}
