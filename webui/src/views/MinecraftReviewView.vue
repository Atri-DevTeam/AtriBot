<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar><button class="ghost-button" :disabled="loading" @click="load">刷新</button><button class="ghost-button" @click="logout">退出</button></template>
    </AppSidebar>
    <div class="sidebar-spacer" />
    <main class="workspace">
      <header class="topbar"><div class="topbar-left"><button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen=true"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M3 12h18M3 18h18"/></svg></button><h2>Minecraft 内容审核</h2></div></header>
      <section class="content mc-review-layout">
        <div class="mc-review-page">
          <section class="errors-surface mc-submit-card">
            <div><h3>录入正版玩家</h3><p>将玩家信息录入审核列表</p></div>
            <form @submit.prevent="submitPlayer"><input v-model.trim="player" placeholder="正版名字、UUID" autocomplete="off"/><button class="primary-button" :disabled="submitting || !player">{{ submitting ? '解析并录入中...' : '录入审核' }}</button></form>
            <p v-if="submitMessage" class="mc-submit-message" :class="{error:submitError}">{{ submitMessage }}</p>
          </section>

          <section class="errors-surface mc-review-card">
            <header class="mc-review-head">
              <div class="mc-type-tabs"><button :class="{active:type==='names'}" @click="switchType('names')">名字审核</button><button :class="{active:type==='skins'}" @click="switchType('skins')">皮肤审核</button></div>
              <div class="mc-status-tabs"><button v-for="entry in statuses" :key="entry.value" :class="{active:status===entry.value}" @click="status=entry.value;page=1;load()">{{ entry.label }}</button></div>
            </header>

            <div v-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="error" class="empty-state error">{{ error }}</div>
            <div v-else-if="!items.length" class="empty-state">当前没有{{ type==='names'?'名字':'皮肤' }}记录</div>
            <div v-else-if="type==='names'" class="mc-name-list">
              <article v-for="item in items" :key="item.id" class="mc-name-row">
                <div class="mc-item-main"><div><strong>{{ item.name || item.originalName || '-' }}</strong><span class="mc-state" :class="stateClass(item.status)">{{ stateText(item.status) }}</span></div><small>ID {{ item.id }}<template v-if="item.reason"> · {{ item.reason }}</template></small></div>
                <div class="mc-actions"><button class="ghost-button approve" :disabled="busy===item.id" @click="openReview(item,'APPROVED')">通过</button><button class="ghost-button reject" :disabled="busy===item.id" @click="openReview(item,'BLACKLISTED')">不通过</button></div>
              </article>
            </div>
            <div v-else class="mc-skin-grid">
              <article v-for="item in items" :key="item.id" class="mc-skin-card">
                <div class="mc-skin-preview"><img :src="previewUrl(item,'SKIN3D')" loading="lazy" alt="皮肤 3D 预览" title="点击放大" @click="openPreview(item,'SKIN3D')"/><img class="avatar" :src="previewUrl(item,'AVATAR')" loading="lazy" alt="头像预览" title="点击放大" @click.stop="openPreview(item,'AVATAR')"/></div>
                <div class="mc-skin-info"><div><span class="mc-state" :class="stateClass(item.status)">{{ stateText(item.status) }}</span><strong>{{ shortSkin(item.skinId) }}</strong></div><small :title="item.skinId">{{ item.skinId }}</small><small v-if="item.sampleUuid">来源 UUID：{{ item.sampleUuid }}</small><small v-if="item.reason">理由：{{ item.reason }}</small></div>
                <div class="mc-actions"><button class="ghost-button approve" :disabled="busy===item.id" @click="openReview(item,'APPROVED')">通过</button><button class="ghost-button reject" :disabled="busy===item.id" @click="openReview(item,'BLACKLISTED')">不通过</button></div>
              </article>
            </div>

            <footer class="mc-pagination"><span>共 {{ total }} 条</span><button class="ghost-button" :disabled="page<=1||loading" @click="page--;load()">上一页</button><b>{{ page }}</b><button class="ghost-button" :disabled="page*size>=total||loading" @click="page++;load()">下一页</button></footer>
          </section>
        </div>
      </section>
    </main>

    <Teleport to="body"><div v-if="reviewDialog" class="mc-dialog-backdrop" @mousedown.self="reviewDialog=null"><section class="mc-dialog"><header><div><h3>{{ reviewDialog.status==='APPROVED'?'通过审核':'设为不通过' }}</h3><p>{{ type==='names' ? (reviewDialog.item.name || reviewDialog.item.originalName) : shortSkin(reviewDialog.item.skinId) }}</p></div><button @click="reviewDialog=null">×</button></header><p v-if="reviewDialog.status==='APPROVED'" class="mc-approve-confirm">确认将该内容设为已通过？</p><label v-else><span>不通过原因</span><textarea v-model.trim="reason" rows="4" placeholder="请输入不通过原因"/></label><p v-if="dialogError" class="mc-dialog-error">{{ dialogError }}</p><footer><button class="ghost-button" @click="reviewDialog=null">取消</button><button class="primary-button" :disabled="busy!==''" @click="review">确认</button></footer></section></div></Teleport>
    <Teleport to="body"><div v-if="largePreview" class="mc-preview-backdrop" @mousedown.self="largePreview=null"><button class="mc-preview-close" aria-label="关闭预览" @click="largePreview=null">×</button><img :src="largePreview.url" :alt="largePreview.alt"/></div></Teleport>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'

