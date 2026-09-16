<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppSidebar from '../components/AppSidebar.vue'
import ChannelFeedBody from '../components/ChannelFeedBody.vue'
import ChannelUserAvatar from '../components/ChannelUserAvatar.vue'
import ChannelReplyComposer from '../components/ChannelReplyComposer.vue'
import { channelUserId, channelUserName, createReplyThread, mergeReplies, replyCount, replyCursor, replyKey, replyRows } from '../lib/channelComments.js'
import { API_BASE } from '../router.js'
import '../styles/channels.css'

const router = useRouter()
const sidebarOpen = ref(false)
const config = ref({})
const accountName = ref('读取中…')
const accountError = ref('')
const guilds = ref([])
const failedGuildAvatars = ref(new Set())
const guild = ref(null)
const info = ref(null)
const boards = ref([])
const boardId = ref('')
const guildFilter = ref('')
const view = ref('feeds')
const keyword = ref('')
const search = ref('')
const items = ref([])
const cursor = ref('')
const hasMore = ref(false)
const loadingGuilds = ref(false)
const loadingInfo = ref(false)
const infoError = ref('')
const loading = ref(false)
const error = ref('')
const guildError = ref('')
const notice = ref('')
const busy = ref(false)
const selected = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const comments = ref([])
const replyThreads = reactive(new Map())
const replyQueueError = ref('')
const commentCursor = ref('')
const commentsMore = ref(false)
const commentsLoading = ref(false)
const commentError = ref('')
const commentText = ref('')
const replyDraft = ref(null)
const replySendError = ref('')
const moveTo = ref('')
const composer = ref(false)
const draft = reactive({ title: '', content: '', markdown: false, channel_id: '', edit: false })
const composeError = ref('')
const preview = computed(() => ({ title: draft.title, content: draft.content, is_markdown: draft.markdown,
  images: draft.edit ? detail.value?.images || [] : [] }))
const visibleGuilds = computed(() => guilds.value.filter(item => `${item.name} ${item.guild_id}`.toLowerCase().includes(guildFilter.value.toLowerCase())))
const navigationBoards = computed(() => boards.value.filter(item => item.channel_name?.trim() !== '全部'))
const boardName = computed(() => boards.value.find(item => item.channel_id === boardId.value)?.channel_name || '全部动态')
const currentInfo = computed(() => info.value || guild.value || {})
const requests = new Map()
let generation = 0
let listVersion = 0
let detailVersion = 0
let guildListVersion = 0
let replyQueueVersion = 0
let replyQueueTask = null
let disposed = false

function safeUrl(url) { return /^https?:\/\//i.test(url || '') ? url : '' }
function guildAvatar(item) {
  const url = safeUrl(item.avatar_url)
  return failedGuildAvatars.value.has(url) ? '' : url
}
function textTime(item) { return item.create_time || item.create_time_human || '' }
function author(item) { return channelUserName(item) }
function unique(list, key) { return [...new Map(list.filter(item => item?.[key]).map(item => [String(item[key]), item])).values()] }

async function api(operation, params = {}, write = false, scope = operation) {
  if (requests.has(scope)) requests.get(scope).abort()
  const controller = new AbortController()
  requests.set(scope, controller)
  try {
    const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))
    const response = await fetch(`${API_BASE}/channels/${write ? 'action' : 'query'}/${operation}${write ? '' : `?${query}`}`, {
      method: write ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
      headers: write ? { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' } : {},
      ...(write ? { body: JSON.stringify({ ...params, confirmed: true }) } : {})
    })
    if (response.status === 401) {
      router.replace('/login')
      throw new Error('WebUI 登录已失效')
    }
    let result
    try { result = await response.json() } catch { throw new Error(`请求失败（HTTP ${response.status}）`) }
    if (!response.ok || result.status !== 200) {
      const failure = new Error(result.message || 'EMA 请求失败')
      failure.status = response.status
      throw failure
    }
    return result.data || {}
  } finally {
    if (requests.get(scope) === controller) requests.delete(scope)
  }
}

function cancelReads() {
  for (const [key, controller] of requests) {
    if (key !== 'mutation' && key !== 'guild-avatar') controller.abort()
  }
}

async function loadAccount() {
  accountName.value = '读取中…'
  accountError.value = ''
  try {
    const data = await api('account')
    accountName.value = [data.global_nickname, data.nickname, data.member_name]
      .find(value => typeof value === 'string' && value.trim()) || '未返回名称'
  } catch (e) {
    if (e.name !== 'AbortError') {
      accountName.value = '获取失败'
      accountError.value = e.message
    }
  }
}

