<template>
  <div class="orphan-cleanup">
    <div class="bs-setting-row">
      <div class="bs-setting-info">
        <span class="bs-setting-label">{{ cleanupConfig.title }}</span>
        <span class="bs-setting-desc">{{ cleanupConfig.description }}</span>
      </div>
      <div class="bs-setting-control cleanup-controls">
        <button type="button" class="chatnt-clear-submit chatnt-scan-submit"
                :disabled="starting || orphanCleanup.running" @click="startCleanup">
          {{ starting ? '启动中…' : orphanCleanup.running ? orphanCleanup.phase : '扫描并清理' }}
        </button>
      </div>
    </div>
    <div v-if="orphanCleanup.state !== 'idle'" class="chatnt-cleanup-progress">
      <div class="chatnt-cleanup-progress-head">
        <span>{{ orphanCleanup.phase }}</span>
        <span>{{ orphanCleanupProgress }}%</span>
      </div>
      <progress :value="orphanCleanupProgress" max="100" :aria-label="cleanupConfig.title + '清理进度'"></progress>
      <div class="chatnt-cleanup-stats">
        <template v-if="orphanCleanup.state === 'scanning'">
          已扫描 {{ orphanCleanup.scannedTargets }} / {{ orphanCleanup.totalTargets }} {{ cleanupConfig.unit }}
        </template>
        <template v-else>
          发现 {{ orphanCleanup.orphanedTargets }} {{ cleanupConfig.orphanUnit }}，已删除 {{ orphanCleanup.deletedRecords }} 条记录
        </template>
      </div>
      <div v-if="orphanCleanup.error" class="chatnt-cleanup-error" role="alert">{{ orphanCleanup.error }}</div>
    </div>
    <div v-if="requestError" class="chatnt-cleanup-error" role="alert">{{ requestError }}</div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  api: { type: Function, required: true },
  kind: { type: String, default: 'group' }
})
const cleanupConfig = computed(() => props.kind === 'friend' ? {
  title: '已删除好友记录', description: '清除已不在好友登记中的私聊记录并归档',
  endpoint: '/chat/cleanup/orphaned-friends', unit: '位好友', orphanUnit: '位已删除好友',
  totalKey: 'totalUsers', scannedKey: 'scannedUsers', orphanedKey: 'orphanedUsers',
  processedKey: 'processedUsers', deletedIdsKey: 'deletedUserIds',
  confirmation: '确认扫描全部私聊记录，并清理已不在好友登记中的用户聊天记录吗？统计数据会先归档，聊天记录删除后无法恢复。'
} : {
  title: '已退出群记录', description: '清除所有已退群记录并归档',
  endpoint: '/chat/cleanup/orphaned-groups', unit: '个群', orphanUnit: '个无效群',
  totalKey: 'totalGroups', scannedKey: 'scannedGroups', orphanedKey: 'orphanedGroups',
  processedKey: 'processedGroups', deletedIdsKey: 'deletedGroupIds',
  confirmation: '确认扫描全部群聊记录，并清理机器人已退出群的记录吗？统计数据会先归档，聊天记录删除后无法恢复。'
})
const orphanCleanup = reactive({
  state: 'idle', phase: '等待开始', running: false, progress: 0,
  totalTargets: 0, scannedTargets: 0, orphanedTargets: 0,
  orphanedRecords: 0, processedTargets: 0, deletedRecords: 0,
  deletedIds: [], error: null, startedAt: null, finishedAt: null
})
const orphanCleanupProgress = computed(() => Math.max(0, Math.min(100, Number(orphanCleanup.progress) || 0)))
const starting = ref(false)
const requestError = ref('')
let pollTimer = null
let disposed = false
let requestVersion = 0

function applyStatus(data) {
  if (!data) return
  const config = cleanupConfig.value
  Object.assign(orphanCleanup, data, {
    totalTargets: data[config.totalKey] || 0,
    scannedTargets: data[config.scannedKey] || 0,
    orphanedTargets: data[config.orphanedKey] || 0,
    processedTargets: data[config.processedKey] || 0,
    deletedIds: Array.isArray(data[config.deletedIdsKey]) ? data[config.deletedIdsKey] : []
  })
}

function schedulePoll() {
  clearTimeout(pollTimer)
  if (!disposed) pollTimer = setTimeout(loadStatus, 700)
}

async function loadStatus() {
  if (disposed || starting.value) return
  clearTimeout(pollTimer)
  const version = ++requestVersion
  try {
    const data = await props.api(cleanupConfig.value.endpoint)
    if (disposed || version !== requestVersion) return
    applyStatus(data)
    requestError.value = ''
    if (orphanCleanup.running) schedulePoll()
  } catch (error) {
    if (disposed || version !== requestVersion) return
    requestError.value = error.message || '获取清理进度失败'
    if (orphanCleanup.running) schedulePoll()
  }
}

async function startCleanup() {
  if (disposed || starting.value || orphanCleanup.running) return
  if (!confirm(cleanupConfig.value.confirmation)) return
  clearTimeout(pollTimer)
  const version = ++requestVersion
  starting.value = true
  requestError.value = ''
  try {
    const data = await props.api(cleanupConfig.value.endpoint, { method: 'POST' })
    if (disposed || version !== requestVersion) return
    applyStatus(data)
    if (orphanCleanup.running) schedulePoll()
  } catch (error) {
    if (!disposed && version === requestVersion) requestError.value = error.message || '无法启动清理任务'
  } finally {
    if (!disposed && version === requestVersion) starting.value = false
  }
}

onMounted(loadStatus)
onBeforeUnmount(() => {
  disposed = true
  requestVersion++
  clearTimeout(pollTimer)
})
defineExpose({ reload: loadStatus })
</script>

<style scoped>
.cleanup-controls { width: 150px; max-width: 50%; }
.chatnt-scan-submit {
  background: #f1f6fa;
  color: #3e6d8c;
}

.chatnt-scan-submit:hover:not(:disabled) {
  background: #e5f0f7;
}

.chatnt-cleanup-progress {
  margin-top: 9px;
}

.chatnt-cleanup-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  color: #697687;
  font-size: 11px;
}

.chatnt-cleanup-progress progress {
  display: block;
  width: 100%;
  height: 6px;
  border: none;
  border-radius: 3px;
  overflow: hidden;
  background: #e5e9ee;
  accent-color: #4c91bf;
}

.chatnt-cleanup-progress progress::-webkit-progress-bar { background: #e5e9ee; }
.chatnt-cleanup-progress progress::-webkit-progress-value { background: #4c91bf; }
.chatnt-cleanup-progress progress::-moz-progress-bar { background: #4c91bf; }

.chatnt-cleanup-stats,
.chatnt-cleanup-error {
  margin-top: 6px;
  color: #8a95a4;
  font-size: 11px;
  line-height: 1.4;
}

.chatnt-cleanup-error { color: #c64949; }
</style>
