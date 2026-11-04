<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import AppShell from '../../shared/components/AppShell.vue'
import SurveyActionsMenu from './SurveyActionsMenu.vue'
import {createWebuiApi} from '../../shared/lib/webuiApi.js'
import {withQqMode} from '../../shared/lib/qqEnvironment.js'
import {API_BASE} from '../../router.js'

const sidebarOpen = ref(false)
const items = ref([])
const total = ref(0)
const offset = ref(0)
const entryBase = ref('')
const selected = ref(null)
const editing = ref(false)
const correcting = ref(false)
const starredOnly = ref(false)
const draft = ref({title: '', description: '', questions: []})
const settings = ref({rewardGold: 0, visible: true, pinned: false, repeatable: false})
const responses = ref([])
const responsePage = ref(1)
const responseTotal = ref(0)
const responseSnapshot = ref(0)
const responsePageSize = ref(20)
const responseLoading = ref(false)
const responseError = ref('')
const pageJump = ref(1)
const answerDialog = ref(null)
const currentAnswer = ref(null)
const answerLoading = ref(false)
const answerError = ref('')
const answerJump = ref(1)
const starBusy = ref(false)
const responsePages = computed(() => Math.max(1, Math.ceil(responseTotal.value / responsePageSize.value)))
let responseGeneration = 0
let answerGeneration = 0
const tab = ref('questions')
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const notice = ref('')
let disposed = false
let selectionGeneration = 0
let listGeneration = 0
const api = createWebuiApi({
  baseUrl: API_BASE, assertActive: () => {
    if (disposed) throw new Error('页面已关闭')
  }, onSessionExpired: () => window.location.replace(withQqMode('/dashboard/'))
})
const locked = computed(() => selected.value && selected.value.status !== 'DRAFT')
const contentLocked = computed(() => locked.value && !correcting.value)
const dirty = computed(() => selected.value && JSON.stringify(draft.value) !== JSON.stringify({
  title: selected.value.title,
  description: selected.value.description,
  questions: selected.value.questions
}))
const settingsDirty = computed(() => selected.value && (settings.value.rewardGold !== selected.value.rewardGold || settings.value.visible !== selected.value.visible || settings.value.pinned !== selected.value.pinned || settings.value.repeatable !== selected.value.repeatable))
const types = [{value: 'SINGLE', label: '单选'}, {value: 'MULTIPLE', label: '多选'}, {value: 'TEXT', label: '文本'}]
const statusName = status => ({DRAFT: '草稿', PUBLISHED: '收集中', CLOSED: '已结束'}[status])
const date = timestamp => new Date(timestamp).toLocaleString('zh-CN', {hour12: false})
const newId = () => crypto.randomUUID().replaceAll('-', '')
const errorText = e => e instanceof Error ? e.message : '操作失败'

async function loadList() {
  const current = ++listGeneration
  loading.value = true
  try {
    const data = await api(`/surveys?offset=${offset.value}`)
    if (current !== listGeneration || disposed) return
    items.value = data.items;
    total.value = data.total;
    entryBase.value = data.entryBase || window.location.origin
  } catch (e) {
    if (!disposed) error.value = errorText(e)
  } finally {
    if (current === listGeneration) loading.value = false
  }
}

async function open(id) {
  const current = ++selectionGeneration
  error.value = '';
  notice.value = '';
  busy.value = true
  try {
    const data = await api(`/surveys/${id}`)
    if (current !== selectionGeneration || disposed) return
    correcting.value = false
    selected.value = data.survey
    settings.value = {
      rewardGold: data.survey.rewardGold ?? 0,
      visible: data.survey.visible ?? true,
      pinned: data.survey.pinned ?? false,
      repeatable: data.survey.repeatable ?? false
    }
    draft.value = structuredClone({
      title: data.survey.title,
      description: data.survey.description,
      questions: data.survey.questions
    })
    editing.value = true;
    tab.value = 'questions';
    resetResponses()
  } catch (e) {
    if (!disposed) error.value = errorText(e)
  } finally {
    if (current === selectionGeneration) busy.value = false
  }
}

function create() {
  if (busy.value) return
  selectionGeneration++
  correcting.value = false
  selected.value = null;
  draft.value = {title: '', description: '', questions: []};
  editing.value = true
  settings.value = {rewardGold: 0, visible: true, pinned: false, repeatable: false}
  tab.value = 'questions';
  resetResponses();
  error.value = '';
  notice.value = ''
  addQuestion()
}

function duplicate(source) {
  correcting.value = false
  selectionGeneration++
  draft.value = {
    title: `${source.title.slice(0, 116)}（副本）`,
    description: source.description,
    questions: source.questions.map(question => ({
      ...question, id: newId(), options: question.options.map(option => ({...option, id: newId()}))
    }))
  }
  settings.value = {
    rewardGold: source.rewardGold,
    visible: source.visible,
    pinned: source.pinned,
    repeatable: source.repeatable
  }
  selected.value = null;
  editing.value = true;
  tab.value = 'questions';
  resetResponses()
  error.value = '';
  notice.value = '已复制为新问卷，修改后保存草稿'
}

function addQuestion() {
  draft.value.questions.push({
    id: newId(),
    title: '',
    type: 'SINGLE',
    required: true,
    options: [{id: newId(), label: ''}, {id: newId(), label: ''}]
  })
}

function setType(question, type) {
  question.type = type
  question.options = type === 'TEXT' ? [] : question.options.length ? question.options : [{
    id: newId(),
    label: ''
  }, {id: newId(), label: ''}]
}

function move(index, delta) {
  const questions = draft.value.questions
  ;[questions[index], questions[index + delta]] = [questions[index + delta], questions[index]]
}

