/* Photo replaces the drop area; cropping is an explicit, reversible large-view operation. */
(()=>{
  let editing=false,gesture=null;
  const stage=$('#photo-stage'),dialog=el('dialog',undefined,'crop-dialog');
  const heading=el('h2','框出要识别的题目'),help=el('p','拖动边角调整大小，拖动框内移动选区。也可以在框外重新拉出选区。','hint');
  const host=el('div',undefined,'crop-stage'),readout=el('p','','hint'),actions=el('div',undefined,'row wrap');
  const cancel=el('button','取消','secondary'),apply=el('button','应用裁剪','primary'),all=el('button','选取整张','quiet');
  actions.append(all,cancel,apply);dialog.append(heading,help,host,readout,actions);document.body.append(dialog);
  canvas.tabIndex=0;
  function pos(e){const r=canvas.getBoundingClientRect();return{x:Math.max(0,Math.min(canvas.width,(e.clientX-r.left)*canvas.width/r.width)),y:Math.max(0,Math.min(canvas.height,(e.clientY-r.top)*canvas.height/r.height))};}
  function handles(s){return {nw:[s.x,s.y],n:[s.x+s.w/2,s.y],ne:[s.x+s.w,s.y],e:[s.x+s.w,s.y+s.h/2],se:[s.x+s.w,s.y+s.h],s:[s.x+s.w/2,s.y+s.h],sw:[s.x,s.y+s.h],w:[s.x,s.y+s.h/2]};}
  function hit(p){const s=state.selection;if(!s)return 'new';const radius=16*canvas.width/Math.max(canvas.getBoundingClientRect().width,1);for(const [key,[x,y]] of Object.entries(handles(s)))if(Math.abs(p.x-x)<=radius&&Math.abs(p.y-y)<=radius)return key;return p.x>s.x&&p.x<s.x+s.w&&p.y>s.y&&p.y<s.y+s.h?'move':'new';}
  paint=function(){
    if(!state.image)return;const ratio=Math.min(1,1800/Math.max(state.image.width,state.image.height));canvas.width=Math.round(state.image.width*ratio);canvas.height=Math.round(state.image.height*ratio);
    const g=canvas.getContext('2d');g.drawImage(state.image,0,0,canvas.width,canvas.height);canvas.style.cursor=editing?'crosshair':'default';
    if(!editing)return;if(!state.selection)state.selection={x:0,y:0,w:canvas.width,h:canvas.height};const s=state.selection;
    const scale=canvas.width/Math.max(canvas.getBoundingClientRect().width,1);g.fillStyle='rgba(10,24,20,.6)';g.fillRect(0,0,canvas.width,canvas.height);
    g.save();g.beginPath();g.rect(s.x,s.y,s.w,s.h);g.clip();g.drawImage(state.image,0,0,canvas.width,canvas.height);g.restore();
    g.strokeStyle='#39dbb3';g.lineWidth=2*scale;g.strokeRect(s.x,s.y,s.w,s.h);g.strokeStyle='rgba(255,255,255,.65)';g.lineWidth=scale;
    for(let i=1;i<3;i++){g.beginPath();g.moveTo(s.x+s.w*i/3,s.y);g.lineTo(s.x+s.w*i/3,s.y+s.h);g.moveTo(s.x,s.y+s.h*i/3);g.lineTo(s.x+s.w,s.y+s.h*i/3);g.stroke();}
    for(const [x,y]of Object.values(handles(s))){g.fillStyle='white';g.fillRect(x-5*scale,y-5*scale,10*scale,10*scale);g.strokeStyle='#176a55';g.strokeRect(x-5*scale,y-5*scale,10*scale,10*scale);}
    readout.textContent='选区 '+Math.round(s.w/ratio)+' × '+Math.round(s.h/ratio)+' 像素 · 请保留完整题干、图形和作答。';apply.disabled=s.w<20||s.h<20;
  };
  function finish(){editing=false;gesture=null;state.selection=null;stage.append(canvas);if(dialog.open)dialog.close();paint();}
  $('#crop').onclick=()=>{if(!state.image)return;editing=true;state.selection=null;host.append(canvas);dialog.showModal();paint();canvas.focus();};
  $('#replace-photo').onclick=()=>$('#photo').click();cancel.onclick=finish;dialog.oncancel=e=>{e.preventDefault();finish();};all.onclick=()=>{state.selection=null;paint();};
  apply.onclick=async()=>{const s=state.selection;if(!s||s.w<20||s.h<20)return;const ratio=state.image.width/canvas.width;const x=Math.round(s.x*ratio),y=Math.round(s.y*ratio),w=Math.min(state.image.width-x,Math.round(s.w*ratio)),h=Math.min(state.image.height-y,Math.round(s.h*ratio));const image=await createImageBitmap(state.image,x,y,w,h);state.image.close();state.image=image;state.photoVersion++;clearDiagnosis();finish();status('#diagnose-status','裁剪已应用。可以直接识别，也可以点击“还原照片”重新调整。');};
  canvas.onpointerdown=e=>{if(!editing)return;const p=pos(e);gesture={p,mode:hit(p),s:{...state.selection}};canvas.setPointerCapture(e.pointerId);e.preventDefault();};
  canvas.onpointermove=e=>{if(!editing)return;const p=pos(e);if(!gesture){const mode=hit(p);canvas.style.cursor=({move:'move',nw:'nwse-resize',se:'nwse-resize',ne:'nesw-resize',sw:'nesw-resize',n:'ns-resize',s:'ns-resize',e:'ew-resize',w:'ew-resize'})[mode]||'crosshair';return;}const {s,mode}=gesture,dx=p.x-gesture.p.x,dy=p.y-gesture.p.y;let n={...s};
    if(mode==='move'){n.x=Math.max(0,Math.min(canvas.width-s.w,s.x+dx));n.y=Math.max(0,Math.min(canvas.height-s.h,s.y+dy));}
    else if(mode==='new')n={x:Math.min(p.x,gesture.p.x),y:Math.min(p.y,gesture.p.y),w:Math.abs(dx),h:Math.abs(dy)};
    else {let l=s.x,r=s.x+s.w,t=s.y,b=s.y+s.h;if(mode.includes('w'))l=Math.min(r-20,Math.max(0,s.x+dx));if(mode.includes('e'))r=Math.max(l+20,Math.min(canvas.width,s.x+s.w+dx));if(mode.includes('n'))t=Math.min(b-20,Math.max(0,s.y+dy));if(mode.includes('s'))b=Math.max(t+20,Math.min(canvas.height,s.y+s.h+dy));n={x:l,y:t,w:r-l,h:b-t};}state.selection=n;paint();};
  canvas.onpointerup=()=>gesture=null;canvas.onpointercancel=()=>gesture=null;
  canvas.onkeydown=e=>{if(!editing||!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key))return;e.preventDefault();const s=state.selection,step=e.shiftKey?10:1;if(e.key==='ArrowLeft')s.x=Math.max(0,s.x-step);if(e.key==='ArrowRight')s.x=Math.min(canvas.width-s.w,s.x+step);if(e.key==='ArrowUp')s.y=Math.max(0,s.y-step);if(e.key==='ArrowDown')s.y=Math.min(canvas.height-s.h,s.y+step);paint();};
  stage.ondragover=e=>e.preventDefault();stage.ondrop=e=>{e.preventDefault();loadPhoto(e.dataTransfer.files[0]);};
})();