async function loadGuilds() {
  if (loadingGuilds.value || busy.value) return
  loadingGuilds.value = true
  guildError.value = ''
  const version = ++guildListVersion
  requests.get('guild-avatar')?.abort()
  failedGuildAvatars.value.clear()
  try {
    await loadAccount()
    if (version !== guildListVersion) return
    const data = await api('guilds')
    const groups = ['joined_guilds', 'managed_guilds', 'created_guilds'].flatMap(key => data[key] || [])
    guilds.value = unique(groups, 'guild_id')
    const next = guilds.value.find(item => item.guild_id === guild.value?.guild_id) || guilds.value[0]
    if (next) await selectGuild(next)
    else { resetGuild(); guild.value = null }
    if (version === guildListVersion) void loadGuildAvatars(version)
  } catch (e) { if (e.name !== 'AbortError') guildError.value = e.message }
  finally { loadingGuilds.value = false }
}

async function loadGuildAvatars(version) {
  // 列表接口不含头像，后台串行补取资料；只保存在当前页面内存中。
  for (const item of guilds.value) {
    if (version !== guildListVersion) return
    if (safeUrl(item.avatar_url)) continue
    try {
      const profile = await api('info', { guild_id: item.guild_id }, false, 'guild-avatar')
      if (version !== guildListVersion) return
      item.avatar_url = safeUrl(profile.avatar_url)
    } catch {
      // 登录失效、限流或离开页面后不继续排队，刷新频道时再尝试。
      return
    }
  }
}

function resetGuild() {
  generation++
  listVersion++
  cancelReads()
  closeDetail()
  info.value = null
  infoError.value = ''
  boards.value = []
  boardId.value = ''
  items.value = []
  cursor.value = ''
  hasMore.value = false
  keyword.value = ''
  search.value = ''
  notice.value = ''
  error.value = ''
  loading.value = false
  composer.value = false
  Object.assign(draft, { title: '', content: '', markdown: false, channel_id: '', edit: false })
}

async function selectGuild(item) {
  if (busy.value) return
  resetGuild()
  guild.value = item
  view.value = 'feeds'
  loadingInfo.value = true
  const version = generation
  const params = { guild_id: item.guild_id }
  // 顺序读取，避免与 CLI 的全局串行队列相互拥塞。
  try {
    const data = await api('boards', params)
    if (version !== generation) return
    boards.value = data.channels || []
    const profile = await api('info', params)
    if (version !== generation) return
    info.value = profile
    item.avatar_url = safeUrl(profile.avatar_url)
  } catch (e) { if (version === generation && e.name !== 'AbortError') infoError.value = e.message }
  finally { if (version === generation) loadingInfo.value = false }
  if (version === generation) await loadItems()
}

async function loadItems(append = false) {
  if (!guild.value || busy.value || (append && (loading.value || !hasMore.value))) return
  const version = ++listVersion
  const gen = generation
  const previous = append ? cursor.value : ''
  loading.value = true
  error.value = ''
  if (!append) { items.value = []; cursor.value = ''; hasMore.value = false }
  const operation = view.value === 'members' ? 'members' : search.value ? 'search' : 'feeds'
  const params = { guild_id: guild.value.guild_id }
  if (operation === 'members') params.next_page_token = previous
  else if (operation === 'search') { params.query = search.value; params.next_page_cookie = previous }
  else { params.channel_id = boardId.value; params.feed_attach_info = previous }
  try {
    const data = await api(operation, params, false, 'list')
    if (version !== listVersion || gen !== generation) return
    const rows = operation === 'members'
      ? ['owners', 'admins', 'robots', 'ai_members', 'members'].flatMap(key => data[key] || [])
      : data.feeds || []
    if (!Array.isArray(rows)) throw new Error('EMA 列表结构无法识别，请检查 CLI 版本')
    items.value = unique(append ? [...items.value, ...rows] : rows, operation === 'members' ? 'tinyid' : 'feed_id')
    const next = operation === 'members' ? data.next_page_token : operation === 'search' ? data.next_page_cookie : data.feed_attach_info
    cursor.value = next || ''
    hasMore.value = data.has_more !== false && rows.length > 0 && Boolean(next) && next !== previous
  } catch (e) { if (version === listVersion && gen === generation && e.name !== 'AbortError') error.value = e.message }
  finally { if (version === listVersion && gen === generation) loading.value = false }
}

function selectBoard(id) {
  if (busy.value) return
  boardId.value = id
  view.value = 'feeds'
  search.value = ''
  keyword.value = ''
  closeDetail()
  loadItems()
}
function selectView(value) {
  if (busy.value) return
  view.value = value
  closeDetail()
  loadItems()
}
function searchFeeds() {
  if (busy.value) return
  search.value = keyword.value.trim()
  view.value = 'feeds'
  closeDetail()
  loadItems()
}

