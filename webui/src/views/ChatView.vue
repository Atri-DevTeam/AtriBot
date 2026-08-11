<template>
  <!-- legacy-chat：与群聊/私聊页一样，把 polish.css 的新版外观层排除掉，本页样式全部走 chat.css -->
  <div class="shell legacy-chat">
    <AppSidebar ref="sidebarRef" v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loadingConvs" @click="loadConversations()">刷新</button>
        <button class="ghost-button" title="恢复列表宽度、输入框高度和导航栏状态" @click="resetLayout">重置</button>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer" />

    <main class="chatnt" :class="{ 'chatnt--chat-open': mobileChatOpen }"
          :style="{ '--chat-list-width': listWidth + 'px' }">
      <!-- ── 左：会话列表 ── -->
      <section class="chatnt-list">
        <div class="chatnt-list-head">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <label class="chatnt-search">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.5" y2="16.5"/>
            </svg>
            <input v-model="search" placeholder="搜索" />
          </label>
          <button class="chatnt-refresh" :class="{ spin: loadingConvs }" title="刷新会话" aria-label="刷新会话" @click="loadConversations()">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 1 1-2.64-6.36"/><polyline points="21 3 21 9 15 9"/>
            </svg>
          </button>
        </div>

        <div class="chatnt-items" @scroll="onConvListScroll">
          <button v-for="c in filteredConvs" :key="convKey(c)"
                  class="cnv" :class="{ active: isActive(c), pinned: isPinned(c) }"
                  @click="selectConv(c)">
            <span class="cnv-avatar" :style="c.type === 'group' ? groupTileStyle(c.openId) : null">
              <template v-if="c.type === 'c2c'">
                <span>{{ convAvatarText(c) }}</span>
                <img v-if="userAvatarUrl(c.openId) && !avatarFailed['cnv-' + c.openId]"
                     :src="userAvatarUrl(c.openId)" :alt="convName(c)"
                     referrerpolicy="no-referrer"
                     @error="avatarFailed['cnv-' + c.openId] = true" />
              </template>
              <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </span>
            <span class="cnv-body">
              <span class="cnv-top">
                <span class="cnv-name">{{ convName(c) }}</span>
                <span class="cnv-time">{{ fmtListTime(c) }}</span>
              </span>
              <span class="cnv-preview">{{ convPreview(c) }}</span>
            </span>
            <!-- 图钉：已置顶常驻，未置顶悬停才出现。只有点图钉才切换置顶 -->
            <span class="cnv-pin" role="button" tabindex="-1"
                  :title="isPinned(c) ? '取消置顶' : '置顶'"
                  :aria-label="isPinned(c) ? '取消置顶' : '置顶'"
                  @click.stop="togglePin(c)">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="17" x2="12" y2="22"/>
                <path d="M9 3h6l-1 6 3 3v2H7v-2l3-3-1-6z"/>
              </svg>
            </span>
          </button>
          <div v-if="loadingConvs && conversations.length === 0" class="empty-state">正在加载会话</div>
          <div v-else-if="filteredConvs.length === 0" class="empty-state">暂无会话</div>
          <template v-if="!loadingConvs && conversations.length > 0 && !search">
            <div v-if="loadingMoreConvs" class="load-tip">正在加载更多会话…</div>
            <div v-else-if="!hasMoreConvs" class="load-tip">— 没有更多会话了 —</div>
          </template>
        </div>
      </section>

      <!-- 列表/聊天窗之间的拖拽分隔条，手机端两栏是互斥全屏，CSS 里隐藏 -->
      <div class="chatnt-splitter" :class="{ dragging: resizingList }"
           role="separator" aria-orientation="vertical" title="拖拽调整列表宽度"
           @mousedown="startListResize" @touchstart="startListResize"
           @dblclick="listWidth = DEFAULT_LIST_WIDTH; saveLayout()"></div>

      <!-- ── 右：聊天窗口 ── -->
      <section class="chatnt-main">
        <template v-if="!active">
          <div class="chatnt-placeholder">
            <img :src="atriImg" alt="" />
            <p>选择一个会话开始查看</p>
          </div>
        </template>
        <template v-else>
          <header class="chatnt-head">
            <button class="chatnt-back" aria-label="返回会话列表" @click="mobileChatOpen = false">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <div class="chatnt-head-info">
              <div class="chatnt-head-name">{{ activeConv ? convHeaderTitle(activeConv) : active.openId }}</div>
              <div class="chatnt-head-sub">{{ active.type === 'group' ? '群聊' : '私聊' }} · {{ active.openId }}</div>
            </div>
            <span class="status-pill"><span class="dot ok"></span>{{ totalMessages }} 条</span>
            <!-- 侧栏按钮：手机和桌面都靠点击展开，不做常驻 -->
            <button v-if="active.type === 'group'" class="chatnt-members-btn"
                    :class="{ active: panel === 'members' }" title="群成员" aria-label="群成员"
                    @click="togglePanel('members')">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </button>
            <button class="chatnt-members-btn" :class="{ active: panel === 'info' || panel === 'user' }"
                    title="信息与设置" aria-label="信息与设置" @click="togglePanel('info')">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9"/><line x1="12" y1="11" x2="12" y2="16"/><line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
            </button>
          </header>

          <div v-if="notice" class="chatnt-notice">{{ notice }}</div>

          <div ref="messageListRef" class="chatnt-msgs" @scroll="onScroll">
            <div v-if="loadingMore" class="load-tip">加载更早的消息…</div>
            <div v-else-if="!hasMore && messages.length > 0" class="load-tip">— 没有更早的消息了 —</div>
            <div v-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
            <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

            <article v-for="message in orderedMessages" :key="message.id"
                     :data-message-id="message.id"
                     class="qm" :class="{ mine: isMe(message), highlighted: highlightedMessageId === message.id }">
              <span class="qm-avatar" title="点击显示/隐藏 ID" @click="toggleUid(message.id)">
                <span>{{ avatarText(message) }}</span>
                <img v-if="avatarUrl(message) && !avatarFailed[message.id]"
                     :src="avatarUrl(message)" :alt="message.username"
                     referrerpolicy="no-referrer"
                     @error="avatarFailed[message.id] = true" />
              </span>
              <div class="qm-main">
                <div class="qm-name" :class="{ 'uid-expanded': expandedIds[message.id] }">
                  <span class="qm-name-text">{{ displayName(message) }}</span>
                  <!-- 自己发的入库时 senderIsBot 恒为 true，isMe 再兜一层防止字段缺失时漏标。
                       图案沿用旧页面，配色改走 currentColor 交给 CSS 管 -->
                  <svg v-if="isMe(message) || message.senderIsBot" class="qm-bot"
                       width="13" height="13" viewBox="0 0 64 64" role="img" aria-label="机器人">
                    <line x1="32" y1="10" x2="32" y2="18" stroke="currentColor" stroke-width="3.5" stroke-linecap="round"/>
                    <circle cx="32" cy="8" r="4" fill="none" stroke="currentColor" stroke-width="3.5"/>
                    <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="currentColor" stroke-width="3.5"/>
                    <rect x="24" y="28" width="4" height="8" rx="2" fill="currentColor"/>
                    <rect x="36" y="28" width="4" height="8" rx="2" fill="currentColor"/>
                  </svg>
                  <!-- 只标群主/管理员，普通成员不标 -->
                  <span v-if="!isMe(message) && active.type === 'group' && isSpecialRole(message.memberRole)"
                        class="qm-role" :class="'role-' + message.memberRole.toLowerCase()">{{ roleLabel(message.memberRole) }}</span>
                  <span v-if="displayUid(message)" class="qm-uid">{{ displayUid(message) }}</span>
                </div>
                <div class="qm-bubble" :class="{ recalled: recalledIds[message.messageOpenId] }"
                     @contextmenu.prevent.stop="onContextMenu($event, message)">
                  <pre v-if="recalledIds[message.messageOpenId]">{{ isMe(message) ? '你撤回了一条消息' : '该消息已被撤回' }}</pre>
                  <template v-else>
                    <div v-if="msgRef(message)" class="qm-ref qm-ref--clickable"
                         title="跳转到引用来源" @click.stop="jumpToReference(message)">
                      <span class="qm-ref-author">{{ msgRef(message).author || '引用消息' }}</span>
                      <div class="qm-ref-content" v-html="renderRefContent(msgRef(message))"></div>
                    </div>
                    <div v-if="message.attachments" class="qm-attach">
                      <template v-for="(att, i) in parseAttach(message.attachments)" :key="message.id + '-' + i">
                        <img v-if="att.type === 'image' && !attachFailed[att.url]"
                             :src="att.url" :alt="att.filename"
                             referrerpolicy="no-referrer"
                             @error="attachFailed[att.url] = true"
                             @click="previewImg = att.url" />
                        <span v-else-if="att.type === 'image'" class="attach-fail">📎 {{ att.filename }}</span>
                        <div v-else-if="att.type === 'video'" class="qm-video">
                          <template v-if="att.url && !attachFailed[att.url]">
                            <video :src="att.url" controls playsinline preload="metadata"
                                   @error="attachFailed[att.url] = true"></video>
                            <!-- 放大按钮单独放角上：点视频主体是播放/暂停，不能兼作放大 -->
                            <button type="button" class="qm-video-expand" title="放大查看" aria-label="放大查看"
                                    @click.stop="previewVideo = att.url">
                              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/>
                                <line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/>
                              </svg>
                            </button>
                          </template>
                          <a v-else-if="att.url" class="attach-fail" :href="att.url" target="_blank" rel="noreferrer">
                            🎬 {{ att.filename || '视频' }}（点击在新标签打开）
                          </a>
                          <span v-else class="attach-fail">🎬 {{ att.filename || '视频' }}</span>
                        </div>
                        <div v-else-if="att.type === 'voice'" class="qm-voice">
                          <div class="qm-voice-title">语音消息</div>
                          <div v-if="att.asrText" class="qm-voice-asr">{{ att.asrText }}</div>
                          <audio v-if="att.voiceUrl" :src="att.voiceUrl" controls preload="none"></audio>
                          <a v-else-if="att.url" :href="att.url" target="_blank" rel="noreferrer">打开原始音频</a>
                        </div>
                        <a v-else-if="att.type === 'file'" class="qm-file"
                           :href="att.url" target="_blank" rel="noreferrer" :title="att.filename">
                          <span class="qm-file-icon">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                              <polyline points="14 2 14 8 20 8"/>
                            </svg>
                          </span>
                          <span class="qm-file-body">
                            <span class="qm-file-name">{{ att.filename || '文件' }}</span>
                            <span class="qm-file-size">{{ fmtSize(att.size) || '点击下载' }}</span>
                          </span>
                        </a>
                      </template>
                    </div>
                    <ArkMessageCard v-if="hasArk(message)" :ark="message.ark" />
                    <pre v-if="!hasArk(message) && message.messageType !== 2 && renderContent(message)">{{ renderContent(message) }}</pre>
                    <div v-if="!hasArk(message) && message.messageType === 2" class="md-body" v-html="renderMd(renderContent(message))"></div>
                  </template>
                </div>
                <div class="qm-time">{{ fmtMsgTime(message.eventTimestamp || message.createdAt) }}</div>
              </div>
            </article>
          </div>

          <div v-if="replyTo" class="chatnt-replybar">
            <span>{{ refMode ? '引用' : '回复' }} {{ replyTo.username || '...' }}：{{ replyPreview }}</span>
            <button aria-label="取消回复" @click="cancelReply">×</button>
          </div>

          <form class="chatnt-composer" @submit.prevent="sendMessage">
            <div class="chatnt-tools">
              <div class="chatnt-type">
                <label :class="{ active: msgType === 'text' }"><input type="radio" v-model="msgType" value="text" />文本</label>
                <label :class="{ active: msgType === 'markdown' }"><input type="radio" v-model="msgType" value="markdown" />MD</label>
                <label :class="{ active: msgType === 'image' }"><input type="radio" v-model="msgType" value="image" />图片</label>
                <label v-if="active.type === 'c2c'" :class="{ active: msgType === 'stream' }"><input type="radio" v-model="msgType" value="stream" />流式</label>
              </div>
              <button v-if="msgType === 'image'" type="button" class="chatnt-tool-btn" title="上传图片" aria-label="上传图片" @click="$refs.fileInputRef.click()">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/>
                </svg>
              </button>
              <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="onFilePicked" />
            </div>
            <img v-if="pastePreview" :src="pastePreview" class="chatnt-paste-preview" title="点击清除" @click="clearSelectedImage" />
            <div class="chatnt-resize-handle" :class="{ dragging: resizingComposer }"
                 title="拖拽调整输入框高度"
                 @mousedown="startComposerResize" @touchstart="startComposerResize"></div>
            <textarea ref="composerRef" v-model="draft" :disabled="sending"
                      :style="{ height: composerHeight + 'px' }"
                      :placeholder="composerPlaceholder"
                      @paste="onPaste"
                      @keydown.enter.exact.prevent="sendMessage"></textarea>
            <div class="chatnt-composer-foot">
              <span class="chatnt-hint">Enter 发送 · Shift+Enter 换行</span>
              <button class="chatnt-send" :disabled="!canSend">{{ sending ? '发送中…' : '发送' }}</button>
            </div>
          </form>

          <!-- ── 右侧栏：成员 / 信息 / 单用户设置 ── -->
          <div v-if="panel" class="chatnt-members-backdrop" @click="closePanel" />
          <aside v-if="panel" class="chatnt-members">
            <div class="chatnt-members-head">
              <button v-if="panel === 'user'" class="chatnt-members-close" aria-label="返回"
                      @click="panel = active.type === 'group' ? 'members' : 'info'">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="15 18 9 12 15 6"/>
                </svg>
              </button>
              <div>
                <div class="chatnt-members-title">
                  {{ panel === 'members' ? '群成员' : panel === 'user' ? '用户设置' : (active.type === 'group' ? '群聊信息' : '用户信息') }}
                </div>
                <div class="chatnt-members-sub">
                  <template v-if="panel === 'members'">{{ members.length }} 人发过言</template>
                  <template v-else-if="panel === 'user'">{{ profileName || profileTarget }}</template>
                  <template v-else>{{ active.openId }}</template>
                </div>
              </div>
              <button class="chatnt-members-close" aria-label="关闭" @click="closePanel">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>

            <!-- ═══ 成员列表 ═══ -->
            <template v-if="panel === 'members'">
              <label class="chatnt-members-search">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.5" y2="16.5"/>
                </svg>
                <input v-model="memberSearch" placeholder="搜索成员" />
              </label>

              <div class="chatnt-members-list">
                <div v-if="loadingMembers" class="chatnt-members-empty">正在加载</div>
                <div v-else-if="filteredMembers.length === 0" class="chatnt-members-empty">没有匹配的成员</div>
                <template v-for="section in memberSections" :key="section.key">
                  <div v-if="section.items.length" class="chatnt-members-group">{{ section.label }}（{{ section.items.length }}）</div>
                  <div v-for="m in section.items" :key="m.unionOpenId" class="mbr">
                    <span class="mbr-avatar">
                      <span>{{ (m.username || '?').slice(0, 1).toUpperCase() }}</span>
                      <img v-if="userAvatarUrl(m.unionOpenId) && !avatarFailed['mbr-' + m.unionOpenId]"
                           :src="userAvatarUrl(m.unionOpenId)" :alt="m.username"
                           referrerpolicy="no-referrer"
                           @error="avatarFailed['mbr-' + m.unionOpenId] = true" />
                    </span>
                    <button class="mbr-body" title="点击 @ 该成员" @click="atMember(m)">
                      <span class="mbr-top">
                        <span class="mbr-name">{{ m.username || 'Unknown' }}</span>
                        <span v-if="isSpecialRole(m.memberRole)" class="qm-role"
                              :class="'role-' + m.memberRole.toLowerCase()">{{ roleLabel(m.memberRole) }}</span>
                      </span>
                      <span class="mbr-sub">{{ m.messageCount }} 条 · {{ fmtMemberTime(m.lastActiveAt) }}</span>
                    </button>
                    <button class="mbr-cog" title="用户设置" aria-label="用户设置"
                            @click.stop="openProfile(m.unionOpenId, m.username)">
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="3"/>
                        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                      </svg>
                    </button>
                  </div>
                </template>
              </div>
            </template>

            <!-- ═══ 会话信息 ═══ -->
            <div v-else-if="panel === 'info'" class="chatnt-members-list chatnt-info">
              <template v-if="active.type === 'group'">
                <div class="chatnt-info-section">
                  <div class="chatnt-info-label chatnt-info-label-line">
                    <span>群聊资料</span>
                    <button class="nt-mini-btn" :disabled="syncingGroupProfile" @click="syncGroupProfile">
                      <svg :class="{ spin: syncingGroupProfile }" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 12a9 9 0 1 1-2.64-6.36"/><polyline points="21 3 21 9 15 9"/>
                      </svg>
                      {{ syncingGroupProfile ? '同步中' : '同步' }}
                    </button>
                  </div>
                  <div class="nt-card">
                    <div class="nt-row"><span class="nt-row-label">群名</span><span class="nt-row-value">{{ groupDisplayName(groupMeta) }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">群 OpenId</span><span class="nt-row-value">{{ groupMeta?.groupOpenId || active.openId }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">成员数</span><span class="nt-row-value">{{ groupMeta?.groupMemberNum ?? '-' }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">加群时间</span><span class="nt-row-value">{{ fmtGroupTime(groupMeta?.joinedAt) }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">Bot 群内 ID</span><span class="nt-row-value">{{ groupMeta?.memberOpenid || '-' }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">Bot 身份</span><span class="nt-row-value">{{ groupRoleLabel(groupMeta?.memberRole) }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">主动消息</span><span class="nt-row-value">{{ groupMeta?.allowProactiveMsg ? '允许' : '未允许' }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">接收消息</span><span class="nt-row-value">{{ recvMsgSettingLabel(groupMeta?.recvMsgSetting) }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">群分类</span><span class="nt-row-value">{{ groupMeta?.groupClassText || '-' }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">群标签</span><span class="nt-row-value">{{ groupTagsText(groupMeta?.groupTags) }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">群简介</span><span class="nt-row-value">{{ groupMeta?.groupFingerMemo || '-' }}</span></div>
                  </div>
                </div>

                <div class="chatnt-info-section">
                  <div class="chatnt-info-label">群聊设置</div>
                  <div class="nt-card">
                    <div class="nt-row">
                      <span class="nt-row-label">白名单</span>
                      <button class="nt-switch" :class="{ on: groupMeta?.whitelist }" role="switch"
                              :aria-checked="!!groupMeta?.whitelist" :disabled="!groupMeta"
                              @click="toggleGroupStatus('whitelist')"><span class="nt-switch-knob" /></button>
                    </div>
                    <div class="nt-row">
                      <span class="nt-row-label">黑名单</span>
                      <button class="nt-switch" :class="{ on: groupMeta?.blacklisted }" role="switch"
                              :aria-checked="!!groupMeta?.blacklisted" :disabled="!groupMeta"
                              @click="toggleGroupStatus('blacklist')"><span class="nt-switch-knob" /></button>
                    </div>
                    <div class="nt-row">
                      <span class="nt-row-label">真实群号</span>
                      <input class="nt-input" v-model="realGroupInput" placeholder="未设置"
                             @blur="saveRealGroup" @keydown.enter="saveRealGroup" />
                    </div>
                    <div class="nt-row">
                      <span class="nt-row-label">邀请人</span>
                      <span class="nt-row-value">{{ groupMeta?.opMemberOpenId || '-' }}</span>
                    </div>
                  </div>
                </div>

                <div class="chatnt-info-section">
                  <div class="chatnt-info-label chatnt-info-label-line">
                    <span>群禁言状态</span>
                    <button class="nt-mini-btn" :disabled="!canQueryMuteState || muteStateLoading" @click="queryMuteState">
                      <svg :class="{ spin: muteStateLoading }" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 12a9 9 0 1 1-2.64-6.36"/><polyline points="21 3 21 9 15 9"/>
                      </svg>
                      {{ muteStateLoading ? '查询中' : '查询' }}
                    </button>
                  </div>
                  <!-- 查询群禁言状态要求机器人是群管理员，不是就禁用，连点都不让点 -->
                  <div v-if="!canQueryMuteState" class="nt-empty">机器人不是群管理员，无法查询</div>
                  <template v-else>
                    <div v-if="muteStateError" class="nt-empty">{{ muteStateError }}</div>
                    <div v-else-if="muteState" class="nt-card">
                      <div class="nt-row">
                        <span class="nt-row-label">全员禁言</span>
                        <span class="nt-row-value">{{ muteModeLabel(muteState.globalRule?.mode) }}</span>
                      </div>
                      <!-- schedule 模式下把定时/周期规则明细列出来，别跟始终禁言混成一个标签 -->
                      <template v-if="muteState.globalRule?.mode === 'schedule'">
                        <div v-if="(muteState.globalRule.scheduleRules || []).length" class="nt-mute-sub">
                          <div class="nt-mute-sub-title">定时禁言（{{ muteState.globalRule.scheduleRules.length }}）</div>
                          <div v-for="r in muteState.globalRule.scheduleRules" :key="r.taskId" class="nt-row">
                            <span class="nt-row-label nt-row-label-ellipsis">{{ fmtMuteRuleTime(r.startAt) }} ~ {{ fmtMuteRuleTime(r.endAt) }}</span>
                            <span class="nt-row-value" :class="{ off: !r.enabled }">{{ r.enabled ? '启用' : '停用' }}</span>
                          </div>
                        </div>
                        <div v-if="(muteState.globalRule.recurringRules || []).length" class="nt-mute-sub">
                          <div class="nt-mute-sub-title">周期禁言（{{ muteState.globalRule.recurringRules.length }}）</div>
                          <div v-for="r in muteState.globalRule.recurringRules" :key="r.taskId" class="nt-row">
                            <span class="nt-row-label nt-row-label-ellipsis">{{ fmtRecurring(r) }}</span>
                            <span class="nt-row-value" :class="{ off: !r.enabled }">{{ r.enabled ? '启用' : '停用' }}</span>
                          </div>
                        </div>
                      </template>
                      <div class="nt-row">
                        <span class="nt-row-label">禁言成员</span>
                        <span class="nt-row-value">{{ (muteState.members || []).length }} 人</span>
                      </div>
                      <div v-if="(muteState.members || []).length" class="nt-card nt-mute-members">
                        <div v-for="m in muteState.members" :key="m.memberOpenId" class="nt-row">
                          <span class="nt-row-label nt-row-label-ellipsis">{{ m.username || shortId(m.memberOpenId) }}</span>
                          <span class="nt-row-value">{{ fmtGroupTime(m.muteExpireAt) }}</span>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>

                <div class="chatnt-info-section">
                  <div class="chatnt-info-label">功能开关</div>
                  <div class="nt-card">
                    <div v-if="funcEntries.length === 0" class="nt-empty">暂无功能配置</div>
                    <div v-for="[key, cfg] in funcEntries" :key="key" class="nt-row">
                      <span class="nt-row-label">{{ key }}</span>
                      <button class="nt-switch" :class="{ on: cfg.enabled }" role="switch"
                              :aria-checked="!!cfg.enabled"
                              @click="toggleFunction(key, !cfg.enabled)"><span class="nt-switch-knob" /></button>
                    </div>
                  </div>
                  <div v-if="addableFunctionKeys.length" class="nt-add">
                    <select class="nt-select" v-model="newFunctionKey">
                      <option value="">选择功能</option>
                      <option v-for="k in addableFunctionKeys" :key="k" :value="k">{{ k }}</option>
                    </select>
                    <button class="nt-btn" :disabled="!newFunctionKey" @click="addFunctionKey">添加</button>
                  </div>
                </div>
              </template>

              <!-- 私聊：信息面板直接就是对端的用户设置 -->
              <template v-else>
                <UserProfileForm :profile="profile" :role-options="ROLE_OPTIONS"
                                 :saving="profileSaving" :error="profileError"
                                 @save="saveProfile" @add-perm="addPermNode" @remove-perm="removePerm" />
              </template>

              <div class="chatnt-info-section">
                <div class="chatnt-info-label">统计</div>
                <div class="nt-card">
                  <div v-if="convStatsError" class="nt-empty">{{ convStatsError }}</div>
                  <div v-else-if="!convStats" class="nt-empty">加载中</div>
                  <template v-else>
                    <template v-if="active.type === 'group'">
                      <div class="nt-row"><span class="nt-row-label">收到消息</span><span class="nt-row-value">{{ convStats.receivedMessages }}</span></div>
                      <div class="nt-row"><span class="nt-row-label">发出消息</span><span class="nt-row-value">{{ convStats.sentMessages }}</span></div>
                      <div class="nt-row"><span class="nt-row-label">活跃人数</span><span class="nt-row-value">{{ convStats.activeUsers }}</span></div>
                    </template>
                    <template v-else>
                      <div class="nt-row"><span class="nt-row-label">私聊收到</span><span class="nt-row-value">{{ convStats.c2cReceivedMessages }}</span></div>
                      <div class="nt-row"><span class="nt-row-label">私聊发出</span><span class="nt-row-value">{{ convStats.c2cSentMessages }}</span></div>
                      <div class="nt-row"><span class="nt-row-label">群内消息</span><span class="nt-row-value">{{ convStats.groupReceivedMessages }}</span></div>
                    </template>
                    <div class="nt-row"><span class="nt-row-label">首次记录</span><span class="nt-row-value">{{ convStats.firstSeenAt || '-' }}</span></div>
                    <div class="nt-row"><span class="nt-row-label">最近记录</span><span class="nt-row-value">{{ convStats.lastSeenAt || '-' }}</span></div>
                  </template>
                </div>
              </div>

              <div class="chatnt-info-section chatnt-clear-section">
                <div class="chatnt-info-label">清除聊天记录</div>
                <div class="nt-card chatnt-clear-card">
                  <div class="chatnt-clear-modes" role="group" aria-label="清除范围">
                    <button type="button" :class="{ active: clearForm.mode === 'all' }"
                            @click="clearForm.mode = 'all'">全部</button>
                    <button type="button" :class="{ active: clearForm.mode === 'first' }"
                            @click="clearForm.mode = 'first'">前 N 条</button>
                    <button type="button" :class="{ active: clearForm.mode === 'range' }"
                            @click="clearForm.mode = 'range'">日期范围</button>
                  </div>
                  <label v-if="clearForm.mode === 'first'" class="chatnt-clear-field">
                    <span>清除前</span>
                    <input v-model.number="clearForm.count" class="nt-input" type="number" min="1" max="1000000" step="1" />
                    <span>条</span>
                  </label>
                  <div v-if="clearForm.mode === 'range'" class="chatnt-clear-range">
                    <label class="chatnt-clear-field"><span>从</span><input v-model="clearForm.start" class="nt-input" type="date" /></label>
                    <label class="chatnt-clear-field"><span>到</span><input v-model="clearForm.end" class="nt-input" type="date" /></label>
                  </div>
                  <button type="button" class="chatnt-clear-submit" :disabled="clearForm.loading"
                          @click="clearCurrentConversation">
                    {{ clearForm.loading ? '清除中…' : '清除记录' }}
                  </button>
                  <div class="chatnt-clear-hint">仅影响当前{{ active.type === 'group' ? '群聊' : '用户' }}，统计数据不会改变</div>
                </div>
              </div>
            </div>

            <!-- ═══ 单用户设置 ═══ -->
            <div v-else-if="panel === 'user'" class="chatnt-members-list chatnt-info">
              <UserProfileForm :profile="profile" :role-options="ROLE_OPTIONS"
                               :saving="profileSaving" :error="profileError"
                               @save="saveProfile" @add-perm="addPermNode" @remove-perm="removePerm" />
            </div>
          </aside>
        </template>
      </section>
    </main>

    <div v-if="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
      <button v-if="active && active.type === 'group' && !isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
              @click="atUser(ctxMenu.message); ctxMenu.visible = false">@ 用户</button>
      <button v-if="!isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
              @click="openProfile(ctxMenu.message.unionOpenId, ctxMenu.message.username); ctxMenu.visible = false">用户设置</button>
      <!-- 禁言不做身份预判：机器人没权限时接口会报错，直接把错误甩给用户看 -->
      <button v-if="active && active.type === 'group' && !isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
              @click.stop="openMutePanel(ctxMenu.message); ctxMenu.visible = false">禁言</button>
      <button v-if="active && active.type === 'group' && !isMe(ctxMenu.message) && ctxMenu.message.unionOpenId"
              @click.stop="unmuteMember(ctxMenu.message); ctxMenu.visible = false">解除禁言</button>
      <button @click="startReply(ctxMenu.message); ctxMenu.visible = false">回复</button>
      <!-- 引用回复走 refMessageId，群聊私聊都支持，但要求来源消息有 ref_idx。
           缺 ref_idx 时置灰而不是隐藏，否则看不出是「不支持」还是「这条不行」 -->
      <button :disabled="!ctxMenu.message.refIdx"
              :title="ctxMenu.message.refIdx ? '' : '这条消息没有记录 ref_idx（引用所需），无法引用'"
              @click="startRefReply(ctxMenu.message); ctxMenu.visible = false">引用回复</button>
      <button @click="copyText(ctxMenu.message.content); ctxMenu.visible = false">复制</button>
      <!-- 别人的消息也给撤回入口，能不能撤由官方接口判定，前端不预判权限 -->
      <button v-if="!recalledIds[ctxMenu.message.messageOpenId]"
              class="ctx-recall"
              @click="recallMsg(ctxMenu.message); ctxMenu.visible = false">撤回</button>
    </div>

    <div v-if="previewImg || previewVideo" class="lightbox" @click="closePreview">
      <img v-if="previewImg" :src="previewImg" referrerpolicy="no-referrer" alt="预览" @click.stop />
      <video v-else :src="previewVideo" controls autoplay playsinline @click.stop></video>
    </div>

    <!-- 禁言设置弹窗：仿权限设置的大 modal，Teleport 到 body 顶层，不依赖父级 v-if -->
    <Teleport to="body">
      <div v-if="mutePanel.visible" class="mute-modal-backdrop" @click="mutePanel.visible = false"></div>
      <div v-if="mutePanel.visible" class="mute-modal" @click.stop>
        <div class="mute-modal-head">
          <div class="mute-modal-title">禁言设置</div>
          <button class="mute-modal-close" aria-label="关闭" @click="mutePanel.visible = false">×</button>
        </div>
        <div class="mute-modal-body">
          <div class="mute-modal-target">对 {{ mutePanel.message?.username || '该成员' }} 执行禁言</div>
          <div class="mute-picker" aria-label="禁言时长">
            <div v-for="field in MUTE_DURATION_FIELDS" :key="field.key"
                 class="mute-picker-field" :class="{ open: mutePickerOpen === field.key }">
              <button type="button" class="mute-picker-trigger"
                      :aria-expanded="mutePickerOpen === field.key"
                      :aria-label="`选择${field.label}`"
                      @click.stop="toggleMutePicker(field.key)">
                <span class="mute-picker-value">{{ muteDuration[field.key] }}</span>
                <span class="mute-picker-unit">{{ field.label }}</span>
                <span class="mute-picker-chevron" aria-hidden="true"></span>
              </button>
              <div v-if="mutePickerOpen === field.key" ref="mutePickerMenuEl" class="mute-picker-menu" role="listbox">
                <button v-for="value in field.options" :key="field.key + '-' + value" type="button"
                        class="mute-picker-option" :class="{ selected: muteDuration[field.key] === value }"
                        role="option" :aria-selected="muteDuration[field.key] === value"
                        @click.stop="selectMuteValue(field.key, value)">
                  {{ value }}
                </button>
              </div>
            </div>
          </div>
          <div class="mute-modal-summary">时长 {{ muteDurationText }}</div>
        </div>
        <div class="mute-modal-foot">
          <button type="button" class="mute-modal-btn" @click="mutePanel.visible = false">取消</button>
          <button type="button" class="mute-modal-btn mute-modal-btn--primary" @click="confirmMute">确认禁言</button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { LEGACY_TOKEN_KEY, API_BASE } from '../router.js'
import { renderFaceTags } from '../messageRender.js'
import { escapeHtml, renderMarkdown as renderMd } from '../lib/markdown.js'
import { hasArkMessage } from '../lib/ark.js'
import AppSidebar from '../components/AppSidebar.vue'
import ArkMessageCard from '../components/ArkMessageCard.vue'
import UserProfileForm from '../components/UserProfileForm.vue'

const router = useRouter()

const atriImg = import.meta.env.BASE_URL + 'img/atri-main.png'

const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')

const conversations = ref([])
const pinnedKeys = ref([])
const loadingConvs = ref(false)
const CONV_LIST_PAGE_SIZE = 100
const hasMoreConvs = ref(true)
const loadingMoreConvs = ref(false)
const search = ref('')
const active = ref(null)               // { type: 'group' | 'c2c', openId }
const mobileChatOpen = ref(false)

const messages = ref([])
const totalMessages = ref(0)
const loadingMessages = ref(false)
const loadingMore = ref(false)
const currentPage = ref(0)
const pageSize = 80

const sending = ref(false)
const draft = ref('')
const msgType = ref('text')
const imageData = ref(null)
const pastePreview = ref(null)
const replyTo = ref(null)
const refMode = ref(false)

const COMPOSER_MIN_HEIGHT = 36
const composerHeight = ref(COMPOSER_MIN_HEIGHT)
const resizingComposer = ref(false)

const DEFAULT_LIST_WIDTH = 296
const LIST_MIN_WIDTH = 200
const LAYOUT_KEY = 'atri.webui.chat_layout'
const listWidth = ref(DEFAULT_LIST_WIDTH)
const resizingList = ref(false)

// 右侧栏：null | 'members' | 'info' | 'user'，三个视图共用一个抽屉
const panel = ref(null)
const members = ref([])
const loadingMembers = ref(false)
const memberSearch = ref('')

// ── 会话信息（群）──
const groupMeta = ref(null)
const realGroupInput = ref('')
const syncingGroupProfile = ref(false)
const funcEntries = ref([])
const knownFunctionKeys = ref([])
const newFunctionKey = ref('')
const convStats = ref(null)
const convStatsError = ref('')
const clearForm = reactive({ mode: 'all', count: 100, start: '', end: '', loading: false })
const muteState = ref(null)
const muteStateLoading = ref(false)
const muteStateError = ref('')

// ── 用户档案（私聊对端 / 群成员）──
const profileTarget = ref('')
const profileName = ref('')
const profile = reactive({ role: 'USER', permissions: [], blocked: false, ignored: false, c2cPush: true })
const profileSaving = ref(false)
const profileError = ref('')
const ROLE_OPTIONS = [
  { key: 'USER', label: '普通' },
  { key: 'ADMIN', label: '管理员' },
  { key: 'OWNER', label: '所有者' }
]

const highlightedMessageId = ref(null)
const notice = ref('')
let highlightTimer = null
let noticeTimer = null

const sidebarOpen = ref(false)
const sidebarRef = ref(null)
const previewImg = ref(null)
const previewVideo = ref(null)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const recalledIds = reactive({})
const avatarFailed = reactive({})
const attachFailed = reactive({})
// 手机端点头像展开 ID（PC 端 ID 常驻，这个状态不参与）
const expandedIds = reactive({})

function toggleUid(id) {
  expandedIds[id] = !expandedIds[id]
}

const messageListRef = ref(null)
const fileInputRef = ref(null)
const composerRef = ref(null)

let eventSource = null
let convRefreshTimer = null

const activeConv = computed(() =>
  active.value ? conversations.value.find(c => c.type === active.value.type && c.openId === active.value.openId) : null
)

const filteredConvs = computed(() => {
  const q = search.value.trim().toLowerCase()
  const list = q
    ? conversations.value.filter(c =>
      convName(c).toLowerCase().includes(q) ||
      c.openId.toLowerCase().includes(q) ||
      convPreview(c).toLowerCase().includes(q))
    : conversations.value
  // 置顶的浮到最前，两组内部各自保持后端给的时间倒序（Array.sort 在现代引擎里是稳定的）
  return [...list].sort((a, b) => (isPinned(b) ? 1 : 0) - (isPinned(a) ? 1 : 0))
})

// 已加载的「非置顶」会话数：置顶会话由后端单独前置、不计入分页偏移，翻页 offset 只按它算
const nonPinnedLoadedCount = computed(() =>
  conversations.value.filter(c => !isPinned(c)).length
)

const filteredMembers = computed(() => {
  const q = memberSearch.value.trim().toLowerCase()
  if (!q) return members.value
  return members.value.filter(m =>
    (m.username || '').toLowerCase().includes(q) ||
    (m.unionOpenId || '').toLowerCase().includes(q)
  )
})

// 仿 QQ NT：按身份分组，后端已排好序，这里只做分桶
const memberSections = computed(() => {
  const list = filteredMembers.value
  return [
    { key: 'owner', label: '群主', items: list.filter(m => m.memberRole === 'OWNER') },
    { key: 'admin', label: '管理员', items: list.filter(m => m.memberRole === 'ADMIN') },
    { key: 'member', label: '成员', items: list.filter(m => !isSpecialRole(m.memberRole)) }
  ]
})

const orderedMessages = computed(() => [...messages.value].reverse())
const hasMore = computed(() => messages.value.length < totalMessages.value)

const canSend = computed(() => {
  if (!active.value || sending.value) return false
  if (msgType.value === 'image') return !!imageData.value
  return !!draft.value.trim()
})

const composerPlaceholder = computed(() => {
  if (msgType.value === 'image') return '图片说明文字（可选），粘贴或上传图片'
  if (msgType.value === 'markdown') return 'Markdown 内容'
  if (msgType.value === 'stream') return '每行一个 delta'
  return '发送消息'
})

const replyPreview = computed(() => {
  if (!replyTo.value) return ''
  const text = stripPreviewTags(replyTo.value.content || '')
  return text.length > 30 ? text.slice(0, 30) + '…' : text
})

onMounted(async () => {
  document.addEventListener('click', onDocumentClick)
  window.addEventListener('resize', clampListWidth)
  loadLayout()
  await loadConfig()
  await Promise.all([loadConversations(), loadPinned()])
  connectSse()
  applyDeepLink()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('resize', clampListWidth)
  if (eventSource) eventSource.close()
  if (convRefreshTimer) clearTimeout(convRefreshTimer)
  if (highlightTimer) clearTimeout(highlightTimer)
  if (noticeTimer) clearTimeout(noticeTimer)
  stopComposerResize()
  stopListResize()
})

function onDocumentClick(e) {
  if (!e.target.closest('.ctx-menu')) ctxMenu.visible = false
  if (!e.target.closest('.mute-picker')) mutePickerOpen.value = null
}

function closePreview() {
  previewImg.value = null
  previewVideo.value = null
}

// ═══════════════ 输入框拖拽调高（自定义手柄，不用原生 textarea resize 角）═══════════════

let composerResizeStartY = 0
let composerResizeStartHeight = 0

function startComposerResize(e) {
  e.preventDefault()
  const point = e.touches ? e.touches[0] : e
  composerResizeStartY = point.clientY
  composerResizeStartHeight = composerHeight.value
  resizingComposer.value = true
  document.addEventListener('mousemove', onComposerResizeMove)
  document.addEventListener('mouseup', stopComposerResize)
  document.addEventListener('touchmove', onComposerResizeMove, { passive: false })
  document.addEventListener('touchend', stopComposerResize)
  document.body.style.cursor = 'ns-resize'
  document.body.style.userSelect = 'none'
}

function onComposerResizeMove(e) {
  const point = e.touches ? e.touches[0] : e
  if (e.touches) e.preventDefault()
  // 手柄在输入框上方：往上拖 = 变高，往下拖 = 变矮
  const delta = composerResizeStartY - point.clientY
  const maxHeight = window.innerHeight * 0.32
  composerHeight.value = Math.min(maxHeight, Math.max(COMPOSER_MIN_HEIGHT, composerResizeStartHeight + delta))
}

function stopComposerResize() {
  const wasResizing = resizingComposer.value
  resizingComposer.value = false
  document.removeEventListener('mousemove', onComposerResizeMove)
  document.removeEventListener('mouseup', stopComposerResize)
  document.removeEventListener('touchmove', onComposerResizeMove)
  document.removeEventListener('touchend', stopComposerResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  if (wasResizing) saveLayout()
}

// ═══════════════ 会话列表拖拽调宽 ═══════════════

let listResizeStartX = 0
let listResizeStartWidth = 0

// 上限跟着窗口走：窗口再窄也不能让列表把聊天窗挤没
function listMaxWidth() {
  return Math.max(LIST_MIN_WIDTH, Math.min(560, window.innerWidth * 0.5))
}

function clampListWidth() {
  listWidth.value = Math.min(listMaxWidth(), Math.max(LIST_MIN_WIDTH, listWidth.value))
}

function startListResize(e) {
  e.preventDefault()
  const point = e.touches ? e.touches[0] : e
  listResizeStartX = point.clientX
  listResizeStartWidth = listWidth.value
  resizingList.value = true
  document.addEventListener('mousemove', onListResizeMove)
  document.addEventListener('mouseup', stopListResize)
  document.addEventListener('touchmove', onListResizeMove, { passive: false })
  document.addEventListener('touchend', stopListResize)
  document.body.style.cursor = 'ew-resize'
  document.body.style.userSelect = 'none'
}

function onListResizeMove(e) {
  const point = e.touches ? e.touches[0] : e
  if (e.touches) e.preventDefault()
  const delta = point.clientX - listResizeStartX
  listWidth.value = Math.min(listMaxWidth(), Math.max(LIST_MIN_WIDTH, listResizeStartWidth + delta))
}

function stopListResize() {
  const wasResizing = resizingList.value
  resizingList.value = false
  document.removeEventListener('mousemove', onListResizeMove)
  document.removeEventListener('mouseup', stopListResize)
  document.removeEventListener('touchmove', onListResizeMove)
  document.removeEventListener('touchend', stopListResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  if (wasResizing) saveLayout()
}

// ═══════════════ 布局记忆 ═══════════════

function loadLayout() {
  try {
    const saved = JSON.parse(localStorage.getItem(LAYOUT_KEY) || '{}')
    if (Number.isFinite(saved.listWidth)) listWidth.value = saved.listWidth
    if (Number.isFinite(saved.composerHeight)) {
      composerHeight.value = Math.max(COMPOSER_MIN_HEIGHT, saved.composerHeight)
    }
  } catch { /* 存的值坏了就用默认布局 */ }
  clampListWidth()
}

function saveLayout() {
  try {
    localStorage.setItem(LAYOUT_KEY, JSON.stringify({
      listWidth: Math.round(listWidth.value),
      composerHeight: Math.round(composerHeight.value)
    }))
  } catch { /* 隐私模式下 localStorage 可能不可写，忽略 */ }
}

// 恢复出厂布局：列表宽度、输入框高度，以及侧边栏的收窄状态
function resetLayout() {
  listWidth.value = DEFAULT_LIST_WIDTH
  composerHeight.value = COMPOSER_MIN_HEIGHT
  try {
    localStorage.removeItem(LAYOUT_KEY)
  } catch { /* ignore */ }
  sidebarRef.value?.resetCollapsed()
}

// ═══════════════ API 基础 ═══════════════

async function api(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
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

async function logout() {
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' })
  } catch { /* ignore */ }
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  router.replace('/login')
}

async function loadConfig() {
  try {
    const data = await api('/config')
    appId.value = data.appId || ''
    botOpenId.value = data.botOpenId || ''
    botName.value = data.botName || 'AtriBot'
  } catch { /* ignore */ }
}

// ═══════════════ 会话列表 ═══════════════

async function loadConversations() {
  if (loadingConvs.value) return
  loadingConvs.value = true
  try {
    // 按「当前已加载的非置顶会话数」重取：首屏 100，SSE 刷新时保持已加载深度，
    // 新消息让某个会话实时跳到顶部、其余整体重排（QQ 式）
    const depth = Math.min(Math.max(nonPinnedLoadedCount.value, CONV_LIST_PAGE_SIZE), 1000)
    const data = await api(`/chat/conversations?limit=${depth}&offset=0`) || { items: [], hasMore: true }
    conversations.value = data.items || []
    hasMoreConvs.value = data.hasMore !== false
  } catch { /* 静默失败，保留旧列表 */ }
  finally { loadingConvs.value = false }
}

async function loadMoreConversations() {
  if (loadingMoreConvs.value || !hasMoreConvs.value || loadingConvs.value || search.value.trim()) return
  loadingMoreConvs.value = true
  // offset 只统计非置顶会话：置顶的由后端单独前置，不计入分页偏移
  const offset = nonPinnedLoadedCount.value
  try {
    const data = await api(`/chat/conversations?limit=${CONV_LIST_PAGE_SIZE}&offset=${offset}`) || { items: [], hasMore: false }
    const existing = new Set(conversations.value.map(c => convKey(c)))
    const fresh = (data.items || []).filter(c => !existing.has(convKey(c)))
    conversations.value = [...conversations.value, ...fresh]
    hasMoreConvs.value = data.hasMore !== false
  } catch { /* ignore */ }
  finally { loadingMoreConvs.value = false }
}

function onConvListScroll(e) {
  const el = e.currentTarget
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 120) {
    loadMoreConversations()
  }
}

function scheduleConvRefresh() {
  if (convRefreshTimer) clearTimeout(convRefreshTimer)
  convRefreshTimer = setTimeout(() => { loadConversations() }, 800)
}

function convKey(c) {
  return `${c.type}:${c.openId}`
}

// ═══════════════ 会话置顶 ═══════════════

async function loadPinned() {
  try {
    pinnedKeys.value = await api('/chat/pinned') || []
  } catch { /* 拿不到就当没有置顶，不影响会话列表 */ }
}

function isPinned(c) {
  return pinnedKeys.value.includes(convKey(c))
}

async function togglePin(c) {
  const key = convKey(c)
  const next = !pinnedKeys.value.includes(key)
  const previous = pinnedKeys.value
  // 先本地翻转，列表立刻重排；接口以返回的全量列表为准，失败则回滚
  pinnedKeys.value = next ? [...previous, key] : previous.filter(k => k !== key)
  try {
    pinnedKeys.value = await api('/chat/pinned', {
      method: 'POST',
      body: JSON.stringify({ key, pinned: next })
    }) || []
  } catch (error) {
    pinnedKeys.value = previous
    showNotice(error.message || '置顶设置失败')
  }
}

function isActive(c) {
  return active.value && active.value.type === c.type && active.value.openId === c.openId
}

function convName(c) {
  if (c.type === 'c2c') return c.name || shortId(c.openId)
  return c.name || (c.realGroupId ? `群 ${c.realGroupId}` : `群 ${shortId(c.openId)}`)
}

// 聊天顶部标题：群名优先，其次真实群号/群 openId，兜底时不做截断。
function convHeaderName(c) {
  if (c.type === 'c2c') return c.name || c.openId
  return c.name || (c.realGroupId ? `群 ${c.realGroupId}` : `群 ${c.openId}`)
}

function convHeaderTitle(c) {
  const title = convHeaderName(c)
  if (c.type !== 'group') return title
  const count = c.groupMemberNum ?? (groupMeta.value?.groupOpenId === c.openId ? groupMeta.value?.groupMemberNum : null)
  return Number.isFinite(Number(count)) && Number(count) > 0 ? `${title} (${count})` : title
}

function shortId(id) {
  if (!id) return '?'
  return id.length > 10 ? id.slice(0, 8) + '…' : id
}

function stripPreviewTags(text) {
  let t = renderFaceTags(text || '')
  t = t.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@…')
  t = t.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  t = t.replace(/<@[A-F0-9]+>/g, '@…')
  return t.replace(/\s+/g, ' ').trim()
}

function convPreview(c) {
  let body = ''
  if (c.lastArk) {
    body = '[卡片消息]'
  } else if (c.lastAttachments) {
    const atts = parseAttach(c.lastAttachments)
    if (atts.some(a => a.type === 'image')) body = '[图片]'
    else if (atts.some(a => a.type === 'video')) body = '[视频]'
    else if (atts.some(a => a.type === 'voice')) body = '[语音]'
    else if (atts.some(a => a.type === 'file')) body = '[文件]'
  }
  if (!body) body = stripPreviewTags(c.lastContent) || ' '
  if (c.lastSenderIsBot) return `${botName.value}: ${body}`
  if (c.type === 'group') {
    // 群消息 username 为空时不拿群自身 openId 顶替发送者名字，避免误导
    const sender = c.lastSenderName || 'Unknown'
    return `${sender}: ${body}`
  }
  return body
}

function convAvatarText(c) {
  const value = (convName(c) || '?').trim()
  return value ? value.slice(0, 1).toUpperCase() : '?'
}

function groupTileStyle(openId) {
  let h = 0
  for (const ch of openId || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return { background: `hsl(${h % 360}, 42%, 62%)` }
}

function userAvatarUrl(openId) {
  if (!appId.value || !openId) return null
  return `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${openId}/640`
}

function fmtListTime(c) {
  const d = parseChatTime(c.lastEventTimestamp) || parseChatTime(c.lastCreatedAt)
  if (!d) return ''
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  if (sameDay(d, now)) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(d, yesterday)) return '昨天'
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}-${pad(d.getDate())}`
  return `${d.getFullYear()}-${d.getMonth() + 1}-${pad(d.getDate())}`
}

// ═══════════════ 消息 ═══════════════

// 群聊和私聊的消息接口路径同构，只差前缀
function messagesBase() {
  const id = encodeURIComponent(active.value.openId)
  return active.value.type === 'group' ? `/groups/${id}/messages` : `/c2c/${id}/messages`
}

function messagesPath(page) {
  return `${messagesBase()}?page=${page}&pageSize=${pageSize}`
}

// 用于丢弃「会话已切换/已有更新的加载」之后才返回的旧响应，避免串会话消息
let convLoadSeq = 0
function currentConvKey() {
  return active.value ? `${active.value.type}:${active.value.openId}` : null
}

async function selectConv(c) {
  if (active.value && isActive(c)) {
    mobileChatOpen.value = true
    return
  }
  active.value = { type: c.type, openId: c.openId }
  mobileChatOpen.value = true
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  cancelReply()
  ctxMenu.visible = false
  mutePanel.visible = false
  closePanel()
  members.value = []
  memberSearch.value = ''
  groupMeta.value = null
  funcEntries.value = []
  convStats.value = null
  convStatsError.value = ''
  muteState.value = null
  muteStateError.value = ''
  draft.value = ''
  imageData.value = null
  pastePreview.value = null
  if (msgType.value === 'stream' && c.type !== 'c2c') msgType.value = 'text'
  await loadLatestMessages()
}

// 深链：/?group=xxx 或 /?user=xxx 直接打开对应会话（群/用户列表页「进入聊天」跳转用）
// 需要 watch query：/?group=A → /?group=B 不会重挂载组件，只能靠路由变化触发
function applyDeepLink() {
  const q = router.currentRoute.value.query
  const target = q.group
    ? { type: 'group', openId: String(q.group) }
    : q.user
      ? { type: 'c2c', openId: String(q.user) }
      : null
  if (target && !isActive(target)) selectConv(target)
}

watch(() => router.currentRoute.value.query, applyDeepLink)

async function loadLatestMessages() {
  if (!active.value) return
  const targetKey = currentConvKey()
  const seq = ++convLoadSeq
  loadingMessages.value = true
  currentPage.value = 1
  try {
    const data = await api(messagesPath(1))
    // 加载期间会话被切换，或又发起了更新的一次加载：这次响应已经过期，丢弃
    if (seq !== convLoadSeq || currentConvKey() !== targetKey) return
    messages.value = data.records || []
    totalMessages.value = data.total || 0
    await nextTick()
    scrollToBottom()
  } catch { /* ignore */ }
  finally { if (seq === convLoadSeq) loadingMessages.value = false }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value || loadingMessages.value || !active.value) return
  const targetKey = currentConvKey()
  loadingMore.value = true
  const el = messageListRef.value
  const prevHeight = el ? el.scrollHeight : 0
  currentPage.value++
  try {
    const data = await api(messagesPath(currentPage.value))
    if (currentConvKey() !== targetKey) return
    messages.value = [...messages.value, ...(data.records || [])]
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - prevHeight
  } catch { currentPage.value-- }
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

function isNearBottom() {
  const el = messageListRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

// ═══════════════ 右侧栏：成员 / 信息 / 单用户设置 ═══════════════

function closePanel() {
  panel.value = null
}

async function togglePanel(name) {
  if (panel.value === name) { closePanel(); return }
  panel.value = name
  if (name === 'members') await loadMembers()
  if (name === 'info') await loadInfoPanel()
}

async function loadInfoPanel() {
  convStats.value = null
  convStatsError.value = ''
  if (!active.value) return
  if (active.value.type === 'group') {
    await Promise.all([loadGroupMeta(), loadGroupFunctions(), loadConvStats()])
  } else {
    await Promise.all([openProfile(active.value.openId, activeConv.value?.name, false), loadConvStats()])
  }
}

// ── 群信息 ──

function groupDisplayName(meta) {
  if (!meta) return activeConv.value?.name || (active.value?.openId ? `群 ${active.value.openId}` : '-')
  return meta.groupName || activeConv.value?.name || (meta.realGroupId ? `群 ${meta.realGroupId}` : `群 ${meta.groupOpenId || active.value?.openId}`)
}

function recvMsgSettingLabel(value) {
  const map = {
    only_mention: '仅 @',
    mention_and_context: '@ 与上下文',
    all: '全部消息'
  }
  return map[value] || value || '-'
}

function groupRoleLabel(value) {
  const map = { OWNER: '群主', ADMIN: '管理员', MEMBER: '成员' }
  return map[value] || value || '-'
}

// 查询群禁言状态要求机器人拥有群管理员身份，群主也算
const canQueryMuteState = computed(() => {
  const role = groupMeta.value?.memberRole
  return role === 'OWNER' || role === 'ADMIN'
})

function muteModeLabel(mode) {
  const map = { none: '未开启', always: '始终禁言', schedule: '定时禁言' }
  return map[mode] || mode || '未开启'
}

const WEEKDAY_LABELS = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

// 定时规则起止时间是 RFC3339，只取 HH:mm
function fmtMuteRuleTime(value) {
  if (!value) return '-'
  const d = parseChatTime(value)
  if (!d) return value
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 周期规则：星期几 + 时段，end_time 小于 start_time 表示跨天到次日
function fmtRecurring(r) {
  const weekdays = Array.isArray(r.weekdays) && r.weekdays.length
    ? r.weekdays.map(w => WEEKDAY_LABELS[w] || w).join(' ')
    : '每天'
  const range = `${r.startTime || '-'}~${r.endTime || '-'}${(r.endTime && r.startTime && r.endTime < r.startTime) ? '（次日）' : ''}`
  return `${weekdays} ${range}`
}

async function queryMuteState() {
  if (!active.value || active.value.type !== 'group' || !canQueryMuteState.value) return
  const groupOpenId = active.value.openId
  muteStateLoading.value = true
  muteStateError.value = ''
  try {
    const data = await api(`/groups/${encodeURIComponent(groupOpenId)}/mute-state`)
    // 请求期间切走了会话，结果作废
    if (!active.value || active.value.type !== 'group' || active.value.openId !== groupOpenId) return
    muteState.value = data || null
  } catch (error) {
    muteState.value = null
    muteStateError.value = error.message || '查询禁言状态失败'
  } finally {
    muteStateLoading.value = false
  }
}

function groupTagsText(tags) {
  if (!Array.isArray(tags) || tags.length === 0) return '-'
  return tags.filter(Boolean).join('、') || '-'
}

function fmtGroupTime(value) {
  if (!value) return '-'
  const d = parseChatTime(value)
  if (!d) return value
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadGroupMeta() {
  try {
    const list = await api('/groups') || []
    groupMeta.value = list.find(g => g.groupOpenId === active.value.openId) || null
    realGroupInput.value = groupMeta.value?.realGroupId || ''
  } catch (error) { showNotice(error.message || '加载群信息失败') }
}

async function syncGroupProfile() {
  if (!active.value || active.value.type !== 'group') return
  const groupOpenId = active.value.openId
  syncingGroupProfile.value = true
  try {
    const data = await api(`/groups/${encodeURIComponent(groupOpenId)}/profile/sync`, { method: 'POST' })
    if (!active.value || active.value.type !== 'group' || active.value.openId !== groupOpenId) return
    groupMeta.value = data || null
    realGroupInput.value = groupMeta.value?.realGroupId || ''
    await loadConversations()
    showNotice('群资料已同步')
  } catch (error) {
    showNotice(error.message || '同步群资料失败')
  } finally {
    syncingGroupProfile.value = false
  }
}

async function toggleGroupStatus(kind) {
  const g = groupMeta.value
  if (!g) return
  // 接口路径用 blacklist，模型字段却叫 blacklisted
  const field = kind === 'whitelist' ? 'whitelist' : 'blacklisted'
  const next = !g[field]
  try {
    await api(`/groups/${encodeURIComponent(g.groupOpenId)}/${kind}?enabled=${next}`, { method: 'POST' })
    g[field] = next
  } catch (error) { showNotice(error.message || '修改失败') }
}

async function saveRealGroup() {
  const g = groupMeta.value
  if (!g) return
  const val = String(realGroupInput.value || '').trim()
  if (val === String(g.realGroupId || '')) return
  try {
    await api(`/groups/${encodeURIComponent(g.groupOpenId)}/real-group-id?value=${encodeURIComponent(val || 'null')}`, { method: 'POST' })
    g.realGroupId = val ? Number(val) : null
    scheduleConvRefresh()
  } catch (error) {
    realGroupInput.value = g.realGroupId || ''
    showNotice(error.message || '保存失败')
  }
}

async function loadGroupFunctions() {
  try {
    const [config, keys] = await Promise.all([
      api(`/groups/${encodeURIComponent(active.value.openId)}/functions`),
      knownFunctionKeys.value.length ? Promise.resolve(knownFunctionKeys.value) : api('/groups/functions/keys')
    ])
    funcEntries.value = Object.entries(config || {})
    knownFunctionKeys.value = keys || []
  } catch (error) { showNotice(error.message || '加载功能配置失败') }
}

const addableFunctionKeys = computed(() => {
  const owned = new Set(funcEntries.value.map(([k]) => k))
  return knownFunctionKeys.value.filter(k => !owned.has(k))
})

async function toggleFunction(key, enabled) {
  try {
    await api(`/groups/${encodeURIComponent(active.value.openId)}/functions/${encodeURIComponent(key)}?enabled=${enabled}`, { method: 'POST' })
    const hit = funcEntries.value.find(([k]) => k === key)
    if (hit) hit[1] = { ...hit[1], enabled }
    else funcEntries.value = [...funcEntries.value, [key, { enabled }]]
  } catch (error) { showNotice(error.message || '切换失败') }
}

async function addFunctionKey() {
  const key = newFunctionKey.value
  if (!key) return
  await toggleFunction(key, true)
  newFunctionKey.value = ''
}

// ── 统计 ──

async function loadConvStats() {
  const isGroup = active.value.type === 'group'
  const path = isGroup
    ? `/public/official/groups/${encodeURIComponent(active.value.openId)}`
    : `/public/official/users/${encodeURIComponent(active.value.openId)}`
  try {
    convStats.value = await api(path)
  } catch (error) {
    convStatsError.value = error.message || '暂无统计数据'
  }
}

async function clearCurrentConversation() {
  if (!active.value || clearForm.loading) return
  if (clearForm.mode === 'first' && (!Number.isInteger(clearForm.count) || clearForm.count < 1)) {
    showNotice('请输入有效的清除条数')
    return
  }
  if (clearForm.mode === 'range' && (!clearForm.start || !clearForm.end || clearForm.end < clearForm.start)) {
    showNotice('请选择有效的日期范围')
    return
  }

  const scopeLabel = active.value.type === 'group' ? '当前群聊' : '当前用户'
  const actionLabel = clearForm.mode === 'all'
    ? '全部聊天记录'
    : clearForm.mode === 'first'
      ? `最早的 ${clearForm.count} 条聊天记录`
      : `${clearForm.start} 至 ${clearForm.end} 的聊天记录`
  if (!confirm(`确认清除${scopeLabel}的${actionLabel}吗？统计数据会保留，删除后无法恢复。`)) return

  clearForm.loading = true
  try {
    const path = active.value.type === 'group'
      ? `/groups/${encodeURIComponent(active.value.openId)}/messages`
      : `/c2c/${encodeURIComponent(active.value.openId)}/messages`
    const body = {mode: clearForm.mode}
    if (clearForm.mode === 'first') body.count = clearForm.count
    if (clearForm.mode === 'range') {
      body.start = clearForm.start
      body.end = clearForm.end
    }
    const result = await api(path, {method: 'DELETE', body: JSON.stringify(body)})
    messages.value = []
    totalMessages.value = 0
    currentPage.value = 0
    await Promise.all([loadConversations(), loadConvStats()])
    showNotice(`已清除 ${result?.deleted || 0} 条记录`)
  } catch (error) {
    showNotice(error.message || '清除聊天记录失败')
  } finally {
    clearForm.loading = false
  }
}

// ── 用户档案（群成员和私聊对端共用一套 /c2c/{id}/profile）──

async function openProfile(userOpenId, displayName, switchPanel = true) {
  profileTarget.value = userOpenId
  profileName.value = displayName || ''
  profileError.value = ''
  if (switchPanel) panel.value = 'user'
  try {
    const data = await api(`/c2c/${encodeURIComponent(userOpenId)}/permissions`)
    profile.role = data?.role || 'USER'
    profile.permissions = [...(data?.permissions || [])]
    profile.blocked = data?.isBlocked || false
    profile.ignored = data?.isIgnored || false
    profile.c2cPush = data?.c2cPush !== false
  } catch {
    // 档案还不存在是正常情况，按默认值让用户直接建
    profile.role = 'USER'
    profile.permissions = []
    profile.blocked = false
    profile.ignored = false
    profile.c2cPush = true
  }
}

function addPermNode(value) {
  if (!value || profile.permissions.includes(value)) return
  profile.permissions = [...profile.permissions, value]
}

function removePerm(perm) {
  profile.permissions = profile.permissions.filter(p => p !== perm)
}

async function saveProfile() {
  if (!profileTarget.value) return
  profileSaving.value = true
  profileError.value = ''
  try {
    await api(`/c2c/${encodeURIComponent(profileTarget.value)}/profile`, {
      method: 'POST',
      body: JSON.stringify({
        role: profile.role,
        permissions: profile.permissions,
        blocked: profile.blocked,
        ignored: profile.ignored,
        c2cPush: profile.c2cPush
      })
    })
    showNotice('已保存')
  } catch (error) {
    profileError.value = error.message || '保存失败'
  } finally {
    profileSaving.value = false
  }
}

async function loadMembers() {
  if (!active.value || active.value.type !== 'group') return
  const targetKey = currentConvKey()
  loadingMembers.value = true
  try {
    const data = await api(`/groups/${encodeURIComponent(active.value.openId)}/members`)
    // 拉取期间切了会话就丢弃，避免把别的群的成员显示出来
    if (currentConvKey() !== targetKey) return
    members.value = data || []
  } catch (error) {
    showNotice(error.message || '加载群成员失败')
  } finally {
    loadingMembers.value = false
  }
}

// 点成员就把 @ 塞进输入框，和右键菜单的「@ 用户」一致
function atMember(member) {
  if (!member.unionOpenId) return
  const tag = `@${member.unionOpenId}`
  draft.value = draft.value ? draft.value + ' ' + tag : tag
  closePanel()
}

function fmtMemberTime(ts) {
  if (!ts) return '未知'
  const d = parseChatTime(ts)
  if (!d) return ts
  const pad = n => String(n).padStart(2, '0')
  const now = new Date()
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}-${pad(d.getDate())}`
  return `${d.getFullYear()}-${d.getMonth() + 1}-${pad(d.getDate())}`
}

// ═══════════════ 引用消息跳转 ═══════════════

function showNotice(text) {
  notice.value = text
  if (noticeTimer) clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 2400)
}

function highlightMessage(id) {
  highlightedMessageId.value = id
  const el = messageListRef.value?.querySelector(`[data-message-id="${id}"]`)
  if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' })
  if (highlightTimer) clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => { highlightedMessageId.value = null }, 1800)
}

// 引用数据里指向来源的那个 id，键名各版本不一，逐层深搜
function findRefIdxValue(value) {
  if (!value || typeof value !== 'object') return ''
  const keys = ['msg_idx', 'msgIdx', 'ref_idx', 'refIdx', 'message_id', 'messageId', 'msg_id', 'msgId']
  for (const key of keys) {
    const candidate = value[key]
    if (typeof candidate === 'string' && candidate.trim()) return candidate.trim()
  }
  for (const item of Array.isArray(value) ? value : Object.values(value)) {
    const found = findRefIdxValue(item)
    if (found) return found
  }
  return ''
}

function getRefTargetMsgIdx(message) {
  try {
    const raw = message.messageReference
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    const fromRef = findRefIdxValue(parsed)
    if (fromRef) return fromRef
  } catch { /* 解析失败就走内容匹配 */ }
  // 兼容旧版 WebUI 主动引用记录：当时把被引用消息的 msg_idx 暂存在本记录 refIdx
  if ((message.eventType === 'BOT_SEND' || message.senderIsBot) && message.refIdx) {
    return message.refIdx
  }
  return ''
}

async function jumpToReference(message) {
  if (!active.value) return
  const msgIdx = getRefTargetMsgIdx(message)
  if (!msgIdx) {
    showNotice('引用来源消息缺少 ref_idx，无法定位')
    return
  }
  const targetKey = currentConvKey()
  try {
    // 定位统一走后端按 refIdx 定位，前端不再做内容/附件匹配（那套容易跳错）
    const params = new URLSearchParams({
      msgIdx,
      pageSize: String(pageSize),
      excludeId: String(message.id)
    })
    const location = await api(`${messagesBase()}/ref?${params}`)
    const page = location.page || 1
    const data = await api(messagesPath(page))
    // 请求期间可能已经切走了会话，翻页结果不能再往新会话里塞
    if (currentConvKey() !== targetKey) return
    messages.value = data.records || []
    totalMessages.value = data.total || totalMessages.value
    currentPage.value = page
    await nextTick()
    highlightMessage(location.record.id)
  } catch (error) {
    showNotice(error.message || '定位引用来源失败')
  }
}

function connectSse() {
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`${API_BASE}/events`, { withCredentials: true })
  eventSource.onmessage = async (e) => {
    try {
      const payload = JSON.parse(e.data)
      if (payload.type !== 'refresh' && payload.type !== 'c2c_refresh') return
      scheduleConvRefresh()
      if (!active.value) return
      const matchesActive =
        (payload.type === 'refresh' && active.value.type === 'group' && payload.groupOpenId === active.value.openId) ||
        (payload.type === 'c2c_refresh' && active.value.type === 'c2c' && payload.userOpenId === active.value.openId)
      if (!matchesActive) return
      const targetKey = currentConvKey()
      const seq = ++convLoadSeq
      const data = await api(messagesPath(1))
      // 这段等待期间会话被切换，或又发起了更新的一次加载：这次响应已经过期，丢弃
      if (seq !== convLoadSeq || currentConvKey() !== targetKey) return
      const latest = data.records || []
      const seen = new Set(messages.value.map(m => m.messageOpenId))
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

// ═══════════════ 发送 ═══════════════

async function sendMessage() {
  if (!canSend.value) return
  sending.value = true
  const type = active.value.type
  try {
    if (msgType.value === 'stream' && type === 'c2c') {
      const body = { userOpenId: active.value.openId, content: draft.value }
      if (replyTo.value) body.replyMessageId = replyTo.value.messageOpenId
      await api('/c2c/stream', { method: 'POST', body: JSON.stringify(body) })
    } else {
      const body = { msgType: msgType.value, content: draft.value.trim() }
      if (type === 'group') body.groupOpenId = active.value.openId
      else body.userOpenId = active.value.openId
      if (msgType.value === 'markdown') {
        body.content = body.content.replace(/@([A-F0-9]{32})/g, '<qqbot-at-user id="$1" />')
      }
      if (msgType.value === 'image' && imageData.value) {
        body.imageType = 'base64'
        body.imageValue = imageData.value
      }
      if (replyTo.value) {
        if (refMode.value) {
          // 引用回复：后端用 refMessageId 走 GroupChat/C2CChat.refMessage，
          // 同时要把来源的展示数据一起带上，否则历史里那条引用块是空的
          body.refMessageId = replyTo.value.refIdx
          body.refAuthor = replyTo.value.username || ''
          body.refContent = replyTo.value.content || ''
          body.refAttachments = replyTo.value.attachments || null
        } else {
          body.replyMessageId = replyTo.value.messageOpenId
        }
      }
      await api(type === 'group' ? '/groups/send' : '/c2c/send', { method: 'POST', body: JSON.stringify(body) })
    }
    draft.value = ''
    imageData.value = null
    pastePreview.value = null
    cancelReply()
    await loadLatestMessages()
    scheduleConvRefresh()
  } catch { /* ignore：发送失败保留草稿 */ }
  finally {
    sending.value = false
    // 发送期间 textarea 是 disabled 的，浏览器会把焦点甩回 body。
    // 等这一帧把 disabled 撤掉之后再收回焦点，否则连着按 Enter 发消息要重新点输入框。
    await nextTick()
    composerRef.value?.focus()
  }
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

watch(msgType, () => { pastePreview.value = null; imageData.value = null })

// ═══════════════ 消息操作 ═══════════════

function onContextMenu(e, message) {
  ctxMenu.visible = true
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.message = message
}

function startReply(message) {
  replyTo.value = message
  refMode.value = false
}

// 引用回复：带 message_reference 主动发，来源消息会显示成可点的引用块
function startRefReply(message) {
  replyTo.value = message
  refMode.value = true
}

function cancelReply() {
  replyTo.value = null
  refMode.value = false
}

async function copyText(text) {
  try { await navigator.clipboard.writeText(text || '') } catch { /* ignore */ }
}

function atUser(message) {
  const tag = `@${message.unionOpenId}`
  draft.value = draft.value ? draft.value + ' ' + tag : tag
}

// 禁言弹窗：老 QQ 风格四段时长选择，提交时换算为后端需要的总秒数
const mutePanel = reactive({ visible: false, message: null })
const mutePickerOpen = ref(null)
const mutePickerMenuEl = ref(null)
const muteDuration = reactive({ days: 0, hours: 1, minutes: 0, seconds: 0 })
const MUTE_DURATION_FIELDS = [
  { key: 'days', label: '天', options: Array.from({ length: 31 }, (_, i) => i) },
  { key: 'hours', label: '小时', options: Array.from({ length: 24 }, (_, i) => i) },
  { key: 'minutes', label: '分钟', options: Array.from({ length: 60 }, (_, i) => i) },
  { key: 'seconds', label: '秒', options: Array.from({ length: 60 }, (_, i) => i) }
]

// 官方接口禁言时长上限 30 天
const MAX_MUTE_SECONDS = 30 * 86400

const muteTotalSeconds = computed(() =>
  muteDuration.days * 86400 +
  muteDuration.hours * 3600 +
  muteDuration.minutes * 60 +
  muteDuration.seconds
)

const muteDurationText = computed(() => {
  const parts = [
    [muteDuration.days, '天'],
    [muteDuration.hours, '小时'],
    [muteDuration.minutes, '分钟'],
    [muteDuration.seconds, '秒']
  ].filter(([value]) => value > 0)
  return parts.length ? parts.map(([value, label]) => `${value} ${label}`).join(' ') : '0 秒'
})

// 右键禁言：群消息记录的 unionOpenId 存的就是群内 member_openid，可直接传给后端
function toggleMutePicker(key) {
  mutePickerOpen.value = mutePickerOpen.value === key ? null : key
  if (mutePickerOpen.value) {
    nextTick(() => {
      const menu = mutePickerMenuEl.value
      if (!menu) return
      const selected = menu.querySelector('.mute-picker-option.selected')
      if (selected) {
        menu.scrollTop = selected.offsetTop - menu.clientHeight / 2 + selected.offsetHeight / 2
      }
    })
  }
}

function selectMuteValue(key, value) {
  muteDuration[key] = value
  mutePickerOpen.value = null
}
function openMutePanel(message) {
  mutePanel.message = message
  muteDuration.days = 0
  muteDuration.hours = 1
  muteDuration.minutes = 0
  muteDuration.seconds = 0
  mutePanel.visible = true
}

async function confirmMute() {
  const m = mutePanel.message
  if (!m || !active.value || active.value.type !== 'group') return
  const seconds = muteTotalSeconds.value
  if (!Number.isFinite(seconds) || seconds <= 0) {
    showNotice('禁言时长无效')
    return
  }
  if (seconds > MAX_MUTE_SECONDS) {
    showNotice('禁言时长不能超过 30 天')
    return
  }
  const target = m.username || '该成员'
  const durationText = muteDurationText.value
  // 先关面板，接口结果晚点再弹
  mutePanel.visible = false
  try {
    await api(`/groups/${encodeURIComponent(active.value.openId)}/mute`, {
      method: 'POST',
      body: JSON.stringify({ memberOpenId: m.unionOpenId, seconds })
    })
    showNotice(`已禁言 ${target} ${durationText}`)
  } catch (error) {
    // 没权限等错误原样抛给用户，前端不做身份判断
    showNotice(error.message || '禁言失败')
  }
}

async function unmuteMember(message) {
  if (!message || !active.value || active.value.type !== 'group') return
  const target = message.username || '该成员'
  try {
    await api(`/groups/${encodeURIComponent(active.value.openId)}/unmute`, {
      method: 'POST',
      body: JSON.stringify({ memberOpenId: message.unionOpenId })
    })
    showNotice(`已解除 ${target} 的禁言`)
  } catch (error) {
    showNotice(error.message || '解除禁言失败')
  }
}

async function recallMsg(message) {
  if (!active.value) return
  try {
    if (active.value.type === 'group') {
      await api('/groups/recall', {
        method: 'POST',
        body: JSON.stringify({ groupOpenId: active.value.openId, messageId: message.messageOpenId })
      })
    } else {
      await api('/c2c/recall', {
        method: 'POST',
        body: JSON.stringify({ userOpenId: active.value.openId, messageId: message.messageOpenId })
      })
    }
    recalledIds[message.messageOpenId] = true
  } catch (error) {
    // 撤回别人的消息常被官方接口拒掉，静默失败会让人以为按钮没生效
    showNotice(error.message || '撤回失败')
  }
}

// ═══════════════ 渲染辅助 ═══════════════

/*
 * 「是不是我们自己发的」不能只看 senderIsBot —— 那个字段来自 user.isBot()，
 * 群里其他机器人的消息同样是 true，会被误判成自己。
 * 我们自己发的消息入库时 event_type 固定为 BOT_SEND、union_openId 固定为
 * 配置里的 officialOpenId（即 /config 的 botOpenId），这两个才是可靠标识。
 * 私聊记录接口没有下发 source 字段，但私聊对端不会是机器人，senderIsBot 可作兜底。
 */
function isMe(message) {
  if (message.eventType === 'BOT_SEND') return true
  if (botOpenId.value && message.unionOpenId === botOpenId.value) return true
  return active.value?.type === 'c2c' && !!message.senderIsBot
}

function avatarUrl(message) {
  if (!appId.value) return null
  if (isMe(message)) {
    return botOpenId.value ? `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${botOpenId.value}/640` : null
  }
  if (active.value?.type === 'c2c') return userAvatarUrl(active.value.openId)
  return message.unionOpenId ? userAvatarUrl(message.unionOpenId) : null
}

function avatarText(message) {
  const name = message.username || (isMe(message) ? botName.value : '?')
  return (name || '?').slice(0, 1).toUpperCase()
}

function roleLabel(r) {
  const map = { OWNER: '群主', ADMIN: '管理员' }
  return map[r] || r
}

function isSpecialRole(r) {
  return !!r && r !== 'MEMBER' && r !== 'USER'
}

function displayName(message) {
  if (isMe(message)) return message.username || botName.value || 'AtriBot'
  return message.username || 'Unknown'
}

// 机器人自己发的消息库里往往没存 union_openId，用 /config 拿到的 botOpenId 兜底，
// 否则我们这侧的消息头会只有名字没有 ID
function displayUid(message) {
  if (message.unionOpenId) return message.unionOpenId
  return isMe(message) ? botOpenId.value : ''
}

function renderContent(message) {
  let text = message.content || ''
  text = renderFaceTags(text)
  text = text.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
  text = text.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
  text = text.replace(/(<@[A-F0-9]+>)\s+\1/g, '$1')
  if (message.eventType === 'GROUP_MESSAGE_CREATE' && message.mentions) {
    try {
      const mentions = typeof message.mentions === 'string' ? JSON.parse(message.mentions) : message.mentions
      if (Array.isArray(mentions)) {
        for (const m of mentions) {
          if (m.userId && m.username) text = text.replaceAll(`<@${m.userId}>`, `@${m.username}`)
        }
      }
    } catch { /* ignore */ }
  }
  return text
}

function hasArk(message) {
  return hasArkMessage(message?.ark)
}

function msgRef(message) {
  try {
    const raw = message.messageReference
    if (!raw) return null
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    const ref = Array.isArray(arr) ? arr[0] : arr
    if (!ref) return null
    return {
      author: ref.author?.username || '',
      content: ref.content || '',
      attachments: Array.isArray(ref.attachments) ? ref.attachments : []
    }
  } catch { return null }
}

function renderRefContent(ref) {
  const parts = []
  if (ref.content) {
    let t = renderFaceTags(ref.content)
    t = t.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
    t = t.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
    if (t.trim()) parts.push(`<p>${escapeHtml(t)}</p>`)
  }
  for (const a of ref.attachments || []) {
    const type = a.content_type || ''
    if (type.startsWith('image/') && a.url) {
      parts.push(`<img src="${escapeHtml(absUrl(a.url))}" referrerpolicy="no-referrer" style="max-width:120px;max-height:80px;border-radius:4px;display:block" alt="图片">`)
    } else if (type.startsWith('video/')) {
      // 引用块只有 120×80，塞播放器没意义，标一下类型就够
      parts.push(`<span>[视频] ${escapeHtml(a.filename || '')}</span>`)
    } else if (type === 'voice') {
      parts.push(`<span>${escapeHtml(a.asr_refer_text || a.filename || '语音消息')}</span>`)
    }
  }
  if (!parts.length) return '&#8203;'
  return parts.join('')
}

function parseAttach(raw) {
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr)) return []
    return arr.map(normalizeAttachment).filter(Boolean)
  } catch { return [] }
}

/*
 * 官方 Bot 的附件 url 不带协议头（形如 multimedia.nt.qq.com.cn/download?...&rkey=...），
 * 直接塞进 src 会被当成站内相对路径。后端 CommandSender#getImageUrls 也是这么补的。
 */
function absUrl(url) {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url)) return url.startsWith('//') ? 'https:' + url : url
  if (url.startsWith('data:')) return url
  return 'https://' + url
}

function normalizeAttachment(att) {
  const contentType = att?.content_type || ''
  if (att?.url && contentType.startsWith('image/')) {
    return { ...att, type: 'image', url: absUrl(att.url) }
  }
  if (contentType.startsWith('video/')) {
    return { ...att, type: 'video', url: absUrl(att.url) }
  }
  if (contentType === 'voice') {
    return {
      ...att,
      type: 'voice',
      url: absUrl(att.url),
      asrText: att.asr_refer_text || '',
      voiceUrl: absUrl(att.voice_wav_url)
    }
  }
  // content_type 为 file，以及任何有 url 但类型不认识的附件，都按文件卡片兜底，
  // 免得像之前那样被静默丢掉、消息看起来是空的
  if (att?.url) {
    return { ...att, type: 'file', url: absUrl(att.url) }
  }
  return null
}

function fmtSize(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return ''
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let value = n
  while (value >= 1024 && i < units.length - 1) { value /= 1024; i++ }
  return `${value >= 10 || i === 0 ? Math.round(value) : value.toFixed(1)} ${units[i]}`
}

function fmtMsgTime(ts) {
  if (!ts) return ''
  const d = parseChatTime(ts)
  if (!d) return ts
  const pad = n => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function parseChatTime(value) {
  if (!value) return null
  const raw = String(value).trim()
  if (!raw) return null
  const normalized = raw.includes('T') ? raw : raw.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}
</script>
