<template>
  <div class="function-settings-panel">
    <div class="function-settings-summary">
      <div class="function-summary-item is-primary">
        <span class="function-settings-value">{{ activeCount }}</span>
        <span class="function-settings-label">正在禁用</span>
      </div>
      <div class="function-summary-item">
        <span class="function-settings-value">{{ configuredCount }}</span>
        <span class="function-settings-label">已设置规则</span>
      </div>
      <div class="function-summary-item">
        <span class="function-settings-value">{{ items.length }}</span>
        <span class="function-settings-label">推送任务</span>
      </div>
    </div>

    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="error" class="empty-state error">{{ error }}</div>
    <div v-else-if="items.length === 0" class="empty-state">暂无推送任务</div>

    <div v-else class="function-settings-list">
      <article v-for="item in items" :key="item.functionId" class="function-setting-card" :class="stateClass(item)">
        <header class="function-setting-card-head">
          <div class="function-setting-main">
          <div class="function-setting-title-row">
            <span class="function-setting-title">{{ item.functionName }}</span>
            <span class="function-setting-state" :class="stateClass(item)">
              {{ stateText(item) }}
            </span>
          </div>
          <code class="function-setting-id">{{ item.functionId }}</code>
          <div class="function-setting-support">
            <span :class="{ muted: !item.groupEnable }">{{ item.groupEnable ? '支持群聊' : '不支持群聊' }}</span>
            <span :class="{ muted: !item.c2cEnable }">{{ item.c2cEnable ? '支持私聊' : '不支持私聊' }}</span>
          </div>
          </div>

          <label class="function-setting-switch-card" :class="{ enabled: drafts[item.functionId].enabled }">
            <span>
              <strong>禁用规则</strong>
              <small>{{ drafts[item.functionId].enabled ? '已启用' : '未启用' }}</small>
            </span>
            <input v-model="drafts[item.functionId].enabled" type="checkbox" />
            <i aria-hidden="true"></i>
          </label>
        </header>

        <div class="function-setting-controls">
          <label class="function-setting-field">
            <span>禁用场景</span>
            <select v-model="drafts[item.functionId].scope" class="function-setting-input">
              <option v-for="opt in scopeOptions(item)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </label>

          <label class="function-setting-field">
            <span>开始时间</span>
            <input v-model="drafts[item.functionId].startTime" class="function-setting-input" type="datetime-local" />
          </label>

          <label class="function-setting-field">
            <span>结束时间</span>
            <input v-model="drafts[item.functionId].endTime" class="function-setting-input" type="datetime-local" />
          </label>

        </div>

        <footer class="function-setting-actions">
          <span class="function-setting-time-hint">时间留空表示不限制</span>
            <button class="primary-button function-setting-btn"
                    :disabled="saving[item.functionId]"
                    @click="saveSetting(item)">
              {{ saving[item.functionId] ? '保存中...' : '保存' }}
            </button>
            <button class="ghost-button function-setting-btn"
                    :disabled="saving[item.functionId] || !item.enabled"
                    @click="clearSetting(item)">
              清除
            </button>
        </footer>
      </article>
    </div>
  </div>
</template>

<script setup>
import {computed, reactive, ref} from 'vue'

const props = defineProps({
  api: {type: Function, required: true}
})

const loading = ref(false)
const error = ref('')
const items = ref([])
const drafts = reactive({})
const saving = reactive({})

const activeCount = computed(() => items.value.filter(item => item.activeNow).length)
const configuredCount = computed(() => items.value.filter(item => item.enabled).length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await props.api('/function-settings')
    items.value = Array.isArray(data) ? data : []
    for (const item of items.value) {
      drafts[item.functionId] = {
        enabled: !!item.enabled,
        scope: normalizeScopeForItem(item, item.scope),
        startTime: toDatetimeInput(item.startTime),
        endTime: toDatetimeInput(item.endTime)
      }
    }
  } catch (e) {
    error.value = e.message
    items.value = []
  } finally {
    loading.value = false
  }
}

function scopeOptions(item) {
  const options = []
  if (item.groupEnable && item.c2cEnable) options.push({value: 'BOTH', label: '群聊 + 私聊'})
  if (item.groupEnable) options.push({value: 'GROUP', label: '仅群聊'})
  if (item.c2cEnable) options.push({value: 'C2C', label: '仅私聊'})
  return options
}

function normalizeScopeForItem(item, scope) {
  const options = scopeOptions(item).map(opt => opt.value)
  if (options.includes(scope)) return scope
  return options[0] || 'BOTH'
}

async function saveSetting(item) {
  const draft = drafts[item.functionId]
  if (!draft) return
  saving[item.functionId] = true
  error.value = ''
  try {
    const saved = await props.api(`/function-settings/${encodeURIComponent(item.functionId)}`, {
      method: 'POST',
      body: JSON.stringify({
        functionName: item.functionName,
        scope: draft.scope,
        startTime: fromDatetimeInput(draft.startTime),
        endTime: fromDatetimeInput(draft.endTime),
        enabled: !!draft.enabled
      })
    })
    replaceItem(saved)
  } catch (e) {
    error.value = e.message
  } finally {
    saving[item.functionId] = false
  }
}

async function clearSetting(item) {
  saving[item.functionId] = true
  error.value = ''
  try {
    await props.api(`/function-settings/${encodeURIComponent(item.functionId)}`, {method: 'DELETE'})
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving[item.functionId] = false
  }
}

function replaceItem(saved) {
  const idx = items.value.findIndex(item => item.functionId === saved.functionId)
  if (idx >= 0) {
    items.value[idx] = saved
  }
  drafts[saved.functionId] = {
    enabled: !!saved.enabled,
    scope: normalizeScopeForItem(saved, saved.scope),
    startTime: toDatetimeInput(saved.startTime),
    endTime: toDatetimeInput(saved.endTime)
  }
}

function stateText(item) {
  if (item.activeNow) return '禁用中'
  if (item.enabled) return '已配置'
  return '未配置'
}

function stateClass(item) {
  return {
    'is-active': item.activeNow,
    'is-configured': item.enabled && !item.activeNow,
    'is-idle': !item.enabled
  }
}

function toDatetimeInput(value) {
  if (!value) return ''
  return String(value).trim().replace(' ', 'T').slice(0, 16)
}

function fromDatetimeInput(value) {
  if (!value) return null
  return String(value).trim().replace('T', ' ') + ':00'
}

defineExpose({load})
</script>
