/* Design-only renderer and interaction checks; no Android/ADB or network content. */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
(async () => {
  const executablePath=process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || (process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':undefined);
  const browser=await chromium.launch({headless:true,executablePath});
  const page=await browser.newPage({viewport:{width:1920,height:1080},deviceScaleFactor:1});
  const errors=[]; page.on('pageerror',e=>errors.push(String(e)));
  const base='file://'+path.join(__dirname,'index.html');
  const out=path.join(__dirname,'../mockups'); fs.mkdirSync(out,{recursive:true});
  const cases=[['home-cinema','populated'],['home-split','populated'],['tv','populated'],['sport','populated'],['library','populated'],['profile','populated'],['home-cinema','no-history'],['tv','no-epg'],['tv','empty'],['sport','offline']];
  for(const [screen,scenario] of cases){
    await page.goto(base+`?capture=1&screen=${screen}&scenario=${scenario}`);await page.evaluate(()=>document.fonts.ready);
    const name=screen+(scenario==='populated'?'':'-'+scenario);
    await page.screenshot({path:path.join(out,name+'.jpg')});
    assert.equal(await page.locator('#tv').evaluate(e=>e.getBoundingClientRect().width),1920);
    assert.equal(await page.locator('#tv .overlay').count(),0);
    const overflow=await page.locator('#tv button').evaluateAll(nodes=>nodes.filter(n=>{const r=n.getBoundingClientRect();return r.x<52||r.right>1868||r.y<30||r.bottom>1040;}).map(n=>n.id));
    assert.deepEqual(overflow,[],screen+' safe-area overflow: '+overflow);
  }
  await page.goto(base+'?capture=1&screen=home-cinema');await page.evaluate(()=>document.fonts.ready);
  assert.equal(await page.locator('.channel-row .channel-card').count(),8);
  await page.evaluate(()=>window.designPreview.focus('ch-4'));
  await page.keyboard.press('Escape'); assert.equal(await page.evaluate(()=>document.activeElement.id),'nav-home-cinema');
  await page.keyboard.press('ArrowDown'); assert.equal(await page.evaluate(()=>document.activeElement.id),'ch-4');
  await page.keyboard.down('Enter');await page.waitForTimeout(600);await page.keyboard.up('Enter');
  assert.equal(await page.locator('[role=dialog]').count(),1);
  assert.equal(await page.locator('.option').count(),3);
  await page.screenshot({path:path.join(out,'quality-picker.jpg')});
  await page.keyboard.press('ArrowDown');await page.keyboard.press('Enter');
  assert.equal(await page.locator('[role=dialog]').count(),0);
  assert.equal(await page.evaluate(()=>window.designPreview.state.choices['Kanal 5'].quality),'FHD');
  assert.equal(await page.evaluate(()=>document.activeElement.id),'ch-4');
  await page.keyboard.press('Enter'); assert.equal(await page.locator('[role=dialog]').count(),1);
  await page.keyboard.press('Escape'); assert.equal(await page.evaluate(()=>document.activeElement.id),'ch-4');
  await page.evaluate(()=>{window.designPreview.state.screen='tv';window.designPreview.state.sub='TV-guide';window.designPreview.render('guide-now');});
  await page.screenshot({path:path.join(out,'tv-guide.jpg')});
  for(const width of [1024,736,360]){await page.setViewportSize({width,height:1100});await page.goto(base);await page.waitForTimeout(50);assert.ok(await page.locator('#viewport').evaluate((e,w)=>e.clientWidth<=w,width));}
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'validation.json'),JSON.stringify({screenshots:12,width:1920,height:1080,scriptErrors:errors,checks:['safe margins on ten screen/state combinations','eight logical Home channels','Back to top navigation and Down restores content focus','long OK opens one picker and suppresses Play','choice returns to caller and changes demo default','short OK opens simulated playback','Back dismisses modal and restores focus','360/736/1024 preview containment'],data:'synthetic design fixtures; no device or media access'},null,2)+'\n');
  console.log('PASS: 12 design screenshots at 1920x1080, layout and interaction checks');await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
