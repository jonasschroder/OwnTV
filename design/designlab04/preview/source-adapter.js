// Lab-only adapters. Never read Android storage, provider URLs, or credentials.
let dataMode='MOCK', liveResult=null, requestGeneration=0;
const mockAdapter=()=>({mode:'MOCK',source:'Deterministiska designfixturer',fetchedAt:null,freshness:'synthetic',sports:{schedule:'MOCK',liveScore:isLive()?'MOCK':'UNAVAILABLE',broadcastAssignment:verified()?'MOCK: verified only inside synthetic scenario':'UNAVAILABLE',library:{mode:'MOCK',channels:hockeyIndexes().map(i=>i?'TV Hockey Live '+i:'TV Hockey')}},twitch:{mode:'MOCK',state:state.twitch},epg:{mode:'MOCK',state:state.epgMode},metadata:{mode:'MOCK'}});
const unavailableAdapter=()=>({mode:'UNAVAILABLE',source:'Simulerat bortfall',fetchedAt:null,freshness:'unknown',reason:'Inga externa data tillgängliga. Inga demodata används som LIVE.'});
const originalRender=render;
const modeLabel=document.createElement('label');modeLabel.textContent='Data ';
const modeSelect=document.createElement('select');modeSelect.id='data-mode';modeSelect.setAttribute('aria-label','Datakälla');
['MOCK','LIVE','UNAVAILABLE'].forEach(x=>modeSelect.add(new Option(x,x)));modeLabel.append(modeSelect);$('lab').prepend(modeLabel);
const statusButton=document.createElement('button');statusButton.textContent='Källstatus';statusButton.setAttribute('aria-expanded','false');$('lab').append(statusButton);
const panel=document.createElement('section');panel.hidden=true;panel.setAttribute('aria-label','Källstatus');panel.style.cssText='position:fixed;z-index:100;right:20px;bottom:90px;max-height:65vh;overflow:auto;width:min(680px,90vw);padding:22px;background:#10251f;border:1px solid #8fc9b1;border-radius:12px;color:#fff;font:15px/1.5 system-ui;white-space:pre-wrap';document.body.append(panel);
function refreshFreshness(){if(liveResult?.mode==='LIVE'){const age=Date.now()-Date.parse(liveResult.fetchedAt);liveResult.freshness=!Number.isFinite(age)||age< -60000?'unknown':age>liveResult.maxAgeSeconds*1000?'stale':'fresh';}}
function sourceStatus(){refreshFreshness();const d=dataMode==='MOCK'?mockAdapter():dataMode==='LIVE'?liveResult:unavailableAdapter();panel.textContent=JSON.stringify(d?.mode==='LIVE'?{...d,sports:{schedule:'VERIFIED RESPONSE: partial fixtures',liveScore:'UNAVAILABLE: not validated',broadcastAssignment:'UNAVAILABLE: no verified current assignment',iptvChannel:'UNAVAILABLE: private library not connected'},twitch:'AUTH_REQUIRED (probe 2026-10-24)',epg:'UNTESTED: authorized source required',metadata:'AUTH_REQUIRED (probe 2026-10-24)'}:d,null,2)+'\n\nIPTV/EPG/logotyper: ej anslutet · auktoriserad profilkälla krävs (värd okänd)\nTwitch: api.twitch.tv + id.twitch.tv · server-OAuth + Client-ID krävs (401 verifierat)\nTMDB: api.themoviedb.org · servernyckel/auktoriserad proxy krävs (401 verifierat); image.tmdb.org bilder ej testade\nHockey: stats.swehockey.se / www.tvmatchen.nu · återanvändningsrätt ej klarlagd. SHL/HA full täckning och aktuella TV-kopplingar saknas\nSe SOURCE-READINESS.md för inventering.';}
statusButton.onclick=()=>{panel.hidden=!panel.hidden;statusButton.setAttribute('aria-expanded',String(!panel.hidden));sourceStatus();};
render=function(wanted){
 refreshFreshness();
 if(dataMode==='MOCK'){originalRender(wanted);sourceStatus();return;}
 actions={};rows=[];$('header').innerHTML=header();$('screen').dataset.page=state.page;state.stack=[];$('modal').innerHTML='';$('screen').inert=false;$('header').inert=false;
 const d=dataMode==='LIVE'?liveResult:unavailableAdapter();
 const usable=d?.mode==='LIVE'&&Array.isArray(d.fixtures)&&['Hem','Sport'].includes(navs[state.page]);
 let html=`<h1>${esc(navs[state.page])}</h1><p class="top-note">${esc(dataMode)} · ${esc(d?.source||'Hämtar publik källa…')}</p>`;
 if(usable){html+=`<p class="notice">Begränsat SHL-urval · ingen verifierad TV-kanal · inga live-resultat utlovas. Tiden visas enligt källan, tidszon ej verifierad.</p>`;
 d.fixtures.forEach((e,i)=>{const id='live-fixture-'+i;html+=`<section class="top-note"><h2>${esc(e.strHomeTeam)} – ${esc(e.strAwayTeam)}</h2><p>${esc(e.dateEvent)} ${esc(e.strTime||'Tid saknas')} · ${esc(e.strStatus||'Status saknas')}</p>${button(id,'secondary','Välj kanal manuellt',()=>toast('Privat kanalbibliotek saknas i labbet. Ingen sändningskoppling är verifierad.'))}</section>`;addRow('publik matchinformation',[id]);});
 }else{html+=`<h2>Data är inte tillgängliga</h2><p class="top-note">${esc(d?.reason||'Denna vy saknar en auktoriserad datakälla i Design Lab. Ingen syntetisk historik, EPG eller inloggad profil visas i LIVE.')}</p>`;html+=button('source-retry','secondary','Visa källstatus',()=>{panel.hidden=false;statusButton.setAttribute('aria-expanded','true');sourceStatus();});addRow('källstatus',['source-retry']);}
 html+=`<p class="library-footer">Källa för denna vy: ${esc(usable?d.source:'—')} · Hämtad: ${esc(usable?d.fetchedAt:'—')} · Färskhet: ${esc(usable?d.freshness:'unknown')}<br>Övriga beroenden är inte anslutna. Byt till MOCK för hela designscenariot.</p>`;
 $('screen').innerHTML=html;document.querySelector('#tv footer > span:last-child').textContent=`DESIGNLABB · ${dataMode} · INGEN STRÖMNING`;focus(wanted||'nav'+state.page,false);fit();sourceStatus();
};
modeSelect.onchange=async()=>{
 state.mem={};state.scroll={};dataMode=modeSelect.value;document.querySelector('.lab-note').textContent=dataMode==='MOCK'?'Endast lokala designfixturer':'Separata publika data · begränsad täckning';['scenario','epg','resume'].forEach(id=>$(id).disabled=dataMode!=='MOCK');const generation=++requestGeneration;state.stack=[];panel.hidden=true;
 if(dataMode==='MOCK'){document.querySelector('#tv footer > span:last-child').textContent='DESIGNLABB 03 · SYNTETISKA DATA · INGEN STRÖMNING';render('nav'+state.page);return;}
 liveResult=null;render('nav'+state.page);
 if(dataMode!=='LIVE')return;
 try{const response=await fetch('/api/fixtures',{signal:AbortSignal.timeout(10000)});if(!response.ok)throw Error();const d=await response.json();if(generation!==requestGeneration)return;
 if(!['LIVE','UNAVAILABLE'].includes(d.mode)||!Array.isArray(d.fixtures)||typeof d.source!=='string')throw Error();
 if(d.mode==='LIVE'){const age=Date.now()-Date.parse(d.fetchedAt);d.freshness=!Number.isFinite(age)||age< -60000?'unknown':age>d.maxAgeSeconds*1000?'stale':'fresh';}
 liveResult=d;
 }catch{if(generation!==requestGeneration)return;liveResult={...unavailableAdapter(),reason:'Lokal proxy eller extern källa kunde inte nås. Ingen mock-ersättning.'};}
 render(state.focus);
};
// One compact developer bar; detailed fixture controls live in a dismissible drawer.
const drawer=document.createElement('section');drawer.id='scenarios-panel';drawer.className='scenario-panel';drawer.hidden=true;drawer.setAttribute('aria-label','Designscenarier');
['scenario','epg','resume','overlay'].forEach(id=>drawer.append($(id).closest('label')));document.body.append(drawer);
const scenariosButton=document.createElement('button');scenariosButton.textContent='Scenarier';scenariosButton.setAttribute('aria-expanded','false');$('lab').insertBefore(scenariosButton,statusButton);
scenariosButton.onclick=()=>{drawer.hidden=!drawer.hidden;panel.hidden=true;statusButton.setAttribute('aria-expanded','false');scenariosButton.setAttribute('aria-expanded',String(!drawer.hidden));};
const originalStatusClick=statusButton.onclick;statusButton.onclick=()=>{drawer.hidden=true;scenariosButton.setAttribute('aria-expanded','false');originalStatusClick();};
function mockSelect(labelText,id,values,apply){const label=document.createElement('label');label.textContent=labelText+' ';const select=document.createElement('select');select.id=id;select.setAttribute('aria-label',labelText);values.forEach(([key,text])=>select.add(new Option(text,key)));label.append(select);drawer.append(label);select.onchange=()=>{apply(select.value);render(state.focus);};return select;}
const twitchSelect=mockSelect('Twitch','twitch-state',[['auth','Inloggning saknas'],['online','Online'],['offline','Offline'],['error','API-fel']],x=>state.twitch=x);
const teamSelect=mockSelect('Favoritlag','favorite-team',[['Brynäs','Brynäs'],['Frölunda','Frölunda']],x=>state.team=x);
const librarySelect=mockSelect('Kanalbibliotek','channel-library',[['full','Mock: alla hockeykanaler'],['partial','Mock: Hockey + Live 2'],['none','Mock: inga hockeykanaler']],x=>state.library=x);
const previousModeChange=modeSelect.onchange;modeSelect.onchange=async()=>{drawer.hidden=true;scenariosButton.setAttribute('aria-expanded','false');await previousModeChange();[twitchSelect,teamSelect,librarySelect].forEach(el=>el.disabled=dataMode!=='MOCK');};
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&(!drawer.hidden||!panel.hidden)){event.preventDefault();event.stopImmediatePropagation();const owner=drawer.hidden?statusButton:scenariosButton;drawer.hidden=true;panel.hidden=true;scenariosButton.setAttribute('aria-expanded','false');statusButton.setAttribute('aria-expanded','false');owner.focus();}},true);
