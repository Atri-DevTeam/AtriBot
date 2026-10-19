<template>
  <button ref="trigger" type="button" class="account-status-trigger" :class="{ 'is-open': open }"
          role="combobox" aria-label="账号状态" aria-haspopup="listbox" :aria-expanded="open"
          :aria-controls="open ? menuId : undefined"
          :aria-activedescendant="open ? `${menuId}-${highlighted}` : undefined"
          :disabled="disabled" @click="toggle" @keydown="onKeydown">
    <span class="account-status-dot" :class="`is-${modelValue.toLowerCase()}`" aria-hidden="true"/>
    <span class="account-status-value">{{ selected.label }}</span>
    <svg class="account-status-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="m7 10 5 5 5-5"/>
    </svg>
  </button>

  <Teleport to="body">
    <Transition name="account-status-menu">
      <div v-if="open" :id="menuId" ref="menu" class="account-status-menu" :style="menuStyle"
           role="listbox" aria-label="账号状态" :data-placement="placement">
        <div class="account-status-menu-heading" aria-hidden="true">账号状态</div>
        <div v-for="(option, index) in options" :id="`${menuId}-${index}`" :key="option.key"
             class="account-status-option" :class="{ 'is-highlighted': highlighted === index, 'is-selected': modelValue === option.key }"
             role="option" :aria-selected="modelValue === option.key"
             @pointermove="highlighted = index" @pointerdown.prevent @click.stop="choose(option.key)">
          <span class="account-status-dot" :class="`is-${option.key.toLowerCase()}`" aria-hidden="true"/>
          <span class="account-status-option-content">
            <span class="account-status-option-label">{{ option.label }}</span>
            <span class="account-status-option-note">{{ descriptions[option.key] }}</span>
          </span>
          <svg v-if="modelValue === option.key" class="account-status-check" width="16" height="16"
               viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m5 12 4 4L19 6"/>
          </svg>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, ref, useId, watch} from 'vue'
import {accountStatusOptions} from '../lib/userProfile.js'

const props = defineProps({
  modelValue: {type: String, required: true},
  disabled: {type: Boolean, default: false}
})
const emit = defineEmits(['update:modelValue'])
const menuId = `account-status-${useId()}`
const trigger = ref(null)
const menu = ref(null)
const open = ref(false)
const highlighted = ref(0)
const placement = ref('bottom')
const menuStyle = ref({})
const options = computed(() => accountStatusOptions.filter(option => option.key !== 'DELETED' || props.modelValue === 'DELETED'))
const selected = computed(() => accountStatusOptions.find(option => option.key === props.modelValue) || {label: '未知状态'})
const descriptions = {
  ACTIVE: '允许正常使用机器人',
  SUSPENDED: '限制指令使用',
  BANNED: '静默忽略消息和交互',
  DELETED: '账号已注销'
}

async function toggle() {
  if (open.value) {
    open.value = false
    return
  }
  if (props.disabled) return
  const rect = trigger.value.getBoundingClientRect()
  const width = Math.min(244, window.innerWidth - 24)
  const below = window.innerHeight - rect.bottom - 18
  const above = rect.top - 18
  placement.value = below < 230 && above > below ? 'top' : 'bottom'
  menuStyle.value = {
    width: `${width}px`,
    left: `${Math.max(12, Math.min(rect.right - width, window.innerWidth - width - 12))}px`,
    maxHeight: `${Math.max(0, placement.value === 'top' ? above : below)}px`,
    ...(placement.value === 'top'
        ? {bottom: `${window.innerHeight - rect.top + 6}px`}
        : {top: `${rect.bottom + 6}px`})
  }
  highlighted.value = Math.max(0, options.value.findIndex(option => option.key === props.modelValue))
  open.value = true
  await nextTick()
  scrollToHighlighted()
}

function choose(value) {
  if (props.disabled) return
  emit('update:modelValue', value)
  open.value = false
}

function scrollToHighlighted() {
  menu.value?.querySelector('.is-highlighted')?.scrollIntoView({block: 'nearest'})
}

