<script setup lang="ts">
import {computed, onBeforeUnmount, reactive, ref, watch} from 'vue'
import Icon from './Icon.vue'
import type {SurveyAnswer, SurveyDetail, SurveyPage, SurveyQuestion, SurveyResult} from '../surveys'

const props = defineProps<{
  target: string; active: boolean
  request: <T>(path: string) => Promise<T | null>
  post: <T>(path: string, body: unknown) => Promise<T | null>
}>()
const emit = defineEmits<{ open: [target: string | null] }>()
const page = ref<SurveyPage>({items: [], total: 0})
const detail = ref<SurveyDetail | null>(null)
const answers = reactive<Record<string, string[]>>({})
const offset = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const fillingAgain = ref(false)
let requestId = crypto.randomUUID()
const confirmDialog = ref<HTMLDialogElement | null>(null)
let generation = 0
const readonly = computed(() => (!!detail.value?.submission && !fillingAgain.value) || detail.value?.survey.status !== 'PUBLISHED')
const completed = computed(() => detail.value?.survey.questions.filter(q => (answers[q.id] || []).some(v => v.trim())).length || 0)
const statusName = (status: string) => status === 'CLOSED' ? '已结束' : '收集中'

async function load() {
  confirmDialog.value?.close()
  const current = ++generation
  loading.value = true
  fillingAgain.value = false;
  requestId = crypto.randomUUID()
  error.value = ''
  detail.value = null
  for (const key of Object.keys(answers)) delete answers[key]
  try {
    if (props.target === 'list') {
      const result = await props.request<SurveyResult<SurveyPage>>(`surveys?offset=${offset.value}`)
      if (current === generation && result) page.value = result.data
    } else {
      const result = await props.request<SurveyResult<SurveyDetail>>(`surveys/${props.target}`)
      if (current === generation && result) {
        detail.value = result.data
        for (const q of result.data.survey.questions) answers[q.id] = [...(result.data.submission?.answers[q.id] || [])]
      }
    }
  } catch (e) {
    if (current === generation) error.value = e instanceof Error ? e.message : '问卷读取失败'
  } finally {
    if (current === generation) loading.value = false
  }
}

watch(() => [props.active, props.target], () => {
  confirmDialog.value?.close()
  generation++
  if (props.active) {
    offset.value = 0;
    void load()
  }
}, {immediate: true})
onBeforeUnmount(() => {
  generation++
})

function toggle(q: SurveyQuestion, id: string, checked: boolean) {
  answers[q.id] = checked ? [...(answers[q.id] || []), id] : (answers[q.id] || []).filter(value => value !== id)
}

function confirmSubmit() {
  if (!detail.value || saving.value || readonly.value) return
  const missing = detail.value.survey.questions.find(q => q.required && !(answers[q.id] || []).some(value => value.trim()))
  if (missing) {
    error.value = `请填写：${missing.title}`;
    return
  }
  error.value = ''
  confirmDialog.value?.showModal()
}