const router=useRouter(), sidebarOpen=ref(false), botName=ref('AtriBot'), appId=ref(''), botOpenId=ref('')
const type=ref('names'), status=ref('PENDING'), page=ref(1), size=20, total=ref(0), items=ref([]), loading=ref(false), error=ref('')
const player=ref(''), submitting=ref(false), submitMessage=ref(''), submitError=ref(false), busy=ref(''), reviewDialog=ref(null), reason=ref(''), dialogError=ref(''), largePreview=ref(null)
const statuses=[{value:'PENDING',label:'待审核'},{value:'APPROVED',label:'已通过'},{value:'BLACKLISTED',label:'未通过'},{value:'ALL',label:'全部'}]

async function api(path,options={}){const res=await fetch(`${API_BASE}${path}`,{credentials:'same-origin',headers:{'Content-Type':'application/json',...(options.headers||{})},...options});let payload;try{payload=await res.json()}catch{throw new Error(`HTTP ${res.status}`)}if(res.status===401){router.replace('/login');throw new Error('未授权')}if(payload.status!==200)throw new Error(payload.message||'请求失败');return payload.data}
async function load(){loading.value=true;error.value='';try{const q=new URLSearchParams({status:status.value,page:String(page.value),size:String(size)});const data=await api(`/minecraft-moderation/${type.value}?${q}`);items.value=data?.items||[];total.value=Number(data?.total)||0}catch(e){error.value=e.message}finally{loading.value=false}}
function switchType(value){type.value=value;page.value=1;load()}
async function submitPlayer(){submitting.value=true;submitMessage.value='';submitError.value=false;try{const data=await api('/minecraft-moderation/players',{method:'POST',body:JSON.stringify({player:player.value})});submitMessage.value=`已录入 ${data.profile.name}，名字和当前皮肤可分别审核`;player.value='';status.value='PENDING';page.value=1;await load()}catch(e){submitError.value=true;submitMessage.value=e.message}finally{submitting.value=false}}
function openReview(item,nextStatus){reviewDialog.value={item,status:nextStatus};reason.value='';dialogError.value=''}
async function review(){const d=reviewDialog.value;busy.value=d.item.id;dialogError.value='';try{await api(`/minecraft-moderation/${type.value}/${d.item.id}`,{method:'PUT',body:JSON.stringify({status:d.status,reason:d.status==='APPROVED'?'':reason.value,reviewer:'webui'})});reviewDialog.value=null;await load()}catch(e){dialogError.value=e.message}finally{busy.value=''}}
function previewUrl(item,previewType){return `${API_BASE}/minecraft-moderation/skins/${encodeURIComponent(item.skinId)}/preview/${previewType}`}
function openPreview(item,previewType){largePreview.value={url:previewUrl(item,previewType),alt:previewType==='AVATAR'?'头像大图':'皮肤 3D 大图'}}
function shortSkin(id){return id ? `皮肤 ${id.slice(0,8)}` : '未知皮肤'}
function stateClass(value){return String(value||'').toLowerCase()}
function stateText(value){return value==='APPROVED'?'已通过':value==='BLACKLISTED'?'未通过':'待审核'}
function logout(){fetch(`${API_BASE}/auth/logout`,{method:'POST',credentials:'same-origin'}).finally(()=>router.replace('/login'))}
onMounted(async()=>{try{const c=await api('/config');botName.value=c.botName||'AtriBot';appId.value=c.appId||'';botOpenId.value=c.botOpenId||''}catch{}await load()})
</script>

