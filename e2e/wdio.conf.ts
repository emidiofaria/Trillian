import type { Options } from '@wdio/types';
import fs from 'fs';
import path from 'path';

// APK path is resolved at runtime — built by CI before this suite runs (E2E-04).
// Locally the Gradle build names outputs DrivingCoach-v<version>-debug.apk, so fall
// back to whatever APK is present rather than a hard-coded filename.
const DEBUG_APK_DIR = path.resolve(__dirname, '../app/build/outputs/apk/debug');

function resolveLocalApk(): string {
  const stable = path.join(DEBUG_APK_DIR, 'app-debug.apk');
  if (fs.existsSync(stable)) return stable;
  const found = fs.existsSync(DEBUG_APK_DIR)
    ? fs.readdirSync(DEBUG_APK_DIR).find((f) => f.endsWith('.apk'))
    : undefined;
  return found ? path.join(DEBUG_APK_DIR, found) : stable;
}

const APK_PATH = process.env.APK_PATH ?? resolveLocalApk();

export const config: Options.Testrunner = {
  runner: 'local',
  autoCompileOpts: {
    autoCompile: true,
    tsNodeOpts: {
      project: path.resolve(__dirname, 'tsconfig.json'),
      transpileOnly: true,
    },
  },

  port: 4723,

  specs: ['./test/specs/**/*.spec.ts'],
  exclude: [],

  maxInstances: 1,

  capabilities: [
    {
      platformName: 'Android',
      'appium:deviceName': 'emulator-5554',
      'appium:platformVersion': '11.0',  // API 30 emulator (E2E-02)
      'appium:automationName': 'UiAutomator2',
      'appium:app': APK_PATH,
      'appium:noReset': false,
      'appium:fullReset': true,
      'appium:newCommandTimeout': 120,
      'appium:autoGrantPermissions': false,  // E2E-F-03 tests permission flows manually
    },
  ],

  logLevel: 'info',
  bail: 0,
  waitforTimeout: 15000,
  connectionRetryTimeout: 120000,
  connectionRetryCount: 3,

  services: [],  // Appium server is started externally in CI (E2E-05)

  framework: 'mocha',
  mochaOpts: {
    ui: 'bdd',
    timeout: 120000,
  },

  reporters: [
    'spec',
    [
      'junit',
      {
        // E2E-09: JUnit XML for CI artifact upload
        outputDir: './results',
        outputFileFormat: () => 'junit-e2e.xml',
        classNameFormat: ({ suiteName }: { suiteName: string }) => suiteName,
        titleFormat: ({ title }: { title: string }) => title,
      },
    ],
  ],
};
