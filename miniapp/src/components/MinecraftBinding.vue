<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue'
import Icon from './Icon.vue'
import type {PrivateRequest} from '../activity'

type MinecraftProfile = { uuid: string | null; username: string | null; avatarUrl: string | null }
const props = defineProps<{ uuid: string; request: PrivateRequest }>()
const profile = ref<MinecraftProfile | null>(null)
const imageFailed = ref(false)
let generation = 0
watch(() => props.uuid, async uuid => {
  const token = ++generation
  profile.value = null
  imageFailed.value = false
  if (!uuid) return
  try {
    const result = await props.request<MinecraftProfile>('minecraft')
    if (token === generation && result?.uuid?.replace(/-/g, '').toLowerCase() === uuid.replace(/-/g, '').toLowerCase())
      profile.value = result
  } catch { /* 保留绑定 UUID，审核服务不可用时显示默认资料 */
  }
}, {immediate: true})
onBeforeUnmount(() => {
  generation++
})
</script>

<template>
  <div class="minecraft-binding">
    <div class="minecraft-player">
      <span class="minecraft-avatar">
        <img v-if="profile?.avatarUrl && !imageFailed" :src="profile.avatarUrl" alt="Minecraft 头像" width="36"
             height="36" @error="imageFailed = true"/>
        <Icon v-else name="box"/>
      </span>
      <span class="minecraft-identity">
        <span class="minecraft-name">{{ profile?.username || 'Minecraft 玩家' }}</span>

      </span>
    </div>
    <div class="minecraft-uuid"><span>UUID</span><code>{{ uuid }}</code></div>
  </div>
</template>

<style scoped>
.minecraft-binding {
  min-width: 0;
  width: 100%;
}

.minecraft-player {
  display: flex;
  align-items: center;
  gap: 10px;
}

.minecraft-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}

.minecraft-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  image-rendering: pixelated;
}

.minecraft-avatar svg {
  width: 20px;
  height: 20px;
  color: var(--ui-muted);
}

.minecraft-identity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  min-width: 0;
}

.minecraft-name {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.minecraft-uuid {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  color: var(--ui-muted);
  font-size: 11px;
  line-height: 1.7;
}

.minecraft-uuid > span {
  flex-shrink: 0;
  font-size: 10px;
  letter-spacing: .04em;
}

.minecraft-uuid code {
  min-width: 0;
  font: inherit;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  overflow-wrap: anywhere;
}
</style>