async function onKeydown(event) {
  if (event.key === 'Tab' || event.key === 'Escape') {
    if (open.value && event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
    }
    open.value = false
    return
  }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', ' '].includes(event.key)) return
  event.preventDefault()
  if (!open.value) {
    await toggle()
    return
  }
  if (event.key === 'Enter' || event.key === ' ') {
    choose(options.value[highlighted.value].key)
    return
  }
  if (event.key === 'Home') highlighted.value = 0
  else if (event.key === 'End') highlighted.value = options.value.length - 1
  else highlighted.value = (highlighted.value + (event.key === 'ArrowDown' ? 1 : -1) + options.value.length) % options.value.length
  await nextTick()
  scrollToHighlighted()
}

function onOutsidePointer(event) {
  if (!trigger.value?.contains(event.target) && !menu.value?.contains(event.target)) open.value = false
}

function onScroll(event) {
  if (!menu.value?.contains(event.target)) open.value = false
}

function close() {
  open.value = false
}

watch(open, value => {
  if (value) {
    document.addEventListener('pointerdown', onOutsidePointer, true)
    window.addEventListener('scroll', onScroll, true)
    window.addEventListener('resize', close)
  } else {
    removeListeners()
  }
})
watch(() => props.disabled, value => {
  if (value) close()
})

function removeListeners() {
  document.removeEventListener('pointerdown', onOutsidePointer, true)
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', close)
}

onBeforeUnmount(removeListeners)
</script>

<style scoped>
.account-status-trigger {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  min-width: 108px;
  min-height: 34px;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
  color: var(--color-text);
  font: inherit;
  font-size: 13px;
  line-height: 18px;
  text-align: left;
  cursor: pointer;
  transition: border-color 150ms ease, background 150ms ease, box-shadow 150ms ease;
}

.account-status-trigger:hover:not(:disabled),
.account-status-trigger.is-open {
  border-color: var(--color-border-strong);
  background: var(--color-surface-alt);
  box-shadow: var(--shadow-sm);
}

.account-status-trigger:focus-visible {
  outline: none;
  border-color: var(--color-accent);
  box-shadow: var(--focus-ring);
}

.account-status-trigger:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.account-status-value { flex: 1; }
.account-status-chevron { flex-shrink: 0; color: var(--color-text-muted); transition: transform 180ms ease; }
.is-open .account-status-chevron { transform: rotate(180deg); }
.account-status-dot { flex: 0 0 7px; width: 7px; height: 7px; border-radius: 50%; background: var(--color-text-subtle); }
.account-status-dot.is-active { background: var(--color-success); }
.account-status-dot.is-suspended { background: var(--color-danger); }
.account-status-dot.is-banned { background: var(--color-text-secondary); }

.account-status-menu {
  position: fixed;
  z-index: 1200;
  box-sizing: border-box;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  box-shadow: 0 8px 28px rgba(16, 20, 28, .12), 0 2px 6px rgba(16, 20, 28, .04);
  font-family: var(--font-sans);
  transform-origin: top right;
}

.account-status-menu[data-placement="top"] { transform-origin: bottom right; }
.account-status-menu-heading { padding: 4px 10px 7px; color: var(--color-text-subtle); font-size: 11px; line-height: 18px; }

.account-status-option {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 54px;
  box-sizing: border-box;
  padding: 9px 10px;
  border-radius: 7px;
  color: var(--color-text);
  cursor: pointer;
  user-select: none;
  transition: background 120ms ease;
}

.account-status-option.is-selected { background: var(--color-surface-hover); }
.account-status-option.is-highlighted { background: var(--color-surface-sunken); }
.account-status-option-content { display: grid; flex: 1; gap: 3px; min-width: 0; }
.account-status-option-label { font-size: 13px; font-weight: 500; line-height: 18px; }
.account-status-option-note { color: var(--color-text-muted); font-size: 11px; line-height: 16px; }
.account-status-check { flex-shrink: 0; color: var(--color-accent); }
.account-status-menu-enter-active, .account-status-menu-leave-active { transition: opacity 140ms ease, transform 140ms ease; }
.account-status-menu-enter-from, .account-status-menu-leave-to { opacity: 0; transform: translateY(-3px) scale(.98); }
.account-status-menu[data-placement="top"].account-status-menu-enter-from,
.account-status-menu[data-placement="top"].account-status-menu-leave-to { transform: translateY(3px) scale(.98); }

@media (prefers-reduced-motion: reduce) {
  .account-status-trigger, .account-status-chevron, .account-status-option,
  .account-status-menu-enter-active, .account-status-menu-leave-active { transition: none; }
}
</style>
