// Harness logic only. Simulated clocks/storage do NOT verify Android WebView isolation or scheduling.
// Dependency: fake-indexeddb@6.0.0 (test tooling only, never bundled in Android).
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {webcrypto}=require('node:crypto');
const {IDBFactory}=require('fake-indexeddb');
const html=fs.readFileSync(path.join(__dirname,'../android/app/src/main/assets/isolation_test.html'),'utf8');
const code=html.match(/<script>([\s\S]*?)<\/script>/)[1];
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function profile(){return {local:new Map(),cookies:new Map(),idb:new IDBFactory()}}
async function load(profile){
  const elements=new Map(), timers=[];
  let reloaded=false;
  const document={
    visibilityState:'visible',addEventListener(){},
    getElementById(id){if(!elements.has(id))elements.set(id,{textContent:'',className:''});return elements.get(id)},
    get cookie(){return [...profile.cookies].map(([k,v])=>`${k}=${v}`).join('; ')},
    set cookie(raw){const row=raw.split(';')[0],split=row.indexOf('=');profile.cookies.set(row.slice(0,split),row.slice(split+1))}
  };
  const context={document,crypto:webcrypto,indexedDB:profile.idb,
    localStorage:{getItem:key=>profile.local.get(key)??null,setItem:(key,value)=>profile.local.set(key,String(value))},
    location:{reload(){reloaded=true}},
    setInterval(callback,delay){timers.push({callback,delay,interval:true})},
    setTimeout(callback,delay){timers.push({callback,delay,interval:false})}
  };
  vm.runInNewContext(code,context,{timeout:1000});
  for(let n=0;n<100&&document.getElementById('idb').textContent==='';n++)await settle();
  assert.notEqual(document.getElementById('idb').textContent,'','IndexedDB completed');
  return {document,timers,tick(){const timer=timers.find(x=>x.interval);assert.equal(timer.delay,3000);timer.callback()},
    reload(){const timer=timers.find(x=>!x.interval);assert.equal(timer.delay,30000);timer.callback();assert.equal(reloaded,true)}};
}
const value=(page,id)=>page.document.getElementById(id).textContent;
(async()=>{
  const profiles=Array.from({length:42},profile);
  let pages=await Promise.all(profiles.map(load));
  const original=pages.map(page=>['qid','local','idb'].map(id=>value(page,id)));
  for(let column=0;column<3;column++)assert.equal(new Set(original.map(row=>row[column])).size,42);
  // Sixty 30-second cycles. These are simulated elapsed ticks, not 30 wall-clock minutes.
  for(let cycle=0;cycle<60;cycle++){
    pages.forEach(page=>{for(let tick=0;tick<10;tick++)page.tick();page.reload()});
    pages=await Promise.all(profiles.map(load));
    pages.forEach((page,index)=>{
      assert.deepEqual(['qid','local','idb'].map(id=>value(page,id)),original[index]);
      assert.match(value(page,'assertion'),/^PASS/);
    });
  }
  profiles.forEach(p=>assert.equal(p.local.get('sixpane.heartbeats'),'600'));
  // A new JS runtime on the same backing profile must preserve the three values.
  const recreated=await load(profiles[0]);
  assert.deepEqual(['qid','local','idb'].map(id=>value(recreated,id)),original[0]);
  profiles[0].cookies.set('Queue-it-qid','changed-cookie');
  assert.match(value(await load(profiles[0]),'assertion'),/^FAIL/,'cookie loss/change must not be hidden');
  profiles[0].cookies.set('Queue-it-qid',original[0][0]);
  assert.match(value(await load(profiles[0]),'assertion'),/^FAIL/,'a later successful reload must retain the failure');
  profiles[1].idb=new IDBFactory();
  assert.match(value(await load(profiles[1]),'assertion'),/^FAIL/,'IndexedDB loss must be reported');
  profiles[2].local.delete('Queue-it-local-only');
  assert.match(value(await load(profiles[2]),'assertion'),/^FAIL/,'local-only value loss must be reported');
  console.log('PASS: harness logic, 42 simulated profiles, 60 reload cycles, 600 heartbeats, runtime recreation and storage-loss detection. NOT an Android endurance test.');
})().catch(error=>{console.error(error);process.exitCode=1});
