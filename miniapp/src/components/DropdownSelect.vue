<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch} from 'vue'
import Icon from './Icon.vue'

type Option = {value: string; label: string; detail?: string}
const props = defineProps<{modelValue: string; label: string; options: Option[]; disabled?: boolean}>()
const emit = defineEmits<{'update:modelValue': [value: string]}>()
const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
const list = ref<HTMLElement>()
const open = ref(false)
const upward = ref(false)
const focused = ref(0)
const id = useId()
const selected = computed(() => props.options.find(option => option.value === props.modelValue))

async function expand() {
  if (props.disabled) return
  focused.value = Math.max(0, props.options.findIndex(option => option.value === props.modelValue))
  const bounds = trigger.value?.getBoundingClientRect()
  const menuHeight = Math.min(props.options.length * 58 + 12, 272)
  upward.value = !!bounds && window.innerHeight - bounds.bottom < menuHeight && bounds.top > window.innerHeight - bounds.bottom
  open.value = true
  await reveal()
}
async function reveal() {
  await nextTick()
  list.value?.querySelector(`[data-index="${focused.value}"]`)?.scrollIntoView({block: 'nearest'})
}
function choose(index: number) {
  const option = props.options[index]
  if (!option) return
  emit('update:modelValue', option.value)
  open.value = false
  trigger.value?.focus({preventScroll: true})
}
function keydown(event: KeyboardEvent) {
  if (props.disabled) return
  if (event.key === 'Tab') { open.value = false; return }
  if (event.key === 'Escape') { if (open.value) event.stopPropagation(); open.value = false; return }
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', ' '].includes(event.key)) return
  event.preventDefault()
  if (!open.value) { void expand(); return }
  if (event.key === 'Enter' || event.key === ' ') { choose(focused.value); return }
  if (event.key === 'Home') focused.value = 0
  else if (event.key === 'End') focused.value = props.options.length - 1
  else focused.value = (focused.value + (event.key === 'ArrowDown' ? 1 : -1) + props.options.length) % props.options.length
  void reveal()
}
function outside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}
watch(() => props.disabled, disabled => { if (disabled) open.value = false })
onMounted(() => document.addEventListener('pointerdown', outside, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', outside, true))
</script>

<template>
  <div ref="root" class="dropdown-select" :class="{open, upward}" @keydown="keydown">
    <button ref="trigger" type="button" class="dropdown-trigger" role="combobox" :aria-label="label"
            aria-haspopup="listbox" :aria-expanded="open" :aria-controls="`${id}-options`"
            :aria-activedescendant="open ? `${id}-option-${focused}` : undefined" :disabled="disabled"
            @click="open ? open = false : expand()">
      <span><strong>{{ selected?.label || '请选择' }}</strong><small v-if="selected?.detail">{{ selected.detail }}</small></span>
      <Icon name="chevron"/>
    </button>
    <div v-if="open" :id="`${id}-options`" ref="list" class="dropdown-options" role="listbox" :aria-label="label">
      <div v-for="(option, index) in options" :id="`${id}-option-${index}`" :key="option.value" :data-index="index"
           class="dropdown-option" :class="{selected: option.value === modelValue, focused: focused === index}"
           role="option" :aria-selected="option.value === modelValue" @pointermove="focused = index"
           @mousedown.prevent @click="choose(index)">
        <span><strong>{{ option.label }}</strong><small v-if="option.detail">{{ option.detail }}</small></span>
        <Icon v-if="option.value === modelValue" name="check"/>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dropdown-select { position: relative; min-width: 0; }
.dropdown-select.open { z-index: 10; }
.dropdown-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 44px;
  padding: 11px 12px;
  text-align: left;
  font: inherit;
  color: var(--price-text, var(--ui-text));
  background: var(--ui-input);
  border: 1px solid var(--price-border, var(--ui-border));
  cursor: pointer;
  border-radius: 6px;
  font-size: 13px;
}
.dropdown-trigger > span, .dropdown-option > span { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.dropdown-trigger strong, .dropdown-option strong { font-weight: 500; overflow-wrap: anywhere; line-height: 1.5; }
.dropdown-trigger small, .dropdown-option small { font-size: 10px; line-height: 1.5; color: var(--price-muted, var(--ui-secondary)); }
.dropdown-trigger > svg { width: 13px; height: 13px; color: var(--price-muted, var(--ui-secondary)); transform: rotate(90deg); transition: transform .18s; }
.open .dropdown-trigger > svg { transform: rotate(-90deg); }
.dropdown-trigger:focus-visible, .open .dropdown-trigger { outline: none; border-color: var(--price-focus, var(--ui-focus)); box-shadow: 0 0 0 3px var(--ui-focus-ring); }
.dropdown-trigger:disabled { opacity: .5; cursor: default; }
.dropdown-options {
  position: absolute;
  z-index: 1;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: 272px;
  padding: 5px;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--price-border, var(--ui-border));
  background: var(--ui-raised);
  box-shadow: var(--ui-shadow);
  border-radius: 8px;
}
.upward .dropdown-options { top: auto; bottom: calc(100% + 6px); }
.dropdown-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 11px; color: var(--price-text, var(--ui-text)); font-size: 12px; cursor: pointer; border-radius: 4px; }
.dropdown-option.focused { background: var(--ui-hover); }
.dropdown-option.selected { color: var(--price-accent-hover, var(--ui-text)); background: var(--ui-hover); }
.dropdown-option > svg { width: 15px; height: 15px; }
@media (prefers-reduced-motion: reduce) { .dropdown-trigger > svg { transition: none; } }
</style>
