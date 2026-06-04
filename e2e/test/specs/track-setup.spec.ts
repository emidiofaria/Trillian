/**
 * E2E-F-04: Track setup — start/finish line
 *
 * Verifies that a user can navigate to the track setup screen, place a start/finish
 * line marker on the map, save it, and that the marker persists after app restart.
 *
 * Precondition: app is on Home screen (permissions already granted via E2E-F-03 run).
 */

import { expect } from '@wdio/globals';

describe('E2E-F-04: Track setup', () => {
  before(async () => {
    await driver.pause(2000);
  });

  it('should navigate to the track setup screen', async () => {
    const trackSetupBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/nav_track_setup")');
    await trackSetupBtn.waitForDisplayed({ timeout: 10000 });
    await trackSetupBtn.click();

    const mapFragment = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/map_view")');
    await mapFragment.waitForDisplayed({ timeout: 15000 });
    expect(await mapFragment.isDisplayed()).toBe(true);
  });

  it('should display the draw start/finish line prompt', async () => {
    const prompt = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/tv_draw_instruction")');
    await prompt.waitForDisplayed({ timeout: 8000 });
    expect(await prompt.isDisplayed()).toBe(true);
  });

  it('should allow placing start/finish markers on the map', async () => {
    // Tap the map in two positions to simulate drawing the start/finish line
    const mapView = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/map_view")');
    const mapRect = await mapView.getSize();
    const mapLoc = await mapView.getLocation();

    const midX = mapLoc.x + Math.floor(mapRect.width / 2);
    const midY = mapLoc.y + Math.floor(mapRect.height / 2);

    await driver.action('pointer')
      .move({ duration: 0, x: midX, y: midY - 50 })
      .down({ button: 0 })
      .up({ button: 0 })
      .perform();

    await driver.pause(500);

    await driver.action('pointer')
      .move({ duration: 0, x: midX, y: midY + 50 })
      .down({ button: 0 })
      .up({ button: 0 })
      .perform();

    const saveBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/btn_save_line")');
    await saveBtn.waitForDisplayed({ timeout: 8000 });
    expect(await saveBtn.isDisplayed()).toBe(true);
  });

  it('should save the start/finish line and return to home', async () => {
    const saveBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/btn_save_line")');
    await saveBtn.click();

    const homeScreen = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/fragment_home")');
    await homeScreen.waitForDisplayed({ timeout: 10000 });
    expect(await homeScreen.isDisplayed()).toBe(true);
  });

  it('should persist the marker after app restart', async () => {
    // Restart the app
    await driver.terminateApp('com.bmw.drivingcoach');
    await driver.activateApp('com.bmw.drivingcoach');
    await driver.pause(3000);

    // Navigate back to track setup
    const trackSetupBtn = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/nav_track_setup")');
    await trackSetupBtn.waitForDisplayed({ timeout: 10000 });
    await trackSetupBtn.click();

    // Saved line indicator should be visible
    const savedLineIndicator = await $('android=new UiSelector().resourceId("com.bmw.drivingcoach:id/tv_saved_line_label")');
    await savedLineIndicator.waitForDisplayed({ timeout: 10000 });
    expect(await savedLineIndicator.isDisplayed()).toBe(true);
  });
});
