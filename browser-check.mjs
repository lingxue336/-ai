import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const tabs=await (await fetch('http://127.0.0.1:9227/json')).json();
const ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j;});let seq=0;const pending=new Map(),errors=[];
ws.onmessage=e=>{const d=JSON.parse(e.data);if(d.id){const p=pending.get(d.id);if(p){pending.delete(d.id);d.error?p.reject(Error(d.error.message)):p.resolve(d.result);}}else if(d.method==='Runtime.exceptionThrown')errors.push(d.params.exceptionDetails.text);};
function cdp(method,params={}){return new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const r=await cdp('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
async function until(expression,ms=30000){const start=Date.now();while(Date.now()-start<ms){if(await evaluate(expression))return;await new Promise(r=>setTimeout(r,500));}throw Error('Timed out: '+expression);}
await cdp('Runtime.enable');await cdp('Page.enable');await cdp('Emulation.setDeviceMetricsOverride',{width:1440,height:1100,deviceScaleFactor:1,mobile:false});
await cdp('Page.navigate',{url:'http://127.0.0.1:8086/'});await until('document.readyState==="complete" && typeof state!=="undefined" && state.skills.length>0');
await fs.mkdir('target/verification',{recursive:true});
async function shot(name){const r=await cdp('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});await fs.writeFile('target/verification/'+name+'.png',Buffer.from(r.data,'base64'));}
await shot('student-desktop');assert.equal(await evaluate('document.querySelector("#teacher").hidden'),true);
const dom=await cdp('DOM.getDocument');const input=await cdp('DOM.querySelector',{nodeId:dom.root.nodeId,selector:'#photo'});
await cdp('DOM.setFileInputFiles',{nodeId:input.nodeId,files:[process.cwd()+'\\target\\verification\\math-work.jpg']});await until('!!state.image');
await evaluate('document.querySelector("#analyze").click()');await until('!!state.diagnosis',240000);
const diagnosis=await evaluate('state.diagnosis');console.log('PHOTO_DIAGNOSIS',JSON.stringify(diagnosis));assert(diagnosis.skillIds.includes('discriminant-count'));assert(!diagnosis.skillIds.includes('discriminant-compute'),'正确算出的判别式不应被标成计算错误');
await shot('student-diagnosed');
await evaluate('document.querySelector("input[name=skill][value=discriminant-count]").click();document.querySelector("#diagnosis form button").click()');
await until('document.querySelector("#lessons").textContent.includes("视频库暂时") || !!document.querySelector("#lessons video")');
console.log('LESSON_RESULT',await evaluate('document.querySelector("#lessons").textContent'));
await until('document.querySelector("#lessons video")?.readyState>=1');
const playback=await evaluate('({src:document.querySelector("#lessons video").getAttribute("src"),duration:document.querySelector("#lessons video").duration})');
assert(playback.src.startsWith('/api/v2/clips/'));assert(playback.duration>19&&playback.duration<22,'必须是20秒独立短片，不是196秒原课');
await evaluate('document.querySelector("#lessons video").muted=true;document.querySelector("#lessons video").play()');await until('document.querySelector("#lessons video").currentTime>0.5');
await evaluate('document.querySelector("#lessons video").pause();document.querySelector("#lessons").scrollIntoView({block:"center"})');await shot('student-clip-playing');
const range=await fetch('http://127.0.0.1:8086'+playback.src,{headers:{Range:'bytes=0-1023'}});assert.equal(range.status,206);assert.equal((await range.arrayBuffer()).byteLength,1024);
console.log('REAL_CLIP_PLAYBACK_OK',JSON.stringify(playback));
await evaluate('window.actualFetch=window.fetch;window.fetch=(...args)=>String(args[0]).includes("/recommendations")?new Promise(resolve=>setTimeout(()=>resolve(new Response(JSON.stringify({lessons:[],message:"STALE_RESULT_SHOULD_NOT_RENDER"}),{headers:{"Content-Type":"application/json"}})),1200)):window.actualFetch(...args);document.querySelector("#diagnosis form button").click();document.querySelector("#student-note").value="新问题";document.querySelector("#student-note").dispatchEvent(new Event("input"));');
await new Promise(r=>setTimeout(r,1700));assert.equal(await evaluate('document.querySelector("#lessons").textContent.includes("STALE_RESULT_SHOULD_NOT_RENDER")'),false);await evaluate('window.fetch=window.actualFetch');
await evaluate('showPage("teacher")');await until('document.querySelector("#system-status").textContent.includes("识别模型")');await shot('teacher-desktop');
await evaluate('showPage("history")');await until('!!document.querySelector(".history-item")');
await cdp('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await evaluate('showPage("learn");window.scrollTo(0,0)');await shot('student-mobile');
assert(await evaluate('document.documentElement.scrollWidth<=window.innerWidth'),'移动端出现横向溢出');assert.deepEqual(errors,[]);console.log('BROWSER_CHECK_OK');ws.close();
