<template>
  <div class="shell legacy-chat">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" :disabled="loadingConvs" @click="loadConversations()">刷新</button>
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
              <img v-if="c.type === 'group' && groupAvatarUrls[c.openId]"
                   :src="groupAvatarUrls[c.openId]" :alt="convName(c)"
                   class="cnv-group-avatar" />
              <template v-else-if="c.type === 'c2c'">
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

          <Transition name="chatnt-notice" mode="out-in">
            <div v-if="notice" :key="notice" class="chatnt-notice" role="status">{{ notice }}</div>
          </Transition>

          <div class="chatnt-message-area">
            <ChatBackground />
            <div ref="messageListRef" class="chatnt-msgs" @scroll="onScroll"
                 @wheel.passive="interruptMessageScroll" @touchstart.passive="interruptMessageScroll"
                 @pointerdown="interruptMessageScroll" @keydown="interruptMessageScroll">
              <div ref="messageContentRef" class="chatnt-msg-content">
              <div v-if="loadingMore" class="load-tip">加载更早的消息…</div>
              <div v-else-if="!hasMore && messages.length > 0" class="load-tip">— 没有更早的消息了 —</div>
              <div v-if="loadingMessages && messages.length === 0" class="empty-state">正在加载消息</div>
              <div v-else-if="messages.length === 0" class="empty-state">暂无消息记录</div>

              <article v-for="message in orderedMessages" :key="message.id"
                       :data-message-id="message.id"
                       class="qm" :class="{ mine: isMe(message), highlighted: highlightedMessageId === message.id, 'qm-arriving': arrivingMessageIds.has(message.id) }"
                       @animationend.self="arrivingMessageIds.delete(message.id)">
                <span class="qm-avatar" title="点击显示/隐藏 ID" @click="toggleUid(message.id)">
                  <span>{{ avatarText(message) }}</span>
                  <img v-if="avatarUrl(message) && !avatarFailed[message.id]"
                       :src="avatarUrl(message)" :alt="message.username"
                       referrerpolicy="no-referrer"
                       @error="avatarFailed[message.id] = true" />
                </span>
                <div class="qm-main">
                  <div class="qm-name" :class="{ 'uid-expanded': expandedIds[message.id] }">
                    <span class="qm-name-text" :class="{ 'bot-staff-name': !isMe(message) && isBotStaff(message) }">{{ displayName(message) }}</span>
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
                  <div v-chat-image-layout class="qm-bubble" :class="{ recalled: recalledIds[message.messageOpenId], 'qm-bubble--forward': !recalledIds[message.messageOpenId] && forwardRecord(message) }"
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
                            <audio v-if="att.voiceUrl && !attachFailed[att.voiceUrl]" :src="att.voiceUrl" controls preload="none"
                                   @error="attachFailed[att.voiceUrl] = true"></audio>
                            <template v-else>
                              <span class="qm-media-hint">{{ att.voiceUrl ? '音频暂时无法播放' : '未保存音频播放地址' }}</span>
                              <a v-if="att.url || att.voiceUrl" class="attach-fail" :href="att.url || att.voiceUrl" target="_blank" rel="noreferrer">打开原始音频</a>
                            </template>
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
                      <div v-if="legacyMedia(message) && !parseAttach(message.attachments).length" class="qm-voice">
                        <div class="qm-voice-title">媒体消息</div>
                        <span class="qm-media-hint">这条历史消息未保存媒体地址，暂时无法预览</span>
                      </div>
                      <ForwardMessageCard v-if="forwardRecord(message)" :record="forwardRecord(message)" />
                      <ArkMessageCard v-if="hasArk(message)" :ark="message.ark" />
                      <pre v-if="!forwardRecord(message) && !hasArk(message) && message.messageType !== 2 && renderContent(message)">{{ renderContent(message) }}</pre>
                      <div v-if="!forwardRecord(message) && !hasArk(message) && message.messageType === 2" class="md-body"
                           v-html="renderMd(renderContent(message))" @error.capture="replaceBrokenMarkdownImage"></div>
                    </template>
                  </div>
                  <div class="qm-time">{{ fmtMsgTime(message.eventTimestamp || message.createdAt) }}</div>
                </div>
              </article>
              </div>
            </div>
            <Transition name="chatnt-new-messages">
              <button v-if="newMessageCount > 0" type="button" class="chatnt-new-messages"
                      :disabled="loadingMessages" :aria-label="`${newMessageCount} 条新消息，跳到最新消息`"
                      @click="jumpToLatestMessages">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M12 4v14m-5-5 5 5 5-5M5 21h14" />
                </svg>
                <span aria-live="polite" aria-atomic="true">{{ newMessageCount > 99 ? '99+' : newMessageCount }} 条新消息</span>
              </button>
            </Transition>
          </div>

          <div v-if="replyTo" class="chatnt-replybar">
            <span>已选 {{ replyTo.username || '...' }}：{{ replyPreview }}</span>
            <button aria-label="取消选择消息" @click="cancelReply">×</button>
          </div>

          <form class="chatnt-composer" @submit.prevent="sendMessage">
            <div class="chatnt-tools" tabindex="0" aria-label="消息类型与发送方式，可左右滑动">
              <div class="chatnt-type">
                <label :class="{ active: msgType === 'text' }"><input type="radio" v-model="msgType" value="text" />文本</label>
                <label :class="{ active: msgType === 'markdown' }"><input type="radio" v-model="msgType" value="markdown" />MD</label>
                <label :class="{ active: msgType === 'image' }"><input type="radio" v-model="msgType" value="image" />图片</label>
                <label :class="{ active: msgType === 'ark' }"><input type="radio" v-model="msgType" value="ark" />Ark</label>
                <label v-if="active.type === 'c2c'" :class="{ active: msgType === 'stream' }"><input type="radio" v-model="msgType" value="stream" />流式</label>
              </div>
              <button v-if="msgType === 'image'" type="button" class="chatnt-tool-btn" title="上传图片" aria-label="上传图片" @click="$refs.fileInputRef.click()">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/>
                </svg>
              </button>
              <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="onFilePicked" />
              <div class="chatnt-send-options" aria-label="发送方式">
                <button type="button" class="chatnt-mode-toggle" :class="{ active: passiveMode }"
                        :aria-pressed="passiveMode" :disabled="!!passiveDisabledReason"
                        :title="passiveDisabledReason || '使用所选消息发送被动回复'" @click="passiveMode = !passiveMode">
                  <span class="chatnt-mode-check" aria-hidden="true">
                    <svg v-if="passiveMode" width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m2 6 2.5 2.5L10 3"/></svg>
                  </span>
                  被动消息
                </button>
                <button type="button" class="chatnt-mode-toggle" :class="{ active: refMode }"
                        :aria-pressed="refMode" :disabled="!!referenceDisabledReason"
                        :title="referenceDisabledReason || '发送时附带所选消息的引用'" @click="refMode = !refMode">
                  <span class="chatnt-mode-check" aria-hidden="true">
                    <svg v-if="refMode" width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m2 6 2.5 2.5L10 3"/></svg>
                  </span>
                  引用
                </button>
                <button v-if="active.type === 'c2c'" type="button" class="chatnt-mode-toggle"
                        :class="{ active: wakeupMode }" :aria-pressed="wakeupMode"
                        title="发送召回消息，无需选择来源消息" @click="toggleWakeupMode">
                  <span class="chatnt-mode-check" aria-hidden="true">
                    <svg v-if="wakeupMode" width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m2 6 2.5 2.5L10 3"/></svg>
                  </span>
                  召回
                </button>
                <button v-if="active.type === 'c2c'" type="button" class="chatnt-tool-btn chatnt-typing-btn"
                        :disabled="sendingInputNotify" :aria-busy="sendingInputNotify"
                        title="发送正在输入提示（60 秒）" aria-label="发送正在输入提示（60 秒）" @click="sendInputNotify">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M5 4.5h14a2.5 2.5 0 0 1 2.5 2.5v9a2.5 2.5 0 0 1-2.5 2.5h-9L5 22v-3.5A2.5 2.5 0 0 1 2.5 16V7A2.5 2.5 0 0 1 5 4.5Z"
                          stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
                    <g fill="currentColor">
                      <circle class="chatnt-typing-dot" cx="7.5" cy="11.5" r="1.25" />
                      <circle class="chatnt-typing-dot" cx="12" cy="11.5" r="1.25" />
                      <circle class="chatnt-typing-dot" cx="16.5" cy="11.5" r="1.25" />
                    </g>
                  </svg>
                </button>
              </div>
            </div>
            <img v-if="pastePreview" :src="pastePreview" class="chatnt-paste-preview" title="点击清除" @click="clearSelectedImage" />
            <div class="chatnt-resize-handle" :class="{ dragging: resizingComposer }"
                 title="拖拽调整输入区高度"
                 @mousedown="startComposerResize" @touchstart="startComposerResize"></div>
            <fieldset v-if="msgType === 'ark'" class="chatnt-ark-editor" :disabled="sending" aria-label="Ark 卡片内容"
                      :style="{ height: activeComposerHeight + 'px' }">
              <label>卡片描述<input v-model="arkDraft.description" placeholder="卡片描述" /></label>
              <label>通知预览<input v-model="arkDraft.prompt" placeholder="消息列表和通知中显示的文字" /></label>
              <div v-for="(item, index) in arkDraft.items" :key="index" class="chatnt-ark-item">
                <label>条目 {{ index + 1 }}<input v-model="item.description" placeholder="条目内容" /></label>
                <label>链接（可选）<input v-model="item.link" placeholder="https://…" inputmode="url" /></label>
                <button type="button" class="nt-mini-btn" :disabled="arkDraft.items.length === 1"
                        :aria-label="`删除条目 ${index + 1}`" @click="arkDraft.items.splice(index, 1)">删除</button>
              </div>
              <button type="button" class="nt-mini-btn" @click="arkDraft.items.push({ description: '', link: '' })">添加条目</button>
            </fieldset>
            <textarea v-if="msgType !== 'ark'" ref="composerRef" v-model="draft" :disabled="sending"
                      :style="{ height: activeComposerHeight + 'px' }"
                      :placeholder="composerPlaceholder"
                      @paste="onPaste"
                      @keydown.enter.exact.prevent="sendMessage"></textarea>
            <div class="chatnt-composer-foot">
              <span class="chatnt-hint">{{ sendModeHint || 'Enter 发送 · Shift+Enter 换行' }}</span>
              <button class="chatnt-send" :disabled="!canSend">{{ sending ? '发送中…' : '发送' }}</button>
            </div>
          </form>

          <!-- ── 右侧栏：成员 / 信息 / 单用户设置 ── -->
          <Transition name="chatnt-panel-backdrop">
            <div v-if="panel" class="chatnt-members-backdrop" @click="closePanel" />
          </Transition>
          <Transition name="chatnt-panel" mode="out-in">
          <aside v-if="panel" :key="panel" class="chatnt-members">
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
                    <button type="button" class="mbr-avatar" title="查看成员详情"
                            :aria-label="`查看 ${m.username || '该成员'} 的详情`" @click.stop="memberInfoTarget = m">
                      <span>{{ (m.username || '?').slice(0, 1).toUpperCase() }}</span>
                      <img v-if="userAvatarUrl(m.unionOpenId) && !avatarFailed['mbr-' + m.unionOpenId]"
                           :src="userAvatarUrl(m.unionOpenId)" :alt="m.username"
                           referrerpolicy="no-referrer"
                           @error="avatarFailed['mbr-' + m.unionOpenId] = true" />
                    </button>
                    <button class="mbr-body" title="点击 @ 该成员" @click="atMember(m)">
                      <span class="mbr-top">
                        <span class="mbr-name" :class="{ 'bot-staff-name': isBotStaff(m) }">{{ m.username || 'Unknown' }}</span>
                        <svg v-if="m.senderIsBot" class="qm-bot"
                             width="13" height="13" viewBox="0 0 64 64" role="img" aria-label="机器人">
                          <line x1="32" y1="10" x2="32" y2="18" stroke="currentColor" stroke-width="3.5" stroke-linecap="round"/>
                          <circle cx="32" cy="8" r="4" fill="none" stroke="currentColor" stroke-width="3.5"/>
                          <rect x="16" y="18" width="32" height="28" rx="10" fill="none" stroke="currentColor" stroke-width="3.5"/>
                          <rect x="24" y="28" width="4" height="8" rx="2" fill="currentColor"/>
                          <rect x="36" y="28" width="4" height="8" rx="2" fill="currentColor"/>
                        </svg>
                        <span v-if="isSpecialRole(m.memberRole)" class="qm-role"
                              :class="'role-' + m.memberRole.toLowerCase()">{{ roleLabel(m.memberRole) }}</span>
                      </span>
                      <span class="mbr-sub">{{ m.messageCount }} 条 · {{ fmtMemberTime(m.lastActiveAt) }}</span>
                    </button>
                    <button class="mbr-mention" title="@ 该成员" aria-label="@ 该成员" @click.stop="atMember(m)">
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <circle cx="12" cy="12" r="4"/>
                        <path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8"/>
                      </svg>
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

                <GroupBlacklistPanel :group-open-id="active.openId" :can-manage="canQueryMuteState"
                                     :request="api" :format-time="fmtGroupTime" @notice="showNotice" />

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
                <UserProfileForm :key="profileTarget" :profile="profile" :role-options="ROLE_OPTIONS"
                                 :saving="profileSaving" :loading="profileLoading" :disabled="!profileReady" :error="profileError"
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
                  <div class="chatnt-clear-hint">仅影响当前{{ active.type === 'group' ? '群聊' : '用户' }}会话聊天数据</div>

                </div>
              </div>
            </div>

            <!-- ═══ 单用户设置 ═══ -->
            <div v-else-if="panel === 'user'" class="chatnt-members-list chatnt-info">
              <UserProfileForm :key="profileTarget" :profile="profile" :role-options="ROLE_OPTIONS"
                               :saving="profileSaving" :loading="profileLoading" :disabled="!profileReady" :error="profileError"
                               @save="saveProfile" @add-perm="addPermNode" @remove-perm="removePerm" />
            </div>
          </aside>
          </Transition>
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
      <button @click="selectReplyTarget(ctxMenu.message); ctxMenu.visible = false">选择</button>
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

    <GroupMemberDialog v-if="memberInfoTarget && active?.type === 'group'"
                       :group-open-id="active.openId" :member="memberInfoTarget" :request="api"
                       :avatar-url="userAvatarUrl(memberInfoTarget.unionOpenId)"
                       @close="memberInfoTarget = null" @mute="muteFromMemberInfo" @removed="memberRemoved" />

    <!-- 禁言设置弹窗：Teleport 到 body，避免聊天布局裁切时长菜单 -->
    <Teleport to="body">
      <div v-if="mutePanel.visible" class="mute-modal-backdrop" @click="mutePanel.visible = false"></div>
      <div v-if="mutePanel.visible" class="mute-modal" @click.stop>
        <div class="mute-modal-head">
          <div class="mute-modal-title">禁言设置</div>
          <button class="mute-modal-close" aria-label="关闭" @click="mutePanel.visible = false">×</button>
        </div>
        <div class="mute-modal-body" @click="mutePickerOpen = null">
          <div class="mute-modal-target">对 {{ mutePanel.message?.username || '该成员' }} 执行禁言</div>
          <div ref="mutePickerEl" class="mute-picker" aria-label="禁言时长">
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
              <div v-if="mutePickerOpen === field.key" class="mute-picker-menu" role="listbox">
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
import { parseForwardContent } from '../lib/forward.js'
import { vChatImageLayout } from '../lib/chatImageLayout.js'
import { countNewMessages, latestMessageId } from '../lib/chatUnread.js'
import { createChatBottomScroller, createChatPositionKeeper, prefersReducedMotion } from '../lib/chatMotion.js'
import { CHAT_LAYOUT_KEY } from '../lib/panelLayout.js'
import GroupAvatarRenderer from '../lib/GroupAvatarRenderer.js'
import AppSidebar from '../components/AppSidebar.vue'
import ArkMessageCard from '../components/ArkMessageCard.vue'
import ForwardMessageCard from '../components/ForwardMessageCard.vue'
import ChatBackground from '../components/ChatBackground.vue'
import UserProfileForm from '../components/UserProfileForm.vue'
import GroupMemberDialog from '../components/GroupMemberDialog.vue'
import GroupBlacklistPanel from '../components/GroupBlacklistPanel.vue'

