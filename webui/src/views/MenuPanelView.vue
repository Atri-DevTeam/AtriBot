<template>
  <div class="shell">
    <AppSidebar v-model:open="sidebarOpen" :app-id="appId" :bot-open-id="botOpenId" :bot-name="botName">
      <template #toolbar>
        <button class="ghost-button" @click="logout">退出</button>
      </template>
    </AppSidebar>
    <div class="sidebar-spacer"/>

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button v-show="!sidebarOpen" class="menu-btn" aria-label="打开侧边栏" @click="sidebarOpen = true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <h2 class="feedback-title">菜单与面板</h2>
          <div class="feedback-tabs">
            <button :class="{ active: tab === 'menu' }" @click="switchTab('menu')">自定义菜单</button>
            <button :class="{ active: tab === 'panel' }" @click="switchTab('panel')">指令面板</button>
          </div>
        </div>
        <div class="topbar-right mp-transfer-actions">
          <input ref="transferFileInput" type="file" accept="application/json,.json" hidden @change="importTransferFile"/>
          <button class="ghost-button" title="导入单个配置" aria-label="导入单个配置" :disabled="transferBusy" @click="transferFileInput?.click()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><polyline points="7 10 12 15 17 10"/><path d="M5 21h14"/></svg>
            <span class="mp-transfer-label">导入单项</span>
          </button>
        </div>
      </header>

      <section class="content feedback-layout">
        <!-- ============ 自定义菜单 ============ -->
        <section v-if="tab === 'menu'" class="chat-panel feedback-panel">
          <div class="chat-head">
            <strong>自定义菜单（单聊）</strong>
            <div class="chat-head-right">
              <span v-if="menuVersion > 0" class="status-pill"><span class="dot ok"></span>版本 {{ menuVersion }}</span>
              <button class="ghost-button" :disabled="menuLoading" @click="loadMenu">
                {{ menuLoading ? '加载中...' : '重新加载' }}
              </button>
              <button class="ghost-button" :disabled="transferBusy || menuLoading || menuItems.length === 0" @click="exportMenuFile">
                导出菜单
              </button>
              <button class="primary-button" :disabled="menuSaving || menuItems.length === 0" @click="saveMenu">
                {{ menuSaving ? '保存中...' : '保存菜单' }}
              </button>
            </div>
          </div>

          <div class="feedback-content">
            <div v-if="menuLoading" class="empty-state">加载中...</div>
            <div v-else-if="menuError" class="empty-state error">{{ menuError }}</div>
            <div v-else class="mp-menu-layout">
              <div class="mp-menu-preview">
                <div class="config-preview-phone">
                  <div class="config-preview-phone-body">
                    <div class="config-preview-phone-body-info mp-phone-info-menu">
                      <div class="config-preview-phone-body-info-head mp-phone-head-menu"></div>
                      <div class="mp-menu-dock">
                        <div v-if="menuItems.length" class="mp-menu-bar" @wheel.prevent="onMenuBarWheel">
                          <div v-for="(item, idx) in menuItems" :key="idx" class="mp-menu-btn">
                            <svg v-if="item.type === 'send_message'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                            <svg v-else-if="item.type === 'link'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                            <svg v-else-if="item.type === 'menu'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
                            <span class="mp-menu-btn-label">{{ item.name || '未命名' }}</span>
                            <span v-if="item.type === 'switch'" class="mp-switch" :class="{ 'mp-switch--on': item.defaultOn }"><span class="mp-switch-knob"/></span>
                            <div v-if="item.type === 'menu' && item.subMenuItems.length" class="mp-sub-popup">
                              <div v-for="(sub, si) in item.subMenuItems" :key="si" class="mp-sub-item">{{ sub.name || '子项' }}</div>
                            </div>
                          </div>
                        </div>
                        <div v-else class="mp-menu-bar mp-menu-bar--empty">
                          <span class="mp-phone-hint">暂无菜单</span>
                        </div>
                        <div class="mp-menu-keyboard"></div>
                      </div>
                    </div>
                  </div>
                  <div class="config-preview-phone-text">
                    <span>手机预览</span>
                  </div>
                </div>
              </div>
              <div class="mp-menu-editor">
              <div v-if="menuItems.length === 0" class="mp-hint">
                尚未设置菜单，点击下方「添加菜单项」开始配置，最多 10 个菜单项
              </div>
              <div v-for="(item, idx) in menuItems" :key="idx" class="mp-menu-item"
                   :class="{ 'mp-dragging': menuDragIndex === idx }"
                   @dragover.prevent @drop.prevent="onMenuDrop(idx)">
                <div class="mp-menu-item-head">
                  <span class="mp-drag" title="拖动排序" draggable="true" @dragstart="onMenuDragStart(idx, $event)" @dragend="onMenuDragEnd">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="9" cy="5" r="1.6"/><circle cx="15" cy="5" r="1.6"/><circle cx="9" cy="12" r="1.6"/><circle cx="15" cy="12" r="1.6"/><circle cx="9" cy="19" r="1.6"/><circle cx="15" cy="19" r="1.6"/></svg>
                  </span>
                  <span class="mp-idx">#{{ idx + 1 }}</span>
                  <select v-model="item.type" class="gs-input mp-type">
                    <option value="send_message">发送消息</option>
                    <option value="link">链接跳转</option>
                    <option value="switch">开关</option>
                    <option value="menu">折叠子菜单</option>
                  </select>
                  <input v-model="item.name" class="gs-input mp-name" type="text" maxlength="10"
                         placeholder="按钮名称（最多 5 个汉字或 10 个字母）"
                         @input="limitMenuNameInput(item, 10, $event)"/>
                  <select v-model="item.align" class="gs-input mp-align">
                    <option value="left">左对齐</option>
                    <option value="right">右对齐</option>
                  </select>
                  <button class="icon-button" title="删除菜单项" @click="removeMenuItem(idx)">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                  </button>
                </div>

                <div class="mp-menu-item-body">
                  <label v-if="item.type === 'send_message'" class="gs-form-row">
                    <span class="gs-form-label">发送内容</span>
                    <input v-model="item.sendMessage" class="gs-input" type="text" placeholder="点击后自动填入输入框的内容"/>
                  </label>

                  <label v-if="item.type === 'link'" class="gs-form-row">
                    <span class="gs-form-label">跳转链接</span>
                    <input v-model="item.link" class="gs-input" type="text" placeholder="必须以 https:// 开头"/>
                  </label>

                  <div v-if="item.type === 'switch'" class="mp-switch-cfg">
                    <label class="gs-form-row">
                      <span class="gs-form-label">开关标识 switch_id</span>
                      <input v-model="item.switchId" class="gs-input" type="text" placeholder="唯一标识，如 search"/>
                    </label>
                    <label class="checkbox-label">
                      <input type="checkbox" v-model="item.defaultOn"/> 默认打开
                    </label>
                  </div>

                  <div v-if="item.type === 'menu'" class="mp-subs">
                    <div class="mp-subs-head">
                      <span class="gs-form-label">子菜单（最多 5 个，不支持再嵌套）</span>
                      <button class="ghost-button mp-mini" :disabled="item.subMenuItems.length >= 5" @click="addSubItem(item)">添加子项</button>
                    </div>
                    <div v-for="(sub, si) in item.subMenuItems" :key="si" class="mp-sub">
                      <select v-model="sub.type" class="gs-input mp-type">
                        <option value="send_message">发送消息</option>
                        <option value="link">链接跳转</option>
                      </select>
                      <input v-model="sub.name" class="gs-input mp-name" type="text" maxlength="14"
                             placeholder="子项名称（最多 7 个汉字或 14 个字母）"
                             @input="limitMenuNameInput(sub, 14, $event)"/>
                      <input v-if="sub.type === 'send_message'" v-model="sub.sendMessage" class="gs-input" type="text" placeholder="发送内容"/>
                      <input v-else v-model="sub.link" class="gs-input" type="text" placeholder="https:// 链接"/>
                      <button class="icon-button" title="删除子项" @click="item.subMenuItems.splice(si, 1)">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <button class="ghost-button mp-add" :disabled="menuItems.length >= 10" @click="addMenuItem">
                + 添加菜单项
              </button>
              </div>
            </div>
          </div>
        </section>

        <!-- ============ 指令面板 ============ -->
        <section v-else class="chat-panel feedback-panel">
          <div class="chat-head">
            <strong>指令面板</strong>
            <div class="chat-head-right">
              <span class="status-pill"><span class="dot ok"></span>{{ panels.length }} 个面板</span>
              <button class="ghost-button" :disabled="panelLoading" @click="loadPanels(true)">
                {{ panelLoading ? '加载中...' : '刷新' }}
              </button>
              <button class="primary-button mp-add-btn" @click="openCreate">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                新建面板
              </button>
            </div>
          </div>

          <div class="feedback-content">
            <div v-if="panelLoading" class="empty-state">加载中...</div>
            <div v-else-if="panelError" class="empty-state error">{{ panelError }}</div>
            <div v-else-if="panels.length === 0" class="empty-state">还没有指令面板，点右上角「新建面板」创建</div>
            <div v-else class="mp-panel-layout">
              <div class="mp-panel-preview">
                <div class="config-preview-phone">
                  <div class="config-preview-phone-body">
                    <div class="config-preview-phone-body-info">
                      <div class="config-preview-phone-body-info-head"></div>
                      <div class="config-preview-phone-body-info-screen">
                        <div class="config-preview-phone-body-info-screen-bar">
                          <div class="bar"></div>
                        </div>
                        <div class="config-preview-phone-body-info-screen-bot">
                          <img v-if="botAvatar" class="bot-logo" :src="botAvatar" referrerpolicy="no-referrer"/>
                          <div v-else class="bot-logo bot-logo--placeholder"></div>
                          <div class="bot-name">{{ botName }}</div>
                        </div>
                        <div class="config-preview-phone-body-info-screen-config">
                          <div v-if="previewItems.length === 0" class="config-preview-phone-body-info-screen-none">
                            <span>暂无指令</span>
                          </div>
                          <div v-for="(it, i) in previewItems" :key="i" class="config-preview-phone-body-info-screen-config-item">
                            <div v-if="it.type === 'link'" class="config-preview-phone-body-info-screen-config-item-server">
                              <span>{{ it.name || '链接' }}</span>
                              <svg class="chevron" width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
                            </div>
                            <div v-else class="config-preview-phone-body-info-screen-config-item-command">
                              <span class="command-name">/{{ it.name || '指令' }}</span>
                              <span class="command-desc">{{ it.desc || '' }}</span>
                            </div>
                          </div>
                        </div>
                        <div class="config-preview-phone-body-info-screen-bothead">
                          <div class="bot-head">
                            <img v-if="botAvatar" class="bot-logo" :src="botAvatar" referrerpolicy="no-referrer"/>
                            <div v-else class="bot-logo bot-logo--placeholder"></div>
                          </div>
                        </div>
                        <div class="config-preview-phone-body-info-screen-board"></div>
                      </div>
                    </div>
                  </div>
                  <div class="config-preview-phone-text">
                    <span>手机预览</span>
                  </div>
                </div>
              </div>
              <div class="mp-panel-grid">
              <article v-for="p in panels" :key="p.panelId" class="mp-panel-card"
                       :class="{ 'mp-panel-card--selected': p.panelId === selectedPanel?.panelId }"
                       @click="selectPanel(p)">
                <div class="mp-panel-card-head">
                  <div class="mp-panel-card-title">
                    <span class="mp-panel-title">{{ p.panel?.remark || '未命名面板' }}</span>
                    <span class="mp-version">版本 {{ p.version }}</span>
                  </div>
                  <div class="mp-panel-badges">
                    <span class="mp-badge" :class="'mp-badge--' + p.scope">
                      <svg v-if="p.scope === 'c2c'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                      <svg v-else-if="p.scope === 'group'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                      <svg v-else-if="p.scope === 'channel'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="9" x2="20" y2="9"/><line x1="4" y1="15" x2="20" y2="15"/><line x1="10" y1="3" x2="8" y2="21"/><line x1="16" y1="3" x2="14" y2="21"/></svg>
                      <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
                      {{ scopeLabel(p.scope) }}
                    </span>
                    <span class="mp-badge" :class="p.targetType === 'specific' ? 'mp-badge--specific' : 'mp-badge--all'">{{ p.targetType === 'specific' ? '指定对象' : '全局' }}</span>
                    <span v-if="isLegacyPanel(p)" class="mp-badge mp-badge--legacy" title="旧版面板">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
                      旧版
                    </span>
                  </div>
                </div>
                <div class="mp-panel-card-body">
                  <div class="mp-items-preview">
                    <span v-for="(it, i) in (p.panel?.items || [])" :key="i" class="mp-item-chip">
                      <svg v-if="it.type === 'link'" class="mp-chip-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>
                      <span v-else class="mp-chip-slash">/</span>
                      {{ it.name }}
                    </span>
                    <span v-if="!(p.panel?.items || []).length" class="gs-muted">无面板元素</span>
                  </div>
                </div>
                <div class="mp-panel-card-foot">
                  <span class="gs-id" :title="p.panelId">#{{ p.panelId }}</span>
                  <div class="mp-panel-actions">
                    <button class="icon-button mp-export" title="导出此面板" :disabled="transferBusy" @click.stop="exportPanelFile(p)">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 15V3"/><polyline points="7 8 12 3 17 8"/><path d="M5 21h14"/></svg>
                    </button>
                    <button class="icon-button mp-edit" title="编辑" @click.stop="openEdit(p)">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
                    </button>
                    <button class="icon-button mp-target" title="关联对象"
                            :disabled="p.targetType !== 'specific'" @click.stop="openTarget(p)">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                    </button>
                    <button class="icon-button mp-danger" :title="isLegacyPanel(p) ? '旧版面板不可删除' : '删除'"
                            :disabled="isLegacyPanel(p)" @click.stop="removePanel(p)">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                    </button>
                  </div>
                </div>
              </article>
              </div>
            </div>
          </div>
        </section>
      </section>
    </main>

    <!-- ============ 面板新建/编辑弹窗 ============ -->
    <div v-if="panelModal" class="modal-backdrop">
      <div class="modal">
        <div class="modal-head">
          <h2>{{ panelModal.mode === 'create' ? '新建面板' : '编辑面板' }}</h2>
          <button class="icon-button" @click="closePanelModal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body gs-form">
          <div v-if="panelModal.mode === 'create'" class="mp-form-grid">
            <label class="gs-form-row">
              <span class="gs-form-label">生效场景</span>
              <select v-model="panelForm.scope" class="gs-input">
                <option value="c2c">单聊 c2c</option>
                <option value="group">群聊 group</option>
                <option value="channel">文字子频道 channel</option>
                <option value="dm">频道私信 dm</option>
              </select>
            </label>
            <label class="gs-form-row">
              <span class="gs-form-label">作用范围</span>
              <select v-model="panelForm.targetType" class="gs-input" :disabled="!scopeSupportSpecific(panelForm.scope)">
                <option value="all" :disabled="createHasAllPanel">全局</option>
                <option value="specific">指定对象</option>
              </select>
            </label>
          </div>
          <label v-if="panelModal.mode === 'create'" class="gs-form-row">
            <span class="gs-form-label">复制自面板（可选）</span>
            <select v-model="panelForm.copySourceId" class="gs-input" @change="copyPanelFromSource">
              <option value="">不复制，创建空白面板</option>
              <option v-for="p in panels" :key="p.panelId" :value="p.panelId">{{ panelOptionLabel(p) }}</option>
            </select>
            <span class="gs-muted">复制来源的备注和面板元素；生效场景、作用范围和关联对象使用当前设置。</span>
          </label>
          <div v-if="panelModal.mode === 'create' && createHasAllPanel" class="gs-muted">
            {{ scopeSupportSpecific(panelForm.scope) ? '该场景已存在全局面板，仅可编辑，可新建多个指定对象面板' : '该场景已存在全局面板且仅支持全局，无法新建，请编辑现有面板' }}
          </div>

          <div v-if="panelModal.mode === 'create' && panelForm.targetType === 'specific'" class="mp-form-grid">
            <label v-if="panelForm.scope === 'c2c'" class="gs-form-row">
              <span class="gs-form-label">用户 openid（每行一个，最多 20）</span>
              <textarea v-model="panelForm.userOpenIdsText" class="gs-textarea" rows="3" placeholder="每行一个用户 openid"/>
            </label>
            <label v-if="panelForm.scope === 'group'" class="gs-form-row">
              <span class="gs-form-label">群 openid（每行一个，最多 20）</span>
              <textarea v-model="panelForm.groupOpenIdsText" class="gs-textarea" rows="3" placeholder="每行一个群 openid"/>
            </label>
          </div>

          <label class="gs-form-row">
            <span class="gs-form-label">备注（不对用户展示）</span>
            <input v-model="panelForm.remark" class="gs-input" type="text" maxlength="255" placeholder="面板备注，可留空"/>
          </label>

          <div v-if="panelModal.mode === 'edit'" class="mp-editor-import">
            <input ref="panelEditorFileInput" type="file" accept="application/json,.json" hidden
                   @change="importPanelIntoEditor"/>
            <div>
              <span class="gs-form-label">导入到当前面板</span>
              <div class="gs-muted">仅替换当前编辑器中的备注和面板元素，生效场景与关联对象保持不变；点击保存后生效。</div>
            </div>
            <button class="ghost-button" :disabled="transferBusy" @click="panelEditorFileInput?.click()">
              选择面板文件
            </button>
          </div>

          <div class="mp-panel-items">
            <div class="mp-subs-head">
              <span class="gs-form-label">面板元素（最多 20 个）</span>
              <button class="ghost-button mp-mini" :disabled="panelForm.items.length >= 20" @click="addPanelItem">添加元素</button>
            </div>
            <div v-for="(it, i) in panelForm.items" :key="i" class="mp-panel-item"
                 :class="{ 'mp-dragging': panelDragIndex === i }"
                 @dragover.prevent @drop.prevent="onPanelDrop(i)">
              <span class="mp-drag" title="拖动排序" draggable="true" @dragstart="onPanelDragStart(i, $event)" @dragend="onPanelDragEnd">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="9" cy="5" r="1.6"/><circle cx="15" cy="5" r="1.6"/><circle cx="9" cy="12" r="1.6"/><circle cx="15" cy="12" r="1.6"/><circle cx="9" cy="19" r="1.6"/><circle cx="15" cy="19" r="1.6"/></svg>
              </span>
              <span class="mp-idx">#{{ i + 1 }}</span>
              <span class="mp-type-icon" :class="it.type === 'link' ? 'mp-type-icon--link' : 'mp-type-icon--command'">
                <svg v-if="it.type === 'link'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>
                <span v-else class="mp-slash">/</span>
              </span>
              <select v-model="it.type" class="gs-input mp-type">
                <option value="command">指令</option>
                <option value="link">链接跳转</option>
              </select>
              <input v-model="it.name" class="gs-input mp-name" type="text" maxlength="14" placeholder="名称"/>
              <input v-if="it.type === 'command'" v-model="it.desc" class="gs-input" type="text" maxlength="30" placeholder="描述"/>
              <template v-else>
                <input v-model="it.link" class="gs-input" type="text" placeholder="链接 URL"/>
                <input v-model="it.desc" class="gs-input" type="text" maxlength="30" placeholder="介绍，可留空"/>
              </template>
              <label class="checkbox-label mp-only-admin">
                <input type="checkbox" v-model="it.onlyAdmin"/> 仅管理员
              </label>
              <button class="icon-button" title="删除元素" @click="panelForm.items.splice(i, 1)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
              </button>
            </div>
            <div v-if="panelForm.items.length === 0" class="gs-muted">暂无元素，点「添加元素」开始</div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closePanelModal">取消</button>
          <button class="primary-button" :disabled="submitting || panelForm.items.length === 0 || (panelModal.mode === 'create' && createBlocked)" @click="savePanel">
            {{ submitting ? '提交中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ 关联对象管理弹窗 ============ -->
    <div v-if="targetModal" class="modal-backdrop" @click.self="closeTarget">
      <div class="modal">
        <div class="modal-head">
          <h2>管理关联对象</h2>
          <button class="icon-button" @click="closeTarget">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body gs-form">
          <div class="gs-muted">{{ targetModal.scope === 'c2c' ? '用户 openid 列表' : '群 openid 列表' }}</div>
          <div class="gs-whitelist-list">
            <div v-if="targetModal.existing.length === 0" class="gs-muted">暂无关联对象</div>
            <div v-for="id in targetModal.existing" :key="id" class="gs-whitelist-chip">
              <span>{{ id }}</span>
              <button class="gs-chip-del" title="移除" :disabled="submitting" @click="removeTarget(id)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
          <label class="gs-form-row">
            <span class="gs-form-label">批量添加（每行一个，最多 20）</span>
            <textarea v-model="targetModal.input" class="gs-textarea" rows="3" :placeholder="targetModal.scope === 'c2c' ? '每行一个用户 openid' : '每行一个群 openid'"/>
          </label>
        </div>
        <div class="modal-foot">
          <button class="ghost-button" @click="closeTarget">关闭</button>
          <button class="primary-button" :disabled="submitting || parseLines(targetModal.input).length === 0" @click="addTarget">
            {{ submitting ? '提交中...' : '添加' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref, reactive, computed, watch, onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import {createTransferDocument, parseTransferDocument} from '../lib/menuPanelTransfer.js'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

const tab = ref('menu')
const transferBusy = ref(false)
const transferFileInput = ref(null)
const panelEditorFileInput = ref(null)

// ============ 自定义菜单 ============
const menuLoading = ref(false)
const menuError = ref('')
const menuVersion = ref(0)
const menuItems = ref([])
const menuSaving = ref(false)

// ============ 指令面板 ============
const PANEL_SCOPES = ['c2c', 'group', 'channel', 'dm']
const panels = ref([])
const panelLoading = ref(false)
const panelError = ref('')
const submitting = ref(false)

const panelModal = ref(null)
const panelForm = reactive({
  scope: 'c2c',
  targetType: 'all',
  copySourceId: '',
  userOpenIdsText: '',
  groupOpenIdsText: '',
  remark: '',
  items: []
})

const createHasAllPanel = computed(() => scopeHasAllPanel(panelForm.scope))
const createBlocked = computed(() => scopeHasAllPanel(panelForm.scope) && !scopeSupportSpecific(panelForm.scope))

const targetModal = ref(null)

// ============ 预览 ============
const selectedPanelId = ref('')
const selectedPanel = computed(() => panels.value.find(p => p.panelId === selectedPanelId.value) || panels.value[0] || null)
const previewItems = computed(() => selectedPanel.value?.panel?.items || [])
const botAvatar = computed(() => appId.value && botOpenId.value ? `https://thirdqq.qlogo.cn/qqapp/${appId.value}/${botOpenId.value}/100` : '')

// ============ 通用 ============

function authHeaders() {
  return {'Content-Type': 'application/json'}
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
  if (res.status === 401) {
    logout()
    throw new Error('未授权')
  }
  if (payload.status !== 200) throw new Error(payload.message || '请求失败')
  return payload.data
}

function logout() {
  fetch(`${API_BASE}/auth/logout`, {method: 'POST', credentials: 'same-origin'}).finally(() => {
    router.replace('/login')
  })
}

function switchTab(name) {
  tab.value = name
  if (name === 'panel' && panels.value.length === 0 && !panelLoading.value) {
    loadPanels(true)
  }
}

async function fetchAllPanels() {
  const result = []
  for (const scope of PANEL_SCOPES) {
    let cursor = ''
    const seenCursors = new Set()
    do {
      const query = new URLSearchParams({scope, limit: '50'})
      if (cursor) query.set('cursor', cursor)
      const page = await api(`/panels?${query}`)
      result.push(...(page?.records || []))
      if (result.length > 200) throw new Error('面板数量超过 200，导出已停止')
      const nextCursor = page?.nextCursor || ''
      if (!nextCursor || page?.isEnd) break
      if (seenCursors.has(nextCursor)) throw new Error(`${scope} 面板分页游标重复，导出已停止`)
      seenCursors.add(nextCursor)
      cursor = nextCursor
    } while (true)
  }
  return result
}

function transferPanelData(record, detail) {
  const targetType = record.targetType || 'all'
  return {
    scope: record.scope,
    targetType,
    panel: record.panel,
    userOpenIds: targetType === 'specific' && record.scope === 'c2c' ? (detail?.userOpenIds || []) : [],
    groupOpenIds: targetType === 'specific' && record.scope === 'group' ? (detail?.groupOpenIds || []) : []
  }
}

function downloadJson(data, filename) {
  const blob = new Blob([JSON.stringify(data, null, 2) + '\n'], {type: 'application/json;charset=utf-8'})
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function safeFilenamePart(value) {
  return (value || 'atribot').trim().replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-+|-+$/g, '') || 'atribot'
}

function targetBatches(values) {
  const batches = []
  for (let index = 0; index < values.length; index += 20) batches.push(values.slice(index, index + 20))
  return batches
}

async function exportMenuFile() {
  transferBusy.value = true
  try {
    const menuData = await api('/menu')
    const document = createTransferDocument({
      menu: menuData?.menu?.items?.length ? menuData.menu : null,
      panels: []
    })
    const date = new Date().toISOString().slice(0, 10)
    downloadJson(document, `${safeFilenamePart(botName.value)}-menu-${date}.json`)
  } catch (e) {
    alert('导出失败: ' + e.message)
  } finally {
    transferBusy.value = false
  }
}

async function exportPanelFile(record) {
  transferBusy.value = true
  try {
    const detail = (record.targetType || 'all') === 'specific'
      ? await api(`/panels/${record.panelId}`)
      : null
    const document = createTransferDocument({
      menu: null,
      panels: [transferPanelData(record, detail)]
    })
    const date = new Date().toISOString().slice(0, 10)
    const name = safeFilenamePart(record.panel?.remark || record.panelId)
    downloadJson(document, `${safeFilenamePart(botName.value)}-panel-${name}-${date}.json`)
  } catch (e) {
    alert('导出失败: ' + e.message)
  } finally {
    transferBusy.value = false
  }
}

async function applyTransferDocument(document) {
  const hasMenu = !!document.menu
  const hasPanel = document.panels.length === 1
  if ((hasMenu ? 1 : 0) + (hasPanel ? 1 : 0) !== 1) {
    throw new Error('导入文件必须只包含一个菜单或一个指令面板')
  }

  if (hasMenu) {
    try {
      await api('/menu', {method: 'PUT', body: JSON.stringify({menu: document.menu})})
    } catch (e) {
      throw new Error(`自定义菜单：${e.message}`)
    }
    await loadMenu()
    alert('导入完成：自定义菜单')
    return
  }

  const entry = document.panels[0]
  const existingPanels = await fetchAllPanels()
  let createdPanelId = ''
  try {
    const existing = entry.targetType === 'all'
      ? existingPanels.find(panel => panel.scope === entry.scope && (panel.targetType || 'all') === 'all')
      : null
    if (existing) {
      await api(`/panels/${existing.panelId}`, {method: 'PUT', body: JSON.stringify({panel: entry.panel})})
    } else {
      const body = {scope: entry.scope, targetType: entry.targetType, panel: entry.panel}
      const targets = entry.scope === 'c2c' ? entry.userOpenIds : entry.groupOpenIds
      const batches = entry.targetType === 'specific' ? targetBatches(targets) : []
      if (batches.length && entry.scope === 'c2c') body.userOpenIds = batches[0]
      if (batches.length && entry.scope === 'group') body.groupOpenIds = batches[0]
      const panelId = await api('/panels', {method: 'POST', body: JSON.stringify(body)})
      createdPanelId = panelId
      for (const batch of batches.slice(1)) {
        const targetBody = {op: 'add'}
        if (entry.scope === 'c2c') targetBody.userOpenIds = batch
        else targetBody.groupOpenIds = batch
        await api(`/panels/${panelId}/target`, {method: 'PUT', body: JSON.stringify(targetBody)})
      }
    }
  } catch (e) {
    const partial = createdPanelId ? '（面板已创建，部分关联对象可能未导入）' : ''
    throw new Error(`${scopeLabel(entry.scope)}面板「${entry.panel.remark || '未命名'}」：${e.message}${partial}`)
  }

  await loadPanels(true)
  alert(`导入完成：${scopeLabel(entry.scope)}面板「${entry.panel.remark || '未命名'}」`)
}

async function importTransferFile(event) {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  transferBusy.value = true
  try {
    const document = parseTransferDocument(await file.text())
    const hasMenu = !!document.menu
    const hasPanel = document.panels.length === 1
    if ((hasMenu ? 1 : 0) + (hasPanel ? 1 : 0) !== 1) {
      throw new Error('只支持导入一个菜单或一个指令面板，请选择单项配置文件')
    }
    const summary = hasMenu
      ? '1 份自定义菜单'
      : `1 个${scopeLabel(document.panels[0].scope)}指令面板`
    const confirmed = confirm(
      `将导入${summary}。\n\n` +
      (hasMenu
        ? '自定义菜单会被覆盖。是否继续？'
        : '同场景的全局面板会更新；指定对象面板会新增。是否继续？')
    )
    if (!confirmed) return
    await applyTransferDocument(document)
  } catch (e) {
    alert('导入失败: ' + e.message)
  } finally {
    transferBusy.value = false
  }
}

async function importPanelIntoEditor(event) {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  transferBusy.value = true
  try {
    const document = parseTransferDocument(await file.text())
    if (document.menu || document.panels.length !== 1) {
      throw new Error('请选择只包含一个指令面板的导出文件')
    }
    const entry = document.panels[0]
    const confirmed = confirm(
      `将用导入文件中的备注和 ${entry.panel.items.length} 个面板元素覆盖当前编辑内容。\n\n` +
      '当前面板的生效场景、作用范围和关联对象不会改变。是否继续？'
    )
    if (!confirmed) return

    panelForm.remark = entry.panel.remark || ''
    panelForm.items = panelToDraftItems(entry.panel)
    if (panelForm.items.length === 0) panelForm.items.push(newPanelDraftItem())
    alert('已导入到当前编辑器，点击“保存”后生效。')
  } catch (e) {
    alert('导入失败: ' + e.message)
  } finally {
    transferBusy.value = false
  }
}

// ============ 自定义菜单 ============

function newMenuDraftItem() {
  return {name: '', type: 'send_message', subMenuItems: [], sendMessage: '', link: '', switchId: '', defaultOn: false, align: 'left'}
}

function newMenuDraftSub() {
  return {name: '', type: 'send_message', sendMessage: '', link: ''}
}

function menuNameLength(value) {
  return Array.from(value || '').reduce((length, char) => length + (char.codePointAt(0) <= 0x7f ? 1 : 2), 0)
}

function truncateMenuName(value, maxLength) {
  let result = ''
  let length = 0
  for (const char of value || '') {
    const charLength = char.codePointAt(0) <= 0x7f ? 1 : 2
    if (length + charLength > maxLength) break
    result += char
    length += charLength
  }
  return result
}

function limitMenuNameInput(item, maxLength, event) {
  if (event.isComposing) return
  const limitedName = truncateMenuName(event.target.value, maxLength)
  item.name = limitedName
  event.target.value = limitedName
}

function addMenuItem() {
  if (menuItems.value.length >= 10) return
  menuItems.value.push(newMenuDraftItem())
}

function removeMenuItem(idx) {
  menuItems.value.splice(idx, 1)
}

function addSubItem(item) {
  if (!item.subMenuItems) item.subMenuItems = []
  if (item.subMenuItems.length >= 5) return
  item.subMenuItems.push(newMenuDraftSub())
}

function onMenuBarWheel(e) {
  const el = e.currentTarget
  const delta = Math.abs(e.deltaY) >= Math.abs(e.deltaX) ? e.deltaY : e.deltaX
  el.scrollLeft += delta
}

// ============ 菜单拖拽排序 ============
const menuDragIndex = ref(-1)

function onMenuDragStart(idx, e) {
  menuDragIndex.value = idx
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

function onMenuDragEnd() {
  menuDragIndex.value = -1
}

function onMenuDrop(idx) {
  const from = menuDragIndex.value
  menuDragIndex.value = -1
  if (from < 0 || from === idx) return
  const arr = menuItems.value
  const [moved] = arr.splice(from, 1)
  arr.splice(idx, 0, moved)
}

async function loadMenu() {
  menuLoading.value = true
  menuError.value = ''
  try {
    const data = await api('/menu')
    menuVersion.value = data?.version || 0
    menuItems.value = (data?.menu?.items || []).map(i => ({
      name: i.name || '',
      type: i.type || 'send_message',
      subMenuItems: (i.subMenuItems || []).map(s => ({
        name: s.name || '',
        type: s.type || 'send_message',
        sendMessage: s.sendMessage || '',
        link: s.link || ''
      })),
      sendMessage: i.sendMessage || '',
      link: i.link || '',
      switchId: i.switchConfig?.switchId || '',
      defaultOn: i.switchConfig?.defaultOn || false,
      align: i.align || 'left'
    }))
  } catch (e) {
    menuError.value = e.message
  } finally {
    menuLoading.value = false
  }
}

function draftToMenuItem(d) {
  const item = {name: d.name || null, type: d.type, align: d.align || null}
  if (d.type === 'send_message') item.sendMessage = d.sendMessage || null
  if (d.type === 'link') item.link = d.link || null
  if (d.type === 'switch') item.switchConfig = {switchId: d.switchId || null, defaultOn: !!d.defaultOn}
  if (d.type === 'menu') {
    item.subMenuItems = (d.subMenuItems || []).map(s => {
      const sub = {name: s.name || null, type: s.type}
      if (s.type === 'send_message') sub.sendMessage = s.sendMessage || null
      if (s.type === 'link') sub.link = s.link || null
      return sub
    })
  }
  return item
}

async function saveMenu() {
  if (menuItems.value.length === 0) {
    alert('请至少配置一个菜单项')
    return
  }
  if (menuItems.value.some(item => menuNameLength(item.name) > 10)) {
    alert('按钮名称最多 5 个汉字或 10 个字母')
    return
  }
  if (menuItems.value.some(item => (item.subMenuItems || []).some(sub => menuNameLength(sub.name) > 14))) {
    alert('子菜单名称最多 7 个汉字或 14 个字母')
    return
  }
  menuSaving.value = true
  try {
    const version = await api('/menu', {
      method: 'PUT',
      body: JSON.stringify({menu: {items: menuItems.value.map(draftToMenuItem)}})
    })
    menuVersion.value = version || menuVersion.value
    alert('菜单保存成功，版本 ' + (version || ''))
  } catch (e) {
    alert('保存失败: ' + e.message)
  } finally {
    menuSaving.value = false
  }
}

// ============ 指令面板 ============

async function loadPanels(reset) {
  if (reset) panelError.value = ''
  panelLoading.value = true
  try {
    const results = await Promise.all(PANEL_SCOPES.map(async scope => {
      try {
        const data = await api(`/panels?scope=${scope}&limit=50`)
        return data?.records || []
      } catch {
        return []
      }
    }))
    panels.value = results.flat()
  } catch (e) {
    panelError.value = e.message
  } finally {
    panelLoading.value = false
  }
}

function newPanelDraftItem() {
  return {name: '', desc: '', type: 'command', onlyAdmin: false, link: ''}
}

function panelToDraftItems(panel) {
  return (panel?.items || []).map(it => ({
    name: it.name || '',
    desc: it.desc || '',
    type: it.type || 'command',
    onlyAdmin: !!it.onlyAdmin,
    link: it.link || ''
  }))
}

function isLegacyPanel(panel) {
  return panel?.panelId?.startsWith('mp_') === true
}

function panelOptionLabel(panel) {
  const name = panel.panel?.remark || '未命名面板'
  return `${name} · ${scopeLabel(panel.scope)} · ${panel.panelId}`
}

function copyPanelFromSource() {
  const source = panels.value.find(p => p.panelId === panelForm.copySourceId)
  if (!source) {
    panelForm.remark = ''
    panelForm.items = [newPanelDraftItem()]
    return
  }
  panelForm.remark = source.panel?.remark || ''
  panelForm.items = panelToDraftItems(source.panel)
  if (panelForm.items.length === 0) panelForm.items.push(newPanelDraftItem())
}

function addPanelItem() {
  if (panelForm.items.length >= 20) return
  panelForm.items.push(newPanelDraftItem())
}

// ============ 面板元素拖拽排序 ============
const panelDragIndex = ref(-1)

function onPanelDragStart(i, e) {
  panelDragIndex.value = i
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

function onPanelDragEnd() {
  panelDragIndex.value = -1
}

function onPanelDrop(i) {
  const from = panelDragIndex.value
  panelDragIndex.value = -1
  if (from < 0 || from === i) return
  const arr = panelForm.items
  const [moved] = arr.splice(from, 1)
  arr.splice(i, 0, moved)
}

function scopeSupportSpecific(scope) {
  return scope === 'c2c' || scope === 'group'
}

function scopeHasAllPanel(scope) {
  return panels.value.some(p => p.scope === scope && (p.targetType || 'all') === 'all')
}

function syncTargetType() {
  if (!scopeSupportSpecific(panelForm.scope)) {
    panelForm.targetType = 'all'
  } else if (scopeHasAllPanel(panelForm.scope)) {
    panelForm.targetType = 'specific'
  } else {
    panelForm.targetType = 'all'
  }
}

watch(() => panelForm.scope, () => syncTargetType())

function selectPanel(p) {
  selectedPanelId.value = p.panelId
}

function openCreate() {
  panelForm.scope = 'c2c'
  panelForm.targetType = 'all'
  panelForm.copySourceId = ''
  panelForm.userOpenIdsText = ''
  panelForm.groupOpenIdsText = ''
  panelForm.remark = ''
  panelForm.items = [newPanelDraftItem()]
  panelModal.value = {mode: 'create', panelId: null}
  syncTargetType()
}

function openEdit(p) {
  panelForm.remark = p.panel?.remark || ''
  panelForm.items = panelToDraftItems(p.panel)
  if (panelForm.items.length === 0) panelForm.items.push(newPanelDraftItem())
  panelModal.value = {mode: 'edit', panelId: p.panelId}
}

function closePanelModal() {
  panelModal.value = null
}

async function savePanel() {
  if (panelForm.items.length === 0) {
    alert('请至少配置一个面板元素')
    return
  }
  submitting.value = true
  try {
    const panel = {
      items: panelForm.items.map(it => {
        const obj = {name: it.name || null, type: it.type, onlyAdmin: !!it.onlyAdmin}
        if (it.type === 'command') obj.desc = it.desc || null
        if (it.type === 'link') {
          obj.link = it.link || null
          obj.desc = it.desc || null
        }
        return obj
      }),
      remark: panelForm.remark || null
    }
    if (panelModal.value.mode === 'create') {
      const body = {scope: panelForm.scope, targetType: panelForm.targetType, panel}
      if (panelForm.targetType === 'specific') {
        if (panelForm.scope === 'c2c') body.userOpenIds = parseLines(panelForm.userOpenIdsText)
        if (panelForm.scope === 'group') body.groupOpenIds = parseLines(panelForm.groupOpenIdsText)
      }
      await api('/panels', {method: 'POST', body: JSON.stringify(body)})
    } else {
      await api(`/panels/${panelModal.value.panelId}`, {method: 'PUT', body: JSON.stringify({panel})})
    }
    closePanelModal()
    await loadPanels(true)
  } catch (e) {
    alert('保存失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

async function removePanel(p) {
  if (isLegacyPanel(p)) return
  if (!confirm(`确认删除面板「${p.panel?.remark || p.panelId}」？`)) return
  try {
    await api(`/panels/${p.panelId}`, {method: 'DELETE'})
    await loadPanels(true)
  } catch (e) {
    alert('删除失败: ' + e.message)
  }
}

async function openTarget(p) {
  submitting.value = false
  try {
    const detail = await api(`/panels/${p.panelId}`)
    const existing = p.scope === 'c2c' ? (detail.userOpenIds || []) : (detail.groupOpenIds || [])
    targetModal.value = {panelId: p.panelId, scope: p.scope, existing, input: ''}
  } catch (e) {
    alert('加载关联对象失败: ' + e.message)
  }
}

function closeTarget() {
  targetModal.value = null
}

async function addTarget() {
  const ids = parseLines(targetModal.value.input)
  if (ids.length === 0) return
  submitting.value = true
  try {
    const body = {op: 'add'}
    if (targetModal.value.scope === 'c2c') body.userOpenIds = ids
    else body.groupOpenIds = ids
    await api(`/panels/${targetModal.value.panelId}/target`, {method: 'PUT', body: JSON.stringify(body)})
    const merged = [...new Set([...targetModal.value.existing, ...ids])]
    targetModal.value.existing = merged
    targetModal.value.input = ''
  } catch (e) {
    alert('添加失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

async function removeTarget(id) {
  if (!confirm(`移除关联对象 ${id}？`)) return
  submitting.value = true
  try {
    const body = {op: 'del'}
    if (targetModal.value.scope === 'c2c') body.userOpenIds = [id]
    else body.groupOpenIds = [id]
    await api(`/panels/${targetModal.value.panelId}/target`, {method: 'PUT', body: JSON.stringify(body)})
    targetModal.value.existing = targetModal.value.existing.filter(x => x !== id)
  } catch (e) {
    alert('移除失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}

// ============ 工具 ============

function parseLines(text) {
  if (!text) return []
  return text.split('\n').map(s => s.trim()).filter(Boolean)
}

function scopeLabel(scope) {
  return {c2c: '单聊', group: '群聊', channel: '文字子频道', dm: '频道私信'}[scope] || scope
}

onMounted(async () => {
  try {
    const config = await api('/config')
    botName.value = config.botName || 'AtriBot'
    appId.value = config.appId || ''
    botOpenId.value = config.botOpenId || ''
  } catch (e) {
    // ignore
  }
  await loadMenu()
})
</script>