<style scoped>
.mc-review-layout{display:block;width:100%;overflow:auto;background:var(--color-bg)}.mc-review-page{width:min(1040px,calc(100% - 40px));margin:0 auto;padding:22px 0 38px;display:flex;flex-direction:column;gap:16px}.mc-submit-card,.mc-review-card{padding:18px}.mc-submit-card{display:grid;grid-template-columns:1fr auto;align-items:center;gap:14px}.mc-submit-card h3{margin:0;font-size:var(--text-lg)}.mc-submit-card p{margin:4px 0 0;color:var(--color-text-muted);font-size:var(--text-xs)}.mc-submit-card form{display:flex;gap:8px}.mc-submit-card input{box-sizing:border-box;width:300px;height:34px;padding:0 10px;border:1px solid var(--color-border-input);border-radius:var(--radius-md);background:var(--color-surface);color:var(--color-text);outline:none}.mc-submit-message{grid-column:1/-1!important;color:var(--color-success)!important}.mc-submit-message.error{color:var(--color-danger)!important}.mc-review-head{display:flex;align-items:center;justify-content:space-between;gap:15px;padding-bottom:14px;border-bottom:1px solid var(--color-hairline)}.mc-type-tabs,.mc-status-tabs{display:flex;gap:5px}.mc-type-tabs button,.mc-status-tabs button{height:30px;padding:0 10px;border:1px solid transparent;border-radius:var(--radius-md);background:transparent;color:var(--color-text-muted);cursor:pointer}.mc-type-tabs button{font-weight:600;font-size:var(--text-sm)}.mc-type-tabs button.active,.mc-status-tabs button.active{border-color:var(--color-border);background:var(--color-surface-sunken);color:var(--color-text-strong)}.mc-name-row{display:flex;align-items:center;justify-content:space-between;gap:15px;padding:14px 2px;border-bottom:1px solid var(--color-hairline)}.mc-item-main>div,.mc-skin-info>div{display:flex;align-items:center;gap:8px}.mc-item-main small,.mc-skin-info small{display:block;margin-top:4px;color:var(--color-text-muted);font-size:var(--text-xs)}.mc-state{display:inline-flex;padding:2px 7px;border-radius:10px;font-size:10px;font-weight:600}.mc-state.pending{background:var(--color-warning-soft);color:var(--color-warning-strong)}.mc-state.approved{background:var(--color-success-soft);color:var(--color-success-strong)}.mc-state.blacklisted{background:var(--color-danger-soft);color:var(--color-danger)}.mc-actions{display:flex;gap:7px}.mc-actions button{min-height:30px;height:30px;font-size:var(--text-xs)}.mc-actions .approve{color:var(--color-success-strong)}.mc-actions .reject{color:var(--color-danger)}.mc-skin-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;padding-top:14px}.mc-skin-card{display:grid;grid-template-columns:120px 1fr;gap:12px;padding:12px;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:var(--color-surface-alt)}.mc-skin-preview{position:relative;height:150px;border-radius:var(--radius-md);overflow:hidden;background:var(--color-surface-sunken)}.mc-skin-preview>img:first-child{width:100%;height:100%;object-fit:contain}.mc-skin-preview .avatar{position:absolute;right:7px;bottom:7px;width:36px;height:36px;border:2px solid var(--color-surface);border-radius:var(--radius-sm);image-rendering:pixelated}.mc-skin-info{min-width:0}.mc-skin-info>small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mc-skin-card .mc-actions{grid-column:1/-1;justify-content:flex-end}.mc-pagination{display:flex;align-items:center;justify-content:flex-end;gap:8px;padding-top:14px}.mc-pagination span{margin-right:auto;color:var(--color-text-muted);font-size:var(--text-xs)}.mc-pagination button{height:30px;min-height:30px}.mc-dialog-backdrop{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:20px;background:rgba(15,23,42,.42)}.mc-dialog{width:min(440px,100%);padding:18px;border:1px solid var(--color-border);border-radius:var(--radius-lg);background:var(--color-surface);box-shadow:var(--shadow-lg)}.mc-dialog header,.mc-dialog footer{display:flex;align-items:center}.mc-dialog header{justify-content:space-between;padding-bottom:12px}.mc-dialog h3{margin:0;font-size:var(--text-lg)}.mc-dialog header p{margin:3px 0 0;color:var(--color-text-muted);font-size:var(--text-xs)}.mc-dialog header button{border:0;background:transparent;color:var(--color-text-muted);font-size:24px}.mc-dialog label{display:flex;flex-direction:column;gap:6px}.mc-dialog label span{font-size:var(--text-xs);color:var(--color-text-muted)}.mc-dialog textarea{padding:9px;border:1px solid var(--color-border-input);border-radius:var(--radius-md);background:var(--color-surface);color:var(--color-text);resize:vertical}.mc-dialog footer{justify-content:flex-end;gap:8px;padding-top:13px}.mc-dialog-error{color:var(--color-danger);font-size:var(--text-sm)}
@media(max-width:760px){.mc-review-page{width:calc(100% - 24px)}.mc-submit-card{grid-template-columns:1fr}.mc-submit-card form{flex-direction:column}.mc-submit-card input{width:100%}.mc-review-head{align-items:flex-start;flex-direction:column}.mc-skin-grid{grid-template-columns:1fr}.mc-name-row{align-items:flex-start;flex-direction:column}.mc-actions{align-self:flex-end}}
.mc-approve-confirm{margin:8px 0 4px;color:var(--color-text);font-size:var(--text-sm)}.mc-skin-preview img{cursor:zoom-in}.mc-preview-backdrop{position:fixed;inset:0;z-index:1100;display:grid;place-items:center;padding:48px;background:rgba(8,12,20,.82);backdrop-filter:blur(4px)}.mc-preview-backdrop>img{display:block;max-width:min(860px,92vw);max-height:86vh;object-fit:contain;image-rendering:auto;border-radius:var(--radius-lg);box-shadow:var(--shadow-lg)}.mc-preview-close{position:fixed;top:18px;right:24px;width:38px;height:38px;border:1px solid rgba(255,255,255,.25);border-radius:50%;background:rgba(0,0,0,.28);color:#fff;font-size:27px;line-height:1;cursor:pointer}
</style>
