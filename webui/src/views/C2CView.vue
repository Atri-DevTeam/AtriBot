<template>
  <!-- legacy-chat：见 DashboardView.vue 的说明，私聊页同样保持原始聊天视觉 -->
  <div class="shell legacy-chat">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loadingUsers" @click="loadUsers">刷新</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer" />

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2>私聊消息</h2>
        </div>
        <span class="status-pill topbar-status"><span class="dot ok"></span>{{ totalMessages }} 条记录</span>
      </header>

      <section class="content">
        <section class="chat-panel">
          <div class="chat-head">
            <div class="group-picker">
              <div class="group-picker-row">
                <button class="group-picker-trigger" @click="toggleDropdown">
                  <span>{{ selectedUserId || '选择用户' }}</span>
                  <span class="arrow" :class="{ up: dropdownOpen }">▾</span>
                </button>
                <button class="filter-return-btn" type="button" title="返回用户总览" aria-label="返回用户总览" @click="returnToUserList">
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round">
                    <rect x="3.25" y="3.25" width="7.5" height="7.5" rx="1.8"/>
                    <rect x="13.25" y="3.25" width="7.5" height="7.5" rx="1.8"/>
                    <rect x="3.25" y="13.25" width="7.5" height="7.5" rx="1.8"/>
                    <rect x="13.25" y="13.25" width="7.5" height="7.5" rx="1.8"/>
                  </svg>
                </button>
              </div>
              <div v-if="dropdownOpen" ref="dropdownRef" class="dropdown-menu">
                <input v-model="userSearch" class="dropdown-search" placeholder="搜索用户 openId…" @click.stop />
                <button v-for="user in filteredUsers" :key="user.userOpenId"
                        class="dropdown-item" :class="{ active: user.userOpenId === selectedUserId }"
                        @click="selectUser(user.userOpenId); closeDropdown()">
                  <span class="c2c-user-head">
                    <span class="c2c-user-avatar">
                      <img
                        v-show="!avatarFailed[user.userOpenId] && avatarUrlForUser(user)"
                        :src="avatarUrlForUser(user)"
                        :alt="user.userOpenId"
                        referrerpolicy="no-referrer"
                        @error="avatarFailed[user.userOpenId] = true"
                      />
                      <span v-show="!avatarUrlForUser(user) || avatarFailed[user.userOpenId]">{{ avatarTextForUser(user) }}</span>
                    </span>
                    <span class="item-id">{{ user.userOpenId }}</span>
                  </span>
                  <span class="item-badges">
                    <svg :class="['c2c-push-mark', { 'is-off': !isC2CPushOn(user) }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ isC2CPushOn(user) ? '主动消息已开启' : '主动消息已关闭' }}</title>
                      <path class="push-bubble" d="M5.25 6.5A3.25 3.25 0 0 1 8.5 3.25h5.9a3.25 3.25 0 0 1 3.25 3.25v4.2a3.25 3.25 0 0 1-3.25 3.25H10.1l-3.55 3.1v-3.18a3.25 3.25 0 0 1-1.3-2.6V6.5Z"/>
                      <path class="push-wave" d="M14.9 6.8c1.05.76 1.72 1.9 1.72 3.2s-.67 2.44-1.72 3.2"/>
                      <path class="push-wave" d="M12.85 8.35c.46.4.75.98.75 1.65s-.29 1.25-.75 1.65"/>
                      <path v-if="!isC2CPushOn(user)" class="push-slash" d="M4.6 4.6 19.4 19.4"/>
                    </svg>
                  </span>
                </button>
              </div>
            </div>
            <div class="chat-head-right">
              <button class="info-toggle" :class="{ active: showInspector }" @click="showInspector = !showInspector" title="用户信息" aria-label="用户信息">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9"/>
                  <line x1="12" y1="7" x2="12" y2="13"/>
                  <circle cx="12" cy="17" r="0.8" fill="currentColor" stroke="none"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="message-list" ref="messageListRef" @scroll="onScroll">
            <div v-if="loadingMore" class="load-tip">加载更早的消息…</div>
            <div v-else-if="!hasMore && messages.length > 0" class="load-tip">— 没有更早的消息了 —</div>
            <div v-if="!selectedUserId" class="group-list-hint">
              <div class="group-list-grid">
                <button v-for="user in users" :key="user.userOpenId"
                        class="group-list-card"
                        @click="selectUser(user.userOpenId)">
                  <span class="c2c-user-head">
                    <span class="c2c-user-avatar c2c-user-avatar--card">
                      <img
                        v-show="!avatarFailed[user.userOpenId] && avatarUrlForUser(user)"
                        :src="avatarUrlForUser(user)"
                        :alt="user.userOpenId"
                        referrerpolicy="no-referrer"
                        @error="avatarFailed[user.userOpenId] = true"
                      />
                      <span v-show="!avatarUrlForUser(user) || avatarFailed[user.userOpenId]">{{ avatarTextForUser(user) }}</span>
                    </span>
                    <span class="group-list-id">{{ user.userOpenId }}</span>
                  </span>
                  <span class="group-list-badges">
                    <svg :class="['c2c-push-mark', 'c2c-push-mark--card', { 'is-off': !isC2CPushOn(user) }]" viewBox="0 0 24 24" aria-hidden="true">
                      <title>{{ isC2CPushOn(user) ? '主动消息已开启' : '主动消息已关闭' }}</title>
                      <path class="push-bubble" d="M5.25 6.5A3.25 3.25 0 0 1 8.5 3.25h5.9a3.25 3.25 0 0 1 3.25 3.25v4.2a3.25 3.25 0 0 1-3.25 3.25H10.1l-3.55 3.1v-3.18a3.25 3.25 0 0 1-1.3-2.6V6.5Z"/>
                      <path class="push-wave" d="M14.9 6.8c1.05.76 1.72 1.9 1.72 3.2s-.67 2.44-1.72 3.2"/>
                      <path class="push-wave" d="M12.85 8.35c.46.4.75.98.75 1.65s-.29 1.25-.75 1.65"/>
                      <path v-if="!isC2CPushOn(user)" class="push-slash" d="M4.6 4.6 19.4 19.4"/>
                    </svg>
                  </span>
                </button>
                <div v-if="users.length === 0" class="empty-state">暂无用户数据</div>
              </div>
            </div>
            <div v-else-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

            <template v-for="message in orderedMessages" :key="message.id">
              <div v-if="message.system" class="system-message">{{ message.text }}</div>
              <article v-else
                       class="message" :class="{ mine: isMe(message) }">
                <div class="avatar">
                  <img v-show="!avatarFailed[message.id] && avatarUrl(message)" :src="avatarUrl(message)"
                       :alt="message.username" referrerpolicy="no-referrer" @error="avatarFailed[message.id] = true" />
                  <span v-show="!avatarUrl(message) || avatarFailed[message.id]">{{ avatarText(message) }}</span>
                </div>
                <div class="message-main">
                  <div class="msg-header">
                    <template v-if="isMe(message)">
                      <svg v-if="message.senderIsBot" class="bot-icon" width="14" height="14" viewBox="0 0 64 64">
                        <line x1="32" y1="10" x2="32" y2="18" stroke="#12B7F5" stroke-width="3.5" stroke-linecap="round"/>
                        <circle cx="32" cy="8" r="4" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                        <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                        <rect x="24" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                        <rect x="36" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                      </svg>
                      <strong class="msg-name">{{ message.username || 'AtriBot' }}</strong>
                    </template>
                    <template v-else>
                      <strong class="msg-name">{{ message.username || 'Unknown' }}</strong>
                      <svg v-if="message.senderIsBot" class="bot-icon" width="14" height="14" viewBox="0 0 64 64">
                        <line x1="32" y1="10" x2="32" y2="18" stroke="#12B7F5" stroke-width="3.5" stroke-linecap="round"/>
                        <circle cx="32" cy="8" r="4" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                        <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="#12B7F5" stroke-width="3.5"/>
                        <rect x="24" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                        <rect x="36" y="28" width="4" height="8" rx="2" fill="#12B7F5"/>
                      </svg>
                    </template>
                  </div>
                  <div class="bubble"
                       @contextmenu.prevent.stop="onContextMenu($event, message)">
                    <div v-if="message.attachments" class="msg-attach">
                      <template v-for="(att, i) in parseAttach(message.attachments)" :key="message.id + '-' + i">
                        <img
                          v-if="att.type === 'image'"
                          v-show="!attachFailed[att.url]"
                          :src="att.url"
                          :alt="att.filename"
                          referrerpolicy="no-referrer"
                          class="clickable"
                          @error="attachFailed[att.url] = true"
                          @click="previewImg = att.url"
                        />
                        <span v-if="att.type === 'image' && attachFailed[att.url]" class="attach-fail">📎 {{ att.filename }}</span>
                        <div v-else-if="att.type === 'voice'" class="voice-attach">
                          <div class="voice-title">语音消息</div>
                          <div v-if="att.asrText" class="voice-asr">{{ att.asrText }}</div>
                          <audio v-if="att.voiceUrl" :src="att.voiceUrl" controls preload="none"></audio>
                          <a v-else-if="att.url" :href="att.url" target="_blank" rel="noreferrer">打开原始音频</a>
                        </div>
                      </template>
                    </div>
                    <ArkMessageCard v-if="hasArk(message)" :ark="message.ark" />
                    <pre v-if="!hasArk(message) && message.messageType !== 2 && renderContent(message)">{{ renderContent(message) }}</pre>
                    <div v-if="!hasArk(message) && message.messageType === 2" class="md-body" v-html="renderMd(renderContent(message))"></div>
                  </div>
                  <!-- 时间放在气泡下方，与群聊消息保持一致 -->
                  <div class="msg-time">{{ fmtTime(message.eventTimestamp || message.createdAt) }}</div>
                </div>
              </article>
            </template>
          </div>

            <div v-if="replyTo" class="reply-bar">
              <span>回复 {{ replyTo.username || '...' }}</span>
              <button @click="replyTo = null">×</button>
            </div>
          <form class="composer" @submit.prevent="sendMessage">
            <div class="composer-type">
              <label :class="{ active: msgType === 'text' }"><input type="radio" v-model="msgType" value="text" />文本</label>
              <label :class="{ active: msgType === 'markdown' }"><input type="radio" v-model="msgType" value="markdown" />Markdown</label>
              <label :class="{ active: msgType === 'image' }"><input type="radio" v-model="msgType" value="image" />图片</label>
              <label :class="{ active: msgType === 'stream' }"><input type="radio" v-model="msgType" value="stream" />流式</label>
            </div>
            <textarea v-model="draft" :disabled="!selectedUserId || sending"
                      :placeholder="msgType === 'image' ? '图片消息' : msgType === 'markdown' ? 'Markdown 内容' : msgType === 'stream' ? '每行一个 delta' : '文本消息'"
                      rows="3" @paste="onPaste"></textarea>
            <div class="composer-image-opts" v-if="msgType === 'image'">
              <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="onFilePicked" />
              <label @click="$refs.fileInputRef.click()">上传图片</label>
              <span v-if="pastePreview" class="composer-image-hint">已选择图片，点击预览可清除</span>
            </div>
            <img v-if="pastePreview" :src="pastePreview" class="paste-preview" @click="clearSelectedImage" title="点击清除" />
            <button class="primary-button" :disabled="!canSend">{{ sending ? '发送中' : '发送' }}</button>
          </form>

          <div v-if="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
            <button v-if="!isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
                    @click="atUser(ctxMenu.message); ctxMenu.visible = false">@ 用户</button>
            <button @click="startReply(ctxMenu.message); ctxMenu.visible = false">回复</button>
            <button @click="copyText(ctxMenu.message.content); ctxMenu.visible = false">复制</button>
            <button v-if="isMe(ctxMenu.message) && !recalledIds[ctxMenu.message.messageOpenId]"
                    class="ctx-recall"
                    @click="recallMsg(ctxMenu.message); ctxMenu.visible = false">撤回</button>
          </div>
        </section>

        <aside class="inspector" :class="{ 'inspector--show': showInspector }">
          <div class="inspector-head">
            <h3>用户信息</h3>
            <button class="inspector-close" aria-label="关闭用户信息" @click="showInspector = false">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <dl v-if="selectedUser">
            <dt>用户开放平台ID</dt>
            <dd>{{ selectedUser.userOpenId }}</dd>
          </dl>
          <template v-if="selectedUser">
          <div class="c2c-profile-card">
            <div class="c2c-profile-head">
              <div class="c2c-profile-avatar">
                <img
                  v-show="!avatarFailed[selectedUser.userOpenId] && avatarUrlForUser(selectedUser)"
                  :src="avatarUrlForUser(selectedUser)"
                  :alt="selectedUser.userOpenId"
                  referrerpolicy="no-referrer"
                  @error="avatarFailed[selectedUser.userOpenId] = true"
                />
                <span v-show="!avatarUrlForUser(selectedUser) || avatarFailed[selectedUser.userOpenId]">{{ avatarTextForUser(selectedUser) }}</span>
              </div>
              <div class="c2c-profile-meta">
                <strong>{{ selectedUser.userOpenId }}</strong>
                <span>当前：{{ c2cRoleLabel(permDraft.role) }} · {{ permNodes.length }} 规则</span>
              </div>
            </div>

            <div class="c2c-profile-section">
              <div class="perm-editor-label">权限组</div>
              <div class="perm-role-grid">
                <button
                  v-for="role in c2cRoleOptions"
                  :key="role.key"
                  type="button"
                  class="perm-role-card"
                  :class="['role-' + role.key.toLowerCase(), { active: permDraft.role === role.key }]"
                  @click="permDraft.role = role.key"
                >
                  <svg v-if="role.key === 'USER'" class="perm-role-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="12" cy="8" r="4.2" fill="none" stroke="currentColor" stroke-width="1.8"/>
                    <path d="M5.5 19c1.6-3.2 4-4.8 6.5-4.8s4.9 1.6 6.5 4.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                  </svg>
                  <svg v-else-if="role.key === 'ADMIN'" class="perm-role-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 3.5 19 7v6c0 4-2.5 6.8-7 8.9-4.5-2.1-7-4.9-7-8.9V7l7-3.5Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
                    <path d="m8.5 12 2 2 4.8-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  <svg v-else class="perm-role-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m5 15 2.2-7.2L12 12l4.8-4.2L19 15H5Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
                    <path d="M6 17h12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                  </svg>
                  <span class="perm-role-card__label">{{ role.label }}</span>
                </button>
              </div>
            </div>

            <div class="c2c-profile-section">
              <div class="perm-editor-label">权限节点</div>
              <div v-if="permNodes.length" class="perm-token-list">
                <button
                  v-for="p in permNodes"
                  :key="p"
                  type="button"
                  class="perm-token"
                  @click="removePerm(p)"
                >
                  <span>{{ p }}</span>
                  <span aria-hidden="true">×</span>
                </button>
              </div>
              <div v-else class="c2c-perm-empty">暂无权限节点</div>
              <form class="perm-add" @submit.prevent="addPerm(newPerm)">
                <input v-model="newPerm" placeholder="添加权限节点" />
                <button type="submit" class="primary-button" :disabled="!newPerm.trim()">添加</button>
              </form>
            </div>

            <div class="c2c-profile-section">
              <div class="perm-editor-label">状态</div>
              <div class="perm-switch-grid">
                <button type="button" class="switch-chip" :class="{ active: permBlocked }" @click="permBlocked = !permBlocked">
                  <span class="switch-dot switch-dot--blocked" />
                  <span>拉黑</span>
                </button>
                <button type="button" class="switch-chip" :class="{ active: permIgnored }" @click="permIgnored = !permIgnored">
                  <span class="switch-dot switch-dot--ignored" />
                  <span>屏蔽</span>
                </button>
                <button type="button" class="switch-chip" :class="{ active: permC2CPush }" @click="permC2CPush = !permC2CPush">
                  <svg :class="['c2c-push-mark', 'c2c-push-mark--inline', { 'is-off': !permC2CPush }]" viewBox="0 0 24 24" aria-hidden="true">
                    <title>{{ permC2CPush ? '主动消息已开启' : '主动消息已关闭' }}</title>
                    <path class="push-bubble" d="M5.25 6.5A3.25 3.25 0 0 1 8.5 3.25h5.9a3.25 3.25 0 0 1 3.25 3.25v4.2a3.25 3.25 0 0 1-3.25 3.25H10.1l-3.55 3.1v-3.18a3.25 3.25 0 0 1-1.3-2.6V6.5Z"/>
                    <path class="push-wave" d="M14.9 6.8c1.05.76 1.72 1.9 1.72 3.2s-.67 2.44-1.72 3.2"/>
                    <path class="push-wave" d="M12.85 8.35c.46.4.75.98.75 1.65s-.29 1.25-.75 1.65"/>
                    <path v-if="!permC2CPush" class="push-slash" d="M4.6 4.6 19.4 19.4"/>
                  </svg>
                  <span>主动消息</span>
                </button>
              </div>
            </div>

            <div class="c2c-profile-actions">
              <button type="button" class="primary-button" :disabled="permSaving" @click="savePermEditor">
                {{ permSaving ? '保存中...' : '确认修改' }}
              </button>
              <button type="button" class="ghost-button danger" @click="openDeleteConfirm">清除档案</button>
            </div>
          </div>
          </template>
          <div v-else class="hint">选择用户后显示详情</div>
          <div class="log-box"><strong>请求状态</strong><p>{{ notice }}</p></div>
          <section v-if="selectedUser" class="stats-section stats-lookup sidebar-stats-section">
            <header class="stats-section-head">
              <h3 class="stats-section-title">用户统计</h3>
            </header>
            <div v-if="userStatsLoading" class="empty-state stats-state">加载中...</div>
            <div v-else-if="userStatsError" class="empty-state error stats-state">{{ userStatsError }}</div>
            <div v-else-if="!userStats" class="empty-state stats-state">暂无统计数据</div>
            <dl v-else class="stats-detail">
              <div class="stats-detail-row">
                <dt>最近用户名</dt>
                <dd>{{ userStats.lastUsername || '-' }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>私聊接收消息</dt>
                <dd>{{ formatNumber(userStats.c2cReceivedMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>私聊发送消息</dt>
                <dd>{{ formatNumber(userStats.c2cSentMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>群聊接收消息</dt>
                <dd>{{ formatNumber(userStats.groupReceivedMessages) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>首次记录</dt>
                <dd>{{ formatStatsTime(userStats.firstSeenAt) }}</dd>
              </div>
              <div class="stats-detail-row">
                <dt>最近记录</dt>
                <dd>{{ formatStatsTime(userStats.lastSeenAt) }}</dd>
              </div>
            </dl>
          </section>
        </aside>
      </section>
    </main>

    <div v-if="deleteConfirmOpen" class="perm-modal-backdrop" @click="closeDeleteConfirm">
      <div class="perm-modal perm-modal--danger" @click.stop>
        <h3>清除用户数据</h3>
        <p class="perm-uid">{{ permTargetId || selectedUserId }}</p>
        <p class="perm-warning">此操作会删除该用户的私聊档案数据</p>
        <div class="danger-confirm">
          <label>输入该用户 openId 以确认</label>
          <input v-model="deleteConfirmText" :placeholder="permTargetId || selectedUserId" />
        </div>
        <div v-if="deleteError" class="perm-editor-error">{{ deleteError }}</div>
        <div class="perm-modal-actions">
          <button type="button" class="ghost-button" @click="closeDeleteConfirm">取消</button>
          <button
            type="button"
            class="primary-button danger"
            :disabled="deleteDeleting || deleteConfirmText.trim() !== (permTargetId || selectedUserId)"
            @click="confirmDeleteUser"
          >
            {{ deleteDeleting ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="previewImg" class="lightbox" @click="previewImg = null">
      <img :src="previewImg" referrerpolicy="no-referrer" @click.stop  alt="t-1"/>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LEGACY_TOKEN_KEY, API_BASE } from '../router.js'
import { renderFaceTags } from '../messageRender.js'
import { renderMarkdown as renderMd } from '../lib/markdown.js'
import AppSidebar from '../components/AppSidebar.vue'
import ArkMessageCard from '../components/ArkMessageCard.vue'
import { hasArkMessage } from '../lib/ark.js'

const router = useRouter()
const route = useRoute()

const users = ref([])
const messages = ref([])
const selectedUserId = ref('')
const loadingUsers = ref(false)
const loadingMessages = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const draft = ref('')
const totalMessages = ref(0)
const notice = ref('')
const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')
const avatarFailed = reactive({})
const attachFailed = reactive({})
const currentPage = ref(0)
const dropdownOpen = ref(false)
const dropdownRef = ref(null)
const dropdownScrollTop = ref(0)
const userSearch = ref('')
const sidebarOpen = ref(false)
const showInspector = ref(false)
const replyTo = ref(null)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const recalledIds = reactive({})
const msgType = ref('text')
const imageData = ref(null)
const pastePreview = ref(null)
const previewImg = ref(null)
const pageSize = 80
const permRole = ref('')
const permBlocked = ref(false)
const permIgnored = ref(false)
const permC2CPush = ref(true)
const permNodes = ref([])
const permTargetId = ref('')
const permSaving = ref(false)
const permError = ref('')
const userStats = ref(null)
const userStatsLoading = ref(false)
const userStatsError = ref('')
const deleteConfirmOpen = ref(false)
const deleteConfirmText = ref('')
const deleteDeleting = ref(false)
const deleteError = ref('')
const permDraft = reactive({
  role: 'USER',
  permissions: [],
  blocked: false,
  ignored: false,
  c2cPush: true
})
const c2cRoleOptions = [
  { key: 'USER', label: '普通' },
  { key: 'ADMIN', label: '管理员' },
  { key: 'OWNER', label: '所有者' }
]
function c2cRoleLabel(r) {
  return c2cRoleOptions.find(item => item.key === r)?.label || '普通'
}
const newPerm = ref('')

const messageListRef = ref(null)
const selectedUser = computed(() => users.value.find(u => u.userOpenId === selectedUserId.value))
const filteredUsers = computed(() => {
  const q = userSearch.value.toLowerCase()
  return q ? users.value.filter(u => u.userOpenId.toLowerCase().includes(q)) : users.value
})
const persistedMessageCount = computed(() => messages.value.filter(m => !m.system).length)
const hasMore = computed(() => persistedMessageCount.value < totalMessages.value)
const orderedMessages = computed(() => [...messages.value].reverse())
const canSend = computed(() => {
  if (!selectedUserId.value || sending.value) return false
  if (msgType.value === 'image') return !!imageData.value
  return !!draft.value.trim()
})

let eventSource = null

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  await loadMeta()
  await loadUsers()
  // 支持从用户列表页 /c2c?user=xxx 直接跳到某个会话
  const targetUser = route.query.user
  if (targetUser) selectUser(String(targetUser))
  connectSse()
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})

function connectSse() {
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`${API_BASE}/events`, { withCredentials: true })
  eventSource.onmessage = async (e) => {
    try {
      const payload = JSON.parse(e.data)
      if (payload.type === 'c2c_push_status') {
        handleC2CPushStatus(payload)
        return
      }
      if (payload.type !== 'c2c_refresh') return
      if (payload.userOpenId !== selectedUserId.value) return
      const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/messages?page=1&pageSize=${pageSize}`)
      const latest = data.records || []
      const seen = new Set(messages.value.filter(m => !m.system).map(m => m.messageOpenId))
      const fresh = latest.filter(m => !seen.has(m.messageOpenId))
      if (fresh.length > 0) {
        messages.value = [...fresh, ...messages.value]
        totalMessages.value = data.total || totalMessages.value
        if (isNearBottom()) { await nextTick(); scrollToBottom() }
      }
    } catch { /* ignore */ }
  }
  eventSource.onerror = () => {
    eventSource.close()
    setTimeout(connectSse, 5000)
  }
}

async function handleC2CPushStatus(payload) {
  const userOpenId = payload?.userOpenId || ''
  if (!userOpenId) return
  const enabled = payload.enabled !== false
  updateC2CPushState(userOpenId, enabled)
  if (userOpenId !== selectedUserId.value) return
  const wasNearBottom = isNearBottom()
  appendSystemMessage(enabled ? '主动消息已开启' : '主动消息已关闭')
  if (wasNearBottom) { await nextTick(); scrollToBottom() }
}

function updateC2CPushState(userOpenId, enabled) {
  const user = users.value.find(u => u.userOpenId === userOpenId)
  if (user) user.c2cPush = enabled
  if (userOpenId === selectedUserId.value) {
    permC2CPush.value = enabled
    permDraft.c2cPush = enabled
  }
}

function appendSystemMessage(text) {
  messages.value = [{
    id: `system-c2c-push-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    system: true,
    text
  }, ...messages.value]
}

function isNearBottom() {
  const el = messageListRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function onDocumentClick(e) {
  if (!e.target.closest('.group-picker')) closeDropdown()
  if (!e.target.closest('.ctx-menu')) ctxMenu.visible = false
}

async function loadMeta() {
  try {
    const data = await api('/config')
    appId.value = data.appId || ''
    botOpenId.value = data.botOpenId || ''
    botName.value = data.botName || 'AtriBot'
  } catch { /* ignore */ }
}

function authHeaders() {
  return { 'Content-Type': 'application/json' }
}

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: authHeaders(),
    credentials: 'same-origin',
    ...options
  })
  if (res.status === 503) {
    logout()
    throw new Error('WebUI 已关闭')
  }
  let payload
  try {
    payload = await res.json()
  } catch {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (res.status === 401) { logout(); throw new Error('未授权') }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function isMe(message) {
  return message.senderIsBot || (botOpenId.value && message.unionOpenId === botOpenId.value)
}

function avatarUrl(message) {
  if (!appId.value) return null
  if (isMe(message)) return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${botOpenId.value}/640`
  if (!message.unionOpenId) return null
  return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${message.unionOpenId}/640`
}

function avatarUrlForUser(user) {
  if (!appId.value || !user?.userOpenId) return null
  return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${user.userOpenId}/640`
}

function isC2CPushOn(user) {
  return user?.c2cPush !== false
}

function syncSelectedUserState(data) {
  if (!data?.userOpenId) return
  const idx = users.value.findIndex(u => u.userOpenId === data.userOpenId)
  if (idx >= 0) {
    users.value[idx] = { ...users.value[idx], ...data }
  } else {
    users.value.unshift({ ...data })
  }

  if (selectedUserId.value === data.userOpenId) {
    permRole.value = data.role || 'USER'
    permNodes.value = [...(data.permissions || [])]
    permBlocked.value = data.isBlocked ?? false
    permIgnored.value = data.isIgnored ?? false
    permC2CPush.value = data.c2cPush !== false
    permDraft.role = permRole.value
    permDraft.permissions = [...permNodes.value]
    permDraft.blocked = permBlocked.value
    permDraft.ignored = permIgnored.value
    permDraft.c2cPush = permC2CPush.value
  }
}

async function logout() {
  try {
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'same-origin'
    })
  } catch { /* ignore */ }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  router.replace('/login')
}

async function loadUsers() {
  loadingUsers.value = true
  try { users.value = await api('/c2c/users') } catch (e) { notice.value = e.message }
  finally { loadingUsers.value = false }
}

function toggleDropdown() {
  if (dropdownOpen.value) {
    closeDropdown()
  } else {
    dropdownOpen.value = true
    nextTick(() => {
      const el = dropdownRef.value
      if (el) el.scrollTop = dropdownScrollTop.value
    })
  }
}

function closeDropdown() {
  if (!dropdownOpen.value) return
  const el = dropdownRef.value
  if (el) dropdownScrollTop.value = el.scrollTop
  dropdownOpen.value = false
}

/** 回到「未选中用户」状态，消息列表位置会重新渲染成用户总览卡片网格 */
function returnToUserList() {
  selectedUserId.value = ''
  closeDropdown()
  // 不动 showInspector：这里没有像群聊那样藏在信息面板里的筛选器，
  // 而且未选中用户时面板内容是空的，强行展开只会留一块空白
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  replyTo.value = null
  ctxMenu.visible = false
  permTargetId.value = ''
  userStats.value = null
  userStatsError.value = ''
  userStatsLoading.value = false
}

async function selectUser(userOpenId) {
  if (selectedUserId.value === userOpenId) {
    void loadUserStats()
    return
  }
  selectedUserId.value = userOpenId
  const current = users.value.find(u => u.userOpenId === userOpenId)
  if (current) {
    permRole.value = current.role || 'USER'
    permNodes.value = [...(current.permissions || [])]
    permBlocked.value = current.isBlocked ?? false
    permIgnored.value = current.isIgnored ?? false
    permC2CPush.value = current.c2cPush !== false
  } else {
    permRole.value = 'USER'
    permNodes.value = []
    permBlocked.value = false
    permIgnored.value = false
    permC2CPush.value = true
  }
  permDraft.role = permRole.value
  permDraft.permissions = [...permNodes.value]
  permDraft.blocked = permBlocked.value
  permDraft.ignored = permIgnored.value
  permDraft.c2cPush = permC2CPush.value
  permTargetId.value = ''
  deleteConfirmOpen.value = false
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  loadPerms()
  await loadLatestMessages()
  void loadUserStats()
}

async function loadUserStats() {
  const userOpenId = selectedUserId.value
  if (!userOpenId) {
    userStats.value = null
    userStatsError.value = ''
    userStatsLoading.value = false
    return
  }
  userStatsLoading.value = true
  userStatsError.value = ''
  try {
    userStats.value = await api(`/public/official/users/${encodeURIComponent(userOpenId)}`)
  } catch (e) {
    userStats.value = null
    userStatsError.value = e.message
  } finally {
    userStatsLoading.value = false
  }
}

async function loadPerms() {
  if (!selectedUserId.value) return
  try {
    const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/permissions`)
    syncSelectedUserState(data)
  } catch {
    permRole.value = 'USER'
    permNodes.value = []
    permBlocked.value = false
    permIgnored.value = false
    permC2CPush.value = true
    permDraft.role = 'USER'
    permDraft.permissions = []
    permDraft.blocked = false
    permDraft.ignored = false
    permDraft.c2cPush = true
  }
}

function addPerm(perm) {
  const key = perm?.trim()
  if (!key) return
  if (!permNodes.value.includes(key)) {
    permNodes.value = [...permNodes.value, key]
    permDraft.permissions = [...permNodes.value]
  }
  newPerm.value = ''
}

function removePerm(perm) {
  permNodes.value = permNodes.value.filter(p => p !== perm)
  permDraft.permissions = [...permNodes.value]
}

async function savePermEditor() {
  const target = selectedUserId.value
  if (!target) return
  permSaving.value = true
  permError.value = ''
  try {
    const data = await api(`/c2c/${encodeURIComponent(target)}/profile`, {
      method: 'POST',
      body: JSON.stringify({
        role: permDraft.role,
        permissions: permDraft.permissions,
        blocked: permDraft.blocked,
        ignored: permDraft.ignored,
        c2cPush: permDraft.c2cPush
      })
    })
    syncSelectedUserState(data)
    notice.value = '已保存'
  } catch (e) {
    permError.value = e.message
  } finally {
    permSaving.value = false
  }
}

function openDeleteConfirm() {
  if (!selectedUserId.value) return
  permTargetId.value = selectedUserId.value
  deleteConfirmText.value = ''
  deleteError.value = ''
  deleteConfirmOpen.value = true
}

function closeDeleteConfirm() {
  deleteConfirmOpen.value = false
  deleteConfirmText.value = ''
  deleteError.value = ''
}

async function confirmDeleteUser() {
  const target = permTargetId.value || selectedUserId.value
  if (!target || deleteConfirmText.value.trim() !== target) return
  deleteDeleting.value = true
  deleteError.value = ''
  try {
    await api(`/c2c/${encodeURIComponent(target)}`, { method: 'DELETE' })
    users.value = users.value.filter(u => u.userOpenId !== target)
    delete avatarFailed[target]
    if (selectedUserId.value === target) {
      selectedUserId.value = ''
      messages.value = []
      totalMessages.value = 0
      currentPage.value = 0
      permRole.value = 'USER'
      permNodes.value = []
      permBlocked.value = false
      permIgnored.value = false
      permC2CPush.value = true
      permDraft.role = 'USER'
      permDraft.permissions = []
      permDraft.blocked = false
      permDraft.ignored = false
      permDraft.c2cPush = true
      userStats.value = null
      userStatsError.value = ''
      userStatsLoading.value = false
    }
    closeDeleteConfirm()
    notice.value = '用户档案数据已删除'
  } catch (e) {
    deleteError.value = e.message
  } finally {
    deleteDeleting.value = false
  }
}

async function loadLatestMessages() {
  if (!selectedUserId.value) return
  loadingMessages.value = true
  currentPage.value = 1
  try {
    const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/messages?page=1&pageSize=${pageSize}`)
    messages.value = data.records || []
    totalMessages.value = data.total || 0
    await nextTick(); scrollToBottom()
  } catch (e) { notice.value = e.message }
  finally { loadingMessages.value = false }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value || loadingMessages.value) return
  loadingMore.value = true
  const el = messageListRef.value
  const prevHeight = el ? el.scrollHeight : 0
  currentPage.value++
  try {
    const data = await api(`/c2c/${encodeURIComponent(selectedUserId.value)}/messages?page=${currentPage.value}&pageSize=${pageSize}`)
    messages.value = [...messages.value, ...(data.records || [])]
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - prevHeight
  } catch (e) { notice.value = e.message; currentPage.value-- }
  finally { loadingMore.value = false }
}

function onScroll() {
  const el = messageListRef.value
  if (!el || loadingMore.value || !hasMore.value) return
  if (el.scrollTop < 60) loadMore()
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function sendMessage() {
  if (!canSend.value) return
  sending.value = true
  try {
    if (msgType.value === 'stream') {
      const body = { userOpenId: selectedUserId.value, content: draft.value }
      if (replyTo.value) body.replyMessageId = replyTo.value.messageOpenId
      await api('/c2c/stream', { method: 'POST', body: JSON.stringify(body) })
    } else {
      const body = { userOpenId: selectedUserId.value, msgType: msgType.value, content: draft.value.trim() }
      if (msgType.value === 'markdown') body.content = body.content.replace(/@([A-F0-9]{32})/g, '<qqbot-at-user id="$1" />')
      if (msgType.value === 'image' && imageData.value) { body.imageType = 'base64'; body.imageValue = imageData.value }
      if (replyTo.value) body.replyMessageId = replyTo.value.messageOpenId
      await api('/c2c/send', { method: 'POST', body: JSON.stringify(body) })
    }
    draft.value = ''; imageData.value = null; pastePreview.value = null; replyTo.value = null; notice.value = '消息已发送'
    await loadLatestMessages()
  } catch (e) { notice.value = e.message }
  finally { sending.value = false }
}

function renderContent(message) {
  let text = message.content || ''
  text = renderFaceTags(text)
  text = text.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
  text = text.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  return text
}

function hasArk(message) {
  return hasArkMessage(message?.ark)
}

function parseAttach(raw) {
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr)) return []
    return arr.map(normalizeAttachment).filter(Boolean)
  } catch { return [] }
}

function normalizeAttachment(att) {
  const contentType = att?.content_type || ''
  if (att?.url && contentType.startsWith('image/')) {
    return { ...att, type: 'image' }
  }
  if (contentType === 'voice') {
    return {
      ...att,
      type: 'voice',
      asrText: att.asr_refer_text || '',
      voiceUrl: att.voice_wav_url || ''
    }
  }
  return null
}

function onPaste(e) {
  if (msgType.value !== 'image') return
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const blob = item.getAsFile()
      const reader = new FileReader()
      reader.onload = () => { imageData.value = reader.result.split(',')[1]; pastePreview.value = reader.result }
      reader.readAsDataURL(blob)
      return
    }
  }
}

function onFilePicked(e) {
  const file = e.target.files?.[0]
  if (!file || !file.type.startsWith('image/')) return
  const reader = new FileReader()
  reader.onload = () => {
    imageData.value = reader.result.split(',')[1]
    pastePreview.value = reader.result
  }
  reader.readAsDataURL(file)
  e.target.value = ''
}

function clearSelectedImage() {
  imageData.value = null
  pastePreview.value = null
}

function onContextMenu(e, message) { ctxMenu.visible = true; ctxMenu.x = e.clientX; ctxMenu.y = e.clientY; ctxMenu.message = message }

async function copyText(text) {
  try { await navigator.clipboard.writeText(text || '') } catch { /* ignore */ }
}

function startReply(message) {
  replyTo.value = message
}

async function recallMsg(message) {
  try {
    await api('/c2c/recall', {
      method: 'POST',
      body: JSON.stringify({ userOpenId: message.unionOpenId, messageId: message.messageOpenId })
    })
    recalledIds[message.messageOpenId] = true
    notice.value = '消息已撤回'
  } catch (e) {
    notice.value = e.message
  }
}

function atUser(message) {
  const tag = `@${message.unionOpenId}`
  draft.value = draft.value ? draft.value + ' ' + tag : tag
}

function fmtTime(ts) {
  if (!ts) return ''
  const d = new Date(ts.includes('T') ? ts : ts.replace(' ', 'T'))
  if (isNaN(d.getTime())) return ts
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatStatsTime(value) {
  if (!value) return '-'
  const raw = String(value)
  const date = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return raw
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatNumber(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return num.toLocaleString('zh-CN')
}

function avatarText(message) {
  const name = message.username || (isMe(message) ? 'Bot' : '?')
  return name.slice(0, 1).toUpperCase()
}

function avatarTextForUser(user) {
  const value = (user?.username || user?.userOpenId || '?').trim()
  return value ? value.slice(0, 1).toUpperCase() : '?'
}

watch(msgType, () => { pastePreview.value = null; imageData.value = null })
</script>
