const fs = require('fs');
const vm = require('vm');
const src = fs.readFileSync(require('path').join(__dirname, '../app/src/main/assets/js/nobar-core.js'), 'utf8');
const ctx = {}; vm.createContext(ctx); vm.runInContext(src, ctx);
const core = ctx.NobarCore;
function eq(a,b,msg){ if(JSON.stringify(a)!==JSON.stringify(b)) throw new Error(`${msg}: ${JSON.stringify(a)} !== ${JSON.stringify(b)}`); }
eq(core.MAX_MEMBERS, 8, 'room capacity');
eq(core.normalizeRoom(' ab-cd 1234 '), 'ABCD1234', 'room normalization');
eq(core.validateChat('  hello <world>  '), 'hello world', 'chat sanitization');
eq(core.validateVideo({type:'yt',id:'abcdefghijk'}), {type:'yt',id:'abcdefghijk'}, 'youtube id');
eq(core.validateVideo({type:'yt',id:'short'}), null, 'invalid youtube id');
eq(core.validateVideo({type:'url',url:'https://example.com/a.mp4'}), {type:'url',url:'https://example.com/a.mp4'}, 'direct video URL');
eq(core.validateVideo({type:'url',url:'javascript:alert(1)'}), null, 'unsafe video URL');
eq(core.canControl(2,false), true, 'duo voting mode allows proposal');
eq(core.canControl(3,false), false, 'non-host cannot control 3+ room');
eq(core.canControl(8,true), true, 'host can control');
console.log('NOBAR JS core tests: 10 assertions passed');
