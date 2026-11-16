const fs = require('fs');
const { chromium } = require('@playwright/test');

// Respect an explicit browser override; otherwise use Playwright's installed Chromium.
if (!process.env.CHROME_BIN) {
  // Playwright's matching headless shell avoids launching the full desktop browser.
  const { registry } = require('playwright-core/lib/server/registry/index');
  const shell = registry.findExecutable('chromium-headless-shell')?.executablePath();
  const browser = shell && fs.existsSync(shell) ? shell : chromium.executablePath();
  if (fs.existsSync(browser)) process.env.CHROME_BIN = browser;
}

module.exports = function (config) {
  config.set({
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    reporters: ['progress'],
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-dev-shm-usage'],
      },
    },
    restartOnFileChange: true,
  });
};
