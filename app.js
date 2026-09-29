let items=[], filter='all', query='', selectedItem=null;
const $=selector=>document.querySelector(selector), gallery=$('#gallery'), count=$('#itemCount'), empty=$('#emptyState');
const dateFormat=new Intl.DateTimeFormat('ja-JP',{year:'numeric',month:'2-digit',day:'2-digit'});
const monthFormat=new Intl.DateTimeFormat('ja-JP',{year:'numeric',month:'long'});

async function api(path, options={}){
  const response=await fetch(path,{credentials:'same-origin',...options});
  if(!response.ok){let message=`通信に失敗しました (${response.status})`;try{message=(await response.json()).error||message;}catch(_){/* non-JSON */}if(response.status===401)showLogin();throw new Error(message);}
  return response.json();
}
function showLogin(){if(!$('#loginDialog').open)$('#loginDialog').showModal();}
async function loadItems(){items=(await api('/api/v1/media')).files;render();}
async function start(){try{const session=await api('/api/v1/session');if(session.authenticated)await loadItems();else showLogin();}catch(error){showLogin();$('#loginError').textContent=error.message;}}
$('#loginForm').addEventListener('submit',async event=>{
  event.preventDefault();const button=$('#loginForm button'),password=$('#loginPassword').value;button.disabled=true;$('#loginError').textContent='';
  try{await api('/api/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({password})});$('#loginPassword').value='';await loadItems();$('#loginDialog').close();}
  catch(error){$('#loginError').textContent=error.message;}finally{button.disabled=false;}
});
$('#logoutButton').addEventListener('click',async()=>{try{await api('/api/v1/logout',{method:'POST',headers:{'X-Tokiha-Request':'1'}});}catch(error){showToast(error.message);return;}items=[];render();showLogin();});
function formatBytes(bytes){
  if(bytes===0)return '0 B';
  const units=['B','KB','MB','GB','TB'], index=Math.min(Math.floor(Math.log(bytes)/Math.log(1024)),units.length-1);
  return `${(bytes/1024**index).toFixed(index>1?1:0)} ${units[index]}`;
}
function updateStorage(){
  const bytes=items.reduce((sum,item)=>sum+item.size,0);
  $('#storageSummary').textContent=items.length?`${items.length}ファイル・${formatBytes(bytes)} ／ サーバーに保存済み`:'写真・動画はまだありません';
  $('#backupButton').disabled=items.length===0;
}
function displayDate(value){return dateFormat.format(new Date(value)).replaceAll('/','.');}
function monthKey(value){const date=new Date(value);return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`;}
function groupByMonth(entries){
  return entries.reduce((groups,item)=>{const key=monthKey(item.capturedAt);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(item);return groups;},new Map());
}
function searchableText(item){
  const date=new Date(item.capturedAt);
  return `${item.title} ${displayDate(item.capturedAt)} ${date.getFullYear()}年${date.getMonth()+1}月`.toLowerCase();
}
function render(){
  const shown=items.filter(item=>(filter==='all'||item.type===filter)&&searchableText(item).includes(query.trim().toLowerCase())).sort((a,b)=>new Date(b.capturedAt)-new Date(a.capturedAt));
  gallery.innerHTML=''; count.textContent=`${shown.length}件`; empty.hidden=shown.length!==0; updateStorage();
  groupByMonth(shown).forEach((monthItems,key)=>{
    const section=document.createElement('section'); section.className='month-section';
    const heading=document.createElement('h2'); heading.className='month-heading'; heading.innerHTML=`${monthFormat.format(new Date(`${key}-01T00:00:00`))}<span>${monthItems.length}件</span>`;
    const grid=document.createElement('div'); grid.className='gallery'; monthItems.forEach(item=>grid.append(createCard(item)));
    section.append(heading,grid); gallery.append(section);
  });
}
function createCard(item){
  const card=document.createElement('button'); card.className='card'; card.dataset.id=item.id;
  const media=item.type==='video'&&!item.poster?`<video src="${item.src}" muted preload="metadata"></video>`:`<img src="${item.src}" alt="">`;
  card.innerHTML=`<div class="card-media">${media}${item.type==='video'?'<span class="video-badge">▶ 動画</span>':''}</div><div class="card-info"><div><p class="card-title">${escapeHtml(item.title)}</p><p class="card-date">撮影日 ${displayDate(item.capturedAt)}</p></div><span aria-hidden="true">見る</span></div>`;
  card.addEventListener('click',()=>openViewer(item)); return card;
}
function escapeHtml(value){const div=document.createElement('div');div.textContent=value;return div.innerHTML;}
function openViewer(item){
  selectedItem=item;
  const host=$('#viewerMedia'); host.innerHTML=''; const element=document.createElement(item.type==='video'&&!item.poster?'video':'img');
  element.src=item.src; if(element.tagName==='VIDEO'){element.controls=true;element.autoplay=true;} else element.alt=item.title;
  host.append(element); $('#viewerTitle').textContent=item.title; $('#viewerDate').textContent=`撮影日 ${displayDate(item.capturedAt)}`;
  const link=$('#downloadLink'); link.href=item.download; link.download=item.name; $('#viewer').showModal();
}

document.querySelectorAll('.filter').forEach(button=>button.addEventListener('click',()=>{const active=document.querySelector('.filter.active');active.classList.remove('active');active.setAttribute('aria-pressed','false');button.classList.add('active');button.setAttribute('aria-pressed','true');filter=button.dataset.filter;render();}));
$('#uploadButton').addEventListener('click',()=>$('#fileInput').click());
$('#fileInput').addEventListener('change',async event=>{
  const files=[...event.target.files];if(!files.length)return;
  const uploadButton=$('#uploadButton');uploadButton.disabled=true;uploadButton.querySelector('span').textContent='保存中…';
  let saved=0,failed=[];
  for(const file of files){
    const extension=file.name.slice(file.name.lastIndexOf('.')).toLowerCase();
    const mime=file.type||({'.heic':'image/heic','.heif':'image/heif','.mov':'video/quicktime'}[extension]||'');
    try{await api('/api/v1/media',{method:'POST',headers:{'Content-Type':mime,'X-Tokiha-Request':'1','X-File-Name':encodeURIComponent(file.name),'X-Captured-At':await captureDate(file)},body:file});saved++;}
    catch(error){failed.push(`${file.name}: ${error.message}`);}
  }
  try{if(saved)await loadItems();}catch(error){failed.push(error.message);}
  showToast(failed.length?`${saved}件保存・${failed.length}件失敗：${failed[0]}`:`${saved}件をサーバーに保存しました`);
  event.target.value='';uploadButton.disabled=false;uploadButton.querySelector('span').textContent='追加する';
});
async function captureDate(file){
  if(file.type==='image/jpeg'){
    try {const exif=readExifDate(new DataView(await file.slice(0,256*1024).arrayBuffer()));if(exif)return exif;}
    catch(error){console.info('撮影日の読み取りに失敗したため、ファイル日付を使用します。',error);}
  }
  return new Date(file.lastModified||Date.now()).toISOString();
}
function readExifDate(view){
  if(view.byteLength<4||view.getUint16(0)!==0xffd8)return null;
  let offset=2;
  while(offset+4<view.byteLength){const marker=view.getUint16(offset);const length=view.getUint16(offset+2);if(marker===0xffe1)return readTiffDate(view,offset+10);if(length<2)break;offset+=2+length;}
  return null;
}
function readTiffDate(view,start){
  const little=view.getUint16(start)===0x4949, get16=position=>view.getUint16(position,little), get32=position=>view.getUint32(position,little);
  const first=start+get32(start+4); let exifOffset;
  for(let i=0,count=get16(first);i<count;i++){const entry=first+2+i*12;if(get16(entry)===0x8769)exifOffset=start+get32(entry+8);}
  if(!exifOffset)return null;
  for(let i=0,count=get16(exifOffset);i<count;i++){const entry=exifOffset+2+i*12;if(get16(entry)===0x9003){const position=start+get32(entry+8);let raw='';for(let j=0;j<19;j++)raw+=String.fromCharCode(view.getUint8(position+j));const parsed=new Date(raw.replace(/^(.{4}):(.{2}):/,'$1-$2-').replace(' ','T'));if(!Number.isNaN(parsed.valueOf()))return parsed.toISOString();}}
  return null;
}
$('#closeViewer').addEventListener('click',()=>$('#viewer').close());
$('#viewer').addEventListener('click',event=>{if(event.target===$('#viewer'))$('#viewer').close();});
$('#searchButton').addEventListener('click',()=>{$('#searchPanel').hidden=false;$('#searchInput').focus();});
$('#closeSearch').addEventListener('click',()=>{$('#searchPanel').hidden=true;$('#searchInput').value='';query='';render();});
$('#searchInput').addEventListener('input',event=>{query=event.target.value;render();});
$('#autoBackupHelp').addEventListener('click',()=>$('#backupHelpDialog').showModal());
$('#editDateButton').addEventListener('click',()=>{if(!selectedItem)return;$('#captureDateInput').value=selectedItem.capturedAt.slice(0,10);$('#viewer').close();$('#dateDialog').showModal();});
$('#dateDialog').addEventListener('close',async()=>{
  if($('#dateDialog').returnValue!=='save'||!selectedItem)return;
  const value=$('#captureDateInput').value;if(!value)return;
  const original=new Date(selectedItem.capturedAt);const [year,month,day]=value.split('-').map(Number);original.setFullYear(year,month-1,day);
  try{await api(`/api/v1/media/${selectedItem.id}`,{method:'PATCH',headers:{'Content-Type':'application/json','X-Tokiha-Request':'1'},body:JSON.stringify({capturedAt:original.toISOString()})});await loadItems();showToast('撮影日を変更しました');}
  catch(error){showToast(error.message);}
});
$('#backupButton').addEventListener('click',()=>{window.location.href='/api/v1/backup/archive';});
function showToast(message){const element=$('#toast');element.textContent=message;element.classList.add('show');setTimeout(()=>element.classList.remove('show'),4500);}
start();