async function save() {
  if (busy.value) return
  if (!validSettings()) return
  busy.value = true;
  error.value = '';
  notice.value = ''
  try {
    const id = selected.value?.id
    if (id && settingsDirty.value) {
      selected.value = await api(`/surveys/${id}/settings`, {
        method: 'PUT',
        body: JSON.stringify({revision: selected.value.revision, ...settings.value})
      })
    }
    const result = await api(id ? `/surveys/${id}` : '/surveys', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(id ? {
        revision: selected.value.revision,
        definition: draft.value
      } : {...draft.value, ...settings.value})
    })
    await open(id || result.id)
    notice.value = locked.value ? '修改已保存' : '草稿已保存'
    await loadList()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

function validSettings() {
  if (Number.isInteger(settings.value.rewardGold) && settings.value.rewardGold >= 0 && settings.value.rewardGold <= 2147483647) return true
  error.value = '奖励金粒数须为 0–2147483647 之间的整数'
  return false
}

async function saveSettings() {
  if (!selected.value || busy.value || !validSettings()) return
  busy.value = true;
  error.value = '';
  notice.value = ''
  try {
    selected.value = await api(`/surveys/${selected.value.id}/settings`, {
      method: 'PUT',
      body: JSON.stringify({revision: selected.value.revision, ...settings.value})
    })
    notice.value = '设置已保存'
    await loadList()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

async function changeStatus(status, survey = selected.value) {
  if (!survey || busy.value) return
  if (!window.confirm(status === 'PUBLISHED' ? '发布后仅可修改文字，确认开始收集答卷？' : '确认停止收集答卷？')) return
  busy.value = true;
  error.value = ''
  try {
    const id = survey.id
    await api(`/surveys/${id}/status`, {method: 'POST', body: JSON.stringify({revision: survey.revision, status})})
    if (editing.value) await open(id)
    await loadList()
    notice.value = status === 'PUBLISHED' ? '问卷已发布' : '问卷已关闭'
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

async function remove(survey) {
  if (!survey || busy.value || !window.confirm('确认删除这份问卷及全部答卷？删除后无法恢复。')) return
  busy.value = true;
  error.value = ''
  try {
    await api(`/surveys/${survey.id}`, {method: 'DELETE'})
    resetResponses();
    editing.value = false;
    selected.value = null;
    offset.value = 0;
    notice.value = '问卷已删除';
    await loadList()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

function resetResponses() {
  starredOnly.value = false
  responseGeneration++
  responseLoading.value = false;
  responseError.value = '';
  responses.value = []
  responsePage.value = 1;
  pageJump.value = 1;
  responseSnapshot.value = 0;
  responseTotal.value = 0
  closeAnswer()
}

async function loadResponses(page = 1, refresh = false) {
  if (!selected.value || !Number.isInteger(page) || page < 1) return
  const generation = ++responseGeneration
  const id = selected.value.id
  responseLoading.value = true;
  responseError.value = '';
  tab.value = 'responses'
  try {
    const snapshot = refresh ? 0 : responseSnapshot.value
    const data = await api(`/surveys/${id}/responses?page=${page}&snapshot=${snapshot}&starredOnly=${starredOnly.value}`)
    if (disposed || generation !== responseGeneration || selected.value?.id !== id) return
    responses.value = data.items;
    responseTotal.value = data.total;
    responseSnapshot.value = data.snapshot
    responsePage.value = data.page;
    responsePageSize.value = data.pageSize;
    pageJump.value = data.page
    if (!starredOnly.value && (refresh || !snapshot)) selected.value.responseCount = data.total
  } catch (e) {
    if (!disposed && generation === responseGeneration) responseError.value = errorText(e)
  } finally {
    if (generation === responseGeneration) responseLoading.value = false
  }
}

function jumpListPage() {
  const page = Number(pageJump.value)
  if (!Number.isInteger(page) || page < 1 || page > responsePages.value) {
    responseError.value = `请输入 1–${responsePages.value} 之间的页码`
    return
  }
  void loadResponses(page)
}

function closeAnswer() {
  answerGeneration++
  answerDialog.value?.close()
  currentAnswer.value = null;
  answerLoading.value = false;
  answerError.value = ''
}

async function openAnswer(position) {
  if (!selected.value || starBusy.value) return
  const value = Number(position)
  if (!Number.isInteger(value) || value < 1 || value > responseTotal.value) {
    answerError.value = `请输入 1–${responseTotal.value} 之间的答卷序号`
    return
  }
  const generation = ++answerGeneration
  const id = selected.value.id
  answerLoading.value = true;
  answerError.value = ''
  await nextTick()
  if (disposed || generation !== answerGeneration) return
  if (!answerDialog.value.open) answerDialog.value.showModal()
  try {
    const data = await api(`/surveys/${id}/responses/at/${value}?snapshot=${responseSnapshot.value}&starredOnly=${starredOnly.value}`)
    if (disposed || generation !== answerGeneration || selected.value?.id !== id) return
    currentAnswer.value = data;
    answerJump.value = data.position
    await nextTick()
    if (generation === answerGeneration) answerDialog.value?.querySelector('.survey-dialog-body')?.scrollTo(0, 0)
  } catch (e) {
    if (!disposed && generation === answerGeneration) answerError.value = errorText(e)
  } finally {
    if (generation === answerGeneration) answerLoading.value = false
  }
}

async function toggleStar(response) {
  if (starBusy.value || !selected.value) return
  const id = selected.value.id
  const starred = !response.starred
  const inDialog = answerDialog.value?.open
  starBusy.value = true
  if (inDialog) answerError.value = ''; else responseError.value = ''
  try {
    await api(`/surveys/${id}/responses/${response.id}/star`, {method: 'PUT', body: JSON.stringify({starred})})
    if (disposed || selected.value?.id !== id) return
    const item = responses.value.find(item => item.id === response.id)
    if (item) item.starred = starred
    if (currentAnswer.value?.answer.id === response.id) currentAnswer.value.starred = starred
    if (starredOnly.value && !starred) {
      closeAnswer()
      await loadResponses(responsePage.value)
    }
  } catch (e) {
    if (!disposed) {
      if (inDialog) answerError.value = errorText(e); else responseError.value = errorText(e)
    }
  } finally {
    starBusy.value = false
  }
}

function answerText(question, response) {
  return (response.answers[question.id] || []).map(value => question.type === 'TEXT' ? value : question.options.find(option => option.id === value)?.label || value).join('、') || '未填写'
}

function cancelCorrection() {
  const source = selected.value
  draft.value = {
    title: source.title,
    description: source.description,
    questions: source.questions.map(q => ({...q, options: q.options.map(o => ({...o}))}))
  }
  correcting.value = false
}

async function listAction(action, survey) {
  if (busy.value) return
  error.value = '';
  notice.value = ''
  if (action === 'link') return copyLink(survey)
  if (action === 'delete') return remove(survey)
  if (action === 'status') return changeStatus(survey.status === 'PUBLISHED' ? 'CLOSED' : 'PUBLISHED', survey)
  busy.value = true
  try {
    const data = await api(`/surveys/${survey.id}`)
    if (!disposed) duplicate(data.survey)
  } catch (e) {
    if (!disposed) error.value = errorText(e)
  } finally {
    busy.value = false
  }
}

async function copyLink(survey) {
  const link = `${entryBase.value}/dashboard/surveys/${survey.id}?_wwv=9983&qq=1`
  try {
    await navigator.clipboard.writeText(link);
    notice.value = '填写链接已复制'
  } catch {
    notice.value = `填写链接：${link}`
  }
}

function paginate(delta) {
  offset.value += delta;
  void loadList()
}

onMounted(loadList)
onBeforeUnmount(() => {
  disposed = true;
  selectionGeneration++;
  listGeneration++;
  responseGeneration++;
  closeAnswer()
})
</script>

<template>
  <AppShell v-model:open="sidebarOpen">
    <main class="workspace surveys-workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round">
              <path d="M3 6h18M3 12h18M3 18h18"/>
            </svg>
          </button>
          <h2>问卷调查</h2>
        </div>
        <div class="survey-actions">
          <button v-if="!editing" class="icon-button" aria-label="刷新问卷" title="刷新" :disabled="loading"
                  @click="loadList">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 1 1-9-9c2.5 0 4.9 1 6.7 2.8L21 8M21 3v5h-5"/>
            </svg>
          </button>
          <button v-if="!editing" class="primary-button" :disabled="busy" @click="create">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            新建问卷
          </button>
          <button v-else class="ghost-button" :disabled="busy" @click="editing = false; loadList()">返回列表</button>
        </div>
      </header>

      <nav v-if="editing" class="survey-tabs" aria-label="问卷管理">
        <button :class="{active: tab === 'questions'}" :aria-pressed="tab === 'questions'" @click="tab = 'questions'">
          问卷内容
        </button>
        <button v-if="selected" :disabled="busy" :class="{active: tab === 'responses'}"
                :aria-pressed="tab === 'responses'" @click="loadResponses()">答卷 <span>{{
            selected.responseCount
          }}</span></button>
      </nav>

      <div class="surveys-scroll">
        <div class="surveys-page">
          <p v-if="error" class="survey-feedback error" role="alert">{{ error }}</p>
          <p v-if="notice" class="survey-feedback" role="status">{{ notice }}</p>
          <template v-if="!editing">
            <div class="survey-list-heading"><h3>全部问卷</h3><span>共 {{ total }} 份</span></div>
            <div v-if="loading" class="survey-empty" role="status">正在读取问卷…</div>
            <div v-else-if="!items.length" class="survey-empty">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4"
                   stroke-linecap="round" stroke-linejoin="round">
                <rect x="5" y="4" width="14" height="17" rx="2"/>
                <path d="M9 3h6v3H9zM9 11h6M9 15h4"/>
              </svg>
              <strong>暂无问卷</strong><span>新建问卷后即可编辑题目并发布</span>
              <button class="ghost-button" :disabled="busy" @click="create">创建第一份问卷</button>
            </div>
            <div v-else class="survey-list">
              <article v-for="item in items" :key="item.id" class="survey-card">
                <button class="survey-card-open" :disabled="busy" @click="open(item.id)">
                  <div class="survey-card-main">
                    <div class="survey-card-meta"><span class="survey-id">#{{ item.id }}</span><span
                        class="survey-badge" :class="item.status.toLowerCase()"><i/>{{ statusName(item.status) }}</span><span
                        v-if="item.repeatable" class="survey-badge">可重复填写</span><span v-if="item.pinned"
                                                                                           class="survey-badge pinned">置顶</span><span
                        v-if="item.visible === false" class="survey-badge draft">列表隐藏</span><span
                        v-if="item.rewardGold > 0" class="survey-badge">{{ item.rewardGold }} 金粒</span></div>
                    <h3>{{ item.title }}</h3>
                    <p>{{ item.description || '未填写说明' }}</p>
                  </div>
                  <div class="survey-card-count"><strong>{{ item.responseCount }}</strong><span>份答卷</span></div>
                  <svg class="survey-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none"
                       stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="m9 5 7 7-7 7"/>
                  </svg>
                </button>
                <div class="survey-card-footer">
                  <time>更新于 {{ date(item.updatedAt) }}</time>
                  <SurveyActionsMenu class="survey-card-menu" :survey="item" :disabled="busy"
                                     @action="listAction($event, item)"/>
                </div>
              </article>
            </div>
            <nav v-if="total > 50" class="survey-pagination" aria-label="问卷分页">
              <button class="ghost-button" :disabled="offset === 0 || loading" @click="paginate(-50)">上一页</button>
              <span>{{ offset / 50 + 1 }} / {{ Math.ceil(total / 50) }}</span>
              <button class="ghost-button" :disabled="offset + 50 >= total || loading" @click="paginate(50)">下一页
              </button>
            </nav>
          </template>

          <template v-else>
            <div class="survey-editor-heading">
              <div class="survey-editor-title"><h3>{{ selected ? selected.title : '新建问卷' }}</h3><span
                  class="survey-badge" :class="selected?.status.toLowerCase() || 'draft'"><i/>{{
                  selected ? statusName(selected.status) : '未保存'
                }}</span></div>
              <div v-if="selected" class="survey-actions">
                <template v-if="locked && tab === 'questions'">
                  <button v-if="!correcting" class="ghost-button" :disabled="busy" @click="correcting = true">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                         stroke-linecap="round" stroke-linejoin="round">
                      <path d="m15 5 4 4M4 20l4-1L20 7a3 3 0 0 0-4-4L4 15Z"/>
                    </svg>
                    编辑
                  </button>
                  <template v-else>
                    <button class="ghost-button" :disabled="busy" @click="cancelCorrection">取消编辑</button>
                    <button class="primary-button" :disabled="busy || !dirty" @click="save">保存修改</button>
                  </template>
                </template>
                <button v-if="selected.status === 'DRAFT'" class="primary-button"
                        :disabled="busy || dirty || settingsDirty" @click="changeStatus('PUBLISHED')">发布问卷
                </button>
              </div>
            </div>

            <form v-if="tab === 'questions'" class="survey-editor" @submit.prevent="save">
              <section class="survey-panel survey-settings">
                <header class="survey-panel-heading"><h4>填写设置</h4>
                  <button v-if="selected" type="button" class="ghost-button" :disabled="busy || !settingsDirty"
                          @click="saveSettings">保存设置
                  </button>
                </header>
                <div class="survey-basic-fields">
                  <label class="survey-field survey-reward">奖励金粒<input v-model.number="settings.rewardGold"
                                                                           type="number" min="0" max="2147483647"
                                                                           step="1" required :disabled="busy"/><small>提交成功后发放一次，0
                    为不奖励</small></label>
                  <div><label class="survey-visibility"><input v-model="settings.repeatable" type="checkbox"
                                                               :disabled="busy"/><span>允许重复填写</span></label>
                    <p class="survey-setting-note">每次提交单独保存，金粒仅首次填写发放</p></div>
                  <div><label class="survey-visibility"><input v-model="settings.visible" type="checkbox"
                                                               :disabled="busy"/><span>在问卷列表显示</span></label>
                    <p class="survey-setting-note">隐藏后仍可通过链接访问，停止收集后不可提交</p></div>
                  <div><label class="survey-visibility"><input v-model="settings.pinned" type="checkbox"
                                                               :disabled="busy"/><span>置顶问卷</span></label>
                    <p class="survey-setting-note">置顶问卷优先显示，多份置顶按创建顺序倒序排列</p></div>
                </div>
              </section>
              <fieldset :disabled="contentLocked || busy" class="survey-fields">
                <section class="survey-panel survey-basic">
                  <header class="survey-panel-heading"><h4>基本信息</h4><span v-if="selected" class="survey-id">#{{
                      selected.id
                    }}</span></header>
                  <div class="survey-basic-fields">
                    <label class="survey-field">问卷标题<input v-model="draft.title" type="text" maxlength="120"
                                                               required placeholder="填写问卷标题"/></label>
                    <label class="survey-field">问卷说明<textarea v-model="draft.description" rows="2" maxlength="3000"
                                                                  placeholder="填写说明或调查目的（选填）"/></label>
                  </div>
                </section>
                <div class="survey-list-heading survey-questions-heading"><h3>题目设置</h3>
                  <span>{{ draft.questions.length }} 题</span></div>
                <article v-for="(question, index) in draft.questions" :key="question.id"
                         class="survey-panel survey-question-editor">
                  <header class="survey-panel-heading">
                    <div class="survey-question-label"><span
                        class="survey-question-number">{{ String(index + 1).padStart(2, '0') }}</span><h4>问题
                      {{ index + 1 }}</h4></div>
                    <div v-if="!locked" class="survey-actions">
                      <button type="button" class="icon-button" :disabled="index === 0" aria-label="上移问题"
                              title="上移" @click="move(index, -1)">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M12 19V5m-6 6 6-6 6 6"/>
                        </svg>
                      </button>
                      <button type="button" class="icon-button" :disabled="index === draft.questions.length - 1"
                              aria-label="下移问题" title="下移" @click="move(index, 1)">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                          <path d="M12 5v14m-6-6 6 6 6-6"/>
                        </svg>
                      </button>
                      <button type="button" class="ghost-button danger" @click="draft.questions.splice(index, 1)">删除
                      </button>
                    </div>
                  </header>
                  <div class="survey-question-body">
                    <div class="survey-question-tools">
                      <div class="survey-type-options" aria-label="题型">
                        <button v-for="type in types" :key="type.value" type="button"
                                :class="{active: question.type === type.value}"
                                :aria-pressed="question.type === type.value" :disabled="locked"
                                @click="setType(question, type.value)">{{ type.label }}
                        </button>
                      </div>
                      <label class="survey-required"><input v-model="question.required" type="checkbox"
                                                            :disabled="locked"/>必填</label>
                    </div>
                    <input v-model="question.title" type="text" :aria-label="`问题 ${index + 1} 标题`" required
                           maxlength="300" placeholder="填写问题"/>
                    <div v-if="question.type !== 'TEXT'" class="survey-option-editors">
                      <div v-for="(option, optionIndex) in question.options" :key="option.id" class="survey-option-row">
                        <span class="survey-option-marker" :class="{multiple: question.type === 'MULTIPLE'}">{{
                            String.fromCharCode(65 + optionIndex)
                          }}</span><input v-model="option.label" type="text"
                                          :aria-label="`问题 ${index + 1} 选项 ${optionIndex + 1}`" required
                                          maxlength="200" placeholder="选项内容"/>
                        <button v-if="!locked" type="button" class="survey-remove-option" aria-label="删除选项"
                                :disabled="question.options.length <= 2"
                                @click="question.options.splice(optionIndex, 1)">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                               stroke-width="1.8" stroke-linecap="round">
                            <path d="m6 6 12 12M6 18 18 6"/>
                          </svg>
                        </button>
                      </div>
                      <button v-if="!locked && question.options.length < 20" type="button" class="survey-add"
                              @click="question.options.push({id: newId(), label: ''})">＋ 添加选项
                      </button>
                    </div>
                    <p v-else class="survey-text-hint">文本回答，最多 2000 字</p>
                  </div>
                </article>
                <button v-if="!locked" type="button" class="survey-add-question" @click="addQuestion">＋ 添加问题
                </button>
              </fieldset>
              <footer class="survey-editor-footer"><span>{{
                  locked ? '已发布问卷仅可修改文字' : '保存草稿后可发布问卷'
                }}</span>
                <button v-if="!locked" class="primary-button" type="submit" :disabled="busy || !draft.questions.length">
                  {{ busy ? '保存中…' : '保存草稿' }}
                </button>
              </footer>
            </form>

            <section v-else class="survey-responses" aria-label="问卷答卷">
              <div class="survey-list-heading"><h3>提交记录 <span>共 {{ responseTotal }} 份</span></h3>
                <div class="survey-actions"><label class="survey-star-filter"><input v-model="starredOnly"
                                                                                     type="checkbox"
                                                                                     :disabled="responseLoading || starBusy"
                                                                                     @change="closeAnswer(); loadResponses(1, true)"/>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z"/>
                  </svg>
                  仅看星标</label>
                  <button class="icon-button" aria-label="刷新答卷" title="刷新答卷"
                          :disabled="responseLoading || starBusy" @click="loadResponses(1, true)">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                         stroke-linecap="round" stroke-linejoin="round">
                      <path d="M21 12a9 9 0 1 1-9-9c2.5 0 4.9 1 6.7 2.8L21 8M21 3v5h-5"/>
                    </svg>
                  </button>
                </div>
              </div>
              <p v-if="responseError" class="survey-feedback error" role="alert">{{ responseError }}</p>
              <p v-if="responseLoading" class="survey-empty" role="status">正在读取答卷…</p>
              <p v-else-if="!responses.length" class="survey-empty">{{ starredOnly ? '暂无星标答卷' : '暂无答卷' }}</p>
              <div v-else class="survey-response-list">
                <div class="survey-response-columns" aria-hidden="true">
                  <span>星标</span><span>提交人</span><span>提交时间</span><span/></div>
                <article v-for="(response, index) in responses" :key="response.id" class="survey-response-row">
                  <button class="survey-star" :class="{starred: response.starred}"
                          :aria-label="response.starred ? '取消星标' : '标记星标'" :aria-pressed="response.starred"
                          :disabled="starBusy" @click="toggleStar(response)">
                    <svg width="18" height="18" viewBox="0 0 24 24" :fill="response.starred ? 'currentColor' : 'none'"
                         stroke="currentColor" stroke-width="1.6" stroke-linejoin="round">
                      <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z"/>
                    </svg>
                  </button>
                  <button class="survey-response-open"
                          @click="openAnswer((responsePage - 1) * responsePageSize + index + 1)">
                    <span class="survey-response-person"><strong>{{ response.username || '未命名用户' }}</strong><code>{{
                        response.userOpenId
                      }}</code></span>
                    <time>{{ date(response.submittedAt) }}</time>
                    <span class="survey-response-view">查看答卷 <svg width="14" height="14" viewBox="0 0 24 24"
                                                                     fill="none" stroke="currentColor"
                                                                     stroke-width="1.8" stroke-linecap="round"
                                                                     stroke-linejoin="round"><path
                        d="m9 5 7 7-7 7"/></svg></span>
                  </button>
                </article>
              </div>
              <nav v-if="responseTotal" class="survey-pagination survey-response-pagination" aria-label="答卷分页">
                <button class="ghost-button" :disabled="responseLoading || starBusy || responsePage <= 1"
                        aria-label="上一页" @click="loadResponses(responsePage - 1)">
                  <svg class="survey-page-arrow" aria-hidden="true" width="16" height="16" viewBox="0 0 24 24"
                       fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
                       stroke-linejoin="round">
                    <path d="m14 6-6 6 6 6"/>
                  </svg>
                  <span class="survey-page-label">上一页</span></button>
                <span>{{ responsePage }} / {{ responsePages }}</span>
                <button class="ghost-button" :disabled="responseLoading || starBusy || responsePage >= responsePages"
                        aria-label="下一页" @click="loadResponses(responsePage + 1)">
                  <svg class="survey-page-arrow" aria-hidden="true" width="16" height="16" viewBox="0 0 24 24"
                       fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
                       stroke-linejoin="round">
                    <path d="m10 6 6 6-6 6"/>
                  </svg>
                  <span class="survey-page-label">下一页</span></button>
                <form class="survey-page-jump" @submit.prevent="jumpListPage"><label>跳至 <input v-model="pageJump"
                                                                                                 type="number" min="1"
                                                                                                 :max="responsePages"
                                                                                                 aria-label="答卷列表页码"
                                                                                                 :disabled="responseLoading || starBusy"/>
                  页</label>
                  <button class="ghost-button" :disabled="responseLoading || starBusy">跳转</button>
                </form>
              </nav>
            </section>
          </template>
        </div>
      </div>
      <dialog ref="answerDialog" class="survey-answer-dialog" aria-labelledby="survey-answer-title"
              @cancel.prevent="closeAnswer"
              @click="event => { if (event.target === event.currentTarget) closeAnswer() }">
        <header class="survey-dialog-header">
          <div><h3 id="survey-answer-title">查看答卷</h3>
            <p>{{ selected?.title }}</p></div>
          <button class="icon-button" aria-label="关闭答卷" @click="closeAnswer">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                 stroke-linecap="round">
              <path d="m6 6 12 12M6 18 18 6"/>
            </svg>
          </button>
        </header>
        <div class="survey-dialog-body" :aria-busy="answerLoading">
          <p v-if="answerError" class="survey-feedback error" role="alert">{{ answerError }}</p>
          <p v-if="answerLoading" class="survey-empty" role="status">正在读取答卷…</p>
          <article v-else-if="currentAnswer" class="survey-response">
            <header class="survey-answer-person">
              <div><strong>{{ currentAnswer.answer.username || '未命名用户' }}</strong>
                <time>{{ date(currentAnswer.answer.submittedAt) }}</time>
              </div>
              <button class="ghost-button survey-star-action" :class="{starred: currentAnswer.starred}"
                      :aria-pressed="currentAnswer.starred" :disabled="starBusy"
                      @click="toggleStar({id: currentAnswer.answer.id, starred: currentAnswer.starred})">
                <svg width="16" height="16" viewBox="0 0 24 24" :fill="currentAnswer.starred ? 'currentColor' : 'none'"
                     stroke="currentColor" stroke-width="1.6" stroke-linejoin="round">
                  <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z"/>
                </svg>
                {{ currentAnswer.starred ? '取消星标' : '标记星标' }}
              </button>
            </header>
            <p class="survey-response-id"><span>用户平台 ID</span><code>{{ currentAnswer.answer.userOpenId }}</code></p>
            <dl>
              <div v-for="(question, index) in selected.questions" :key="question.id">
                <dt><span>{{ String(index + 1).padStart(2, '0') }}</span>{{ question.title }}</dt>
                <dd>{{ answerText(question, currentAnswer.answer) }}</dd>
              </div>
            </dl>
          </article>
        </div>
        <footer class="survey-dialog-footer">
          <div class="survey-answer-navigation">
            <button class="ghost-button"
                    :disabled="answerLoading || starBusy || !currentAnswer || currentAnswer.position <= 1"
                    aria-label="上一份" @click="openAnswer(currentAnswer.position - 1)">
              <svg class="survey-page-arrow" aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="m14 6-6 6 6 6"/>
              </svg>
              <span class="survey-page-label">上一份</span></button>
            <span>{{ currentAnswer?.position || '—' }} / {{ responseTotal }}</span>
            <button class="ghost-button"
                    :disabled="answerLoading || starBusy || !currentAnswer || currentAnswer.position >= responseTotal"
                    aria-label="下一份" @click="openAnswer(currentAnswer.position + 1)">
              <svg class="survey-page-arrow" aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="m10 6 6 6-6 6"/>
              </svg>
              <span class="survey-page-label">下一份</span></button>
          </div>
          <form class="survey-page-jump" @submit.prevent="openAnswer(answerJump)"><label>跳至 <input
              v-model="answerJump" type="number" min="1" :max="responseTotal" aria-label="答卷序号"
              :disabled="answerLoading || starBusy"/> 份</label>
            <button class="ghost-button" :disabled="answerLoading || starBusy">跳转</button>
          </form>
        </footer>
      </dialog>
    </main>
  </AppShell>
</template>

<style scoped>
.surveys-workspace {
  --color-accent: #0099ff;
  --color-accent-hover: #008bea;
  --color-accent-active: #007dd4;
  --color-accent-soft: #edf7ff;
  --color-accent-border: #cce9ff;
  --color-accent-text: #0086e6;
  --color-border: #e9eaed;
  --color-border-input: #dedfe3;
  --color-border-strong: #c5d7e8;
  --radius-md: 8px;
  --radius-lg: 12px;
  --focus-ring: 0 0 0 3px rgb(0 153 255 / 12%);
  min-width: 0;
  overflow: hidden;
}

.surveys-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px 28px;
  scrollbar-gutter: stable;
}

.surveys-page {
  max-width: 1120px;
  margin-inline: auto;
}

.survey-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.survey-actions .primary-button {
  align-self: center;
  gap: 6px;
}

.survey-actions .icon-button {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
}

.survey-list-heading, .survey-editor-heading, .survey-editor-title, .survey-panel-heading, .survey-question-label, .survey-question-tools, .survey-editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.survey-list-heading {
  margin-bottom: 12px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.survey-list-heading h3 {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.survey-list {
  display: grid;
  gap: 12px;
}

.survey-card {
  position: relative;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.survey-card-open {
  display: flex;
  align-items: center;
  gap: 24px;
  width: 100%;
  padding: 16px 20px 0;
  text-align: left;
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-text);
  transition: border-color .15s, background .15s;
}

.survey-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px 8px 20px;
}

.survey-card:hover {
  border-color: var(--color-accent-border);
  background: var(--color-surface-alt);
}

.survey-card-main {
  flex: 1;
  min-width: 0;
}

.survey-badge.pinned {
  color: var(--color-accent-text);
  background: var(--color-accent-soft);
}

.survey-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.survey-id {
  color: var(--color-text-muted);
  font: 12px var(--font-mono);
}

.survey-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 5px;
  background: var(--color-surface-sunken);
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.survey-badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.survey-badge.published {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.survey-badge.closed {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.survey-card h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 8px 0 4px;
  overflow-wrap: anywhere;
}

.survey-card p {
  margin: 0 0 8px;
  color: var(--color-text-muted);
  font-size: 12px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow-wrap: anywhere;
}

.survey-card-count {
  display: grid;
  gap: 2px;
  min-width: 64px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.survey-card-count strong {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
}

.survey-card-count span, time {
  font-size: 11px;
  color: var(--color-text-muted);
}

.survey-chevron {
  flex-shrink: 0;
  color: var(--color-text-subtle);
}

.survey-tabs {
  display: flex;
  flex-shrink: 0;
  gap: 24px;
  padding: 0 24px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.survey-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 0 2px;
  background: none;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--color-text-muted);
  font-size: 13px;
}

.survey-tabs button.active {
  color: var(--color-accent-text);
  border-bottom-color: var(--color-accent);
  font-weight: 600;
}

.survey-tabs span {
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--color-surface-sunken);
  font-size: 11px;
}

.survey-tabs button.active span {
  background: var(--color-accent-soft);
}

.survey-editor-heading {
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.survey-editor-title {
  justify-content: flex-start;
  flex-wrap: wrap;
  min-width: 0;
}

.survey-editor-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.survey-fields {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}

.survey-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  overflow: hidden;
}

.survey-panel-heading {
  min-height: 48px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--color-hairline);
}

.survey-panel-heading h4 {
  margin: 0;
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
}

.survey-basic-fields {
  display: grid;
  gap: 16px;
  padding: 16px 20px 20px;
}

.survey-field {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 32px;
}

.survey-editor input:not([type=checkbox]), .survey-editor textarea {
  width: 100%;
  min-width: 0;
  min-height: 32px;
  font-size: 13px;
  line-height: 1.55;
}

.survey-editor textarea {
  resize: vertical;
}

.survey-settings {
  margin-bottom: 16px;
}

.survey-reward {
  max-width: 380px;
  row-gap: 4px;
}

.survey-reward small {
  grid-column: 2;
}

.survey-reward small, .survey-setting-note {
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.6;
  font-weight: 400;
}

.survey-visibility {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
}

.survey-visibility input {
  width: 15px;
  height: 15px;
  margin: 0;
  accent-color: var(--color-accent);
}

.survey-setting-note {
  margin: 6px 0 0 23px;
}

.survey-editor input:disabled, .survey-editor textarea:disabled {
  background: var(--color-surface-alt);
  color: var(--color-text-secondary);
  cursor: default;
}

.survey-editor input:focus, .survey-editor textarea:focus {
  outline: none;
  border-color: var(--color-accent);
  box-shadow: var(--focus-ring);
}

.survey-questions-heading {
  margin-top: 24px;
}

.survey-question-editor {
  margin-bottom: 12px;
}

.survey-question-label {
  justify-content: flex-start;
  gap: 10px;
}

.survey-question-number {
  color: var(--color-text-muted);
  font: 12px var(--font-mono);
}

.survey-question-body {
  padding: 16px 20px 20px;
}

.survey-question-tools {
  margin-bottom: 14px;
}

.survey-type-options {
  display: inline-flex;
  padding: 3px;
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
  gap: 2px;
}

.survey-type-options button {
  min-height: 26px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--color-text-muted);
  font-size: 12px;
}

.survey-type-options button.active {
  color: var(--color-accent-text);
  border-color: var(--color-border);
  background: var(--color-surface);
}

.survey-required {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.survey-required input {
  accent-color: var(--color-accent);
  margin: 0;
}

.survey-option-editors {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.survey-option-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.survey-option-marker {
  display: grid;
  place-items: center;
  flex: 0 0 22px;
  height: 22px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  color: var(--color-text-muted);
  font-size: 11px;
}

.survey-option-marker.multiple {
  border-radius: 5px;
}

.survey-remove-option {
  display: grid;
  place-items: center;
  flex: 0 0 28px;
  height: 28px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--color-text-muted);
}

.survey-remove-option:hover:not(:disabled) {
  color: var(--color-danger);
  background: var(--color-danger-soft);
}

.survey-add {
  justify-self: start;
  padding: 4px 0;
  margin-left: 32px;
  border: 0;
  background: none;
  color: var(--color-accent-text);
  font-size: 12px;
}

.survey-text-hint {
  color: var(--color-text-muted);
  font-size: 12px;
  margin: 12px 0 0;
  padding: 12px;
  background: var(--color-surface-alt);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
}

.survey-add-question {
  width: 100%;
  min-height: 40px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-md);
  color: var(--color-accent-text);
  background: var(--color-surface);
  font-size: 13px;
}

.survey-add-question:hover {
  background: var(--color-accent-soft);
  border-color: var(--color-accent-border);
}

.survey-editor-footer {
  margin-top: 16px;
  padding: 14px 0 0;
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-muted);
}

.survey-feedback {
  margin: 0 0 16px;
  padding: 10px 14px;
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  border: 1px solid var(--color-accent-border);
  border-radius: var(--radius-md);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.survey-feedback.error {
  color: var(--color-danger);
  background: var(--color-danger-soft);
  border-color: transparent;
}

.survey-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 64px 16px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
}

.survey-empty > svg {
  color: var(--color-text-subtle);
  margin-bottom: 4px;
}

.survey-empty strong {
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 500;
}

.survey-empty .ghost-button {
  margin-top: 6px;
}

.survey-star-filter {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.survey-star-filter input {
  margin: 0;
  accent-color: #d5a61a;
}

.survey-star-filter svg {
  color: #f2c230;
}

.survey-response {
  margin-bottom: 12px;
}

.survey-response header strong {
  font-size: 13px;
}

.survey-response-body {
  padding: 16px 20px;
}

.survey-response-id {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.survey-response-id code {
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  overflow-wrap: anywhere;
}

.survey-response dl {
  margin: 16px 0 0;
}

.survey-response dl > div {
  padding: 12px 0;
  border-top: 1px solid var(--color-hairline);
}

.survey-response dl > div:last-child {
  padding-bottom: 0;
}

.survey-response dt {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
  overflow-wrap: anywhere;
}

.survey-response dt span {
  font-family: var(--font-mono);
}

.survey-response dd {
  margin: 6px 0 0 25px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.survey-list-heading h3 > span {
  margin-left: 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.survey-list-heading .icon-button {
  display: grid;
  place-items: center;
}

.survey-response-list {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.survey-response-columns {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 170px 96px;
  gap: 12px;
  padding: 10px 16px;
  color: var(--color-text-muted);
  background: var(--color-surface-alt);
  font-size: 12px;
}

.survey-response-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-top: 1px solid var(--color-hairline);
}

.survey-response-row:hover {
  background: var(--color-surface-alt);
}

.survey-star {
  display: grid;
  place-items: center;
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  margin-right: 12px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  color: var(--color-text-subtle);
  background: transparent;
}

.survey-star:hover:not(:disabled) {
  background: #fff9e6;
  color: #f2c230;
}

.survey-star.starred, .survey-answer-person .survey-star-action.starred svg {
  color: #f2c230;
}

.survey-response-open {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 170px 96px;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  padding: 14px 0;
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--color-text);
}

.survey-response-person {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.survey-response-person strong {
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.survey-response-person code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-muted);
  font: 11px var(--font-mono);
}

.survey-response-view {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  font-size: 12px;
  color: var(--color-accent-text);
}

.survey-response-pagination {
  flex-wrap: nowrap;
  overflow-x: auto;
}

.survey-page-arrow {
  display: none;
}

.survey-response-pagination > span, .survey-answer-navigation > span {
  white-space: nowrap;
}

.survey-page-jump, .survey-page-jump label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.survey-page-jump {
  flex-shrink: 0;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-muted);
}

.survey-page-jump .ghost-button {
  font-size: 12px;
  font-weight: 400;
}

.workspace .survey-page-jump input {
  width: 64px;
  padding: 0 6px;
  text-align: center;
  font-size: 12px;
}

.survey-answer-dialog {
  width: min(760px, calc(100vw - 32px));
  max-width: none;
  max-height: min(860px, calc(100dvh - 48px));
  margin: auto;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-lg);
}

.survey-answer-dialog[open] {
  display: flex;
  flex-direction: column;
}

.survey-answer-dialog::backdrop {
  background: rgb(16 24 40 / 40%);
}

.survey-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-hairline);
}

.survey-dialog-header > div {
  min-width: 0;
}

.survey-dialog-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.survey-dialog-header p {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.survey-dialog-header .icon-button {
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.survey-dialog-body {
  overflow-y: auto;
  min-height: 0;
  padding: 20px;
}

.survey-answer-person {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.survey-answer-person > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.survey-answer-person strong {
  font-size: 14px;
  overflow-wrap: anywhere;
}

.survey-star-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.survey-dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  flex-wrap: nowrap;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-hairline);
}

.survey-answer-navigation {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.survey-pagination {
  display: flex;
  justify-content: center;
  gap: 16px;
  align-items: center;
  margin-top: 24px;
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 768px) {
  .surveys-scroll {
    padding: 16px 12px 24px;
  }

  .survey-tabs {
    padding-inline: 16px;
  }

  .survey-card-open {
    gap: 12px;
    padding: 14px 14px 0;
  }

  .survey-card-footer {
    padding-left: 14px;
  }

  .survey-card-count {
    min-width: 44px;
  }

  .survey-card-count strong {
    font-size: 20px;
  }

  .survey-card .survey-chevron {
    display: none;
  }

  .survey-editor-heading {
    gap: 12px;
  }

  .survey-editor-heading > .survey-actions {
    width: 100%;
  }

  .survey-panel-heading {
    padding: 10px 14px;
    gap: 8px;
  }

  .survey-basic-fields, .survey-question-body, .survey-response-body {
    padding: 14px;
  }

  .survey-field {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
    line-height: 1.55;
  }

  .survey-reward small {
    grid-column: 1;
  }

  .survey-editor input:not([type=checkbox]), .survey-editor textarea {
    min-height: 36px;
  }

  .survey-question-editor .survey-actions {
    gap: 6px;
  }

  .survey-response header {
    flex-wrap: wrap;
  }

  .survey-response-columns {
    display: none;
  }

  .survey-response-row {
    gap: 8px;
    padding: 0 12px;
  }

  .survey-response-row:first-child {
    border-top: 0;
  }

  .survey-star {
    margin-right: 0;
  }

  .survey-response-open {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px 10px;
  }

  .survey-response-open time {
    grid-column: 1;
    grid-row: 2;
  }

  .survey-response-view {
    grid-column: 2;
    grid-row: 1 / 3;
    font-size: 11px;
  }

  .survey-answer-dialog {
    width: calc(100vw - 24px);
    max-height: calc(100dvh - 32px);
  }

  .survey-dialog-header, .survey-dialog-body {
    padding: 14px;
  }

  .survey-dialog-footer {
    padding: 12px;
    gap: 8px;
    overflow-x: auto;
  }

  .survey-response-pagination {
    gap: 8px;
  }

  .survey-answer-navigation {
    gap: 8px;
  }

  .survey-page-arrow {
    display: block;
  }

  .survey-page-label {
    display: none;
  }

  .survey-answer-navigation .ghost-button, .survey-response-pagination > .ghost-button {
    display: grid;
    place-items: center;
    flex: 0 0 30px;
    width: 30px;
    padding: 0;
  }

  .survey-page-jump, .survey-page-jump label {
    gap: 6px;
  }

  .workspace .survey-page-jump input {
    width: 44px;
  }

  .survey-page-jump .ghost-button {
    padding-inline: 8px;
  }
}
</style>