function closeDetail() {
  if (busy.value) return
  detailVersion++
  requests.get('detail')?.abort()
  requests.get('comments')?.abort()
  cancelReplyReads()
  selected.value = null
}
function clearDetailContent() {
  detail.value = null
  detailLoading.value = false
  commentsLoading.value = false
  comments.value = []
  replyThreads.clear()
  replyQueueError.value = ''
  commentText.value = ''
  replyDraft.value = null
  replySendError.value = ''
  detailError.value = ''
  commentError.value = ''
}
async function openDetail(item) {
  if (busy.value) return
  closeDetail()
  clearDetailContent()
  selected.value = item
  const version = detailVersion
  detailLoading.value = true
  try {
    const data = await api('detail', { guild_id: guild.value.guild_id, feed_id: item.feed_id, channel_id: item.channel_id || boardId.value }, false, 'detail')
    if (version !== detailVersion) return
    if (!data.feed?.feed_id) throw new Error('EMA 未返回帖子详情，该帖子可能已删除或无权访问')
    detail.value = data.feed
    moveTo.value = data.feed.channel_id || ''
    await loadComments()
  } catch (e) { if (version === detailVersion && e.name !== 'AbortError') detailError.value = e.message }
  finally { if (version === detailVersion) detailLoading.value = false }
}
function target() {
  const post = detail.value
  return {
    guild_id: post?.guild_id || guild.value?.guild_id,
    channel_id: post?.channel_id,
    feed_id: post?.feed_id,
    create_time: String(post?.create_time_raw || ''),
    feed_create_time: String(post?.create_time_raw || ''),
    user_id: post?.author_id
  }
}
async function loadComments(append = false) {
  if (!detail.value || commentsLoading.value) return
  const version = detailVersion
  const previous = append ? commentCursor.value : ''
  commentsLoading.value = true
  commentError.value = ''
  if (!append) {
    cancelReplyReads()
    replyThreads.clear()
    replyQueueError.value = ''
    comments.value = []; commentsMore.value = false; commentCursor.value = ''
  }
  try {
    const data = await api('comments', { ...target(), attach_info: previous }, false, 'comments')
    if (version !== detailVersion) return
    const rows = data.comments || []
    comments.value = unique(append ? [...comments.value, ...rows] : rows, 'comment_id')
    for (const comment of rows) {
      if (!replyThreads.has(comment.comment_id)) replyThreads.set(comment.comment_id, createReplyThread(comment))
    }
    startReplyQueue()
    commentCursor.value = data.attach_info || ''
    commentsMore.value = data.has_more !== false && rows.length > 0 && Boolean(data.attach_info) && data.attach_info !== previous
  } catch (e) { if (version === detailVersion && e.name !== 'AbortError') commentError.value = e.message }
  finally { if (version === detailVersion) commentsLoading.value = false }
}

function cancelReplyReads() {
  replyQueueVersion++
  for (const [key, controller] of requests) if (key.startsWith('replies:')) controller.abort()
}

function threadFor(comment) {
  if (!replyThreads.has(comment.comment_id)) replyThreads.set(comment.comment_id, createReplyThread(comment))
  return replyThreads.get(comment.comment_id)
}

function replyLabel(comment) {
  const count = Math.max(replyCount(comment) || 0, threadFor(comment).items.length)
  return count > 0 ? `展开 ${count} 条回复` : '查看回复'
}

function toggleReplies(comment) {
  const thread = threadFor(comment)
  thread.expanded = !thread.expanded
}

function startReplyQueue() {
  if (disposed || replyQueueTask || busy.value || !selected.value || !detail.value || replyQueueError.value) return
  const version = replyQueueVersion
  const job = {}
  replyQueueTask = job
  void (async () => {
    try {
      while (version === replyQueueVersion && selected.value && detail.value && !busy.value && !replyQueueError.value) {
        const comment = comments.value.find(item => threadFor(item).more && !threadFor(item).error)
        if (!comment) break
        await loadReplies(comment)
        if (version !== replyQueueVersion) break
        if (threadFor(comment).error) replyQueueError.value = threadFor(comment).error
      }
    } finally {
      if (replyQueueTask === job) {
        replyQueueTask = null
        if (version !== replyQueueVersion) startReplyQueue()
      }
    }
  })()
}

function resumeReplyQueue() {
  replyQueueError.value = ''
  for (const thread of replyThreads.values()) thread.error = ''
  startReplyQueue()
}

watch(busy, value => { if (!value) startReplyQueue() })