const router = useRouter()

const atriImg = import.meta.env.BASE_URL + 'img/atri-main.png'

const appId = ref('')
const botOpenId = ref('')
const botName = ref('AtriBot')
const groupAvatarUrls = reactive({})

const groupAvatarRenderer = new GroupAvatarRenderer({
  loadMembers: groupOpenId => api(`/groups/${encodeURIComponent(groupOpenId)}/members`),
  avatarUrl: memberOpenId => userAvatarUrl(memberOpenId)
})

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
const newMessageCount = ref(0)
let knownLatestMessageId = 0n
let viewingHistoryPage = false
let followingLatest = true

const sending = ref(false)
const sendingInputNotify = ref(false)
const draft = ref('')
const arkDraft = reactive({ description: '', prompt: '', items: [{ description: '', link: '' }] })
const msgType = ref('text')
const imageData = ref(null)
const pastePreview = ref(null)
const replyTo = ref(null)
const passiveMode = ref(false)
const refMode = ref(false)
const wakeupMode = ref(false)

const COMPOSER_MIN_HEIGHT = 36
const composerHeight = ref(COMPOSER_MIN_HEIGHT)
const ARK_COMPOSER_MIN_HEIGHT = 120
const arkComposerHeight = ref(220)
const activeComposerHeight = computed({
  get: () => msgType.value === 'ark' ? arkComposerHeight.value : composerHeight.value,
  set: height => {
    if (msgType.value === 'ark') arkComposerHeight.value = height
    else composerHeight.value = height
  }
})
const resizingComposer = ref(false)

