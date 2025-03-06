import {test} from '@fixtures/8.6';
import {navigateToApp} from '@pages/8.6/UtilitiesPage';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Login Tests', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Login on Operate', async ({
    page,
    operateLoginPage,
    operateHomePage,
  }) => {
    await navigateToApp(page, 'operate');
    await operateLoginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test('Basic Login on TaskList', async ({
    page,
    taskListLoginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await taskListLoginPage.login('demo', 'demo');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible();
  });
});