async function loadReplies(comment) {
  if (!detail.value || busy.value) return
  const thread = threadFor(comment)
  if (thread.loading || !thread.more) return
  const version = detailVersion
  const previous = replyCursor({ attach_info: thread.cursor })
  if (!previous) {
    thread.more = false
    return
  }
  thread.loading = true
  thread.error = ''
  try {
    const data = await api('replies', { ...target(), comment_id: comment.comment_id, attach_info: previous }, false, `replies:${comment.comment_id}`)
    if (version !== detailVersion || replyThreads.get(comment.comment_id) !== thread) return
    const rows = replyRows(data)
    const next = replyCursor(data)
    thread.cursors.add(previous)
    thread.items = mergeReplies(thread.items, rows)
    thread.requested = true
    thread.cursor = next
    const more = data.has_more ?? data.hasMore
    thread.more = more !== false && more !== 0 && more !== 'false' && rows.length > 0 && Boolean(next) && !thread.cursors.has(next)
  } catch (e) {
    if (version === detailVersion && replyThreads.get(comment.comment_id) === thread && e.name !== 'AbortError') thread.error = e.message
  } finally {
    if (version === detailVersion && replyThreads.get(comment.comment_id) === thread) thread.loading = false
  }
}

async function mutate(operation, params, question) {
  if (busy.value || (question && !window.confirm(question))) return false
  busy.value = true
  notice.value = ''
  try {
    await api(operation, params, true, 'mutation')
    notice.value = '操作成功'
    return true
  } catch (e) {
    notice.value = e.status === 400
      ? `操作未提交：${e.message}`
      : `操作未确认成功：${e.message}。请先刷新查看实际结果，勿连续重复提交。`
    return false
  } finally { busy.value = false }
}
async function postAction(operation) {
  if (!detail.value) return
  const labels = { like: '点赞', unlike: '取消点赞', delete: '删除帖子（不可撤销）', pin: '置顶', unpin: '取消置顶', essence: '设为精华', unessence: '取消精华', move: '移动帖子到所选板块' }
  const post = { ...detail.value }
  const params = target()
  if (operation === 'move') { params.original_channel_id = params.channel_id; params.channel_id = moveTo.value }
  if (!await mutate(operation, params, `确定要${labels[operation]}吗？`)) return
  if (operation === 'delete') closeDetail()
  else await openDetail({ ...post, channel_id: operation === 'move' ? moveTo.value : post.channel_id })
  await loadItems()
}
async function sendComment() {
  if (!commentText.value.trim() || !detail.value) return
  if (await mutate('comment', { ...target(), content: commentText.value }, '确定向这条帖子发表评论吗？')) {
    commentText.value = ''
    await loadComments()
  }
}
function openReply(comment, reply = null) {
  if (busy.value || commentsLoading.value || !detail.value) return
  if (replyDraft.value?.content.trim() && !window.confirm('切换回复对象会清空当前回复草稿，继续吗？')) return
  const params = {
    ...target(), feed_author_id: channelUserId(detail.value),
    comment_id: comment.comment_id, comment_author_id: channelUserId(comment),
    comment_create_time: String(comment.create_time_raw || '')
  }
  if (reply) Object.assign(params, {
    target_reply_id: reply.reply_id, target_user_id: channelUserId(reply), target_user_nick: author(reply)
  })
  if (!params.feed_author_id || !params.comment_author_id || !params.comment_create_time ||
      (reply && (!params.target_reply_id || !params.target_user_id))) {
    notice.value = '这条评论缺少回复所需的作者、时间或回复 ID，请刷新评论后重试。'
    return
  }
  replySendError.value = ''
  replyDraft.value = { params, name: author(reply || comment), content: '' }
}
function editingReply(comment, reply = null) {
  return replyDraft.value?.params.comment_id === comment.comment_id &&
    (replyDraft.value.params.target_reply_id || '') === (reply?.reply_id || '')
}
function cancelReply() {
  if (busy.value) return
  replyDraft.value = null
  replySendError.value = ''
}
async function sendReply() {
  const draft = replyDraft.value
  if (!draft?.content.trim() || busy.value || commentsLoading.value) return
  if (!window.confirm(`确定回复「${draft.name}」吗？`)) return
  replySendError.value = ''
  if (await mutate('reply', { ...draft.params, content: draft.content })) {
    cancelReply()
    await loadComments()
  } else replySendError.value = notice.value
}
async function removeComment(item, owner = false) {
  if (await mutate(owner ? 'delete-comment-owner' : 'delete-comment', {
    ...target(), comment_id: item.comment_id, comment_author_id: item.author_id || item.comment_author_id
  }, '确定删除这条评论吗？此操作不可撤销。')) await loadComments()
}

