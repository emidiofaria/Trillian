/**
 * E2E-F-05: Session recording — start and stop
 *
 * Verifies that a user can start a recording session, stop it, and that:
 * - The session appears in the history list
 * - The JSONL telemetry file written to local storage is non-empty
 *
 * Precondition: app is on Home screen; track start/finish line is configured (E2E-F-04).
 */

import { expect } from '@wdio/globals';

describe('E2E-F-05: Session recording', () => {
  before(async () => {
    await driver.pause(2000);
  });

  it('should display the start recording button on the home screen', async () => {
    const startBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/btn_start_recording")');
    await startBtn.waitForDisplayed({ timeout: 10000 });
    expect(await startBtn.isDisplayed()).toBe(true);
  });

  it('should start a recording session without crashing', async () => {
    const startBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/btn_start_recording")');
    await startBtn.click();

    // Recording indicator (foreground service notification or UI badge) should appear
    const recordingIndicator = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/recording_indicator")');
    await recordingIndicator.waitForDisplayed({ timeout: 15000 });
    expect(await recordingIndicator.isDisplayed()).toBe(true);
  });

  it('should record for a minimum duration before stopping', async () => {
    // Allow at least 5 seconds of telemetry to be collected
    await driver.pause(5000);
  });

  it('should stop the recording session', async () => {
    const stopBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/btn_stop_recording")');
    await stopBtn.waitForDisplayed({ timeout: 10000 });
    await stopBtn.click();

    // Recording indicator should disappear
    const recordingIndicator = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/recording_indicator")');
    await recordingIndicator.waitForDisplayed({ timeout: 10000, reverse: true });
    expect(await recordingIndicator.isDisplayed()).toBe(false);
  });

  it('should show the completed session in the history list', async () => {
    // Navigate to history
    const historyTab = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/nav_history")');
    await historyTab.waitForDisplayed({ timeout: 8000 });
    await historyTab.click();

    const firstSession = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/session_item").instance(0)');
    await firstSession.waitForDisplayed({ timeout: 10000 });
    expect(await firstSession.isDisplayed()).toBe(true);
  });

  it('should have written a non-empty JSONL telemetry file', async () => {
    // Use adb shell to verify the telemetry file exists and is non-empty
    const result = await driver.executeScript('mobile: shell', [{
      command: 'run-as com.bmw.drivingcoach find /data/data/com.bmw.drivingcoach/files -name "*.jsonl" -not -empty',
      includeStderr: false,
    }]) as string;

    expect(result.trim().length).toBeGreaterThan(0);
  });
});
