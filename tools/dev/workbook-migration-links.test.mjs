import assert from 'node:assert/strict';
import {access, readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
const originalDownloads = [
  '20260908WorkbookBitcoin.zip', '20260907WorkbookAutonomeWaffensysteme.zip',
  '20260925Bilderkennung.zip', '20251117ArbeitsheftGesichtserkennung.zip',
  '20260907WorkbookPhishing.zip', '20211110EvakuierungGitterautomat.pdf',
  '20250330ArbeitsheftVisualNovel.pdf', '20250402StickmaschineArbeitsheft.pdf'
];
test('classic PDF/ZIP downloads remain separate from partial digital adaptations', async () => {
  const html = await readFile(path.join(root, 'homepage/index.html'), 'utf8');
  const classicStart = html.indexOf('<h1>Analoge Workbooks (PDF /ZIP)</h1>');
  const progressStart = html.indexOf('<h1>Digitale Workbooks (In Arbeit)</h1>');
  assert(classicStart >= 0 && progressStart > classicStart);
  const classic = html.slice(classicStart, html.indexOf('<section class="owner-row"', classicStart));
  const progress = html.slice(progressStart, html.indexOf('<h1>Technische Demo', progressStart));
  for (const file of originalDownloads) {
    assert(classic.includes('href="../resources/workbookpdfs/' + file + '"'), file);
    await access(path.join(root, 'resources/workbookpdfs', file));
  }
  for (const [page, download] of [
    ['blockchainWorkbook', '20260908WorkbookBitcoin.zip'],
    ['imageRecognitionWorkbook', '20260925Bilderkennung.zip'],
    ['phishingWorkbook', '20260907WorkbookPhishing.zip'],
    ['embroideryWorkbook', '20250402StickmaschineArbeitsheft.pdf']
  ]) {
    const links = [...html.matchAll(/href="([^"]+)"/g)].filter(([,href]) => new URL(href, 'https://example.test/homepage/').pathname === '/homepage/' + page + '/');
    assert.equal(links.length, 1, page + ' has one dedicated digital card');
    assert(progress.includes('href="./' + page + '/"'), page + ' is work in progress');
    assert(!classic.includes(page), 'classic cards link to the source rather than the partial digital version');
    assert(classic.includes(download));
    await access(path.join(root, 'homepage', page, 'index.html'));
  }
  assert(progress.includes('href="./monksWorkbook/"'), 'new partial Mons Komputarius adaptation is work in progress');
  assert.match(progress, /Inhaltsgleichheit geprüft/);
  const guide = await readFile(path.join(root, 'docs/digital-workbook-migration.md'), 'utf8');
  assert.match(guide, /content equivalence/);
});