const DEFAULT_LIST_WIDTH = 296
const LIST_MIN_WIDTH = 200
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
const memberInfoTarget = ref(null)

function muteFromMemberInfo(member) {
  memberInfoTarget.value = null
  openMutePanel(member)
}

function memberRemoved(member) {
  memberInfoTarget.value = null
  const result = member.blacklistFailed
    ? '，但加入群黑名单失败'
    : member.addToMemberBlacklist ? '，并加入群黑名单' : ''
  showNotice(`已将 ${member.username || '该成员'} 踢出群聊${result}`)
}

// ── 用户档案（私聊对端 / 群成员）──
const profileTarget = ref('')
const profileName = ref('')
const profile = reactive({ role: 'USER', permissions: [], blocked: false, ignored: false, c2cPush: true })
const profileSaving = ref(false)
const profileLoading = ref(false)
const profileReady = ref(false)
const profileError = ref('')
let profileLoadSeq = 0
let profileLoadController = null
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
const previewImg = ref(null)
const previewVideo = ref(null)
const ctxMenu = reactive({ visible: false, x: 0, y: 0, message: null })
const recalledIds = reactive({})
const avatarFailed = reactive({})
const attachFailed = reactive({})
// 手机端点头像展开 ID（PC 端 ID 常驻，这个状态不参与）
const expandedIds = reactive({})
const forwardCache = new WeakMap()

function toggleUid(id) {
  expandedIds[id] = !expandedIds[id]
}

const messageListRef = ref(null)
const messageContentRef = ref(null)
const arrivingMessageIds = reactive(new Set())
const messageScroller = createChatBottomScroller(() => messageListRef.value)
const messagePosition = createChatPositionKeeper(() => messageListRef.value, () => messageContentRef.value)
watch([messageListRef, messageContentRef], ([viewport, content], _, onCleanup) => {
  if (!viewport || !content) return
  // load 事件早于图片排版完成，也覆盖不到视频、字体和输入框引起的尺寸变化。
  // 同时观察内容和可视区域，在浏览器绘制前按最终布局补齐底部。
  const observer = new ResizeObserver(syncMessageLayout)
  observer.observe(viewport)
  observer.observe(content)
  onCleanup(() => observer.disconnect())
}, { flush: 'post' })
const fileInputRef = ref(null)
const composerRef = ref(null)

let eventSource = null
let disposed = false
let sseStopped = false
let sseReconnectTimer = null
let sseVerifyTimer = null
let sseVerifyController = null
let convRefreshTimer = null
let convRefreshPending = false
let convRefreshController = null
let messageRefreshTimer = null
let messageRefreshPending = false
let messageRefreshController = null

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

function queueGroupAvatars(list) {
  for (const conversation of list) {
    if (conversation.type !== 'group' || groupAvatarUrls[conversation.openId]) continue
    groupAvatarRenderer.enqueue(conversation.openId).then(url => {
      if (!url) return
      const stillPresent = conversations.value.some(c => c.type === 'group' && c.openId === conversation.openId)
      if (stillPresent) groupAvatarUrls[conversation.openId] = url
    })
  }
}

// Queue only when the app ID or visible conversation IDs actually change.
watch(
  () => [appId.value, ...filteredConvs.value.map(c => `${c.type}:${c.openId}`)].join('\u0000'),
  () => queueGroupAvatars(filteredConvs.value),
  { immediate: true }
)

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

