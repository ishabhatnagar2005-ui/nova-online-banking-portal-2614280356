import { test as base, expect, Page } from '@playwright/test';

type ExpectedHttpError = { path: string; status: number };
type Fixtures = { expectedHttpErrors: ExpectedHttpError[]; browserErrors: void };

// Expected HTTP failures are scoped to an exact endpoint and status, and must
// actually occur. Other browser errors, including uncaught exceptions, fail.
export const test = base.extend<Fixtures>({
  expectedHttpErrors: async ({}, use) => { await use([]); },
  browserErrors: [async ({ context, page, expectedHttpErrors }, use) => {
    const errors: { text: string; url: string; pageError: boolean }[] = [];
    const responses: ExpectedHttpError[] = [];
    const observe = (p: Page) => {
      p.on('pageerror', error => errors.push({ text: error.message, url: '', pageError: true }));
      p.on('console', message => {
        if (message.type() === 'error') errors.push({ text: message.text(), url: message.location().url, pageError: false });
      });
      p.on('response', response => {
        if (response.status() >= 400) responses.push({ path: new URL(response.url()).pathname, status: response.status() });
      });
    };
    observe(page);
    context.on('page', observe);
    await use();
    const key = (error: ExpectedHttpError) => JSON.stringify([error.path, error.status]);
    const counts = (items: ExpectedHttpError[]) => {
      const result = new Map<string, number>();
      for (const item of items) result.set(key(item), (result.get(key(item)) || 0) + 1);
      return result;
    };
    const expectedCounts = counts(expectedHttpErrors);
    const responseCounts = counts(responses);
    expect(Object.fromEntries(responseCounts), 'HTTP error responses must match expected occurrences').toEqual(Object.fromEntries(expectedCounts));
    const consoleCounts = new Map<string, number>();
    const unexpected = errors.filter(error => {
      if (error.pageError || !error.url) return true;
      const match = /^Failed to load resource: the server responded with a status of (\d{3})(?: \([^\n]*\))?$/.exec(error.text);
      if (!match) return true;
      let path: string;
      try { path = new URL(error.url).pathname; } catch { return true; }
      const id = key({ path, status: Number(match[1]) });
      const seen = (consoleCounts.get(id) || 0) + 1;
      consoleCounts.set(id, seen);
      return seen > (expectedCounts.get(id) || 0) || seen > (responseCounts.get(id) || 0);
    });
    expect(unexpected, 'Unexpected browser console or page errors').toEqual([]);
  }, { auto: true }],
});
export { expect };
