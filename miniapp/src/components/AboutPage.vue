<script setup lang="ts">
import {onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import Icon from './Icon.vue'
import type {About} from '../about'
import type {PrivateRequest} from '../activity'
import type {Profile} from '../profile'

const props = defineProps<{ bot: Profile['bot']; request: PrivateRequest; active: boolean }>()
const emit = defineEmits<{ copy: [value: string, label: string] }>()
const about = shallowRef<About | null>(null)
const loading = ref(false)
const error = ref(false)
const botAvatarFailed = ref(false)
const developerAvatarFailed = ref(false)
let disposed = false

watch(() => props.bot.avatarUrl, () => {
  botAvatarFailed.value = false
})
watch(() => about.value?.developerAvatarUrl, () => {
  developerAvatarFailed.value = false
})
watch(() => props.active, active => {
  if (active && !about.value) void load()
}, {immediate: true})
onBeforeUnmount(() => {
  disposed = true
})

async function load() {
  if (loading.value || disposed) return
  loading.value = true
  error.value = false
  try {
    const result = await props.request<About>('about')
    if (!disposed && result) about.value = result
  } catch {
    if (!disposed) error.value = true
  } finally {
    loading.value = false
  }
}

function value(text: string | null | undefined) {
  return !text || text.startsWith('Unknown ') ? '暂未提供' : text
}
</script>

<template>
  <div class="about-content">
    <section class="about-identity glass" aria-labelledby="about-title">
      <span class="about-app-avatar">
        <img v-if="bot.avatarUrl && !botAvatarFailed" :src="bot.avatarUrl" alt="" referrerpolicy="no-referrer"
             @error="botAvatarFailed = true"/>
        <Icon v-else name="bot"/>
      </span>
      <h2 id="about-title">{{ bot.name }}</h2>
      <p>关于本机器人的一些信息</p>
      <span class="about-version">版本 {{ loading && !about ? '读取中…' : value(about?.version) }}</span>
    </section>

    <section class="about-section glass" aria-labelledby="version-title" :aria-busy="loading">
      <header class="about-section-heading">
        <div>
          <Icon name="info"/>
          <h3 id="version-title">版本信息</h3></div>
        <button class="about-refresh" type="button" aria-label="刷新版本信息" :disabled="loading" @click="load">
          <Icon name="refresh" :class="{ 'refresh-spinning': loading }"/>
        </button>
      </header>
      <p v-if="error" class="about-error" role="alert">版本信息暂时无法读取，请稍后重试。</p>
      <dl class="about-details">
        <div>
          <dt>当前版本</dt>
          <dd>{{ value(about?.version) }}</dd>
        </div>
        <div>
          <dt>构建时间</dt>
          <dd>{{ value(about?.buildTime) }}</dd>
        </div>
        <div>
          <dt>分支 / 提交</dt>
          <dd>{{ about ? `${value(about.branch)} / ${value(about.commitId)}` : '暂未提供' }}</dd>
        </div>
      </dl>
    </section>

    <section class="about-section glass" aria-labelledby="developer-title">
      <header class="about-section-heading">
        <div>
          <Icon name="user"/>
          <h3 id="developer-title">开发者</h3></div>
      </header>
      <div class="about-developer">
        <span class="about-developer-avatar">
          <img v-if="about?.developerAvatarUrl && !developerAvatarFailed" :src="about.developerAvatarUrl"
               alt="YZ_Ljc_ 的头像" referrerpolicy="no-referrer" @error="developerAvatarFailed = true"/>
          <Icon v-else name="user"/>
        </span>
        <div><strong>YZ_Ljc_</strong><span>亚托利喵开发者</span></div>
      </div>
    </section>

    <section class="about-section glass" aria-labelledby="contact-title">
      <header class="about-section-heading">
        <div>
          <Icon name="message"/>
          <h3 id="contact-title">反馈与联系</h3></div>
      </header>
      <p class="about-intro">
        若需反馈相关内容，提出改进建议，或在使用过程中遇到任何问题，亦或需要询问相关开发内容，您均可以通过以下方式与开发者取得联系：</p>
      <div class="about-links">
        <button class="about-link" type="button" @click="emit('copy', '818804507', '社区群号')"><span
            class="about-link-icon"><Icon name="users"/></span><span class="about-link-text"><strong>社区交流群</strong><small>818804507</small></span>
          <Icon name="copy"/>
        </button>
        <button class="about-link" type="button" @click="emit('copy', '3199590352', '开发者 QQ')"><span
            class="about-link-icon"><Icon name="user"/></span><span
            class="about-link-text"><strong>开发者 QQ</strong><small>3199590352</small></span>
          <Icon name="copy"/>
        </button>
        <a class="about-link" href="mailto:contact@yzljc.top"><span class="about-link-icon"><Icon
            name="mail"/></span><span class="about-link-text"><strong>联系邮箱</strong><small>contact@yzljc.top</small></span>
          <Icon name="external"/>
        </a>
        <button class="about-link" type="button" @click="emit('copy', '/feedback ', '反馈指令')"><span
            class="about-link-icon"><Icon name="message"/></span><span class="about-link-text"><strong>反馈指令</strong><small>/feedback &lt;反馈或建议内容&gt;</small></span>
          <Icon name="copy"/>
        </button>
      </div>
    </section>

    <section class="about-section glass" aria-labelledby="resources-title">
      <header class="about-section-heading">
        <div>
          <Icon name="book"/>
          <h3 id="resources-title">更多信息</h3></div>
      </header>
      <div class="about-links">
        <a class="about-link" href="https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S" target="_blank" rel="noopener noreferrer"
           referrerpolicy="no-referrer"><span class="about-link-icon"><Icon name="book"/></span><span
            class="about-link-text"><strong>帮助文档</strong><small>查看功能说明与使用帮助</small></span>
          <Icon name="external"/>
        </a>
        <button class="about-link" type="button"
                @click="emit('copy', 'https://github.com/Atri-DevTeam/AtriBot', '仓库链接')"><span
            class="about-link-icon"><Icon name="code"/></span><span
            class="about-link-text"><strong>GitHub 开源仓库</strong><small>Atri-DevTeam / AtriBot</small></span>
          <Icon name="copy"/>
        </button>
        <a class="about-link" href="https://moentg.cn" target="_blank" rel="noopener noreferrer"
           referrerpolicy="no-referrer"><span class="about-link-icon"><Icon name="spark"/></span><span
            class="about-link-text"><strong>页面设计</strong><small>本页面风格有参考开发者 NTG 的设计方案</small></span>
          <Icon name="external"/>
        </a>
      </div>
    </section>
  </div>
</template>

<style scoped>
.about-content {
  display: grid;
  margin: 0 auto;
  animation: arrive .3s ease both;
  width: 100%;
  grid-template-columns: minmax(0, .85fr) minmax(0, 1.15fr);
  gap: 24px 48px;
}

.about-identity {
  display: flex;
  flex-direction: column;
  background: transparent;
  border: 0;
  border-radius: 0;
  align-items: flex-start;
  text-align: left;
  padding: 0 0 24px;
  grid-row: span 2;
}

.about-app-avatar {
  display: grid;
  place-items: center;
  width: 76px;
  height: 76px;
  padding: 5px;
  border: 1px solid var(--ui-border);
  border-radius: 24px;
  background: var(--ui-raised);
  box-shadow: none;
  overflow: hidden;
}

.about-app-avatar img {
  width: 100%;
  height: 100%;
  border-radius: 18px;
  object-fit: cover;
}

.about-app-avatar svg {
  width: 45px;
  height: 45px;
}

.about-identity h2 {
  margin: 16px 0 4px;
  font-weight: 600;
  font-size: 32px;
  letter-spacing: -.04em;
}

.about-identity p {
  margin: 0;
  color: var(--ui-muted);
  font-size: 11px;
  line-height: 1.8;
}

.about-version {
  margin-top: 17px;
  padding: 6px 13px;
  border: 1px solid var(--ui-border);
  background: var(--ui-raised);
  color: var(--ui-secondary);
  border-radius: 4px;
  font-size: 10px;
}

.about-section {
  background: transparent;
  border: 0;
  border-top: 1px solid var(--ui-border);
  border-radius: 0;
  padding: 24px 0;
  margin-top: 0;
}

.about-section-heading, .about-section-heading > div {
  display: flex;
  align-items: center;
}

.about-section-heading {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 13px;
}

.about-section-heading > div {
  gap: 9px;
}

.about-section-heading svg {
  width: 17px;
  height: 17px;
  color: var(--ui-muted);
  display: none;
}

.about-section-heading h3 {
  margin: 0;
  font-weight: 550;
  font-size: 16px;
}

.about-refresh {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  background: transparent;
  color: var(--ui-muted);
}

.about-refresh svg {
  width: 15px;
  height: 15px;
}

.about-error {
  margin: 0 0 8px;
  color: var(--ui-danger);
  font-size: 11px;
}

.about-details {
  margin: 0;
}

.about-details > div {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 15px;
  padding: 11px 0;
  border-top: 1px solid var(--ui-border);
}

.about-details dt {
  color: var(--ui-muted);
  font-size: 11px;
  white-space: nowrap;
}

.about-details dd {
  margin: 0;
  color: var(--ui-text);
  font-size: 11px;
  text-align: right;
  overflow-wrap: anywhere;
}

.about-developer {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 5px 0 1px;
}

.about-developer-avatar {
  display: grid;
  place-items: center;
  width: 49px;
  height: 49px;
  flex-shrink: 0;
  border: 1px solid var(--ui-border);
  border-radius: 15px;
  background: var(--ui-raised);
  overflow: hidden;
}

.about-developer-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.about-developer-avatar svg {
  width: 24px;
  height: 24px;
  color: var(--ui-muted);
}

.about-developer strong, .about-developer span {
  display: block;
}

.about-developer strong {
  font-size: 14px;
  font-weight: 550;
}

.about-developer div > span {
  margin-top: 4px;
  color: var(--ui-muted);
  font-size: 10px;
}

.about-intro {
  margin: 0 0 13px;
  color: var(--ui-secondary);
  font-size: 11px;
  line-height: 1.9;
}

.about-links {
  border-top: 0;
}

.about-link {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-height: 62px;
  border: 0;
  border-bottom: 1px solid var(--ui-border);
  background: transparent;
  color: inherit;
  text-decoration: none;
  text-align: left;
  padding: 14px 0;
}

.about-link:last-child {
  border-bottom: 0;
}

.about-link:hover {
  background: var(--ui-hover);
}

.about-link-icon {
  display: grid;
  place-items: center;
  width: 35px;
  height: 35px;
  flex-shrink: 0;
  border-radius: 11px;
  color: var(--ui-secondary);
  border: 0;
  background: transparent;
}

.about-link-icon svg {
  width: 17px;
  height: 17px;
}

.about-link-text {
  display: block;
  flex: 1;
  min-width: 0;
}

.about-link-text strong, .about-link-text small {
  display: block;
}

.about-link-text strong {
  font-size: 11px;
  font-weight: 550;
}

.about-link-text small {
  margin-top: 4px;
  color: var(--ui-muted);
  font-size: 10px;
  overflow-wrap: anywhere;
}

.about-link > svg {
  width: 15px;
  height: 15px;
  color: var(--ui-subtle);
}

@media (max-width: 760px) {
  .about-content {
    gap: 12px;
  }

  .about-identity {
    padding: 0 0 24px;
  }

  .about-section {
    padding: 24px 0;
    border-radius: 0;
  }
}

@media (max-width: 760px) { .about-content { grid-template-columns: minmax(0, 1fr); gap: 0; } .about-identity { grid-row: auto; } }
</style>