// 仿 QQ NT：按身份分组，后端已排好序，这里只做分桶。
// 角色优先级为群主 > 管理员 > 机器人 > 普通成员，避免机器人重复归类。
const memberSections = computed(() => {
  const list = filteredMembers.value
  return [
    { key: 'owner', label: '群主', items: list.filter(m => m.memberRole === 'OWNER') },
    { key: 'admin', label: '管理员', items: list.filter(m => m.memberRole === 'ADMIN') },
    {
      key: 'bot',
      label: '机器人',
      items: list.filter(m => m.senderIsBot && m.memberRole !== 'OWNER' && m.memberRole !== 'ADMIN')
    },
    {
      key: 'member',
      label: '成员',
      items: list.filter(m => !m.senderIsBot && !isSpecialRole(m.memberRole))
    }
  ]
})

const orderedMessages = computed(() => [...messages.value].reverse())
const hasMore = computed(() => messages.value.length < totalMessages.value)

const canSend = computed(() => {
  if (!active.value || sending.value) return false
  if (msgType.value === 'ark') return !!arkDraft.description.trim() && !!arkDraft.prompt.trim()
    && arkDraft.items.length > 0 && arkDraft.items.every(item => !!item.description.trim())
  if (msgType.value === 'image') return !!imageData.value
  return !!draft.value.trim()
})

// 被动回复来源与引用独立，只检查所选消息是否具备对应 ID。
const passiveDisabledReason = computed(() => {
  if (wakeupMode.value) return '召回消息不使用被动回复来源'
  if (msgType.value === 'ark') return 'Ark 仅支持主动发送'
  if (replyTo.value && (isMe(replyTo.value) || !replyTo.value.messageOpenId)) return '该消息不能作为被动回复的来源'
  return ''
})

const referenceDisabledReason = computed(() => {
  if (wakeupMode.value) return '召回消息不附带引用'
  if (msgType.value === 'ark') return 'Ark 暂不支持引用'
  if (msgType.value === 'stream') return '流式消息暂不支持引用'
  if (replyTo.value && !replyTo.value.refIdx) return '所选消息缺少引用索引，无法附带引用'
  return ''
})

const sendModeHint = computed(() => {
  if (wakeupMode.value) return '发送召回消息'
  if (msgType.value === 'ark') return 'Ark 主动发送 · 填写描述、通知预览和条目内容'
  if (msgType.value === 'stream' && !passiveMode.value) return '流式消息需开启被动消息并选择来源，或开启召回'
  if ((passiveMode.value || refMode.value) && !replyTo.value) return '请右键消息，点击「选择」指定来源'
  if (replyTo.value && !passiveMode.value && !refMode.value) return '未开启被动消息或引用，将按普通消息发送'
  return ''
})

watch([msgType, () => active.value?.type, replyTo, wakeupMode], () => {
  if (active.value?.type !== 'c2c') wakeupMode.value = false
  if (wakeupMode.value) {
    passiveMode.value = false
    refMode.value = false
    return
  }
  if (msgType.value === 'ark') {
    passiveMode.value = false
    refMode.value = false
    return
  }
  if (msgType.value === 'stream' || (replyTo.value && !replyTo.value.refIdx)) refMode.value = false
  if (replyTo.value && (isMe(replyTo.value) || !replyTo.value.messageOpenId)) passiveMode.value = false
})

const composerPlaceholder = computed(() => {
  if (msgType.value === 'image') return '图片说明文字（可选），粘贴或上传图片'
  if (msgType.value === 'markdown') return 'Markdown 内容'
  if (msgType.value === 'stream') return '发送消息 · 单行为一个切片'
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
  if (disposed || sseStopped) return
  await Promise.all([loadConversations(), loadPinned()])
  if (disposed || sseStopped) return
  connectSse()
  applyDeepLink()
})

onBeforeUnmount(() => {
  // 先失效异步回调，再释放连接和定时器，避免请求完成后重新调度。
  disposed = true
  convLoadSeq++
  resetProfile()
  cancelMessageRefresh()
  convRefreshController?.abort()
  messageScroller.cancel()
  messagePosition.clear()
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('resize', clampListWidth)
  stopSse()
  if (convRefreshTimer) clearTimeout(convRefreshTimer)
  convRefreshTimer = null
  convRefreshPending = false
  if (highlightTimer) clearTimeout(highlightTimer)
  if (noticeTimer) clearTimeout(noticeTimer)
  stopComposerResize()
  stopListResize()
  groupAvatarRenderer.dispose()
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
  composerResizeStartHeight = Math.min(activeComposerHeight.value, window.innerHeight * 0.32)
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
  const minHeight = msgType.value === 'ark' ? ARK_COMPOSER_MIN_HEIGHT : COMPOSER_MIN_HEIGHT
  activeComposerHeight.value = Math.min(maxHeight, Math.max(minHeight, composerResizeStartHeight + delta))
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
    const saved = JSON.parse(localStorage.getItem(CHAT_LAYOUT_KEY) || '{}')
    if (Number.isFinite(saved.listWidth)) listWidth.value = saved.listWidth
    if (Number.isFinite(saved.composerHeight)) {
      composerHeight.value = Math.max(COMPOSER_MIN_HEIGHT, saved.composerHeight)
    }
    if (Number.isFinite(saved.arkComposerHeight)) {
      arkComposerHeight.value = Math.max(ARK_COMPOSER_MIN_HEIGHT, saved.arkComposerHeight)
    }
  } catch { /* 存的值坏了就用默认布局 */ }
  clampListWidth()
}

function saveLayout() {
  try {
    localStorage.setItem(CHAT_LAYOUT_KEY, JSON.stringify({
      listWidth: Math.round(listWidth.value),
      composerHeight: Math.round(composerHeight.value),
      arkComposerHeight: Math.round(arkComposerHeight.value)
    }))
  } catch { /* 隐私模式下 localStorage 可能不可写，忽略 */ }
}

// ═══════════════ API 基础 ═══════════════

async function api(path, options) {
  if (disposed || sseStopped) throw new Error('会话已结束')
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    ...options
  })
  if (disposed || sseStopped) throw new Error('会话已结束')
  if (res.status === 401 || res.status === 503) {
    expireSession()
    throw new Error(res.status === 401 ? '未授权' : 'WebUI 暂不可用')
  }
  const text = await res.text()
  if (disposed || sseStopped) throw new Error('会话已结束')
  let payload
  try {
    payload = JSON.parse(text)
  } catch {
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

async function logout() {
  if (disposed || sseStopped) return
  stopSse()
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' })
  } catch { /* ignore */ }
  if (disposed) return
  try { localStorage.removeItem(LEGACY_TOKEN_KEY) } catch { /* 存储不可用不影响退出。 */ }
  window.location.replace(router.resolve('/login').href)
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
  if (disposed) return
  if (loadingConvs.value || loadingMoreConvs.value) {
    scheduleConvRefresh()
    return
  }
  if (convRefreshTimer) clearTimeout(convRefreshTimer)
  convRefreshTimer = null
  convRefreshPending = false
  loadingConvs.value = true
  const controller = new AbortController()
  convRefreshController = controller
  try {
    // 按「当前已加载的非置顶会话数」重取：首屏 100，SSE 刷新时保持已加载深度，
    // 新消息让某个会话实时跳到顶部、其余整体重排（QQ 式）
    const depth = Math.min(Math.max(nonPinnedLoadedCount.value, CONV_LIST_PAGE_SIZE), 1000)
    const data = await api(`/chat/conversations?limit=${depth}&offset=0`, { signal: controller.signal }) || { items: [], hasMore: true }
    if (disposed) return
    conversations.value = data.items || []
    hasMoreConvs.value = data.hasMore !== false
  } catch { /* 静默失败，保留旧列表 */ }
  finally {
    convRefreshController = null
    loadingConvs.value = false
    if (convRefreshPending) scheduleConvRefresh()
  }
}

