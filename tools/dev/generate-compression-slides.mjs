// Generate immutable illustrations from the retained original's canvas slideshow, offline.
import fs from 'node:fs/promises';
import path from 'node:path';
import {chromium} from 'playwright';
const root=path.resolve(import.meta.dirname,'../..');
const source=path.join(root,'resources/programs/20260907Datenkompression');
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',args:['--no-sandbox']});
try {
 const page=await browser.newPage({viewport:{width:1100,height:900}});
 await page.setContent('<div id="widget-s2-jpeg-slideshow"></div>');
 await page.evaluate(() => {window.getLangValue=(obj,key)=>key.split('.').reduce((v,k)=>v?.[k],obj)});
 for(const file of ['lang/de.js','js/step-slideshow.js','js/jpeg-intro-slideshow.js'])await page.addScriptTag({content:await fs.readFile(path.join(source,file),'utf8')});
 const jpg=await fs.readFile(path.join(root,'resources/workbookresources/compression/source-cat.jpg'));
 await page.evaluate(data=>{window.KATZE_DATA_URL=data;setupJPEGIntroSlideshow(window.LANG_DE)},'data:image/jpeg;base64,'+jpg.toString('base64'));
 await page.locator('.step-slideshow-next').waitFor();
 for(let i=0;i<6;i++){
  await page.locator('.step-slideshow-stage').screenshot({path:path.join(root,`resources/workbookresources/compression/jpeg-slide-${i+1}.png`)});
  if(i<5)await page.locator('.step-slideshow-next').click();
 }
} finally {await browser.close()}
