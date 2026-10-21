(() => {
  'use strict';
  const root = document.getElementById('tv');
  const params = new URLSearchParams(location.search);
  const state = { screen: params.get('screen') || 'home-cinema', scenario: params.get('scenario') || 'populated', apps: false, rowOffset: 0, pendingQuality: 'HD', sub: '', overlay: null, modalReturn: null, focused: '', memories: {}, choices: {} };
  const channels = ['SVT1','SVT2','TV3','TV4','Kanal 5','TV6','Sjuan','TV8','Kunskapskanalen','SVT24'];
  const programmes = ['Vinter vid kusten','Livet i skogen','Hemvägen','En kväll tillsammans','Långt från staden','Bakom kulisserna','Vardagens hjältar','Resan norrut','Vår planet','Kvällens samtal'];
  const titles = ['Norrsken','Sista båten','Mellan städer','Den andra sidan','En ny början','Längs kusten'];
  const labels = { 'home-compare':'Hem', 'home-cinema':'Hem', 'home-split':'Hem', tv:'TV', sport:'Sport', library:'Film & serier', profile:'Profil' };
  const esc = text => String(text).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function button(id, text, action, classes='', zone='content', extra='') { return `<button id="${id}" data-zone="${zone}" data-action="${action}" class="${classes}" ${extra}>${text}</button>`; }
  function header() {
    return `<header class="topbar"><div class="brand">Min <span>TV</span></div><nav class="tabs" aria-label="Huvudnavigation">${[['home-cinema','Hem'],['tv','TV'],['sport','Sport'],['library','Film & serier']].map(([key,label]) => button(`nav-${key}`,label,`route:${key}`,labels[state.screen]===label?'active':'','nav')).join('')}</nav><div class="identity"><span class="clock">20:14</span>${button('nav-profile','<span class="avatar">P</span>Profil', 'route:profile','profile-button','nav')}</div></header>`;
  }
  function card(name,i,zone='content') {
    const quality = state.choices[name]?.quality || 'HD';
    return button(`ch-${i}`, `<span class="logo">${esc(name)}<span class="quality-label">${quality}</span></span><span class="show">${state.scenario==='no-epg'?'Ingen programinformation':esc(programmes[i])}</span><span class="progress"><span style="width:${state.scenario==='no-epg'?0:23}%"></span></span><span class="time">${state.scenario==='no-epg'?'EPG saknas':'20:00–21:00'}</span>`, `play:${name}`, 'channel-card',zone,`data-quality="${esc(name)}"`);
  }
  function hero() {
    const fresh=state.scenario==='no-history', series=state.scenario==='active-series', missing=state.scenario==='no-epg';
    return `<section class="smart-hero ${series?'series-hero':''}"><div class="hero-main"><p class="eyebrow">${series?'Fortsätt din serie':fresh?'Dina kanaler, samlade':'Tillbaka till din kanal'}</p><div class="hero-heading"><span class="channel-stamp">${series?'S1':'SVT1'}</span><div><p class="hero-kicker">${series?'Norrsken · Säsong 1, avsnitt 3':fresh?'Tillgänglig i din profil':'SVT1 · '+(state.choices.SVT1?.quality||'HD')}</p><h1>${series?'Vägen hem':fresh?'Börja med SVT1':missing?'SVT1, direkt':'Vinter vid kusten'}</h1></div></div><p class="programme-meta">${series?'18 av 48 min · 30 min kvar':missing?'Programinformation saknas. Kanalen är spelbar.':'20:00–21:00 · 46 min kvar'}</p>${missing?'':'<div class="progress"><span style="width:'+(series?38:23)+'%"></span></div>'}<div class="actions">${button('hero-play',series?'▶ Fortsätt avsnitt':'▶ Spela SVT1',series?'info:Fortsätt Norrsken S1 A3':'play:SVT1','primary','content',series?'data-vod="true"':'data-quality="SVT1"')}${button('hero-secondary',series?'Alla avsnitt':'TV-guide',series?'info:Avsnittslista':'guide')}</div></div><aside class="hero-companion"><p class="eyebrow">${series?'Senast på TV':fresh?'Hitta på din TV':'Härnäst på SVT1'}</p><h2>${series?'SVT1':missing?'TV-guiden':fresh?'Åtta kanaler. En rad.':'Äventyret väntar'}</h2><p>${series?'Öppna din senaste kanal direkt.':missing?'Vi visar inga gamla program som aktuella.':fresh?'Välj en kanal nedan. Favoriter behövs inte.':'21:00–22:00 · Dokumentär'}</p><div class="companion-bottom">${button('hero-companion',series?'▶ Spela SVT1':fresh?'Alla svenska kanaler →':'Öppna TV-guide →',series?'play:SVT1':fresh?'route:tv':'guide','section-link','content',series?'data-quality="SVT1"':'')}</div></aside></section>`;
  }
  function resume() { return `<div class="section-title resume-title"><h2>Fortsätt titta</h2><small>Sparade positioner · senast spelat först</small></div><div class="resume-row">${[0,1,2].map(i=>button('resume-'+i,`<span class="resume-thumb">${i===0?'S1':'FILM'}</span><span><h3>${titles[i]}</h3><p>${i===0?'S1 · A3 · 30 min kvar':i===1?'Film · 42 min kvar':'Film · 19 min kvar'}</p></span>`,'info:'+titles[i],'resume-card','content','data-vod="true"')).join('')}</div>`; }
  function nowRow(count=8) {return `<div class="section-title"><div><h2>På TV just nu</h2><small>Svenska kanaler · ${state.rowOffset+1}–${Math.min(state.rowOffset+count,channels.length)} av ${channels.length}</small></div>${button('all-tv','Alla kanaler →','route:tv','section-link')}</div><div class="channel-row count-${count}">${channels.slice(state.rowOffset,state.rowOffset+count).map((c,i)=>card(c,i+state.rowOffset)).join('')}</div><div class="row-controls">${button('row-prev','← Föregående','row:prev','section-link','content',state.rowOffset===0?'disabled':'')}${button('row-next','Fler kanaler →','row:next','section-link','content',state.rowOffset+count>=channels.length?'disabled':'')}</div>`;}
  function home() {
    if(state.scenario==='empty')return emptyPage('Ditt hem börjar med en källa','Lägg till din egen spellista. Inga kanaler ingår i Min TV.','Lägg till källa');
    if(state.screen==='home-compare')return `<div class="page-heading"><h1>Sex breda eller åtta kompakta?</h1><p>Samma TV, samma textstorlek · jämför utan favoritlista</p></div><div class="comparison-row"><h2>6 · Mer plats för programtitlar</h2>${nowRow(6).replace(/id="([^"]+)"/g,'id="six-$1"')}</div><div class="comparison-row"><h2>8 · Rekommenderad standardordning direkt synlig</h2>${nowRow(8).replace(/id="([^"]+)"/g,'id="eight-$1"')}</div>`;
    if(state.screen==='home-split')return `<div class="alternative"><section class="alternative-intro"><p class="eyebrow">Din kväll</p><h1>Vad vill du se?</h1><p>Kanalerna först. Din senaste visning finns alltid nära.</p>${button('alt-recent','▶ Tillbaka till SVT1','play:SVT1','primary','content','data-quality="SVT1"')}${button('alt-guide','TV-guide','guide')}</section><section><div class="section-title"><h2>På TV just nu</h2><small>Fasta kanalplatser</small></div><div class="alternative-grid">${channels.slice(0,8).map((c,i)=>card(c,i)).join('')}</div></section></div>${state.scenario==='no-history'?'':resume()}`;
    return `${hero()}${nowRow(state.scenario==='six-cards'?6:8)}${['no-history','no-progress','active-series'].includes(state.scenario)?'':resume()}`;
  }
  function secondary(items,active) {return `<nav class="subnav" aria-label="Delvyer">${items.map((s,i)=>button(`sub-${i}`,s,`sub:${s}`,active===s?'active':'','sub')).join('')}</nav>`;}
  function emptyPage(title,body,cta='Utforska TV') {return `<section class="empty"><p class="eyebrow">Din profil</p><h1>${title}</h1><p>${body}</p><div class="actions">${button('empty-action',cta,`info:${cta}`,'primary')}${button('empty-profile','Byt profil','route:profile')}</div></section>`;}
  function guide() {return `<div class="page-heading"><h1>TV-guide</h1>${button('guide-now','Nu','info:Nu visar aktuell tid','section-link')}</div><div class="guide-table"><div class="guide-ruler"><span>I dag · demo</span><span>20:00</span><span>20:30</span><span>21:00</span></div>${channels.slice(0,5).map((c,i)=>`<div class="guide-row"><span>${c}</span>${button('g'+i+'a',programmes[i],`play:${c}`,'current','content',`data-quality="${c}"`)}${button('g'+i+'b','Nästa demoprogram',`info:Programdetaljer`)}${button('g'+i+'c','Kommande',`info:Programdetaljer`)}</div>`).join('')}</div>`;}
  function tv() {
    const sub = state.sub || 'Svenska kanaler';
    let body = '';
    if(state.scenario==='empty') body = emptyPage('Inga TV-kanaler i den här profilen','Välj en tillgänglig källa eller lägg till en egen spellista. Favoriter behöver inte vara ifyllda.','Hantera källor');
    else if(sub==='TV-guide') body=guide();
    else if(sub==='Favoriter') body=emptyPage('Dina favoriter samlas här','Svenska kanaler är tillgängliga även när favoritlistan är tom. Lägg till en favorit via kanalens alternativ.');
    else if(sub==='Inspelningar') body=emptyPage('Inspelningar och planerade program','Här återanvänds dagens inspelningslista. Spela, planera och hantera sparade inspelningar.','Öppna inspelningslista');
    else body=`<div class="tv-body"><div class="tv-grid">${channels.slice(0,8).map((c,i)=>card(c,i)).join('')}</div><aside class="tv-preview"><div class="preview-box">SVT1</div><p class="eyebrow">Markerad kanal · HD</p><h2>${state.scenario==='no-epg'?'SVT1':programmes[0]}</h2><p>${state.scenario==='no-epg'?'Programinformation saknas. Kanalen kan fortfarande öppnas.':'Demoprogram · 20:00–21:00. Beskrivningen följer markerad kanal och program.'}</p><div class="actions">${button('tv-preview-play','▶ Spela','play:SVT1','primary','content','data-quality="SVT1"')}</div><p style="margin-top:26px;font-size:18px">Håll OK för källa och kvalitet</p></aside></div>`;
    return `<div class="page-heading"><h1>TV</h1><p>Din profil · tillgängliga källor · grupperade kanalversioner</p></div>${secondary(['Svenska kanaler','Alla kanaler','Favoriter','TV-guide','Inspelningar','Sök'],sub)}${body}`;
  }
  function sport() { const offline=state.scenario==='offline';
    return `<div class="page-heading"><h1>Din sport, samlad</h1>${button('sport-settings','Anpassa sport','info:Sportinställningar','section-link')}</div>${secondary(['För dig','Mina lag','Matcher','Kanaler','Twitch'],state.sub || 'För dig')}<div class="sports-grid"><section class="sport-card"><div class="team-mark">FBK</div><p class="eyebrow">Mina lag</p><h2>Färjestad BK</h2><p>${offline?'Offline. Senast sparade uppgifter visas med sin ålder.':'Nästa match visas när ett verifierat spelschema finns. Ingen matchtid hittas på i denna skiss.'}</p>${button('teams','Välj mina lag','info:Lagväljare')}</section><section class="sport-card"><span class="status-line">${offline?'Offline · ingen ny hämtning':'Utgå från din TV-guide'}</span><h2>Matcher på din TV</h2><p>En sändning blir spelbar först när match, kanal och aktuell profil har verifierats.</p>${button('sports-channels','Visa sportkanaler','info:Sportkanaler')}</section><section class="sport-card twitch"><div class="team-mark">T</div><p class="eyebrow">Twitch · valfritt</p><h2>Dina streams</h2><p>${offline?'Status är inte tillgänglig offline.':'Återanvänd din befintliga anslutning och öppna i en kompatibel TV-app.'}</p>${button('twitch','Öppna Twitch','info:Twitch-öppning')}</section></div><div class="section-title"><h2>${offline?'Vad du fortfarande kan göra':'När något saknas'}</h2></div><p class="muted" style="font-size:25px;line-height:1.65">${offline?'Dina sparade lag och tillgängliga TV-kanaler finns kvar. En gammal match märks aldrig som live utan aktuell evidens.':'Tomt schema, saknad sändning och nätverksfel visas var för sig. Ingen extra sporthämtning startar på Hem.'}</p>`;
  }
  function library() {
    const sub=state.sub||'För dig';
    if(state.scenario==='empty') return emptyPage('Här finns plats för dina berättelser','Filmer och serier visas när de finns i profilens tillgängliga källor. Inga tjänster eller titlar ingår.','Hantera källor');
    if(sub==='Mitt bibliotek') return `<div class="page-heading"><h1>Mitt bibliotek</h1></div>${secondary(['För dig','Filmer','Serier','Sök'],sub)}<div class="hub">${['Favoriter','Historik','Nedladdningar','Inspelningar'].map((v,i)=>button('lib'+i,v,'info:'+v)).join('')}</div>`;
    return `<div class="page-heading"><h1>Film & serier</h1><p>Din katalog · inga nya abonnemang</p></div>${secondary(['För dig','Filmer','Serier','Sök'],sub)}<div class="section-title"><h2>Fortsätt där du var</h2><small>Illustrativa titlar · inget verkligt innehåll</small></div><div class="poster-row">${titles.map((t,i)=>button('poster'+i,`<span class="poster-art"></span><span class="poster-genre">${i%2?'SERIE':'FILM'}</span><span class="poster-copy"><h3>${t}</h3><p>${i%2?'S1 · A3 · fortsätt':'Återuppta filmen'}</p></span>`,`info:${t}`,'poster','content','data-vod="true"')).join('')}</div>`;
  }
  function profile() {return `<div class="page-heading"><h1>Profil & inställningar</h1>${button('settings-search','Sök inställningar','search','section-link')}</div><div class="profile-grid"><aside class="profile-card"><span class="avatar large-avatar">P</span><h2>Profil 1</h2><p>Dina val följer profilen.</p>${button('switch-profile','Byt profil','info:Profilväljare')}${button('manage-profiles','Hantera profiler','info:Profilhantering')}<p style="margin-top:40px;font-size:19px">Min TV · Om & uppdateringar</p>${button('about','Om Min TV','info:Om och uppdateringskontroll')}</aside><section><div class="section-title" style="margin-top:0"><h2>Ditt bibliotek</h2></div><div class="hub">${['Favoriter','Historik','Nedladdningar','Inspelningar'].map((v,i)=>button('hub'+i,v,'info:'+v)).join('')}</div><div class="section-title"><h2>Inställningar</h2></div><div class="settings-grid">${[['Källor & TV-guide','Spellistor, EPG, matchning'],['Ljud & undertexter','Språk, spår, undertextstil'],['Utseende & Hem','Layout, teman, Mina appar'],['Spelare & inspelning','Bild, uppspelning, lagring'],['Data & säkerhet','Backup, återställning, Local Sync'],['App & diagnostik','Fellogg, nätverk, uppdateringar']].map(([h,d],i)=>button('setting'+i,`<strong>${h}</strong><small>${d}</small>`,'info:'+h)).join('')}</div></section></div>`;}
  function overlay() {
    if(!state.overlay) return '';
    if(state.overlay.type==='quality') {
      const name=state.overlay.name; const selected=state.choices[name]?.quality || 'HD';
      return `<div class="overlay" role="dialog" aria-modal="true" aria-label="Källa och kvalitet"><section class="dialog"><p class="eyebrow">Källa & kvalitet</p><h1>${esc(name)}</h1><p>OK på en version sparar ditt val och startar kanalen. Bara tillgängliga källor visas · syntetiskt exempel.</p>${[['HD','Källa A'],['FHD','Källa B'],['4K','Källa C']].map(([q,s],i)=>button('quality'+i,`<span>${q===selected?'✓':'○'}</span><span>${q} · ${s}</span><span class="badge">${q===selected?'Standard · ':''}Spara & spela →</span>`,`choose:${q}`,'option','modal')).join('')}${button('save-only','Spara markerad version som standard — spela inte','save-only','save-only','modal')}<p class="dialog-footer">Tillbaka avbryter utan ändring · Uppspelning och val simuleras endast i skissen</p></section></div>`;
    }
    const search=state.overlay.type==='search';
    return `<div class="overlay" role="dialog" aria-modal="true" aria-label="Förhandsvisning"><section class="dialog"><p class="eyebrow">Designförhandsvisning</p><h1>${esc(state.overlay.name)}</h1><p>${search?'Kontextuell sökning öppnas här. I Android används den befintliga sökmotorn och IME; denna skiss ansluter inte till din katalog.':'Den befintliga funktionen öppnas här i den framtida Android-vyn. Skissen spelar inte upp media, hämtar data eller ändrar dina installationer.'}</p>${button('close-overlay','Tillbaka','close','primary','modal')}</section></div>`;
  }
  function render(focusId) {
    root.classList.toggle('with-apps',state.apps);
    root.innerHTML=header()+(state.screen.startsWith('home')?home():state.screen==='tv'?tv():state.screen==='sport'?sport():state.screen==='library'?library():profile())+`<footer class="footer"><strong>OK välj / spela &nbsp; · &nbsp; Håll OK källa / alternativ &nbsp; · &nbsp; Tillbaka</strong><span>DESIGNPROTOTYP · SYNTETISKA DATA · 1920 × 1080</span></footer>`+(state.apps && state.screen.startsWith('home')?`<section class="apps-row">${button('app-svt','SVT Play','info:Extern app')}${button('app-other','Mina appar','info:Valfria externa appar')}</section>`:'')+overlay();
    if(focusId) focus(focusId);
    document.getElementById('screen').value=state.screen;
    document.getElementById('scenario').value=state.scenario;
    scale();
  }
  function focus(id) {
    const el=root.querySelector('#'+CSS.escape(id)); if(!el || el.disabled)return false;
    root.querySelectorAll('.focused').forEach(n=>n.classList.remove('focused')); el.classList.add('focused'); el.focus({preventScroll:true});state.focused=id;if(id.startsWith('quality'))state.pendingQuality=['HD','FHD','4K'][Number(id.slice(7))];
    root.classList.toggle('compact',el.dataset.zone!=='nav');
    if(el.dataset.zone==='content')state.memories[state.screen+'|'+state.sub]=id;
    return true;
  }
  function first(zone) {return root.querySelector(`button[data-zone="${zone}"]:not(:disabled)`)?.id;}
  function contentEntry() { return state.memories[state.screen+'|'+state.sub] || first('content') || first('sub') || 'nav-profile'; }
  function close() {const id=state.modalReturn;state.overlay=null;render(id||contentEntry());}
  function openQuality(name) {state.modalReturn=state.focused;state.pendingQuality=state.choices[name]?.quality||'HD';state.overlay={type:'quality',name};render('quality'+Math.max(0,['HD','FHD','4K'].indexOf(state.choices[name]?.quality || 'HD')));}
  function act(action,el) {
    if(action.startsWith('route:')) {state.screen=action.slice(6);state.sub='';state.overlay=null;render(el?.dataset.zone==='nav'?el.id:contentEntry());}
    else if(action.startsWith('sub:')) {state.sub=action.slice(4);if(state.sub==='Sök'){state.modalReturn=state.focused;state.overlay={type:'search',name:'Sök i '+labels[state.screen]};render('close-overlay');}else render(el?.id||first('sub'));}
    else if(action.startsWith('choose:')) {const name=state.overlay.name;state.choices[name]={quality:action.slice(7)};state.overlay={type:'info',name:'Spela '+name+' · '+action.slice(7)+' (simulerat)'};render('close-overlay');}
    else if(action==='save-only'){state.choices[state.overlay.name]={quality:state.pendingQuality};close();}
    else if(action.startsWith('row:')){const count=state.scenario==='six-cards'?6:8;state.rowOffset=action==='row:next'?Math.min(channels.length-count,state.rowOffset+count):Math.max(0,state.rowOffset-count);render('ch-'+state.rowOffset);}
    else if(action==='close')close();
    else if(action==='guide') {state.screen='tv';state.sub='TV-guide';render('guide-now');}
    else {state.modalReturn=state.focused;state.overlay={type:action==='search'?'search':'info',name:action.startsWith('play:')?'Spela '+action.slice(5):action.replace(/^info:/,'')};render('close-overlay');}
  }
  function back() {
    if(state.overlay){close();return;}
    const active = state.screen.startsWith('home')?'nav-home-cinema':'nav-'+state.screen;
    const el=root.querySelector('#'+CSS.escape(state.focused));
    if(el?.dataset.zone==='content'){focus(first('sub')||active);return;}
    if(el?.dataset.zone==='sub'){focus(active);return;}
    if(state.screen!=='home-cinema' && state.screen!=='home-split'){state.screen='home-cinema';state.sub='';render('nav-home-cinema');return;}
    state.modalReturn=state.focused;state.overlay={type:'info',name:'Tillbaka till Google TV'};render('close-overlay');
  }
  function move(key) {
    const current=root.querySelector('#'+CSS.escape(state.focused));if(!current){focus(contentEntry());return;}
    if(state.overlay?.type==='quality' && key==='ArrowRight' && current.id.startsWith('quality')){focus('save-only');return;}
    if(state.overlay?.type==='quality' && key==='ArrowLeft' && current.id==='save-only'){focus('quality'+['HD','FHD','4K'].indexOf(state.pendingQuality));return;}
    if(current.dataset.zone==='nav' && key==='ArrowDown') {
      const target=current.dataset.action.slice(6);
      if(target!==state.screen && !(target==='home-cinema'&&state.screen==='home-split')){state.screen=target;state.sub='';render();}
      focus(first('sub') || contentEntry());return;
    }
    if(current.dataset.zone==='sub' && key==='ArrowDown'){focus(contentEntry());return;}
    if(state.screen==='home-cinema' && current.id.startsWith('ch-')) {
      const index=Number(current.id.slice(3)), count=state.scenario==='six-cards'?6:8;
      if(key==='ArrowRight' && index===state.rowOffset+count-1 && index<channels.length-1){state.rowOffset++;render('ch-'+(index+1));return;}
      if(key==='ArrowLeft' && index===state.rowOffset && index>0){state.rowOffset--;render('ch-'+(index-1));return;}
    }
    const a=current.getBoundingClientRect(), ax=a.x+a.width/2, ay=a.y+a.height/2;
    let candidates=[...root.querySelectorAll('button:not(:disabled)')].filter(n=>n!==current && n.getClientRects().length && (state.overlay?n.dataset.zone==='modal':n.dataset.zone!=='modal'));
    if(current.dataset.zone==='nav' && (key==='ArrowLeft'||key==='ArrowRight'))candidates=candidates.filter(n=>n.dataset.zone==='nav');
    if(current.dataset.zone==='sub' && (key==='ArrowLeft'||key==='ArrowRight'))candidates=candidates.filter(n=>n.dataset.zone==='sub');
    const horizontal=key==='ArrowLeft'||key==='ArrowRight',sign=key==='ArrowLeft'||key==='ArrowUp'?-1:1;
    const ranked=candidates.map(n=>{const b=n.getBoundingClientRect(),dx=b.x+b.width/2-ax,dy=b.y+b.height/2-ay;return{n,forward:(horizontal?dx:dy)*sign,off:Math.abs(horizontal?dy:dx)};}).filter(x=>x.forward>3).sort((x,y)=>(x.forward+x.off*3)-(y.forward+y.off*3));
    if(ranked[0])focus(ranked[0].n.id);
  }
  root.addEventListener('click',e=>{const b=e.target.closest('button');if(b){focus(b.id);act(b.dataset.action,b);}});
  let timer=null,held=false,pressed=false;
  document.addEventListener('keydown',e=>{
    if(e.target.closest('.review-tools'))return;
    if(e.key.startsWith('Arrow')){e.preventDefault();move(e.key);}
    if(e.key==='Escape'||e.key==='Backspace'){e.preventDefault();back();}
    if(e.key==='Enter') {e.preventDefault();if(pressed)return;pressed=true;held=false;timer=setTimeout(()=>{const b=root.querySelector('#'+CSS.escape(state.focused));if(b?.dataset.quality){held=true;openQuality(b.dataset.quality);}else if(b?.dataset.vod){held=true;state.modalReturn=state.focused;state.overlay={type:'info',name:'Alternativ · versioner, favorit, information'};render('close-overlay');}},500);}
  });
  document.addEventListener('keyup',e=>{if(e.key==='Enter'&&pressed){e.preventDefault();clearTimeout(timer);pressed=false;if(!held){const b=root.querySelector('#'+CSS.escape(state.focused));if(b)act(b.dataset.action,b);}held=false;}});
  document.getElementById('screen').addEventListener('change',e=>{state.rowOffset=0;state.screen=e.target.value;state.sub='';state.overlay=null;render(contentEntry());});
  document.getElementById('scenario').addEventListener('change',e=>{state.rowOffset=0;state.scenario=e.target.value;state.overlay=null;state.sub='';render();focus(contentEntry());});
  document.getElementById('apps').addEventListener('change',e=>{state.apps=e.target.checked;render(contentEntry());});
  document.getElementById('source-demo').addEventListener('click',()=>openQuality('SVT1'));
  function scale(){const width=params.has('capture')?1920:Math.min(window.innerWidth,1920);const factor=width/1920;const view=document.getElementById('viewport');view.style.width=width+'px';view.style.height=1080*factor+'px';root.style.transform=`scale(${factor})`;}
  if(params.has('capture'))document.body.classList.add('capture');
  window.addEventListener('resize',scale);render();focus(contentEntry());
  window.designPreview={state,render,focus,move,back,openQuality};
})();
