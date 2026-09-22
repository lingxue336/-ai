import assert from 'node:assert/strict';
const tabs=await(await fetch('http://127.0.0.1:9227/json')).json();
const ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
let seq=0;const pending=new Map();ws.onmessage=e=>{const d=JSON.parse(e.data);if(d.id&&pending.has(d.id)){pending.get(d.id)(d.result);pending.delete(d.id);}};
const cdp=(method,params)=>new Promise(r=>{const id=++seq;pending.set(id,r);ws.send(JSON.stringify({id,method,params}));});
async function request(path,body){const r=await fetch('http://127.0.0.1:8086/api/v2'+path,body===undefined?{}:{method:'POST',headers:body instanceof FormData?{}:{'Content-Type':'application/json'},body:body instanceof FormData?body:JSON.stringify(body)});const data=await r.json();assert(r.ok,JSON.stringify(data));return data;}
async function analyze(lines){const expression=`(()=>{const c=document.createElement('canvas');c.width=1300;c.height=600;const g=c.getContext('2d');g.fillStyle='white';g.fillRect(0,0,1300,600);g.fillStyle='black';g.font='32px Microsoft YaHei';${JSON.stringify(lines)}.forEach((s,i)=>g.fillText(s,40,65+i*85));return c.toDataURL('image/jpeg',.95);})()`;
const result=await cdp('Runtime.evaluate',{expression,returnByValue:true});const data=new FormData();data.append('photo',new Blob([Buffer.from(result.result.value.split(',')[1],'base64')],{type:'image/jpeg'}),'generated-test.jpg');data.append('grade','9');
const {jobId}=await request('/diagnoses',data);for(let i=0;i<180;i++){const j=await request('/jobs/'+jobId);if(j.state==='done')return j.result;if(j.state==='failed')throw Error(j.message);await new Promise(r=>setTimeout(r,1000));}throw Error('model timeout');}
const zero=await analyze(['不解方程，判断方程实数根的情况。','x² - 2x + 1 = 0','我的解答：a=1，b=-2，c=1','Δ = (-2)² - 4×1×1 = 0','所以有两个不相等的实数根。','我不知道最后一步对不对。']);
console.log('ZERO_CASE',JSON.stringify(zero));assert(zero.skillIds.includes('discriminant-count'));
const match=await request('/recommendations',{diagnosisId:zero.id,skillId:'discriminant-count'});console.log('ZERO_MATCH',JSON.stringify(match));assert.equal(match.lessons.length,0,'只有Δ<0片段时，不应推给Δ=0的卡点');
const noWork=await analyze(['初三数学：判断实数根的情况。','x² - 2x + 3 = 0']);console.log('NO_WORK',JSON.stringify(noWork));assert(noWork.readable&&noWork.juniorMath);assert(/未|没有|无/.test(noWork.studentWork),'不能编造不存在的学生作答');
const nonMath=await analyze(['英语练习：请翻译下面句子。','Hello, how are you?']);console.log('NON_MATH',JSON.stringify(nonMath));assert(!nonMath.juniorMath||!nonMath.readable);
console.log('SEMANTIC_GUARDS_OK');ws.close();
