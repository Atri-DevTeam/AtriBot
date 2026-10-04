<template>
  <div class="config-preview-phone">
    <div class="config-preview-phone-body">
      <div class="config-preview-phone-body-info panel-phone" :data-scope="scope">
        <div class="panel-chat-head" :class="{'panel-chat-head--channel': isChannel}">
          <svg class="panel-back" viewBox="0 0 24 24"><path d="m15 4-8 8 8 8"/></svg>
          <div class="panel-chat-title">
            <div><span>{{ chatTitle }}</span><img v-if="scope === 'c2c' || scope === 'dm'" :src="botIcon" alt="机器人"/></div>
            <small v-if="scope === 'c2c'"><i/>在线</small>
            <small v-else-if="scope === 'dm'">来自频道</small>
          </div>
          <svg class="panel-menu-icon" viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
        </div>

        <section class="panel-sheet" aria-label="指令面板预览">
          <div class="panel-handle" aria-hidden="true"><span/></div>
          <div v-if="scope === 'c2c'" class="panel-sheet-heading panel-sheet-heading--quick">
            <svg viewBox="0 0 24 24"><path d="m7 5-6 7 6 7m10-14 6 7-6 7M14 3l-4 18"/></svg>
            <span>快捷指令</span>
          </div>
          <div v-else class="panel-sheet-heading">
            <img class="panel-bot-avatar" :src="avatarSource" alt="" referrerpolicy="no-referrer" @error="avatarFailed = true"/>
            <span>{{ botName }}</span>
          </div>

          <div ref="listElement" class="panel-command-list" tabindex="0" role="region" aria-label="指令列表">
            <div v-if="!items.length" class="panel-empty">暂无指令</div>
            <div v-for="(item, index) in items" :key="index" class="panel-command-row" :class="{'panel-command-row--link': item.type === 'link'}">
              <span class="panel-command-name" :title="item.name">{{ item.type === 'link' ? (item.name || '链接') : commandName(item.name) }}</span>
              <svg v-if="item.type === 'link'" class="panel-link-arrow" viewBox="0 0 24 24"><path d="m9 5 7 7-7 7"/></svg>
              <span v-else class="panel-command-desc" :title="item.desc">{{ item.desc || '' }}</span>
            </div>
          </div>

          <div v-if="scope === 'group' || scope === 'channel'" class="panel-bot-tabs" aria-label="机器人选择栏">
            <span v-if="scope === 'channel'" class="panel-recent" title="最近使用">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 5v7h5"/></svg>
            </span>
            <span class="panel-selected-bot" :title="botName">
              <img class="panel-bot-avatar" :src="avatarSource" alt="当前机器人" referrerpolicy="no-referrer" @error="avatarFailed = true"/>
            </span>
          </div>
        </section>

        <div class="panel-composer" :class="{'panel-composer--channel': isChannel}" aria-hidden="true">
          <div class="panel-input-row">
            <span v-if="isChannel" class="panel-user-avatar"><svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 22v-3a8 8 0 0 1 16 0v3"/></svg></span>
            <div class="panel-input">/</div>
            <template v-if="isChannel">
              <svg v-for="tool in channelTools" :key="tool.name" class="panel-tool" viewBox="0 0 24 24"><path :d="tool.path"/></svg>
            </template>
            <span class="panel-send">发送</span>
          </div>
          <div v-if="!isChannel" class="panel-tools">
            <svg v-for="tool in chatTools" :key="tool.name" class="panel-tool" viewBox="0 0 24 24"><path :d="tool.path"/></svg>
          </div>
        </div>
      </div>
    </div>
    <div class="config-preview-phone-text">手机预览 · {{ scopeName }}</div>
  </div>
</template>

<script setup>
import {computed, ref, watch} from 'vue'
import botIcon from '../../../assets/bot-blue.svg'

const props = defineProps({
  scope: {type: String, default: 'c2c'},
  items: {type: Array, default: () => []},
  botName: {type: String, default: 'AtriBot'},
  botAvatar: {type: String, default: ''}
})
const isChannel = computed(() => props.scope === 'channel' || props.scope === 'dm')
const scopeName = computed(() => ({c2c: '私聊', dm: '频道私信', group: '群聊', channel: '文字子频道'}[props.scope] || props.scope))
const chatTitle = computed(() => props.scope === 'group' ? '群聊' : props.scope === 'channel' ? '文字子频道' : props.botName)
const avatarFailed = ref(false)
const avatarSource = computed(() => props.botAvatar && !avatarFailed.value ? props.botAvatar : botIcon)
const listElement = ref(null)
watch(() => props.botAvatar, () => { avatarFailed.value = false })
watch([() => props.scope, () => props.items], () => {
  if (listElement.value) listElement.value.scrollTop = 0
}, {flush: 'post'})