async function loadMoreConversations() {
  if (disposed || loadingMoreConvs.value || !hasMoreConvs.value || loadingConvs.value || search.value.trim()) return
  loadingMoreConvs.value = true
  // offset 只统计非置顶会话：置顶的由后端单独前置，不计入分页偏移
  const offset = nonPinnedLoadedCount.value
  try {
    const data = await api(`/chat/conversations?limit=${CONV_LIST_PAGE_SIZE}&offset=${offset}`) || { items: [], hasMore: false }
    if (disposed) return
    const existing = new Set(conversations.value.map(c => convKey(c)))
    const fresh = (data.items || []).filter(c => !existing.has(convKey(c)))
    conversations.value = [...conversations.value, ...fresh]
    hasMoreConvs.value = data.hasMore !== false
  } catch { /* ignore */ }
  finally {
    loadingMoreConvs.value = false
    if (convRefreshPending) scheduleConvRefresh()
  }
}

function onConvListScroll(e) {
  const el = e.currentTarget
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 120) {
    loadMoreConversations()
  }
}

function scheduleConvRefresh() {
  if (disposed || sseStopped) return
  convRefreshPending = true
  if (convRefreshTimer || loadingConvs.value || loadingMoreConvs.value) return
  // 固定刷新窗口；持续到来的事件只标记补刷，不推迟已排队的请求。
  convRefreshTimer = setTimeout(() => {
    convRefreshTimer = null
    if (convRefreshPending) loadConversations()
  }, 800)
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
  if (!body && c.isMe && parseLegacyMedia(c.lastContent)) body = '[媒体消息]'
  if (!body) body = stripPreviewTags(c.lastContent) || ' '
  if (c.isMe) return `${botName.value}: ${body}`
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
  resetProfile()
  messageScroller.cancel()
  clearReferencePosition()
  arrivingMessageIds.clear()
  mobileChatOpen.value = true
  messages.value = []
  totalMessages.value = 0
  currentPage.value = 0
  newMessageCount.value = 0
  knownLatestMessageId = 0n
  viewingHistoryPage = false
  followingLatest = true
  cancelReply()
  ctxMenu.visible = false
  wakeupMode.value = false
  mutePanel.visible = false
  memberInfoTarget.value = null
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
  resetArkDraft()
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

async function loadLatestMessages({ animate = false } = {}) {
  if (disposed || !active.value) return
  cancelMessageRefresh()
  const targetKey = currentConvKey()
  const seq = ++convLoadSeq
  loadingMessages.value = true
  loadingMore.value = false
  currentPage.value = 1
  try {
    const data = await api(messagesPath(1))
    // 加载期间会话被切换，或又发起了更新的一次加载：这次响应已经过期，丢弃
    if (seq !== convLoadSeq || currentConvKey() !== targetKey) return
    if (animate) markArrivingMessages(data.records || [])
    else arrivingMessageIds.clear()
    messages.value = data.records || []
    totalMessages.value = data.total || 0
    knownLatestMessageId = latestMessageId(messages.value)
    viewingHistoryPage = false
    newMessageCount.value = 0
    await nextTick()
    if (seq !== convLoadSeq || currentConvKey() !== targetKey) return
    scrollToBottom(animate ? 'smooth' : 'auto')
  } catch { /* ignore */ }
  finally {
    if (seq === convLoadSeq) {
      loadingMessages.value = false
      if (messageRefreshPending) scheduleMessageRefresh()
    }
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value || loadingMessages.value || !active.value) return
  const targetKey = currentConvKey()
  const seq = convLoadSeq
  loadingMore.value = true
  const el = messageListRef.value
  const nextPage = currentPage.value + 1
  try {
    const data = await api(messagesPath(nextPage))
    if (currentConvKey() !== targetKey || seq !== convLoadSeq) return
    const viewportTop = el?.getBoundingClientRect().top || 0
    const anchor = el && [...el.querySelectorAll('.qm')].find(node => node.getBoundingClientRect().bottom > viewportTop)
    const anchorTop = anchor?.getBoundingClientRect().top
    if (anchor) messagePosition.hold(anchor, anchorTop - viewportTop - el.clientTop)
    const seen = new Set(messages.value.map(message => message.id))
    currentPage.value = nextPage
    messages.value = [...messages.value, ...(data.records || []).filter(message => !seen.has(message.id))]
    // 加载提示与历史记录在同一次布局更新中处理，避免提示消失后再次推移内容。
    loadingMore.value = false
    await nextTick()
    if (currentConvKey() !== targetKey || seq !== convLoadSeq) return
    messagePosition.sync()
  } catch { /* 保留当前页，下次向上滚动时重试。 */ }
  finally { if (currentConvKey() === targetKey && seq === convLoadSeq) loadingMore.value = false }
}

function onScroll() {
  const el = messageListRef.value
  if (!el || loadingMessages.value || messageScroller.running || messagePosition.active) return
  // 布局变化和程序定位也会发出 scroll，不能因此误判用户离开了底部。
  // 用户滚轮、触摸或拖动滚动条时会先由 interruptMessageScroll 解除跟随。
  if (followingLatest && !viewingHistoryPage) {
    syncMessageLayout()
    return
  }
  followingLatest = !viewingHistoryPage && el.scrollHeight - el.scrollTop - el.clientHeight <= 1
  if (followingLatest) newMessageCount.value = 0
  if (followingLatest || loadingMore.value || !hasMore.value) return
  if (el.scrollTop < 60) loadMore()
}

function syncMessageLayout() {
  if (messagePosition.active) {
    messagePosition.sync()
    return
  }
  if (!followingLatest || viewingHistoryPage || loadingMessages.value || loadingMore.value || messageScroller.running) return
  const el = messageListRef.value
  if (!el || !el.clientHeight) return
  const bottom = Math.max(0, el.scrollHeight - el.clientHeight)
  if (Math.abs(el.scrollTop - bottom) > 1) messageScroller.scroll('auto')
}

function scrollToBottom(behavior = 'auto') {
  const el = messageListRef.value
  if (!el) return
  clearReferencePosition()
  followingLatest = true
  newMessageCount.value = 0
  messageScroller.scroll(behavior)
}

function interruptMessageScroll() {
  messageScroller.cancel()
  messagePosition.cancel()
  followingLatest = false
}

function markArrivingMessages(records) {
  if (prefersReducedMotion()) return
  const seen = new Set(messages.value.map(message => message.id))
  for (const message of records) {
    if (!seen.has(message.id) && latestMessageId([message]) > knownLatestMessageId) {
      arrivingMessageIds.add(message.id)
    }
  }
}

async function jumpToLatestMessages() {
  if (viewingHistoryPage || totalMessages.value > messages.value.length && newMessageCount.value > pageSize) {
    await loadLatestMessages()
    return
  }
  scrollToBottom('smooth')
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
    newMessageCount.value = 0
    knownLatestMessageId = 0n
    viewingHistoryPage = false
    followingLatest = true
    await Promise.all([loadConversations(), loadConvStats()])
    showNotice(`已清除 ${result?.deleted || 0} 条记录`)
  } catch (error) {
    showNotice(error.message || '清除聊天记录失败')
  } finally {
    clearForm.loading = false
  }
}


