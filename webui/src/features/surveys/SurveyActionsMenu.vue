<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

const props = defineProps({survey: {type: Object, required: true}, disabled: Boolean})
defineOptions({inheritAttrs: false})
const emit = defineEmits(['action'])
const trigger = ref(null)
const menu = ref(null)
const expanded = ref(false)
const position = ref({left: '0px', top: '0px'})
const actions = computed(() => [
  {id: 'copy', label: '复制为新问卷', path: 'M9 9h12v12H9zM5 15H3V3h12v2'},
  {
    id: 'link',
    label: '复制填写链接',
    disabled: props.survey.status === 'DRAFT',
    path: 'M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2'
  },
  {
    id: 'status',
    label: props.survey.status === 'PUBLISHED' ? '停止收集' : props.survey.status === 'CLOSED' ? '重新开放' : '发布问卷',
    path: props.survey.status === 'PUBLISHED' ? 'M8 5v14M16 5v14' : 'm8 4 12 8-12 8Z'
  },
  {id: 'delete', label: '删除问卷', path: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7'}
])

function close() {
  if (menu.value?.matches(':popover-open')) menu.value.hidePopover()
}

function toggle() {
  if (props.disabled) return
  if (menu.value.matches(':popover-open')) {
    close();
    return
  }
  const rect = trigger.value.getBoundingClientRect()
  menu.value.showPopover()
  const {width, height} = menu.value.getBoundingClientRect()
  const top = rect.bottom + height + 12 <= window.innerHeight ? rect.bottom + 6 : rect.top - height - 6
  position.value = {
    left: `${Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8))}px`,
    top: `${Math.max(8, top)}px`
  }
  menu.value.querySelector('button:not(:disabled)')?.focus({preventScroll: true})
}

function choose(action) {
  close();
  emit('action', action.id)
}

function scroll(event) {
  if (!menu.value?.contains(event.target)) close()
}

function navigate(event) {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const buttons = [...menu.value.querySelectorAll('button:not(:disabled)')]
  const index = buttons.indexOf(document.activeElement)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
  buttons[next]?.focus()
}

onMounted(() => {
  window.addEventListener('scroll', scroll, true);
  window.addEventListener('resize', close)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', scroll, true);
  window.removeEventListener('resize', close)
})
</script>

<template>
  <button ref="trigger" v-bind="$attrs" class="survey-more" :disabled="disabled"
          :aria-label="`${survey.title}的更多操作`" aria-haspopup="menu" :aria-expanded="expanded" @click="toggle">
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="5" cy="12" r="1.7"/>
      <circle cx="12" cy="12" r="1.7"/>
      <circle cx="19" cy="12" r="1.7"/>
    </svg>
  </button>
  <div ref="menu" popover="auto" class="survey-action-menu" :style="position" role="menu"
       :aria-label="`${survey.title}的操作`" @toggle="expanded = $event.newState === 'open'" @keydown="navigate">
    <button v-for="action in actions" :key="action.id" role="menuitem" :class="{danger: action.id === 'delete'}"
            :disabled="disabled || action.disabled" @click="choose(action)">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
           stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path :d="action.path"/>
      </svg>
      {{ action.label }}
    </button>
  </div>
</template>

<style scoped>
.survey-more {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 30px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}

.survey-more:hover, .survey-more[aria-expanded=true] {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.survey-action-menu {
  position: fixed;
  inset: auto;
  margin: 0;
  padding: 5px;
  width: 176px;
  max-width: calc(100vw - 16px);
  border: 1px solid var(--color-border);
  border-radius: 9px;
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: 0 8px 28px rgb(24 36 52 / 14%);
}

.survey-action-menu button {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 34px;
  padding: 7px 9px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: inherit;
  text-align: left;
  font-size: 12px;
  cursor: pointer;
}

.survey-action-menu button:hover:not(:disabled), .survey-action-menu button:focus-visible {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
  outline: none;
}

.survey-action-menu button.danger {
  color: var(--color-danger);
}

.survey-action-menu button.danger:hover {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.survey-action-menu button:disabled {
  opacity: .45;
  cursor: default;
}

.survey-action-menu svg {
  flex-shrink: 0;
}
</style>