async function submit() {
  if (!detail.value || saving.value || readonly.value || !confirmDialog.value?.open) return
  confirmDialog.value.close()
  saving.value = true
  error.value = ''
  const current = generation
  try {
    const result = await props.post<SurveyResult<SurveyAnswer>>(`surveys/${detail.value.survey.id}/responses`, {
      revision: detail.value.survey.revision, answers, requestId
    })
    if (current === generation && detail.value && result) {
      detail.value.submission = result.data;
      fillingAgain.value = false
    }
  } catch (e) {
    if (current === generation) error.value = e instanceof Error ? e.message : '提交失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

function fillAgain() {
  if (!detail.value?.survey.repeatable || detail.value.survey.status !== 'PUBLISHED' || saving.value) return
  for (const key of Object.keys(answers)) delete answers[key]
  fillingAgain.value = true;
  requestId = crypto.randomUUID();
  error.value = ''
}

function nextPage(delta: number) {
  offset.value += delta;
  void load()
}
</script>

<template>
  <section class="survey-panel" aria-label="问卷填写">
    <header class="survey-heading">
      <button class="survey-back" @click="emit('open', target === 'list' ? null : 'list')"><span
          aria-hidden="true">‹</span>{{ target === 'list' ? '关于' : '问卷列表' }}
      </button>
      <button class="survey-refresh" aria-label="刷新问卷" :disabled="loading || saving" @click="load">
        <Icon name="refresh"/>
      </button>
    </header>
    <div v-if="loading" class="survey-empty" role="status">正在读取问卷…</div>
    <template v-else-if="target === 'list'">
      <div class="survey-intro"><span class="survey-eyebrow">参与调查</span>
        <h2>问卷填写</h2>
        <p>查看开放的问卷与已提交的答卷</p></div>
      <div v-if="!page.items.length && !error" class="survey-empty">暂无问卷</div>
      <button v-for="item in page.items" :key="item.id" class="survey-list-item" @click="emit('open', String(item.id))">
        <div>
          <div class="survey-tags">
            <span v-if="item.pinned" class="survey-tag survey-pinned">置顶</span>
            <span class="survey-tag" :class="{muted: item.status === 'CLOSED'}">{{
                item.submitted && !item.repeatable ? '已提交' : statusName(item.status)
              }}</span>
            <span v-if="item.repeatable" class="survey-tag survey-pinned">可重复填写</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description || '查看问卷' }}</p>
        </div>
        <span aria-hidden="true">›</span>
      </button>
      <nav v-if="page.total > 50" class="survey-pagination" aria-label="问卷分页">
        <button :disabled="offset === 0" @click="nextPage(-50)">上一页</button>
        <span>{{ offset / 50 + 1 }}</span>
        <button :disabled="offset + 50 >= page.total" @click="nextPage(50)">下一页</button>
      </nav>
    </template>
    <template v-else-if="detail">
      <div class="survey-intro">
        <span class="survey-tag" :class="{muted: detail.survey.status === 'CLOSED'}">{{
            statusName(detail.survey.status)
          }}</span>
        <h2>{{ detail.survey.title }}</h2>
        <p class="survey-description">{{ detail.survey.description }}</p>
        <p v-if="!detail.submission && !readonly && detail.survey.rewardGold > 0">提交奖励：{{
            detail.survey.rewardGold
          }} 金粒</p>
      </div>
      <div v-if="detail.submission && !fillingAgain" class="survey-notice" role="status">
        <strong>已提交，感谢参与</strong><span v-if="detail.submission.rewardGold > 0">已发放 {{
          detail.submission.rewardGold
        }} 金粒</span><span>{{
          new Date(detail.submission.submittedAt).toLocaleString('zh-CN', {hour12: false})
        }}</span>
        <button v-if="detail.survey.repeatable && detail.survey.status === 'PUBLISHED'" class="survey-again"
                @click="fillAgain">再次填写
        </button>
      </div>
      <p v-else-if="readonly" class="survey-notice">本问卷已停止收集</p>
      <form @submit.prevent="confirmSubmit">
        <fieldset v-for="(question, index) in detail.survey.questions" :key="question.id" :disabled="readonly || saving"
                  class="survey-question">
          <legend><span class="survey-number">{{ String(index + 1).padStart(2, '0') }}</span>{{ question.title }}<span
              v-if="question.required" class="survey-required">*</span></legend>
          <small class="survey-kind">{{
              question.type === 'MULTIPLE' ? '多选' : question.type === 'SINGLE' ? '单选' : '文本'
            }} · {{ question.required ? '必填' : '选填' }}</small>
          <textarea v-if="question.type === 'TEXT'" :aria-label="question.title"
                    :value="answers[question.id]?.[0] || ''" maxlength="2000" rows="4" placeholder="填写你的回答"
                    @input="answers[question.id] = [($event.target as HTMLTextAreaElement).value]"/>
          <div v-else class="survey-options">
            <label v-for="option in question.options" :key="option.id" class="survey-option"
                   :class="{selected: answers[question.id]?.includes(option.id)}">
              <input :type="question.type === 'SINGLE' ? 'radio' : 'checkbox'" :name="question.id"
                     :checked="answers[question.id]?.includes(option.id)"
                     @change="question.type === 'SINGLE' ? answers[question.id] = [option.id] : toggle(question, option.id, ($event.target as HTMLInputElement).checked)"/>
              <span>{{ option.label }}</span>
            </label>
          </div>
        </fieldset>
        <footer v-if="!readonly" class="survey-submit"><span>{{ completed }} / {{
            detail.survey.questions.length
          }} 题已填写</span>
          <button type="submit" :disabled="saving">{{ saving ? '提交中…' : '提交问卷' }}</button>
        </footer>
      </form>
    </template>
    <p v-if="error" class="survey-error" role="alert">{{ error }}</p>
    <dialog ref="confirmDialog" class="survey-confirm" aria-labelledby="survey-confirm-title"
            @click.self="confirmDialog?.close()">
      <h3 id="survey-confirm-title">确认提交问卷？</h3>
      <p>{{
          detail?.survey.repeatable ? '本次提交将保存为一份新答卷，请确认填写内容' : '提交后无法修改答卷，请确认填写内容'
        }}</p>
      <div>
        <button type="button" @click="confirmDialog?.close()">继续填写</button>
        <button type="button" class="confirm-primary" :disabled="saving" @click="submit">确认提交</button>
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.survey-panel {
  max-width: 760px;
  margin: 0 auto;
  color: var(--ui-text);
}

.survey-confirm {
  width: min(360px, calc(100vw - 32px));
  margin: auto;
  padding: 24px;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  background: var(--ui-panel);
  color: var(--ui-text);
  box-shadow: 0 12px 48px #0003;
}

.survey-confirm::backdrop {
  background: #11182766;
}

.survey-confirm h3 {
  margin: 0 0 12px;
  font-size: 17px;
  font-weight: 500;
}

