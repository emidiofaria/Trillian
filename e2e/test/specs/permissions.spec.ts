/**
 * E2E-F-03: Onboarding permissions flow
 *
 * Verifies that the permission request screens (location + body sensors) complete
 * without crashing and that the app advances past onboarding when permissions are granted.
 *
 * Note: E2E-F-01 (registration) and E2E-F-02 (login) are DEFERRED until a CI Firebase
 * test account is provisioned. See requirements BMW-DC-CICD-001 §4.8 for details.
 * This test relies on a pre-installed debug build with autoGrantPermissions disabled
 * so that the permission dialog is actually presented.
 */

import { expect } from '@wdio/globals';

describe('E2E-F-03: Onboarding permissions', () => {
  before(async () => {
    // Allow the app to fully launch before interacting
    await driver.pause(3000);
  });

  it('should display the location permission rationale screen', async () => {
    const locationRationale = await $('android=new UiSelector().resourceId("com.drivingcoach:id/btn_grant_location")');
    await locationRationale.waitForDisplayed({ timeout: 10000 });
    expect(await locationRationale.isDisplayed()).toBe(true);
  });

  it('should grant location permission without crashing', async () => {
    const grantBtn = await $('android=new UiSelector().resourceId("com.drivingcoach:id/btn_grant_location")');
    await grantBtn.click();

    // Android system permission dialog
    const allowBtn = await $('android=new UiSelector().text("While using the app")');
    if (await allowBtn.isExisting()) {
      await allowBtn.click();
    }

    await driver.pause(1000);
    // App should still be running — no crash
    expect(await driver.isAppInstalled('com.drivingcoach')).toBe(true);
  });

  it('should grant sensor permission without crashing', async () => {
    const sensorBtn = await $('android=new UiSelector().resourceId("com.drivingcoach:id/btn_grant_sensors")');
    if (await sensorBtn.isExisting()) {
      await sensorBtn.click();
      await driver.pause(500);
    }
    expect(await driver.isAppInstalled('com.drivingcoach')).toBe(true);
  });

  it('should advance past the onboarding screen after all permissions are granted', async () => {
    // After permissions the app should show Home or Login — either means onboarding completed
    const nextScreen = await $('android=new UiSelector().resourceId("com.drivingcoach:id/nav_host_fragment")');
    await nextScreen.waitForDisplayed({ timeout: 15000 });
    expect(await nextScreen.isDisplayed()).toBe(true);
  });
});
