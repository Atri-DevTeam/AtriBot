<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {formatTime} from '../../../shared/lib/time.js'
const props = defineProps({groupOpenId: {type: String, required: true}, request: {type: Function, required: true}})
const items = ref([]), total = ref(0), page = ref(1), category = ref(''), search = ref(''), selected = ref(null), loading = ref(false), error = ref('')
const categories = [{value: '', label: '全部操作'}, {value: 'KEYWORD_RECALL', label: '内容规则'}, {value: 'AI_RECALL', label: 'AI 审查'}, {value: 'JOIN_REVIEW', label: '入群审核'}]
const actions = {recall: '撤回', mute: '禁言', approve: '通过', reject: '拒绝'}
let controller, disposed = false
async function load(reset = false) {
  if (reset) page.value = 1
  selected.value = null
  controller?.abort()
  const current = controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const query = new URLSearchParams({page: page.value, pageSize: 20, category: category.value, keyword: search.value.trim()})
    const result = await props.request(`/group-moderation/${encodeURIComponent(props.groupOpenId)}/logs?${query}`, {signal: current.signal})
    if (disposed || current !== controller) return
    items.value = result.items || []
    total.value = result.total || 0
  } catch (e) { if (!disposed && current === controller && !current.signal.aborted) error.value = e.message }
  finally { if (!disposed && current === controller) loading.value = false }
}
onMounted(() => load())
onBeforeUnmount(() => { disposed = true; controller?.abort() })
</script>
<template>
  <div class="mod-sections">
    <template v-if="selected">
      <button class="mod-button mod-back" type="button" @click="selected = null">← 返回日志</button>
      <section class="mod-card"><header class="mod-card-head"><div><h3>{{ actions[selected.action] || selected.action }}</h3><p>{{ formatTime(selected.createdAt) }}</p></div><span class="mod-tag">{{ categories.find(item => item.value === selected.category)?.label || selected.category }}</span></header>
        <div class="mod-card-body"><label>成员 OpenID<span class="mod-content">{{ selected.targetMemberOpenId || '未记录' }}</span></label><label>处理原因<p class="mod-content">{{ selected.detail || '无详情' }}</p></label><label>原始内容<p class="mod-content mod-original">{{ selected.originalContent == null ? '该记录未保存原始内容' : selected.originalContent || '（内容为空）' }}</p></label></div>
      </section>
    </template>
    <template v-else>
      <form class="mod-log-filters" @submit.prevent="load(true)"><select v-model="category" aria-label="日志分类" @change="load(true)"><option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option></select><div class="mod-row"><input v-model="search" aria-label="搜索操作日志" placeholder="搜索成员、原因或原文"/><button class="mod-button" type="submit">搜索</button></div></form>
      <p v-if="error" class="mod-alert" role="alert">{{ error }} <button class="mod-button" @click="load()">重试</button></p>
      <p v-else-if="loading" class="mod-empty">正在加载日志…</p>
      <p v-else-if="!items.length" class="mod-empty">暂无符合条件的操作日志。</p>
      <div v-else class="mod-log-list"><button v-for="item in items" :key="item.id" class="mod-log-item" @click="selected = item"><span class="mod-row"><strong>{{ actions[item.action] || item.action }}</strong><time>{{ formatTime(item.createdAt) }}</time></span><span class="mod-log-reason">{{ item.detail || '无详情' }}</span><small>{{ item.targetMemberOpenId || '未记录成员' }}</small></button></div>
      <div class="mod-row mod-pagination"><button class="mod-button" :disabled="loading || page <= 1" @click="page--; load()">上一页</button><small>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }} · {{ total }} 条</small><button class="mod-button" :disabled="loading || page * 20 >= total" @click="page++; load()">下一页</button></div>
    </template>
  </div>
</template>