function openComposer(edit = false, markdown = false) {
  if (busy.value) return
  const post = edit ? detail.value : null
  if (edit && !post) return
  Object.assign(draft, {
    edit, title: post?.title || '', content: post?.content || '', markdown: post ? Boolean(post.is_markdown) : markdown,
    channel_id: post?.channel_id || boardId.value || ''
  })
  composeError.value = ''
  composer.value = true
}
async function submitPost() {
  if (!draft.channel_id || !draft.content.trim() || (draft.markdown && !draft.title.trim())) {
    composeError.value = '请选择板块并填写正文；Markdown 长帖还需要标题。'
    return
  }
  const params = { ...(draft.edit ? target() : { guild_id: guild.value.guild_id }), ...draft }
  if (await mutate(draft.edit ? 'edit' : 'publish', params, `确定${draft.edit ? '修改' : '发布'}帖子到「${guild.value.name}」吗？`)) {
    composer.value = false
    const oldPost = detail.value
    if (draft.edit && oldPost) await openDetail(oldPost)
    await loadItems()
  } else composeError.value = notice.value
}

onMounted(async () => {
  fetch(`${API_BASE}/config`, { credentials: 'same-origin', cache: 'no-store' })
    .then(response => response.json()).then(data => { config.value = data.data || {} }).catch(() => {})
  await loadGuilds()
})
onBeforeUnmount(() => {
  disposed = true
  cancelReplyReads()
  generation++; listVersion++; detailVersion++; guildListVersion++
  for (const controller of requests.values()) controller.abort()
  requests.clear()
})
</script>

