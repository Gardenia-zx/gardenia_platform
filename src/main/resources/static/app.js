const state={draftId:localStorage.getItem('zg_draft_id')||'',accessToken:localStorage.getItem('zg_access_token')||'',refreshToken:localStorage.getItem('zg_refresh_token')||''};
const $=s=>document.querySelector(s);const responseView=$('#responseView');const detailView=$('#detailView');const relationView=$('#relationView');const ragView=$('#ragView');
$('#accessToken').value=state.accessToken;$('#refreshToken').value=state.refreshToken;$('#currentDraftId').textContent=state.draftId||'未创建';
function headers(extra={}){const h={'Content-Type':'application/json',...extra};const t=$('#accessToken').value.trim();if(t)h.Authorization=`Bearer ${t}`;return h;}
function formDataObj(form){const data=Object.fromEntries(new FormData(form).entries());Object.keys(data).forEach(k=>{if(data[k]==='')delete data[k];});return data;}
function show(target,data){target.textContent=typeof data==='string'?data:JSON.stringify(data,null,2);}
async function request(url,{method='GET',body,raw=false,auth=true,headers:extH={}}={}){const h=auth?headers(extH):{'Content-Type':'application/json',...extH};if(raw)delete h['Content-Type'];const r=await fetch(url,{method,headers:h,body:raw?body:(body?JSON.stringify(body):undefined)});let data=null;const tx=await r.text();try{data=tx?JSON.parse(tx):{ok:r.ok,status:r.status};}catch{data=tx;}if(!r.ok)throw{status:r.status,data};return data;}
function bindForm(id,fn){$(id).addEventListener('submit',async e=>{e.preventDefault();try{const data=await fn(e.target);show(responseView,data);}catch(err){show(responseView,err);}})}

$('#tabNav').addEventListener('click',e=>{if(e.target.tagName!=='BUTTON')return;document.querySelectorAll('#tabNav button').forEach(b=>b.classList.remove('active'));e.target.classList.add('active');document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));$('#'+e.target.dataset.tab).classList.add('active');});
$('#saveTokenBtn').onclick=()=>{localStorage.setItem('zg_access_token',$('#accessToken').value.trim());localStorage.setItem('zg_refresh_token',$('#refreshToken').value.trim());show(responseView,'Token 已保存');};
$('#clearTokenBtn').onclick=()=>{$('#accessToken').value='';$('#refreshToken').value='';localStorage.removeItem('zg_access_token');localStorage.removeItem('zg_refresh_token');show(responseView,'Token 已清空');};

bindForm('#sendCodeForm',f=>request('/api/v1/auth/send-code',{method:'POST',auth:false,body:formDataObj(f)}));
bindForm('#registerForm',f=>request('/api/v1/auth/register',{method:'POST',auth:false,body:{...formDataObj(f),agreeTerms:new FormData(f).get('agreeTerms')==='on'}}));
bindForm('#loginForm',async f=>{const d=await request('/api/v1/auth/login',{method:'POST',auth:false,body:formDataObj(f)});if(d?.token){$('#accessToken').value=d.token.accessToken||'';$('#refreshToken').value=d.token.refreshToken||'';localStorage.setItem('zg_access_token',$('#accessToken').value);localStorage.setItem('zg_refresh_token',$('#refreshToken').value);}return d;});
bindForm('#tokenRefreshForm',async f=>{const refreshToken=formDataObj(f).refreshToken||$('#refreshToken').value.trim();const d=await request('/api/v1/auth/token/refresh',{method:'POST',auth:false,body:{refreshToken}});$('#accessToken').value=d.accessToken||'';$('#refreshToken').value=d.refreshToken||'';return d;});
bindForm('#resetPwdForm',f=>request('/api/v1/auth/password/reset',{method:'POST',auth:false,body:formDataObj(f)}));
$('#meBtn').onclick=async()=>{try{show(responseView,await request('/api/v1/auth/me'));}catch(e){show(responseView,e)}};
$('#logoutBtn').onclick=async()=>{try{show(responseView,await request('/api/v1/auth/logout',{method:'POST',body:{refreshToken:$('#refreshToken').value.trim()}}));}catch(e){show(responseView,e)}};

function renderFeed(list,target){target.innerHTML='';(list||[]).forEach(it=>{const d=document.createElement('div');d.className='feed-item';d.innerHTML=`<h4>${it.title||'无标题'} <small>#${it.id}</small></h4><p>${it.description||''}</p><p>作者：${it.authorNickname||'-'} ｜ ❤️${it.likeCount||0} ⭐${it.favoriteCount||0}</p>`;target.appendChild(d);});}
bindForm('#feedForm',async f=>{const d=formDataObj(f);const res=await request(`/api/v1/knowposts/feed?page=${d.page||1}&size=${d.size||10}`,{auth:false});renderFeed(res.items,$('#feedCards'));return res;});
bindForm('#searchForm',async f=>{const d=formDataObj(f);const q=new URLSearchParams({q:d.q,size:d.size||10});if(d.tags)q.set('tags',d.tags);if(d.after)q.set('after',d.after);const res=await request(`/api/v1/search?${q.toString()}`);renderFeed(res.items,$('#feedCards'));return res;});
bindForm('#suggestForm',f=>{const d=formDataObj(f);return request(`/api/v1/search/suggest?prefix=${encodeURIComponent(d.prefix)}&size=${d.size||8}`);});