.survey-confirm p {
  margin: 0;
  font-size: 13px;
  color: var(--ui-muted);
  line-height: 1.8;
}

.survey-confirm > div {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.survey-confirm button {
  padding: 9px 14px;
  font-size: 13px;
  border: 1px solid var(--ui-border);
  border-radius: 5px;
  background: var(--ui-input);
  color: inherit;
}

.survey-confirm .confirm-primary {
  border-color: var(--ui-accent);
  background: var(--ui-accent);
  color: var(--ui-on-accent);
}

.survey-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 20px;
}

.survey-back, .survey-refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  color: var(--ui-muted);
  background: none;
  border: 0;
  font-size: 13px;
}

.survey-back > span {
  font-size: 24px;
  line-height: 1;
}

.survey-intro {
  padding: 14px 0 28px;
  border-bottom: 1px solid var(--ui-border);
}

.survey-eyebrow {
  color: var(--ui-subtle);
  font-size: 11px;
  letter-spacing: .12em;
}

.survey-intro h2 {
  margin: 12px 0;
  font-size: 25px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.survey-intro p {
  font-size: 13px;
  color: var(--ui-muted);
  line-height: 1.8;
}

.survey-description {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.survey-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.survey-tag {
  display: inline-flex;
  flex-shrink: 0;
  white-space: nowrap;
  border: 1px solid var(--ui-border);
  color: var(--ui-accent);
  padding: 3px 7px;
  font-size: 11px;
  border-radius: 4px;
}

.survey-pinned {
  background: var(--ui-accent-soft);
}

.survey-tag.muted {
  color: var(--ui-muted);
}

.survey-list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 22px 0;
  text-align: left;
  background: none;
  color: inherit;
  border: 0;
  border-bottom: 1px solid var(--ui-border);
}

.survey-list-item div {
  min-width: 0;
}

.survey-list-item h3 {
  margin: 12px 0 8px;
  font-size: 17px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.survey-list-item p {
  margin: 0;
  color: var(--ui-muted);
  font-size: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.survey-list-item > span {
  color: var(--ui-muted);
  font-size: 24px;
}

.survey-notice {
  display: grid;
  gap: 8px;
  margin: 24px 0 0;
  padding: 16px;
  border-left: 2px solid var(--ui-accent);
  background: var(--ui-panel);
  font-size: 13px;
}

.survey-again {
  justify-self: start;
  padding: 7px 12px;
  border: 1px solid var(--ui-border);
  border-radius: 5px;
  background: var(--ui-input);
  color: var(--ui-accent);
  font-size: 13px;
}

.survey-notice span {
  color: var(--ui-muted);
  font-size: 12px;
}

.survey-question {
  margin: 30px 0 0;
  padding: 0 0 28px;
  min-width: 0;
  border: 0;
  border-bottom: 1px solid var(--ui-border);
}

.survey-question legend {
  padding: 0;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.survey-number {
  margin-right: 12px;
  font-size: 12px;
  color: var(--ui-muted);
  font-variant-numeric: tabular-nums;
}

.survey-required {
  margin-left: 7px;
  color: var(--ui-danger);
}

.survey-kind {
  display: block;
  margin: 8px 0 16px;
  color: var(--ui-subtle);
  font-size: 11px;
}

.survey-options {
  display: grid;
  gap: 8px;
}

.survey-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border: 1px solid var(--ui-border);
  background: var(--ui-input);
  border-radius: 5px;
  font-size: 14px;
  cursor: pointer;
  overflow-wrap: anywhere;
}

.survey-option.selected {
  border-color: var(--ui-focus);
  background: var(--ui-accent-soft);
}

.survey-option input {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  margin: 0;
  accent-color: var(--ui-accent);
}

.survey-question textarea {
  width: 100%;
  padding: 12px;
  background: var(--ui-input);
  border: 1px solid var(--ui-border);
  border-radius: 5px;
  color: inherit;
  font: inherit;
  font-size: 16px;
  resize: vertical;
}

.survey-question textarea:focus {
  outline: 1px solid var(--ui-focus);
}

.survey-question:disabled input, .survey-question:disabled textarea {
  opacity: 1;
}

.survey-submit, .survey-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: 24px 0;
}

.survey-submit span {
  color: var(--ui-muted);
  font-size: 12px;
}

.survey-submit button {
  background: var(--ui-accent);
  color: var(--ui-on-accent);
  border: 0;
  border-radius: 5px;
  padding: 12px 24px;
  font-size: 14px;
}

.survey-pagination button {
  padding: 8px 12px;
  border: 1px solid var(--ui-border);
  background: var(--ui-panel);
  color: inherit;
  border-radius: 4px;
}

.survey-empty {
  padding: 50px 0;
  text-align: center;
  color: var(--ui-muted);
  font-size: 13px;
}

.survey-error {
  color: var(--ui-danger);
  font-size: 13px;
  line-height: 1.7;
}
</style>
