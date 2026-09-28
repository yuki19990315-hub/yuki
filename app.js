const samples = [
  {id:'s1',title:'海までドライブ',capturedAt:'2026-09-21T10:30:00',type:'image',src:'assets/coast.svg'},
  {id:'s2',title:'夏の午後',capturedAt:'2026-08-14T14:10:00',type:'image',src:'assets/summer.svg'},
  {id:'s3',title:'はじめての一歩',capturedAt:'2026-08-02T09:00:00',type:'video',src:'assets/steps.svg',poster:true},
  {id:'s4',title:'公園の帰り道',capturedAt:'2026-06-19T17:30:00',type:'image',src:'assets/park.svg'},
  {id:'s5',title:'朝ごはん',capturedAt:'2026-06-03T08:00:00',type:'image',src:'assets/breakfast.svg'},
  {id:'s6',title:'小さな花火大会',capturedAt:'2026-05-04T20:00:00',type:'video',src:'assets/fireworks.svg',poster:true}
];
let items=[...samples], filter='all', query='', selectedItem=null;
const $=selector=>document.querySelector(selector), gallery=$('#gallery'), count=$('#itemCount'), empty=$('#emptyState');
const dateFormat=new Intl.DateTimeFormat('ja-JP',{year:'numeric',month:'2-digit',day:'2-digit'});
const monthFormat=new Intl.DateTimeFormat('ja-JP',{year:'numeric',month:'long'});

function userItems(){return items.filter(item=>item.file);}
function formatBytes(bytes){
  if(bytes===0)return '0 B';
  const units=['B','KB','MB','GB','TB'], index=Math.min(Math.floor(Math.log(bytes)/Math.log(1024)),units.length-1);
  return `${(bytes/1024**index).toFixed(index>1?1:0)} ${units[index]}`;
}
function updateStorage(){
  const uploaded=userItems(), bytes=uploaded.reduce((sum,item)=>sum+item.file.size,0);
  $('#storageSummary').textContent=uploaded.length?`${uploaded.length}ファイル・${formatBytes(bytes)} ／ この端末から追加`:'追加したファイルはまだありません';
  $('#backupButton').disabled=uploaded.length===0;
}
function displayDate(value){return dateFormat.format(new Date(value)).replaceAll('/','. ');}
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
  const link=$('#downloadLink'); link.href=item.src; link.download=item.name||`${item.title}.${item.type==='video'?'mp4':'jpg'}`; $('#viewer').showModal();
}

document.querySelectorAll('.filter').forEach(button=>button.addEventListener('click',()=>{const active=document.querySelector('.filter.active');active.classList.remove('active');active.setAttribute('aria-pressed','false');button.classList.add('active');button.setAttribute('aria-pressed','true');filter=button.dataset.filter;render();}));
$('#uploadButton').addEventListener('click',()=>$('#fileInput').click());
$('#fileInput').addEventListener('change',async event=>{
  const files=[...event.target.files].filter(file=>file.type.match(/^(image|video)\//));
  if(!files.length){showToast('写真または動画を選んでください');return;}
  const uploadButton=$('#uploadButton');uploadButton.disabled=true;uploadButton.querySelector('span').textContent='読み込み中…';
  const additions=await Promise.all(files.map(async file=>({id:crypto.randomUUID(),title:file.name.replace(/\.[^.]+$/,''),name:file.name,capturedAt:await captureDate(file),type:file.type.startsWith('video')?'video':'image',src:URL.createObjectURL(file),file})));
  items.unshift(...additions); render(); showToast(`${additions.length}件の思い出を追加しました`); event.target.value='';uploadButton.disabled=false;uploadButton.querySelector('span').textContent='追加する';
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
  while(offset+4<view.byteLength){const marker=view.getUint16(offset);const length=view.getUint16(offset+2);if(marker===0xffe1)return readTiffDate(view,offset+10);offset+=2+length;}
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
$('#dateDialog').addEventListener('close',()=>{
  if($('#dateDialog').returnValue!=='save'||!selectedItem)return;
  const value=$('#captureDateInput').value;if(!value)return;
  const original=new Date(selectedItem.capturedAt);const [year,month,day]=value.split('-').map(Number);original.setFullYear(year,month-1,day);selectedItem.capturedAt=original.toISOString();render();showToast('撮影日を変更しました');
});
$('#backupButton').addEventListener('click',createBackup);
async function createBackup(){
  const uploaded=userItems(); if(!uploaded.length)return; const button=$('#backupButton');button.disabled=true;button.textContent='作成中…';
  try {const metadata={exportedAt:new Date().toISOString(),files:uploaded.map(({name,capturedAt,type,file})=>({name,capturedAt,type,size:file.size,lastModified:file.lastModified}))};const entries=[...uploaded.map(item=>({name:`media/${safeName(item.name)}`,data:item.file})),{name:'tokiha-backup.json',data:new Blob([JSON.stringify(metadata,null,2)],{type:'application/json'})}];const archive=await createTar(entries),url=URL.createObjectURL(archive),link=document.createElement('a');link.href=url;link.download=`tokiha-backup-${new Date().toISOString().slice(0,10)}.tar`;link.click();setTimeout(()=>URL.revokeObjectURL(url),30000);showToast(`${uploaded.length}件のバックアップを保存しました`);}catch(error){console.error(error);showToast('バックアップを作成できませんでした');}finally{button.disabled=false;button.textContent='今すぐPCに保存';}
}
function safeName(name){return name.replace(/[\\/:*?"<>|\x00-\x1f]/g,'_').slice(0,90)||'media';}
async function createTar(entries){const blocks=[];for(const entry of entries){const bytes=new Uint8Array(await entry.data.arrayBuffer()),header=new Uint8Array(512),view=new TextEncoder();header.set(view.encode(entry.name).slice(0,100),0);writeTarField(header,100,8,'0000644');writeTarField(header,108,8,'0000000');writeTarField(header,116,8,'0000000');writeTarField(header,124,12,bytes.length.toString(8).padStart(11,'0'));writeTarField(header,136,12,Math.floor(Date.now()/1000).toString(8).padStart(11,'0'));header.fill(32,148,156);header[156]=48;header.set(view.encode('ustar\0'),257);header.set(view.encode('00'),263);const checksum=header.reduce((sum,value)=>sum+value,0).toString(8).padStart(6,'0');writeTarField(header,148,8,checksum);blocks.push(header,bytes,new Uint8Array((512-bytes.length%512)%512));}blocks.push(new Uint8Array(1024));return new Blob(blocks,{type:'application/x-tar'});}
function writeTarField(header,offset,length,value){const bytes=new TextEncoder().encode(value);header.set(bytes.slice(0,length-1),offset);header[offset+length-1]=0;}
function showToast(message){const element=$('#toast');element.textContent=message;element.classList.add('show');setTimeout(()=>element.classList.remove('show'),2400);}
render();
