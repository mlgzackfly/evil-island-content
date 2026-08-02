import { mkdir } from "node:fs/promises";
import { chromium } from "playwright";

const baseURL = "http://127.0.0.1:4173";
const outputDir = new URL("../test-results/", import.meta.url);
await mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function overlaps(a, b) {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
}

const browser = await chromium.launch({ headless: true });
const failures = [];

async function verifyDesktop() {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  const runtimeErrors = [];
  page.on("pageerror", (error) => runtimeErrors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") runtimeErrors.push(message.text());
  });
  await page.goto(baseURL, { waitUntil: "networkidle" });
  await page.locator("canvas").waitFor({ state: "visible" });
  const canvas = await page.locator("canvas").boundingBox();
  assert(canvas && canvas.width >= 1200 && canvas.height >= 700, "Desktop canvas did not fill the viewport");
  assert(await page.getByTestId("choose-outward").isVisible(), "Orientation selection is not visible on first visit");
  await page.screenshot({ path: new URL("desktop-orientation.png", outputDir).pathname, fullPage: true });

  await page.getByTestId("choose-outward").click();
  await page.locator("#orientation-modal").waitFor({ state: "hidden" });
  await page.keyboard.down("d");
  await page.waitForTimeout(3000);
  await page.keyboard.up("d");
  await page.waitForTimeout(250);
  const dao = Number(await page.locator("#dao-value").innerText());
  assert(dao >= 40, `Movement did not reach the higher-dao zone (dao=${dao})`);

  for (let i = 0; i < 4; i += 1) {
    await page.keyboard.press("j");
    await page.waitForTimeout(880);
  }
  await page.screenshot({ path: new URL("desktop-patrol.png", outputDir).pathname, fullPage: true });
  const eventText = await page.locator("#event-feed").innerText();
  assert(eventText.includes("倒下") || eventText.includes("取得一份"), "The controlled battle did not defeat a zaiochi");
  assert(runtimeErrors.length === 0, `Desktop runtime errors: ${runtimeErrors.join(" | ")}`);
  await context.close();
}

async function verifyMobile() {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  const runtimeErrors = [];
  page.on("pageerror", (error) => runtimeErrors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") runtimeErrors.push(message.text());
  });
  await page.goto(baseURL, { waitUntil: "networkidle" });
  await page.getByTestId("choose-inward").click();
  await page.locator("#orientation-modal").waitFor({ state: "hidden" });

  const formula = await page.locator(".formula-dock").boundingBox();
  const dpad = await page.locator(".touch-pad").boundingBox();
  const actions = await page.locator(".touch-actions").boundingBox();
  const vitals = await page.locator(".vitals").boundingBox();
  const mission = await page.locator(".mission").boundingBox();
  assert(formula && dpad && actions && vitals && mission, "Mobile control surfaces are not visible");
  assert(!overlaps(formula, dpad), "Formula dock overlaps the movement pad");
  assert(!overlaps(formula, actions), "Formula dock overlaps the action buttons");
  assert(!overlaps(vitals, formula), "Vitals overlap the formula dock");
  assert(!overlaps(mission, vitals), "Mission text overlaps the vitals panel");

  const rightButton = page.getByRole("button", { name: "向右" });
  await rightButton.dispatchEvent("pointerdown");
  await page.waitForTimeout(550);
  await rightButton.dispatchEvent("pointerup");
  await page.getByRole("button", { name: "攻擊" }).click();
  await page.waitForTimeout(180);
  await page.screenshot({ path: new URL("mobile-patrol.png", outputDir).pathname, fullPage: true });
  assert(runtimeErrors.length === 0, `Mobile runtime errors: ${runtimeErrors.join(" | ")}`);
  await context.close();
}

for (const [name, verification] of [["desktop", verifyDesktop], ["mobile", verifyMobile]]) {
  try {
    await verification();
    console.log(`PASS ${name}`);
  } catch (error) {
    failures.push(`${name}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

await browser.close();
if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}
