// SPDX-License-Identifier: Apache-2.0
// Runs the generators of the design page (texture points and city lights) and writes what Seeded.java must give back:
// the counts, the sums of every component and the first and last point. The page is the authority for the look.
// usage: node make-seeded-fixture.mjs DESIGN.html OUT.txt
import { readFileSync, writeFileSync } from 'node:fs';
const [page, out] = process.argv.slice(2);
const src = readFileSync(page, 'utf8');
const between = (a, b) => { const i = src.indexOf(a); if (i < 0) throw new Error('missing ' + a); const j = src.indexOf(b, i); return src.slice(i, j); };
const code = 'const D2R=Math.PI/180;const vec=(lat,lon)=>{const lo=lon*D2R,la=lat*D2R,c=Math.cos(la);return [c*Math.sin(lo),Math.sin(la),c*Math.cos(lo)];};\n'
  + between('const CITIES=', '/* fine texture') + between('/* fine texture', '/* ---------- what is on the globe');
const f = new Function(code + '; return {CITIES, LIGHTS, TEX};');
const { CITIES, LIGHTS, TEX } = f();
const r6 = (x) => Math.round(x * 1e6) / 1e6;
const lines = [];
lines.push(`cities\t${CITIES.length}`);
lines.push(`tex\t${TEX.length}`);
lines.push(`lights\t${LIGHTS.length}`);
const sum = (arr, g) => r6(arr.reduce((s, p) => s + g(p), 0));
lines.push(`texsum\t${sum(TEX, p => p.v[0])}\t${sum(TEX, p => p.v[1])}\t${sum(TEX, p => p.v[2])}\t${sum(TEX, p => p.s)}\t${sum(TEX, p => p.a)}`);
lines.push(`lightsum\t${sum(LIGHTS, p => p.v[0])}\t${sum(LIGHTS, p => p.v[1])}\t${sum(LIGHTS, p => p.v[2])}\t${sum(LIGHTS, p => p.s)}\t${sum(LIGHTS, p => p.a)}`);
const colours = { '255,176,64': 0, '255,233,184': 1, '95,224,255': 2 };
lines.push(`lightcolours\t${[0, 1, 2].map(k => LIGHTS.filter(p => colours[p.c] === k).length).join('\t')}`);
const row = (n, p) => `${n}\t${[...p.v, p.s, p.a].map(r6).join('\t')}${p.c ? '\t' + colours[p.c] : ''}`;
lines.push(row('tex0', TEX[0]), row('tex1', TEX[1]), row('texlast', TEX[TEX.length - 1]));
lines.push(row('light0', LIGHTS[0]), row('light1', LIGHTS[1]), row('lightlast', LIGHTS[LIGHTS.length - 1]));
writeFileSync(out, lines.join('\n') + '\n');
// the city list as the Java class needs it
writeFileSync(out + '.cities', CITIES.map(c => c.join(',')).join(';') + '\n');