// ── 用户档案（群成员和私聊对端共用一套 /c2c/{id}/profile）──

function resetProfile() {
  profileLoadSeq++
  profileLoadController?.abort()
  profileLoadController = null
  profileTarget.value = ''
  profileName.value = ''
  profileLoading.value = false
  profileReady.value = false
  profileError.value = ''
  Object.assign(profile, { role: 'USER', permissions: [], blocked: false, ignored: false, c2cPush: true })
}

async function openProfile(userOpenId, displayName, switchPanel = true) {
  if (disposed || !userOpenId) return
  resetProfile()
  const seq = profileLoadSeq
  const controller = new AbortController()
  profileLoadController = controller
  profileTarget.value = userOpenId
  profileName.value = displayName || ''
  profileLoading.value = true
  if (switchPanel) panel.value = 'user'
  try {
    const data = await api(`/c2c/${encodeURIComponent(userOpenId)}/permissions`, { signal: controller.signal })
    // 目标相同也可能已重新打开，必须同时校验本次读取的版本。
    if (disposed || seq !== profileLoadSeq || profileTarget.value !== userOpenId) return
    profile.role = data?.role || 'USER'
    profile.permissions = [...(data?.permissions || [])]
    profile.blocked = data?.isBlocked || false
    profile.ignored = data?.isIgnored || false
    profile.c2cPush = data?.c2cPush !== false
    profileReady.value = true
  } catch (error) {
    if (disposed || seq !== profileLoadSeq || profileTarget.value !== userOpenId) return
    profileError.value = error.message || '加载用户档案失败，请重新打开用户设置'
  } finally {
    if (seq === profileLoadSeq) {
      profileLoading.value = false
      profileLoadController = null
    }
  }
}

function addPermNode(value) {
  if (!profileReady.value || profileLoading.value || profileSaving.value || !value || profile.permissions.includes(value)) return
  profile.permissions = [...profile.permissions, value]
}

function removePerm(perm) {
  if (!profileReady.value || profileLoading.value || profileSaving.value) return
  profile.permissions = profile.permissions.filter(p => p !== perm)
}