$('#createDraftBtn').onclick=async()=>{try{const d=await request('/api/v1/knowposts/drafts',{method:'POST'});state.draftId=d.id;localStorage.setItem('zg_draft_id',d.id);$('#currentDraftId').textContent=d.id;show(responseView,d);}catch(e){show(responseView,e)}};
bindForm('#presignForm',f=>{const d=formDataObj(f);return request('/api/v1/storage/presign',{method:'POST',body:{...d,postId:d.postId||state.draftId}});});
bindForm('#confirmForm',f=>{const d=formDataObj(f);return request(`/api/v1/knowposts/${d.id||state.draftId}/content/confirm`,{method:'POST',body:{objectKey:d.objectKey,etag:d.etag,size:Number(d.size),sha256:d.sha256}});});
bindForm('#patchPostForm',f=>{const d=formDataObj(f);const body={};['title','description','visible'].forEach(k=>d[k]&&(body[k]=d[k]));if(d.tagId)body.tagId=Number(d.tagId);if(d.tags)body.tags=d.tags.split(',').map(s=>s.trim()).filter(Boolean);if(d.imgUrls)body.imgUrls=d.imgUrls.split(',').map(s=>s.trim()).filter(Boolean);body.isTop=new FormData(f).get('isTop')==='on';return request(`/api/v1/knowposts/${d.id||state.draftId}`,{method:'PATCH',body});});
$('#publishBtn').onclick=async()=>{const id=$('#publishId').value.trim()||state.draftId;try{show(responseView,await request(`/api/v1/knowposts/${id}/publish`,{method:'POST'}));}catch(e){show(responseView,e)}};
$('#mineBtn').onclick=async()=>{try{const d=await request('/api/v1/knowposts/mine?page=1&size=20');renderFeed(d.items,$('#mineCards'));show(responseView,d);}catch(e){show(responseView,e)}};

bindForm('#detailForm',async f=>{const d=formDataObj(f);$('#actionEntityId').value=d.id;const res=await request(`/api/v1/knowposts/detail/${d.id}`,{auth:false});show(detailView,res);return res;});
async function action(path){const entityId=$('#actionEntityId').value.trim();if(!entityId)throw new Error('请先输入帖子ID');return request(`/api/v1/action/${path}`,{method:'POST',body:{entityType:'knowpost',entityId}})}
$('#likeBtn').onclick=()=>action('like').then(r=>show(responseView,r)).catch(e=>show(responseView,e));$('#unlikeBtn').onclick=()=>action('unlike').then(r=>show(responseView,r)).catch(e=>show(responseView,e));$('#favBtn').onclick=()=>action('fav').then(r=>show(responseView,r)).catch(e=>show(responseView,e));$('#unfavBtn').onclick=()=>action('unfav').then(r=>show(responseView,r)).catch(e=>show(responseView,e));
$('#counterBtn').onclick=async()=>{const id=$('#actionEntityId').value.trim();try{show(responseView,await request(`/api/v1/counter/knowpost/${id}?metrics=like,fav`));}catch(e){show(responseView,e)}};

bindForm('#followForm',f=>{const d=formDataObj(f);return request(`/api/v1/relation/follow?toUserId=${d.toUserId}`,{method:'POST'});});
bindForm('#unfollowForm',f=>{const d=formDataObj(f);return request(`/api/v1/relation/unfollow?toUserId=${d.toUserId}`,{method:'POST'});});
bindForm('#statusForm',async f=>{const d=formDataObj(f);const r=await request(`/api/v1/relation/status?toUserId=${d.toUserId}`);show(relationView,r);return r;});
bindForm('#counterUserForm',async f=>{const d=formDataObj(f);const r=await request(`/api/v1/relation/counter?userId=${d.userId}`);show(relationView,r);return r;});
bindForm('#followingForm',async f=>{const d=formDataObj(f);const q=new URLSearchParams({userId:d.userId,limit:d.limit||20,offset:d.offset||0});if(d.cursor)q.set('cursor',d.cursor);const r=await request(`/api/v1/relation/following?${q.toString()}`);show(relationView,r);return r;});
bindForm('#followersForm',async f=>{const d=formDataObj(f);const q=new URLSearchParams({userId:d.userId,limit:d.limit||20,offset:d.offset||0});if(d.cursor)q.set('cursor',d.cursor);const r=await request(`/api/v1/relation/followers?${q.toString()}`);show(relationView,r);return r;});

bindForm('#profilePatchForm',f=>request('/api/v1/profile',{method:'PATCH',body:formDataObj(f)}));
$('#avatarForm').addEventListener('submit',async e=>{e.preventDefault();const fd=new FormData(e.target);try{show(responseView,await request('/api/v1/profile/avatar',{method:'POST',body:fd,raw:true,headers:{}}));}catch(err){show(responseView,err);}});

bindForm('#descForm',f=>request('/api/v1/knowposts/description/suggest',{method:'POST',body:formDataObj(f)}));
$('#ragForm').addEventListener('submit',e=>{e.preventDefault();const d=formDataObj(e.target);const t=$('#accessToken').value.trim();const params=new URLSearchParams({question:d.question,topK:d.topK||5,maxTokens:d.maxTokens||1024});const url=`/api/v1/knowposts/${d.id}/qa/stream?${params.toString()}`;const es=new EventSource(url);ragView.textContent='';es.onmessage=evt=>{ragView.textContent+=evt.data;};es.onerror=()=>{ragView.textContent+='\n[流结束/异常]';es.close();};show(responseView,{hint:'RAG SSE 已启动（注意：EventSource 无法附带 Bearer 头，若接口要求鉴权可改为 fetch stream）',url,hasToken:!!t});});
