<template>
  <div class="config-preview-phone">
    <div class="config-preview-phone-body">
      <div class="config-preview-phone-body-info mp-phone-info-menu" @keydown.esc="closeMenu">
        <div class="mp-phone-chat-head">
          <svg width="12" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 4-8 8 8 8"/></svg>
          <div class="mp-phone-chat-title">
            <div><span>{{ botName }}</span><img :src="botIcon" width="12" height="12" alt="机器人"/></div>
            <small><i/>在线</small>
          </div>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
        </div>
        <div class="mp-phone-chat-content">
          <span class="mp-phone-chat-time">09:45</span>
          <div class="mp-phone-chat-bubble">你好喵～<br/>点击下方菜单开始使用吧</div>
        </div>
        <div class="mp-menu-dock">
          <div v-if="items.length" class="mp-menu-bar" @wheel.prevent="onMenuBarWheel">
            <button v-for="(item, idx) in items" :key="idx" type="button" class="mp-menu-btn"
                    :class="{ 'mp-menu-btn--on': item.type === 'switch' && switchEnabled(item) }"
                    :aria-expanded="item.type === 'menu' ? activeMenu === item : undefined"
                    :aria-pressed="item.type === 'switch' ? switchEnabled(item) : undefined"
                    :aria-controls="item.type === 'menu' && activeMenu === item ? sheetId : undefined"
                    @click="activateItem(item)">
              <svg v-if="item.type === 'send_message'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 19 19 5M6 5h13v13"/></svg>
              <svg v-else-if="item.type === 'link'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
              <svg v-else-if="item.type === 'menu'" class="mp-menu-list-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="6" height="6" rx="2"/><rect x="2" y="10" width="6" height="6" rx="2"/><circle cx="5" cy="21" r="2"/><path d="M11 3h11M11 7h11M11 11h11M11 15h11M11 19h11M11 23h11"/></svg>
              <span class="mp-menu-btn-label">{{ item.name || '未命名' }}</span>
              <span v-if="item.type === 'switch'" class="mp-switch-dot" aria-hidden="true"/>
            </button>
          </div>
          <div v-else class="mp-menu-bar mp-menu-bar--empty"><span class="mp-phone-hint">暂无菜单</span></div>
          <div class="mp-menu-composer" aria-hidden="true">
            <div class="mp-menu-input-row"><div class="mp-menu-input"/><span class="mp-menu-send">发送</span></div>
            <div class="mp-menu-tools">
              <svg viewBox="0 0 24 24"><rect x="9" y="2" width="6" height="13" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3"/></svg>
              <svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8" cy="9" r="2"/><path d="m5 19 8-7 8 6"/></svg>
              <svg viewBox="0 0 24 24"><path d="M8 5 10 2h4l2 3h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z"/><circle cx="12" cy="12" r="4"/></svg>
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M8 9h.01M16 9h.01M7 14a5 5 0 0 0 10 0"/></svg>
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M6 12h12M12 6v12"/></svg>
            </div>
          </div>
        </div>
        <Transition name="mp-sub" :duration="{enter: 300, leave: 220}">
          <div v-if="activeMenu" class="mp-sub-overlay">
            <button type="button" class="mp-sub-backdrop" tabindex="-1" aria-label="关闭子菜单" @click="closeMenu"/>
            <section :id="sheetId" class="mp-sub-sheet" :aria-label="activeMenu.name || '子菜单'">
              <button type="button" class="mp-sub-sheet-handle" aria-label="关闭子菜单" @click="closeMenu"><span/></button>
              <div class="mp-sub-sheet-title"><svg class="mp-menu-list-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="6" height="6" rx="2"/><rect x="2" y="10" width="6" height="6" rx="2"/><circle cx="5" cy="21" r="2"/><path d="M11 3h11M11 7h11M11 11h11M11 15h11M11 19h11M11 23h11"/></svg><span>{{ activeMenu.name || '未命名' }}</span></div>
              <div class="mp-sub-sheet-items">
                <button v-for="(sub, idx) in activeMenu.subMenuItems" :key="idx" type="button" class="mp-sub-item" @click="closeMenu">{{ sub.name || '子项' }}</button>
                <div v-if="!activeMenu.subMenuItems?.length" class="mp-sub-empty">暂无子菜单项</div>
              </div>
            </section>
          </div>
        </Transition>
      </div>
    </div>
    <div class="config-preview-phone-text"><span>手机预览</span></div>
  </div>
</template>

<script setup>
import {computed, reactive, ref, useId, watch} from 'vue'
import botIcon from '../../../assets/bot-blue.svg'

const props = defineProps({
  items: {type: Array, default: () => []},
  botName: {type: String, default: 'AtriBot'}
})
const sheetId = useId()
const selectedMenu = ref(null)
const activeMenu = computed(() => props.items.includes(selectedMenu.value) && selectedMenu.value?.type === 'menu' ? selectedMenu.value : null)
const switchStates = reactive(new Map())

watch(() => props.items.map(item => item.defaultOn), () => switchStates.clear())
watch(activeMenu, item => { if (!item) closeMenu() })

function closeMenu() {
  selectedMenu.value = null
}

function switchEnabled(item) {
  return switchStates.get(item) ?? !!item.defaultOn
}

function activateItem(item) {
  if (item.type === 'menu') selectedMenu.value = activeMenu.value === item ? null : item
  else {
    closeMenu()
    if (item.type === 'switch') switchStates.set(item, !switchEnabled(item))
  }
}

function onMenuBarWheel(event) {
  const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX) ? event.deltaY : event.deltaX
  event.currentTarget.scrollLeft += delta
}
</script>