async function saveProfile() {
  if (disposed || !profileTarget.value || !profileReady.value || profileLoading.value || profileSaving.value) return
  const savedUserId = profileTarget.value
  const savedRole = profile.role
  const seq = profileLoadSeq
  profileSaving.value = true
  profileError.value = ''
  try {
    await api(`/c2c/${encodeURIComponent(savedUserId)}/profile`, {
      method: 'POST',
      body: JSON.stringify({
        role: savedRole,
        permissions: profile.permissions,
        blocked: profile.blocked,
        ignored: profile.ignored,
        c2cPush: profile.c2cPush
      })
    })
    if (disposed) return
    const applyRole = user => user.unionOpenId === savedUserId && !user.senderIsBot
      ? { ...user, userRole: savedRole } : user
    messages.value = messages.value.map(applyRole)
    members.value = members.value.map(applyRole)
    if (seq === profileLoadSeq && profileTarget.value === savedUserId) showNotice('已保存')
  } catch (error) {
    if (!disposed && seq === profileLoadSeq && profileTarget.value === savedUserId) {
      profileError.value = error.message || '保存失败'
    }
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

function clearReferencePosition() {
  messagePosition.clear()
  highlightedMessageId.value = null
  if (highlightTimer) clearTimeout(highlightTimer)
  highlightTimer = null
}

function highlightMessage(id) {
  clearReferencePosition()
  messageScroller.cancel()
  followingLatest = false
  highlightedMessageId.value = id
  const viewport = messageListRef.value
  const el = viewport?.querySelector(`[data-message-id="${id}"]`)
  if (el && viewport.clientHeight) messagePosition.scrollTo(el)
  highlightTimer = setTimeout(() => {
    highlightedMessageId.value = null
  }, 1800)
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
  interruptMessageScroll()
  const msgIdx = getRefTargetMsgIdx(message)
  if (!msgIdx) {
    showNotice('引用来源消息缺少 ref_idx，无法定位')
    return
  }
  const targetKey = currentConvKey()
  const seq = ++convLoadSeq
  loadingMore.value = false
  loadingMessages.value = false
  const loaded = messages.value.find(record => record.refIdx === msgIdx && record.id !== message.id)
  if (loaded) {
    highlightMessage(loaded.id)
    if (messageRefreshPending) scheduleMessageRefresh()
    return
  }
  loadingMessages.value = true
  try {
    // 来源不在已加载列表时，由后端按 refIdx 定位所在页。
    const params = new URLSearchParams({
      msgIdx,
      pageSize: String(pageSize),
      excludeId: String(message.id)
    })
    const location = await api(`${messagesBase()}/ref?${params}`)
    if (currentConvKey() !== targetKey || seq !== convLoadSeq) return
    const page = location.page || 1
    const data = await api(messagesPath(page))
    // 请求期间可能已经切走了会话，翻页结果不能再往新会话里塞
    if (currentConvKey() !== targetKey || seq !== convLoadSeq) return
    messages.value = data.records || []
    totalMessages.value = data.total || totalMessages.value
    currentPage.value = page
    viewingHistoryPage = page > 1
    followingLatest = false
    await nextTick()
    if (currentConvKey() !== targetKey || seq !== convLoadSeq) return
    highlightMessage(location.record.id)
  } catch (error) {
    showNotice(error.message || '定位引用来源失败')
  } finally {
    if (currentConvKey() === targetKey && seq === convLoadSeq) {
      loadingMessages.value = false
      if (messageRefreshPending) scheduleMessageRefresh()
    }
  }
}

function cancelMessageRefresh() {
  if (messageRefreshTimer) clearTimeout(messageRefreshTimer)
  messageRefreshTimer = null
  messageRefreshPending = false
  messageRefreshController?.abort()
}

function scheduleMessageRefresh() {
  if (disposed || sseStopped || !active.value) return
  messageRefreshPending = true
  if (messageRefreshTimer || messageRefreshController || loadingMessages.value) return
  // 每个窗口最多发起一次刷新；请求期间的事件在完成后合并补刷。
  messageRefreshTimer = setTimeout(() => {
    messageRefreshTimer = null
    refreshActiveMessages()
  }, 200)
}

async function refreshActiveMessages() {
  if (disposed || sseStopped || !active.value || !messageRefreshPending || messageRefreshController || loadingMessages.value) return
  messageRefreshPending = false
  const targetKey = currentConvKey()
  const seq = convLoadSeq
  const controller = new AbortController()
  messageRefreshController = controller
  try {
    const data = await api(messagesPath(1), { signal: controller.signal })
    if (disposed || controller.signal.aborted || seq !== convLoadSeq || currentConvKey() !== targetKey) return
    const latest = data.records || []
    const count = countNewMessages(latest, knownLatestMessageId, totalMessages.value, data.total || 0)
    const follow = !messagePosition.active && !viewingHistoryPage && (messageScroller.running || isNearBottom())
    const seen = new Set(messages.value.map(m => m.id))
    const fresh = latest.filter(m => !seen.has(m.id))
    if (follow) markArrivingMessages(fresh)
    const latestId = latestMessageId(latest)
    if (latestId > knownLatestMessageId) knownLatestMessageId = latestId
    totalMessages.value = data.total ?? totalMessages.value
    if (viewingHistoryPage) {
      newMessageCount.value += count
      return
    }
    if (fresh.length > 0) {
      followingLatest = follow
      messages.value = [...fresh, ...messages.value]
      if (!follow) newMessageCount.value += count
      await nextTick()
      if (disposed || controller.signal.aborted || currentConvKey() !== targetKey || seq !== convLoadSeq) return
      if (follow && followingLatest) scrollToBottom('smooth')
    }
  } catch { /* 保留当前消息，后续事件可再次触发刷新。 */ }
  finally {
    messageRefreshController = null
    if (messageRefreshPending) scheduleMessageRefresh()
  }
}

function stopSse() {
  sseStopped = true
  if (sseReconnectTimer) clearTimeout(sseReconnectTimer)
  if (sseVerifyTimer) clearTimeout(sseVerifyTimer)
  sseReconnectTimer = null
  sseVerifyTimer = null
  sseVerifyController?.abort()
  sseVerifyController = null
  if (eventSource) eventSource.close()
  eventSource = null
}

function expireSession() {
  if (disposed || sseStopped) return
  stopSse()
  convLoadSeq++
  resetProfile()
  cancelMessageRefresh()
  convRefreshController?.abort()
  if (convRefreshTimer) clearTimeout(convRefreshTimer)
  convRefreshTimer = null
  convRefreshPending = false
  // 失效通知只结束本页，不能清除其他标签页可能已更新的会话 Cookie。
  try { localStorage.removeItem(LEGACY_TOKEN_KEY) } catch { /* 存储不可用不影响退出。 */ }
  window.location.replace(router.resolve('/login').href)
}

function scheduleSseReconnect() {
  if (disposed || sseStopped || sseReconnectTimer) return
  sseReconnectTimer = setTimeout(() => {
    sseReconnectTimer = null
    reconnectSse()
  }, 5000)
}

async function reconnectSse() {
  if (disposed || sseStopped || sseVerifyController) return
  const controller = new AbortController()
  sseVerifyController = controller
  sseVerifyTimer = setTimeout(() => controller.abort(), 5000)
  try {
    const response = await fetch(`${API_BASE}/auth/verify`, {
      credentials: 'same-origin',
      cache: 'no-store',
      signal: controller.signal
    })
    if (disposed || sseStopped || sseVerifyController !== controller) return
    if (response.status === 401 || response.status === 503) {
      expireSession()
    } else if (response.status === 200) {
      connectSse()
    } else {
      scheduleSseReconnect()
    }
  } catch {
    if (!disposed && !sseStopped && sseVerifyController === controller) scheduleSseReconnect()
  } finally {
    if (sseVerifyController === controller) {
      if (sseVerifyTimer) clearTimeout(sseVerifyTimer)
      sseVerifyTimer = null
      sseVerifyController = null
    }
  }
}

function connectSse() {
  if (disposed || sseStopped) return
  if (sseReconnectTimer) clearTimeout(sseReconnectTimer)
  sseReconnectTimer = null
  if (eventSource) eventSource.close()
  const source = new EventSource(`${API_BASE}/events`, { withCredentials: true })
  eventSource = source
  source.addEventListener('session-expired', () => {
    if (disposed || sseStopped || eventSource !== source) return
    expireSession()
  })
  source.onopen = () => {
    if (disposed || eventSource !== source) return
    scheduleConvRefresh()
    scheduleMessageRefresh()
  }
  source.onmessage = (e) => {
    if (disposed || eventSource !== source) return
    try {
      const payload = JSON.parse(e.data)
      if (payload.type !== 'refresh' && payload.type !== 'c2c_refresh') return
      scheduleConvRefresh()
      if (!active.value) return
      const matchesActive =
        (payload.type === 'refresh' && active.value.type === 'group' && payload.groupOpenId === active.value.openId) ||
        (payload.type === 'c2c_refresh' && active.value.type === 'c2c' && payload.userOpenId === active.value.openId)
      if (matchesActive) scheduleMessageRefresh()
    } catch { /* ignore */ }
  }
  source.onerror = () => {
    if (disposed || sseStopped || eventSource !== source) return
    source.close()
    eventSource = null
    scheduleSseReconnect()
  }
}

// ═══════════════ 发送 ═══════════════

async function sendInputNotify() {
  if (active.value?.type !== 'c2c' || sendingInputNotify.value) return
  const targetKey = currentConvKey()
  const openId = active.value.openId
  sendingInputNotify.value = true
  try {
    await api(`/c2c/${encodeURIComponent(openId)}/input-notify`, { method: 'POST' })
    if (currentConvKey() === targetKey) showNotice('已发送正在输入提示（60 秒）')
  } catch (error) {
    if (currentConvKey() === targetKey) showNotice(error.message || '发送输入状态失败')
  } finally {
    sendingInputNotify.value = false
  }
}

async function sendMessage() {
  if (!canSend.value) return
  const useWakeup = active.value.type === 'c2c' && wakeupMode.value
  if (!useWakeup && (passiveMode.value || refMode.value) && !replyTo.value) {
    showNotice('请先右键消息，点击「选择」指定来源')
    return
  }
  if (!useWakeup && passiveMode.value && passiveDisabledReason.value) {
    showNotice(passiveDisabledReason.value)
    return
  }
  if (!useWakeup && refMode.value && referenceDisabledReason.value) {
    showNotice(referenceDisabledReason.value)
    return
  }
  if (msgType.value === 'stream' && !passiveMode.value && !useWakeup) {
    showNotice('流式消息需要开启被动消息或召回')
    return
  }
  sending.value = true
  const type = active.value.type
  const targetKey = currentConvKey()
  try {
    if (msgType.value === 'stream' && type === 'c2c') {
      const body = { userOpenId: active.value.openId, content: draft.value }
      if (useWakeup) body.wakeup = true
      else if (passiveMode.value) body.replyMessageId = replyTo.value.messageOpenId
      await api('/c2c/stream', { method: 'POST', body: JSON.stringify(body) })
    } else {
      const body = { msgType: msgType.value, content: draft.value.trim() }
      if (type === 'group') body.groupOpenId = active.value.openId
      else body.userOpenId = active.value.openId
      if (useWakeup) body.wakeup = true
      if (msgType.value === 'ark') {
        delete body.content
        body.ark = {
          description: arkDraft.description.trim(),
          prompt: arkDraft.prompt.trim(),
          items: arkDraft.items.map(item => ({ description: item.description.trim(), link: item.link.trim() || null }))
        }
      }
      if (msgType.value === 'markdown') {
        body.content = body.content.replace(/@([A-F0-9]{32})/g, '<qqbot-at-user id="$1" />')
      }
      if (msgType.value === 'image' && imageData.value) {
        body.imageType = 'base64'
        body.imageValue = imageData.value
      }
      if (!useWakeup && replyTo.value) {
        if (refMode.value) {
          // 群聊和私聊的文本、Markdown、图片均可独立引用或同时被动回复。
          body.refMessageId = replyTo.value.refIdx
          body.refAuthor = replyTo.value.username || ''
          body.refContent = replyTo.value.content || ''
          body.refAttachments = replyTo.value.attachments || null
        }
        if (passiveMode.value) {
          body.replyMessageId = replyTo.value.messageOpenId
        }
      }
      await api(type === 'group' ? '/groups/send' : '/c2c/send', { method: 'POST', body: JSON.stringify(body) })
    }
    if (currentConvKey() !== targetKey) { scheduleConvRefresh(); return }
    draft.value = ''
    resetArkDraft()
    imageData.value = null
    pastePreview.value = null
    cancelReply()
    wakeupMode.value = false
    await loadLatestMessages({ animate: true })
    scheduleConvRefresh()
  } catch (error) {
    showNotice(error.message || '发送消息失败')
  } finally {
    sending.value = false
    // 发送期间 textarea 是 disabled 的，浏览器会把焦点甩回 body。
    // 等这一帧把 disabled 撤掉之后再收回焦点，否则连着按 Enter 发消息要重新点输入框。
    await nextTick()
    composerRef.value?.focus()
  }
}

function resetArkDraft() {
  arkDraft.description = ''
  arkDraft.prompt = ''
  arkDraft.items = [{ description: '', link: '' }]
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

function selectReplyTarget(message) {
  wakeupMode.value = false
  replyTo.value = message
  nextTick(() => composerRef.value?.focus())
}

function toggleWakeupMode() {
  if (active.value?.type !== 'c2c') return
  wakeupMode.value = !wakeupMode.value
  if (wakeupMode.value) cancelReply()
}

function cancelReply() {
  replyTo.value = null
  passiveMode.value = false
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
const mutePickerEl = ref(null)
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
      const menu = mutePickerEl.value?.querySelector('.mute-picker-menu')
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
  mutePickerOpen.value = null
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

function isBotStaff(user) {
  return !user.senderIsBot && (user.userRole === 'OWNER' || user.userRole === 'ADMIN')
}

// 机器人自己发的消息库里往往没存 union_openId，用 /config 拿到的 botOpenId 兜底，
// 否则我们这侧的消息头会只有名字没有 ID
function displayUid(message) {
  if (message.unionOpenId) return message.unionOpenId
  return isMe(message) ? botOpenId.value : ''
}

function renderContent(message) {
  if (legacyMedia(message)) return ''
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

// 旧的 BOT_SEND 记录只有媒体凭证，不能把 file_info 当作音频 URL 或 base64 音频。
function parseLegacyMedia(content) {
  if (typeof content !== 'string') return null
  const match = content.match(/^\s*\[media\]\s*(\{[\s\S]*\})\s*$/)
  if (!match) return null
  try {
    const media = JSON.parse(match[1])
    return typeof media?.file_info === 'string' && media.file_info.trim() ? media : null
  } catch { return null }
}

function legacyMedia(message) {
  return isMe(message) ? parseLegacyMedia(message.content) : null
}

function hasArk(message) {
  return hasArkMessage(message?.ark)
}

function forwardRecord(message) {
  if (!message || typeof message !== 'object') return null
  const content = message.content || ''
  const cached = forwardCache.get(message)
  if (cached && cached.content === content) return cached.value
  const value = parseForwardContent(content)
  forwardCache.set(message, { content, value })
  return value
}

// Markdown 里的图片由 v-html 动态生成，不能在 img 上绑定 Vue 的 @error。
// 在父容器捕获加载错误后替换节点，避免失效图片仍按声明尺寸撑开消息气泡。
function replaceBrokenMarkdownImage(event) {
  const image = event.target
  if (!(image instanceof HTMLImageElement) || !image.closest('.md-body')) return
  const fallback = document.createElement('span')
  fallback.className = 'md-image-fallback'
  fallback.textContent = '[不支持加载的图片]'
  image.replaceWith(fallback)
}

function msgRef(message) {
  try {
    const raw = message.messageReference
    if (!raw) return null
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    const ref = Array.isArray(arr) ? arr[0] : arr
    if (!ref) return null
    // 新引用对象优先展示预览，完整内容保存在 content；旧记录继续使用 content。
    const preview = typeof ref.preview === 'string' ? ref.preview : ''
    const fullContent = typeof ref.content === 'string' ? ref.content : ''
    // 自发消息带 WebUI 快照；平台接收消息仍只使用 preview / content。
    const display = ref.webui && typeof ref.webui === 'object' ? ref.webui : null
    let content = preview.trim() ? preview + '...' : fullContent
    const attachments = Array.isArray(display?.attachments) ? display.attachments
      : Array.isArray(ref.attachments) ? ref.attachments : []
    if (display && !content.trim() && !attachments.length && findRefIdxValue(ref)) content = '[引用消息]'
    if (!content.trim() && !attachments.length) return null
    return {
      author: display?.author || ref.author?.username || '',
      content,
      attachments
    }
  } catch { return null }
}

function renderRefContent(ref) {
  const parts = []
  if (ref.content) {
    let t = parseLegacyMedia(ref.content) ? '[媒体消息]' : renderFaceTags(ref.content)
    t = t.replace(/<qqbot-at-user id="([A-F0-9]+)"\s*\/>/g, '@$1')
    t = t.replace(/<qqbot-cmd-input[^>]*show="([^"]*)"[^>]*\/>/g, '$1')
    if (t.trim()) parts.push(`<p>${escapeHtml(t)}</p>`)
  }
  for (const a of ref.attachments || []) {
    if (!a || typeof a !== 'object') continue
    const type = a.content_type || ''
    if (type === 'image' || type.startsWith('image/')) {
      if (a.url) {
        parts.push(`<img src="${escapeHtml(absUrl(a.url))}" referrerpolicy="no-referrer" style="max-width:120px;max-height:80px;border-radius:4px;display:block" alt="图片">`)
      } else {
        parts.push(`<span>[图片] ${escapeHtml(a.filename || '')}</span>`)
      }
    } else if (type === 'video' || type.startsWith('video/')) {
      // 引用块只有 120×80，塞播放器没意义，标一下类型就够
      parts.push(`<span>[视频] ${escapeHtml(a.filename || '')}</span>`)
    } else if (type === 'voice' || type === 'audio' || type.startsWith('audio/')) {
      parts.push(`<span>[语音] ${escapeHtml(a.asr_refer_text || a.filename || '')}</span>`)
    } else {
      parts.push(`<span>[文件] ${escapeHtml(a.filename || '')}</span>`)
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
  if (contentType === 'voice' || contentType.startsWith('audio/')) {
    return {
      ...att,
      type: 'voice',
      url: absUrl(att.url),
      asrText: att.asr_refer_text || '',
      voiceUrl: absUrl(att.voice_wav_url || att.url)
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