<template>
  <div class="shell channel-shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="config.appId || ''" :bot-open-id="config.botOpenId || ''" :bot-name="config.botName || 'AtriBot'" />
    <div class="sidebar-spacer" />
    <main class="workspace channel-workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button class="menu-btn" aria-label="打开导航" @click="sidebarOpen = true">☰</button>
          <h2>频道</h2>
          <span class="channel-account" :title="accountError || `当前账号：${accountName}`" role="status">当前账号：{{ accountName }}</span>
        </div>
        <button class="ghost-button" :disabled="loadingGuilds || busy" @click="loadGuilds">{{ loadingGuilds ? '读取中…' : '刷新频道' }}</button>
      </header>
      <div v-if="notice" class="channel-notice" role="status">{{ notice }}<button aria-label="关闭提示" @click="notice = ''">×</button></div>
      <div class="channel-layout">
        <aside class="channel-navigation">
          <div class="channel-nav-title"><strong>我的频道</strong><span>{{ guilds.length }}</span></div>
          <input v-model="guildFilter" class="channel-filter" placeholder="查找已加入的频道" aria-label="查找频道">
          <p v-if="guildError" class="channel-error" role="alert">{{ guildError }}</p>
          <p v-if="!guilds.length && !loadingGuilds && !guildError" class="channel-empty">尚未加入频道</p>
          <div class="channel-guild-list">
            <button v-for="item in visibleGuilds" :key="item.guild_id" class="channel-guild" :class="{ active: item.guild_id === guild?.guild_id }" :disabled="busy || loadingGuilds" @click="selectGuild(item)">
              <span class="channel-avatar">
                <img v-if="guildAvatar(item)" :key="item.avatar_url" :src="guildAvatar(item)" :alt="`${item.name}的频道头像`"
                     loading="lazy" referrerpolicy="no-referrer" @error="failedGuildAvatars.add(item.avatar_url)">
                <template v-else>{{ item.name?.slice(0, 1) || '#' }}</template>
              </span>
              <span><strong>{{ item.name }}</strong><small>{{ item.role }} · {{ item.member_count ?? '—' }} 位成员</small></span>
            </button>
          </div>
        </aside>

        <section class="channel-main">
          <div v-if="!guild" class="channel-landing"><span>#</span><h2>从一个频道开始</h2><p>选择已加入的社区，浏览板块、帖子和成员。</p></div>
          <template v-else>
            <header class="channel-community">
              <img v-if="safeUrl(currentInfo.avatar_url)" :src="safeUrl(currentInfo.avatar_url)" alt="频道头像" referrerpolicy="no-referrer">
              <span v-else class="channel-community-avatar">{{ guild.name?.slice(0, 1) }}</span>
              <div><small>腾讯频道 / {{ guild.role }}</small><h1>{{ currentInfo.name }}</h1><p>{{ currentInfo.profile || '在这里分享与交流' }}</p></div>
              <div class="channel-banner-tools">
                <details class="channel-search-popover">
                  <summary aria-label="搜索频道帖子" title="搜索频道帖子">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg>
                  </summary>
                  <form class="channel-search" @submit.prevent="searchFeeds">
                    <input v-model="keyword" maxlength="200" placeholder="搜索整个频道的帖子" aria-label="搜索频道帖子">
                    <button :disabled="busy || loadingInfo">搜索</button>
                  </form>
                </details>
                <button :disabled="busy" title="频道成员" aria-label="查看频道成员" @click="selectView('members')">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4v3"/></svg>
                </button>
              </div>
            </header>
            <div class="channel-tabs">
              <nav class="channel-board-list" aria-label="频道板块">
                <button :class="{ active: !boardId && view === 'feeds' && !search }" :aria-pressed="!boardId && view === 'feeds' && !search" :disabled="busy || loadingInfo" @click="selectBoard('')">全部</button>
                <button v-for="board in navigationBoards" :key="board.channel_id" :class="{ active: boardId === board.channel_id && view === 'feeds' && !search }" :aria-pressed="boardId === board.channel_id && view === 'feeds' && !search" :disabled="busy || loadingInfo" @click="selectBoard(board.channel_id)">{{ board.channel_name }}</button>
              </nav>
              <button :disabled="loading || busy || loadingInfo" title="刷新当前内容" @click="loadItems()">刷新</button>
            </div>
            <div class="channel-scroll">
              <p v-if="infoError" class="channel-error" role="alert">{{ infoError }} <button :disabled="busy || loadingInfo" @click="selectGuild(guild)">重新读取资料</button></p>
              <p v-if="loadingInfo" class="channel-empty">正在读取频道资料…</p>
              <div v-if="error" class="channel-error" role="alert">{{ error }} <button :disabled="loading || busy" @click="loadItems()">重试</button></div>
              <p v-if="search && view === 'feeds'" class="channel-search-label">整个频道中关于「{{ search }}」的帖子 <button :disabled="busy" @click="keyword = ''; searchFeeds()">清除</button></p>
              <div v-if="view === 'members'" class="channel-members">
                <article v-for="member in items" :key="member.tinyid" class="channel-member"><span class="channel-avatar">{{ author(member).slice(0, 1) }}</span><div><strong>{{ author(member) }}</strong><small>{{ member.role }} · {{ member.tinyid }}</small><small v-if="member['加入时间']">加入于 {{ member['加入时间'] }}</small></div></article>
              </div>
              <template v-else>
                <article v-for="post in items" :key="post.feed_id" class="channel-post">
                  <div class="channel-post-author">
                    <span class="channel-avatar">{{ author(post).slice(0, 1) }}</span>
                    <div><strong>{{ author(post) }}</strong><small>{{ textTime(post) }} <span v-if="post.channel_name">· {{ post.channel_name }}</span></small></div>
                    <button class="channel-post-more" :disabled="busy" aria-label="帖子详情与管理" title="帖子详情与管理" @click="openDetail(post)">···</button>
                  </div>
                  <button v-if="post.title" class="channel-post-title" :disabled="busy" @click="openDetail(post)">{{ post.title }}</button>
                  <ChannelFeedBody :feed="post" />
                  <footer>
                    <span v-if="post.view_count != null" class="channel-post-views">浏览 {{ post.view_count }}</span>
                    <button :disabled="busy" aria-label="查看帖子点赞与互动" @click="openDetail(post)">♡ {{ post.prefer_count ?? 0 }}</button>
                    <button :disabled="busy" @click="openDetail(post)">评论 {{ post.comment_count ?? 0 }}</button>
                    <button :disabled="busy" @click="openDetail(post)">查看全文 ↗</button>
                  </footer>
                </article>
              </template>
              <div v-if="loading" class="channel-empty">正在加载{{ view === 'members' ? '成员' : '帖子' }}…</div>
              <div v-else-if="!items.length && !error" class="channel-empty">{{ view === 'members' ? '暂无可见成员' : search ? '没有找到相关帖子' : '这个板块还没有可见帖子' }}</div>
              <button v-if="hasMore" class="channel-load-more" :disabled="loading || busy" @click="loadItems(true)">加载更多</button>
              <p v-else-if="items.length && !loading" class="channel-end">已显示本次可获取的全部内容</p>
            </div>
            <footer class="channel-publisher">
              <button class="channel-publish-placeholder" :disabled="busy || loadingInfo || !boards.length" @click="openComposer()">期待你的分享</button>
              <span class="channel-publish-context">{{ boardName }}</span>
              <button class="channel-publish-long" :disabled="busy || loadingInfo || !boards.length" @click="openComposer(false, true)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m16 3 5 5L8 21H3v-5Z M13 6l5 5"/></svg>
                发长文
              </button>
            </footer>
          </template>
        </section>

        <aside v-if="guild" class="channel-about">
          <h3>频道简介</h3>
          <p>{{ currentInfo.profile || guild.name }}</p>
          <dl>
            <dt>成员</dt><dd>{{ currentInfo.member_count ?? '—' }}</dd>
            <dt>我的身份</dt><dd>{{ guild.role }}</dd>
            <dt>频道号</dt><dd>{{ currentInfo.guild_number }}</dd>
            <dt>频道 ID</dt><dd>{{ guild.guild_id }}</dd>
            <template v-if="boardId && view === 'feeds' && !search">
              <dt>板块号</dt><dd>{{ boardId }}</dd>
            </template>
          </dl>
          <a v-if="safeUrl(currentInfo.share_url)" :href="safeUrl(currentInfo.share_url)" target="_blank" rel="noopener noreferrer">在腾讯频道打开 ↗</a>
        </aside>
      </div>
    </main>

    <Transition name="channel-drawer" :duration="{ enter: 320, leave: 220 }" @after-leave="!selected && clearDetailContent()">
    <div v-if="selected" class="channel-overlay" @click.self="closeDetail" @keydown.esc="closeDetail">
      <section class="channel-detail" role="dialog" aria-modal="true" aria-label="帖子详情" tabindex="-1">
        <header class="channel-dialog-head"><strong>帖子详情</strong><button :disabled="busy" aria-label="关闭帖子详情" @click="closeDetail">×</button></header>
        <div class="channel-dialog-scroll">
          <p v-if="detailLoading && !detail" class="channel-empty">正在获取帖子详情…</p>
          <p v-if="detailError" class="channel-error">{{ detailError }} <button :disabled="busy || detailLoading" @click="openDetail(selected)">重试</button></p>
          <template v-if="detail">
            <div class="channel-post-author"><span class="channel-avatar">{{ author(detail).slice(0, 1) }}</span><div><strong>{{ author(detail) }}</strong><small>{{ textTime(detail) }} · {{ detail.channel_name }}</small></div></div>
            <h2>{{ detail.title }}</h2><ChannelFeedBody :feed="detail" detail />
            <div class="channel-post-actions"><span>♡ {{ detail.prefer_count ?? 0 }}</span><button :disabled="busy" @click="postAction('like')">点赞</button><button :disabled="busy" @click="postAction('unlike')">取消点赞</button><button :disabled="busy" @click="openComposer(true)">编辑</button><a v-if="safeUrl(detail.share_url)" :href="safeUrl(detail.share_url)" target="_blank" rel="noopener noreferrer">原帖 ↗</a><button :disabled="busy" @click="openDetail(detail)">刷新</button></div>
            <details class="channel-management"><summary>帖子管理</summary><div class="channel-post-actions"><button :disabled="busy" @click="postAction('pin')">置顶</button><button :disabled="busy" @click="postAction('unpin')">取消置顶</button><button :disabled="busy" @click="postAction('essence')">精华</button><button :disabled="busy" @click="postAction('unessence')">取消精华</button><button class="channel-danger" :disabled="busy" @click="postAction('delete')">删除帖子</button></div><div class="channel-move"><select v-model="moveTo" aria-label="移动到板块" :disabled="busy"><option v-for="board in boards" :key="board.channel_id" :value="board.channel_id">{{ board.channel_name }}</option></select><button :disabled="busy || !moveTo || moveTo === detail.channel_id" @click="postAction('move')">移动到此板块</button></div></details>
            <section class="channel-comments">
              <div class="channel-comments-heading">
                <h3>评论</h3>
                <button class="channel-comment-link" :disabled="busy || commentsLoading" @click="loadComments()">刷新评论</button>
              </div>
              <form class="channel-comment-form" @submit.prevent="sendComment">
                <textarea v-model="commentText" maxlength="10000" rows="3" placeholder="说点什么，参与讨论…" aria-label="评论正文" :disabled="busy" />
                <div class="channel-comment-compose-actions">
                  <button class="channel-primary" :disabled="busy || !commentText.trim()">{{ busy ? '处理中…' : '发表评论' }}</button>
                </div>
              </form>
              <p v-if="commentError" class="channel-error" role="alert">{{ commentError }} <button :disabled="commentsLoading" @click="loadComments()">重试</button></p>
              <p v-if="replyQueueError" class="channel-error" role="alert">回复加载已暂停：{{ replyQueueError }} <button :disabled="busy" @click="resumeReplyQueue">继续加载</button></p>
              <p v-if="!comments.length && !commentsLoading && !commentError" class="channel-empty">还没有评论，来聊两句吧</p>
              <article v-for="comment in comments" :key="comment.comment_id" class="channel-comment">
                <ChannelUserAvatar :user="comment" />
                <div class="channel-comment-main">
                  <header class="channel-comment-meta">
                    <div><strong>{{ author(comment) }}</strong><time>{{ textTime(comment) }}</time></div>
                    <details class="channel-comment-menu">
                      <summary aria-label="评论操作" title="评论操作">···</summary>
                      <div>
                        <button :disabled="busy" @click="removeComment(comment)">删除自己的评论</button>
                        <button :disabled="busy" @click="removeComment(comment, true)">以帖主身份删除</button>
                      </div>
                    </details>
                  </header>
                  <ChannelFeedBody :feed="comment" detail />
                  <button class="channel-comment-link channel-reply-action" :disabled="busy || commentsLoading" @click="openReply(comment)">回复</button>
                  <ChannelReplyComposer v-if="editingReply(comment)" v-model="replyDraft.content" :name="replyDraft.name"
                                        :busy="busy || commentsLoading" :error="replySendError" @submit="sendReply" @cancel="cancelReply" />
                  <button v-if="threadFor(comment).items.length || threadFor(comment).more" class="channel-comment-link channel-thread-toggle"
                          :aria-expanded="threadFor(comment).expanded" @click="toggleReplies(comment)">
                    {{ threadFor(comment).expanded ? '收起回复' : replyLabel(comment) }}
                    <span v-if="threadFor(comment).items.length">{{ threadFor(comment).items.length }}</span>
                  </button>
                  <div v-if="threadFor(comment).expanded && (threadFor(comment).items.length || threadFor(comment).more || threadFor(comment).error)" class="channel-reply-thread">
                    <article v-for="reply in threadFor(comment).items" :key="replyKey(reply)" class="channel-reply">
                      <ChannelUserAvatar :user="reply" small />
                      <div class="channel-reply-main">
                        <header class="channel-comment-meta"><div><strong>{{ author(reply) }}<span v-if="reply.target_user || reply.target_user_id" class="channel-reply-to"> 回复 <span>{{ reply.target_user || reply.target_user_id }}</span></span></strong><time>{{ textTime(reply) }}</time></div></header>
                        <ChannelFeedBody :feed="reply" detail />
                        <button class="channel-comment-link channel-reply-action" :disabled="busy || commentsLoading" @click="openReply(comment, reply)">回复</button>
                        <ChannelReplyComposer v-if="editingReply(comment, reply)" v-model="replyDraft.content" :name="replyDraft.name"
                                              :busy="busy || commentsLoading" :error="replySendError" @submit="sendReply" @cancel="cancelReply" />
                      </div>
                    </article>
                    <p v-if="threadFor(comment).loading" class="channel-thread-status" role="status">
                      <svg class="channel-more-spinner" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 12a9 9 0 1 1-9-9" /></svg>
                      正在加载回复…
                    </p>
                    <p v-else-if="threadFor(comment).error" class="channel-thread-status">回复暂未加载完成</p>
                    <p v-else-if="threadFor(comment).more" class="channel-thread-status">{{ replyQueueError ? '等待继续加载' : '等待加载…' }}</p>
                  </div>
                </div>
              </article>
              <p v-if="commentsLoading && !comments.length" class="channel-empty">读取评论中…</p>
              <button v-if="commentsMore" class="channel-more-comments" :disabled="commentsLoading || busy" :aria-busy="commentsLoading" @click="loadComments(true)">
                <svg v-if="commentsLoading" class="channel-more-spinner" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 12a9 9 0 1 1-9-9" /></svg>
                <span>{{ commentsLoading ? '正在加载…' : '加载更多评论' }}</span>
                <svg v-if="!commentsLoading" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
              </button>
            </section>
          </template>
        </div>
        <p v-if="notice" class="channel-notice" role="status">{{ notice }}</p>
      </section>
    </div>

    </Transition>

    <div v-if="composer" class="channel-overlay channel-compose-overlay" @keydown.esc="!busy && (composer = false)">
      <section class="channel-compose" role="dialog" aria-modal="true" :aria-label="draft.edit ? '编辑帖子' : '发布帖子'">
        <header class="channel-dialog-head"><strong>{{ draft.edit ? '编辑帖子' : '发布帖子' }}</strong><button :disabled="busy" aria-label="关闭编辑器" @click="composer = false">×</button></header>
        <form class="channel-dialog-scroll" @submit.prevent="submitPost">
          <p class="channel-compose-hint">发送到 {{ guild?.name }}</p>
          <label>发布板块<select v-model="draft.channel_id" :disabled="busy || draft.edit" required><option value="" disabled>选择一个板块</option><option v-for="board in boards" :key="board.channel_id" :value="board.channel_id">{{ board.channel_name }}</option></select></label>
          <label>标题<input v-model="draft.title" maxlength="300" :disabled="busy" :required="draft.markdown" placeholder="短帖可不填；Markdown 长帖必填"></label>
          <label class="channel-checkbox"><input v-model="draft.markdown" type="checkbox" :disabled="busy">使用 Markdown 长帖</label>
          <label>正文<textarea v-model="draft.content" rows="10" maxlength="60000" required :disabled="busy" placeholder="写下想分享的内容…" /></label>
          <details class="channel-preview"><summary>预览正文</summary><ChannelFeedBody :feed="preview" detail /></details>
          <p v-if="composeError" class="channel-error" role="alert">{{ composeError }}</p>
          <div class="channel-compose-footer"><button type="button" :disabled="busy" @click="composer = false">取消</button><button class="channel-primary" :disabled="busy">{{ busy ? '正在提交…' : draft.edit ? '确认修改' : '发布到频道' }}</button></div>
        </form>
      </section>
    </div>
  </div>
</template>
