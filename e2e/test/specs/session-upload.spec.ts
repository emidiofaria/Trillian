/**
 * E2E-F-06: Session upload
 *
 * Verifies that the upload status for the most recently recorded session transitions
 * from PENDING to UPLOADED within 60 seconds when the network is available.
 *
 * Precondition: a completed session exists in history (E2E-F-05 must have run).
 * The CI emulator has outbound network access by default.
 */

import { expect } from '@wdio/globals';

const UPLOAD_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 3_000;

describe('E2E-F-06: Session upload', () => {
  before(async () => {
    // Ensure we are on the history screen
    const historyTab = await $('android=new UiSelector().resourceId("com.drivingcoach:id/nav_history")');
    await historyTab.waitForDisplayed({ timeout: 8000 });
    const isActive = await historyTab.getAttribute('selected');
    if (isActive !== 'true') {
      await historyTab.click();
    }
    await driver.pause(1000);
  });

  it('should show an upload status badge on the most recent session', async () => {
    const uploadBadge = await $('android=new UiSelector().resourceId("com.drivingcoach:id/upload_status").instance(0)');
    await uploadBadge.waitForDisplayed({ timeout: 10000 });
    expect(await uploadBadge.isDisplayed()).toBe(true);
  });

  it('should transition to UPLOADED status within 60 seconds', async () => {
    const deadline = Date.now() + UPLOAD_TIMEOUT_MS;
    let uploadedText: string | null = null;

    while (Date.now() < deadline) {
      const badge = await $('android=new UiSelector().resourceId("com.drivingcoach:id/upload_status").instance(0)');
      const text = await badge.getText();
      if (text === 'UPLOADED') {
        uploadedText = text;
        break;
      }
      await driver.pause(POLL_INTERVAL_MS);
    }

    expect(uploadedText).toBe('UPLOADED');
  });
});