const icons = {
  microphone: 'M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0ZM5 10v2a7 7 0 0 0 14 0v-2M12 19v3',
  image: 'M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3ZM10 9a2 2 0 1 1-4 0 2 2 0 0 1 4 0ZM5 19l8-7 8 6',
  camera: 'M8 5l2-3h4l2 3h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  effect: 'M4 13a8 8 0 0 0 16 0M2 10l9 3-3 5-5-3ZM22 10l-9 3 3 5 5-3ZM5 7a8 8 0 0 1 10-3M19 1v6M16 4h6',
  smile: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM8 8v3M16 8v3M7 14a5 5 0 0 0 10 0',
  plus: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM6 12h12M12 6v12'
}
const tool = name => ({name, path: icons[name]})
const channelTools = ['smile', 'image'].map(tool)
const chatTools = computed(() => (props.scope === 'group'
  ? ['microphone', 'image', 'camera', 'effect', 'smile', 'plus']
  : ['microphone', 'image', 'camera', 'smile', 'plus']).map(tool))
const commandName = name => '/' + (name || '指令').replace(/^\/+/, '')
</script>

<style scoped>
.panel-phone {
  background: #fff;
  color: #202020;
  font-family: Arial, "PingFang SC", "Microsoft YaHei", sans-serif;
}
.panel-phone svg {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  flex-shrink: 0;
}
.panel-chat-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
  height: 54px;
  padding: 12px 10px 8px;
  background: #f3f3f3;
}
.panel-chat-head::after {
  content: "";
  position: absolute;
  inset: 0;
  background: #0006;
}
.panel-chat-head--channel { background: linear-gradient(110deg, #00b7d4, #008fd0); }
.panel-back { width: 12px; height: 16px; }
.panel-menu-icon { width: 16px; height: 16px; }
.panel-chat-title { flex: 1; min-width: 0; font-size: 12px; }
.panel-chat-title > div { display: flex; align-items: center; gap: 4px; }
.panel-chat-title span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.panel-chat-title img { width: 12px; height: 12px; flex-shrink: 0; }
.panel-chat-title small { display: flex; align-items: center; gap: 3px; margin-top: 3px; font-size: 8px; }
.panel-chat-title i { width: 6px; height: 6px; border-radius: 50%; background: #31df8b; }
.panel-sheet {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin-top: -4px;
  border-radius: 7px 7px 0 0;
  background: #fff;
  overflow: hidden;
}
.panel-handle { display: flex; justify-content: center; height: 16px; flex-shrink: 0; padding-top: 4px; }
.panel-handle span { width: 23px; height: 3px; border-radius: 3px; background: #ccc; }
.panel-sheet-heading {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex-shrink: 0;
  height: 28px;
  padding: 0 10px 6px;
  color: #c7c7c7;
  font-size: 11px;
}
.panel-sheet-heading span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.panel-sheet-heading--quick { color: #202020; font-size: 12px; }
.panel-sheet-heading svg { width: 13px; height: 13px; }
.panel-bot-avatar { width: 16px; height: 16px; flex-shrink: 0; object-fit: contain; border-radius: 50%; }
.panel-command-list { flex: 1; min-height: 0; padding: 0 10px; overflow-y: auto; scrollbar-width: none; }
.panel-command-list::-webkit-scrollbar { display: none; }
.panel-command-list:focus-visible { outline: 1px solid #00a9f3; outline-offset: -1px; }
.panel-command-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 29px; border-bottom: 1px solid #f7f7f7; }
.panel-command-name { min-width: 0; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.panel-command-desc { max-width: 65%; min-width: 0; color: #c7c7c7; font-size: 9px; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.panel-command-row--link { min-height: 31px; }
.panel-link-arrow { width: 10px; height: 10px; color: #999; }
.panel-empty { display: grid; place-items: center; min-height: 72px; color: #999; font-size: 12px; }
.panel-bot-tabs { display: flex; align-items: stretch; gap: 15px; height: 34px; flex-shrink: 0; padding: 2px 8px 0; }
.panel-selected-bot { display: flex; align-items: center; justify-content: center; position: relative; width: 20px; padding-bottom: 5px; }
.panel-selected-bot .panel-bot-avatar { width: 18px; height: 18px; }
.panel-selected-bot::after { content: ""; position: absolute; bottom: 0; left: 0; right: 0; height: 2px; border-radius: 2px; background: #00a9f3; }
.panel-recent { display: flex; align-items: center; padding-bottom: 5px; color: #29b5e0; }
.panel-recent svg { width: 19px; height: 19px; }
.panel-composer { flex-shrink: 0; height: 76px; padding: 5px 10px 10px; background: #f7f7f7; }
.panel-input-row { display: flex; align-items: center; gap: 7px; }
.panel-input { flex: 1; min-width: 0; height: 26px; padding: 3px 8px; border-radius: 20px; background: #fff; font-size: 18px; line-height: 20px; }
.panel-send { flex-shrink: 0; padding: 6px 11px; border-radius: 11px; background: #00a9f3; color: #fff; font-size: 11px; line-height: 14px; }
.panel-tools { display: flex; align-items: center; justify-content: space-between; padding: 15px 3px 0; }
.panel-tool { width: 18px; height: 18px; }
.panel-composer--channel { height: 46px; padding: 8px 10px 10px; background: #fff; }
.panel-composer--channel .panel-input-row { gap: 8px; }
.panel-composer--channel .panel-input { padding: 3px 0; }
.panel-composer--channel .panel-send { border-radius: 15px; padding-inline: 9px; }
.panel-user-avatar { display: grid; place-items: center; flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%; overflow: hidden; background: #eee; color: #aaa; }
.panel-user-avatar svg { width: 17px; height: 17px; }
</style>
