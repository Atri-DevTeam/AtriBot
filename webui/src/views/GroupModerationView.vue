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
          <h2 class="feedback-title">群管系统</h2>
          <div class="feedback-tabs">
            <button :class="{ active: tab === 'keyword' }" @click="tab = 'keyword'">违规词撤回</button>
            <button :class="{ active: tab === 'ai' }" @click="tab = 'ai'">AI消息审查</button>
            <button :class="{ active: tab === 'join' }" @click="tab = 'join'">入群审核</button>
            <button :class="{ active: tab === 'logs' }" @click="switchToLogs">操作日志</button>
          </div>
        </div>
      </header>

      <section class="content feedback-layout">
        <section class="chat-panel feedback-panel">
          <div class="chat-head gm-head">
            <strong>群配置</strong>
            <div class="chat-head-right">
              <select v-model="groupOpenId" class="gs-group-select" @change="loadSettings">
                <option value="">选择群...</option>
                <option v-for="g in groups" :key="g.groupOpenId" :value="g.groupOpenId">{{ g.groupName || g.groupOpenId }}</option>
              </select>
              <button v-if="tab !== 'logs'" class="primary-button" :disabled="!groupOpenId || saving" @click="saveSettings">
                {{ saving ? '保存中...' : '保存设置' }}
              </button>
            </div>
          </div>

          <div class="feedback-content">
            <div v-if="!groupOpenId" class="empty-state">请先选择群（机器人身份需为管理员...）</div>
            <div v-else-if="loading" class="empty-state">加载中...</div>
            <div v-else-if="loadError" class="empty-state error">{{ loadError }}</div>

            <!-- 违规词撤回 -->
            <div v-else-if="tab === 'keyword'" class="gm-admin-page">
              <section class="gm-admin-hero">
                <div><h3>违规内容处理</h3><p>消息命中关键词、链接或小程序规则后执行指定操作。</p></div>
                <label class="gm-switch-card" :class="{ enabled: settings.keywordRecall.enabled }">
                  <span class="gm-switch-copy">
                    <strong>{{ settings.keywordRecall.enabled ? '已启用' : '未启用' }}</strong>
                    <small>{{ settings.keywordRecall.enabled ? '对新群消息检查规则' : '不检查违规内容规则' }}</small>
                  </span>
                  <input type="checkbox" v-model="settings.keywordRecall.enabled"/>
                  <span class="gm-switch" aria-hidden="true"></span>
                </label>
              </section>

              <section class="gm-ai-section">
                <div class="gm-ai-section-head">
                  <div><span class="gm-ai-step">01</span><h4>内容规则</h4><p>每条规则可以单独设置撤回、提醒和禁言操作。</p></div>
                  <button class="primary-button" type="button" @click="addRule">新增规则</button>
                </div>
                <div class="gm-rule-list">
                <div v-if="settings.keywordRecall.rules.length === 0" class="gm-rule-empty">暂无规则。新增规则后设置匹配内容和处理操作。</div>
                <div v-for="(rule, i) in settings.keywordRecall.rules" :key="rule.ruleId" class="gm-rule-item">
                  <div class="gm-rule-row">
                    <select v-model="rule.type" class="gs-input gm-rule-type">
                      <option value="KEYWORD">关键词</option>
                      <option value="LINK">链接</option>
                      <option value="MINI_PROGRAM">小程序</option>
                    </select>
                    <select v-model="rule.matchMode" class="gs-input gm-rule-mode" :disabled="rule.type !== 'KEYWORD'">
                      <option value="CONTAINS">包含</option>
                      <option value="EQUALS">完全相等</option>
                    </select>
                    <input v-model="rule.keyword" class="gs-input gm-rule-keyword" type="text"
                           :disabled="rule.type !== 'KEYWORD'" placeholder="命中词"/>
                    <input v-model="rule.remark" class="gs-input gm-rule-remark" type="text" placeholder="规则备注"/>
                    <button class="ghost-button gm-rule-toggle" :class="{ open: expandedRuleId === rule.ruleId }"
                            :title="expandedRuleId === rule.ruleId ? '收起处理配置' : '配置命中后处理'"
                            :aria-expanded="expandedRuleId === rule.ruleId" @click="toggleRuleAction(rule.ruleId)">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></svg>
                    </button>
                    <button class="ghost-button danger gm-rule-del" title="删除规则" @click="removeRule(i)">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                    </button>
                  </div>
                  <div v-if="expandedRuleId === rule.ruleId" class="gm-rule-action">
                    <div class="gs-form-label">命中后处理</div>
                    <ActionEditor v-model="rule.action"/>
                  </div>
                  <div v-else class="gm-rule-summary">命中后：{{ actionSummary(rule.action) }}</div>
                </div>
                </div>
              </section>
            </div>

            <!-- AI 审核 -->
            <div v-else-if="tab === 'ai'" class="gm-ai-page">
              <section class="gm-ai-hero">
                <div>
                  <h3>AI 消息审查</h3>
                  <p>配置群消息的 AI 审查规则、提示词和违规处理方式。</p>
                </div>
                <label class="gm-switch-card" :class="{ enabled: settings.aiRecall.enabled }">
                  <span class="gm-switch-copy">
                    <strong>{{ settings.aiRecall.enabled ? '已启用' : '未启用' }}</strong>
                    <small>{{ settings.aiRecall.enabled ? '对新群消息执行 AI 审查' : '不审查群消息' }}</small>
                  </span>
                  <input type="checkbox" v-model="settings.aiRecall.enabled"/>
                  <span class="gm-switch" aria-hidden="true"></span>
                </label>
              </section>

              <section class="gm-ai-section">
                <div class="gm-ai-section-head">
                  <div>
                    <span class="gm-ai-step">01</span>
                    <h4>生效时间</h4>
                    <p>限制 AI 审查生效的日期、星期和每日时间段。</p>
                  </div>
                  <label class="checkbox-label">
                    <input type="checkbox" v-model="settings.aiRecall.schedule.enabled"/> 启用定时
                  </label>
                </div>

                <div v-if="!settings.aiRecall.schedule.enabled" class="gm-schedule-disabled">
                  当前为全天生效，启用定时后可设置周期。
                </div>
                <div v-else class="gm-schedule-config">
                  <div class="gm-schedule-row">
                    <span class="gm-schedule-label">生效日期</span>
                    <label class="gm-date-field">
                      <span>开始</span>
                      <input v-model="settings.aiRecall.schedule.startDate" class="gs-input" type="date"/>
                    </label>
                    <span class="gm-schedule-separator">至</span>
                    <label class="gm-date-field">
                      <span>结束</span>
                      <input v-model="settings.aiRecall.schedule.endDate" class="gs-input" type="date"/>
                    </label>
                    <span class="gm-schedule-hint">留空表示不限制</span>
                  </div>

                  <div class="gm-schedule-row gm-week-row">
                    <span class="gm-schedule-label">重复周期</span>
                    <div class="gm-weekdays">
                      <button v-for="day in weekDays" :key="day.value" type="button" class="gm-weekday"
                              :class="{ active: settings.aiRecall.schedule.daysOfWeek.includes(day.value) }"
                              @click="toggleScheduleDay(day.value)">{{ day.short }}</button>
                    </div>
                    <div class="gm-schedule-shortcuts">
                      <button class="ghost-button" type="button" @click="setScheduleDays([1, 2, 3, 4, 5, 6, 7])">每天</button>
                      <button class="ghost-button" type="button" @click="setScheduleDays([1, 2, 3, 4, 5])">工作日</button>
                      <button class="ghost-button" type="button" @click="setScheduleDays([6, 7])">周末</button>
                    </div>
                  </div>

                  <div class="gm-schedule-row">
                    <span class="gm-schedule-label">每日时段</span>
                    <input v-model="settings.aiRecall.schedule.startTime" class="gs-input gm-time-input" type="time"/>
                    <span class="gm-schedule-separator">至</span>
                    <input v-model="settings.aiRecall.schedule.endTime" class="gs-input gm-time-input" type="time"/>
                    <span class="gm-schedule-hint">结束时间早于开始时间时按跨天计算</span>
                  </div>
                  <div class="gm-schedule-summary">{{ scheduleSummary }}</div>
                </div>
              </section>

              <section class="gm-ai-section">
                <div class="gm-ai-section-head">
                  <div>
                    <span class="gm-ai-step">02</span>
                    <h4>预选配置</h4>
                    <p>选择后将覆盖当前审核提示词。预选配置可以新增、编辑和删除。</p>
                  </div>
                  <button class="primary-button" type="button" @click="startCreatePreset">新增预选配置</button>
                </div>

                <div class="gm-preset-grid">
                  <article v-for="(preset, presetIndex) in settings.aiRecall.promptPresets" :key="preset.id" class="gm-preset-card"
                           :class="{ active: settings.aiRecall.systemPrompt === preset.prompt }">
                    <div class="gm-preset-top">
                      <span class="gm-preset-icon">✦</span>
                      <span class="gm-preset-kind">配置 {{ String(presetIndex + 1).padStart(2, '0') }}</span>
                      <span v-if="settings.aiRecall.systemPrompt === preset.prompt" class="gm-preset-active">已选择</span>
                    </div>
                    <h5>{{ preset.name }}</h5>
                    <p>{{ preset.description }}</p>
                    <div class="gm-preset-actions">
                      <button class="primary-button" type="button" @click="applyPreset(preset)">选择</button>
                      <button class="ghost-button" type="button" @click="startEditPreset(preset)">编辑</button>
                      <button class="ghost-button danger" type="button" @click="deletePreset(preset.id)">删除</button>
                    </div>
                  </article>
                </div>
              </section>

              <section v-if="presetEditor.open" class="gm-preset-editor">
                <div class="gm-ai-section-head compact">
                  <div>
                    <span class="gm-ai-step">✦</span>
                    <h4>{{ presetEditor.id ? '编辑预选配置' : '新增预选配置' }}</h4>
                  </div>
                  <button class="ghost-button" type="button" @click="closePresetEditor">取消</button>
                </div>
                <div class="gm-preset-editor-grid">
                  <label class="gs-form-row">
                    <span class="gs-form-label">配置名称</span>
                    <input v-model="presetEditor.name" class="gs-input" maxlength="30" placeholder="例如：服务器交易审查"/>
                  </label>
                  <label class="gs-form-row">
                    <span class="gs-form-label">配置说明</span>
                    <input v-model="presetEditor.description" class="gs-input" maxlength="80" placeholder="填写该配置的审查范围"/>
                  </label>
                </div>
                <label class="gs-form-row">
                  <span class="gs-form-label">提示词</span>
                  <textarea v-model="presetEditor.prompt" class="gs-textarea gm-prompt-textarea" rows="10"
                            placeholder="填写违规判断规则和放行条件"/>
                </label>
                <div class="gm-editor-footer">
                  <span>{{ presetEditor.prompt.length }} 字</span>
                  <button class="primary-button" type="button" @click="savePreset">保存预选配置</button>
                </div>
              </section>

              <section class="gm-ai-section gm-prompt-workbench">
                <div class="gm-ai-section-head">
                  <div>
                    <span class="gm-ai-step">03</span>
                    <h4>审核提示词</h4>
                    <p>用于判断群消息是否违规，保存群配置后生效。</p>
                  </div>
                  <span class="gm-char-count">{{ settings.aiRecall.systemPrompt.length }} 字</span>
                </div>
                <textarea v-model="settings.aiRecall.systemPrompt" class="gs-textarea gm-prompt-textarea" rows="14"
                          placeholder="选择预选配置或直接填写审核提示词"/>
              </section>

              <section class="gm-ai-section">
                <div class="gm-ai-section-head">
                  <div>
                    <span class="gm-ai-step">04</span>
                    <h4>违规处理</h4>
                    <p>设置 AI 判定消息违规后执行的操作。</p>
                  </div>
                </div>
                <div class="gm-action-panel"><ActionEditor v-model="settings.aiRecall.action"/></div>
              </section>
            </div>

            <!-- 入群审核 -->
            <div v-else-if="tab === 'join'" class="gm-admin-page">
              <section class="gm-admin-hero">
                <div>
                  <h3>入群审查</h3>
                  <p>规则按顺序执行，产生“通过”或“拒绝”结果后停止。</p>
                </div>
                <label class="gm-switch-card" :class="{ enabled: settings.joinReview.enabled }">
                  <span class="gm-switch-copy">
                    <strong>{{ settings.joinReview.enabled ? '已启用' : '未启用' }}</strong>
                    <small>{{ settings.joinReview.enabled ? '自动处理入群申请' : '入群申请交由管理员处理' }}</small>
                  </span>
                  <input type="checkbox" v-model="settings.joinReview.enabled"/>
                  <span class="gm-switch" aria-hidden="true"></span>
                </label>
              </section>

              <section class="gm-ai-section">
                <div class="gm-ai-section-head">
                  <div>
                    <span class="gm-ai-step">01</span>
                    <h4>审核规则</h4>
                    <p>支持关键词和 AI 规则。全部规则继续执行时，不会自动处理申请。</p>
                  </div>
                  <div class="gm-head-actions">
                    <button class="ghost-button" type="button" @click="addJoinRule('KEYWORD')">新增关键词规则</button>
                    <button class="primary-button" type="button" @click="addJoinRule('AI')">新增 AI 规则</button>
                  </div>
                </div>

                <div v-if="settings.joinReview.rules.length === 0" class="gm-rule-empty">
                  暂无审核规则。未配置规则时，入群申请不会自动处理。
                </div>
                <div v-else class="gm-join-rule-list">
                  <article v-for="(rule, index) in settings.joinReview.rules" :key="rule.id" class="gm-join-rule-card">
                    <header class="gm-join-rule-head">
                      <span class="gm-rule-order">{{ index + 1 }}</span>
                      <label class="gm-mini-switch"><input type="checkbox" v-model="rule.enabled"/><span></span></label>
                      <span class="gm-rule-type-badge">{{ rule.type === 'AI' ? 'AI' : '关键词' }}</span>
                      <input v-model="rule.name" class="gm-rule-name-input" maxlength="30" placeholder="规则名称"/>
                      <div class="gm-rule-tools">
                        <button class="ghost-button" type="button" title="上移" :disabled="index === 0" @click="moveJoinRule(index, -1)">↑</button>
                        <button class="ghost-button" type="button" title="下移" :disabled="index === settings.joinReview.rules.length - 1" @click="moveJoinRule(index, 1)">↓</button>
                        <button class="ghost-button danger" type="button" @click="removeJoinRule(index)">删除</button>
                      </div>
                    </header>

                    <div class="gm-join-rule-body">
                      <template v-if="rule.type === 'KEYWORD'">
                        <div class="gm-rule-fields two-columns">
                          <label class="gs-form-row">
                            <span class="gs-form-label">匹配方式</span>
                            <select v-model="rule.matchMode" class="gs-input">
                              <option value="CONTAINS">包含任一关键词</option>
                              <option value="EQUALS">验证内容完全等于关键词</option>
                            </select>
                          </label>
                          <label class="gs-form-row">
                            <span class="gs-form-label">命中后</span>
                            <select v-model="rule.onMatch" class="gs-input">
                              <option value="APPROVE">通过申请</option>
                              <option value="REJECT">拒绝申请</option>
                              <option value="CONTINUE">继续下一条规则</option>
                            </select>
                          </label>
                        </div>
                        <label class="gs-form-row">
                          <span class="gs-form-label">关键词（每行一个）</span>
                          <textarea class="gs-textarea" rows="4" placeholder="填写验证消息或问答答案中需要匹配的关键词"
                                    :value="(rule.keywords || []).join('\n')"
                                    @change="event => rule.keywords = parseLines(event.target.value)"/>
                        </label>
                      </template>

                      <template v-else>
                        <label class="gs-form-row">
                          <span class="gs-form-label">AI 提示词</span>
                          <textarea v-model="rule.aiSystemPrompt" class="gs-textarea gm-prompt-textarea" rows="7"
                                    placeholder="填写入群申请的违规判断规则"/>
                        </label>
                        <div class="gm-rule-fields two-columns">
                          <label class="gs-form-row">
                            <span class="gs-form-label">判定违规时</span>
                            <select v-model="rule.onViolation" class="gs-input">
                              <option value="REJECT">拒绝申请</option>
                              <option value="APPROVE">通过申请</option>
                              <option value="CONTINUE">继续下一条规则</option>
                            </select>
                          </label>
                          <label class="gs-form-row">
                            <span class="gs-form-label">判定正常时</span>
                            <select v-model="rule.onPass" class="gs-input">
                              <option value="CONTINUE">继续下一条规则</option>
                              <option value="APPROVE">通过申请</option>
                              <option value="REJECT">拒绝申请</option>
                            </select>
                          </label>
                        </div>
                      </template>

                      <label v-if="joinRuleCanReject(rule)" class="gs-form-row">
                        <span class="gs-form-label">此规则的拒绝理由</span>
                        <input v-model="rule.rejectReason" class="gs-input" maxlength="120"
                               placeholder="留空时使用下方默认拒绝理由"/>
                      </label>
                    </div>
                  </article>
                </div>
              </section>

              <section class="gm-ai-section">
                <div class="gm-ai-section-head">
                  <div><span class="gm-ai-step">02</span><h4>通用设置</h4></div>
                </div>
                <div class="gm-rule-fields two-columns">
                  <label class="gs-form-row">
                    <span class="gs-form-label">默认拒绝理由</span>
                    <input v-model="settings.joinReview.rejectReason" class="gs-input" type="text"
                           placeholder="规则未单独填写拒绝理由时使用"/>
                  </label>
                  <label class="checkbox-label gm-notify-option">
                    <input type="checkbox" v-model="settings.joinReview.notifyDebugGroup"/> 通知到开发组
                  </label>
                </div>
              </section>
            </div>

            <!-- 操作日志 -->
            <div v-else-if="tab === 'logs'" class="gm-logs">
              <div class="errors-summary">
                <div class="errors-hero">
                  <span class="errors-hero-value">{{ logStats.all }}</span>
                  <span class="errors-hero-label">操作记录</span>
                </div>
                <dl class="errors-metrics">
                  <div class="errors-metric">
                    <dt class="errors-metric-label">今日</dt>
                    <dd class="errors-metric-value">{{ logStats.today }}</dd>
                  </div>
                  <div class="errors-metric">
                    <dt class="errors-metric-label">24 小时</dt>
                    <dd class="errors-metric-value">{{ logStats.last24h }}</dd>
                  </div>
                  <div class="errors-metric">
                    <dt class="errors-metric-label">关键词撤回</dt>
                    <dd class="errors-metric-value">{{ logStats.keywordRecall }}</dd>
                  </div>
                  <div class="errors-metric">
                    <dt class="errors-metric-label">AI 撤回</dt>
                    <dd class="errors-metric-value">{{ logStats.aiRecall }}</dd>
                  </div>
                  <div class="errors-metric">
                    <dt class="errors-metric-label">入群审核</dt>
                    <dd class="errors-metric-value">{{ logStats.joinReview }}</dd>
                  </div>
                </dl>
              </div>

              <div class="sendlogs-tabs" role="tablist">
                <button v-for="t in logTabs" :key="t.value" class="sendlogs-tab"
                        :class="{ active: logCategory === t.value }" type="button" role="tab"
                        :aria-selected="logCategory === t.value" @click="selectLogCategory(t.value)">
                  <span>{{ t.label }}</span>
                  <i>{{ logTabCount(t.value) }}</i>
                </button>
              </div>

              <div class="errors-search">
                <input v-model="logSearchInput" class="errors-search-input" type="text"
                       placeholder="搜索成员 / 处理 / 详情..." @keyup.enter="doLogSearch"/>
                <button class="primary-button errors-search-btn" @click="doLogSearch">查询</button>
                <button v-if="logKeyword" class="ghost-button errors-search-btn" @click="resetLogSearch">重置</button>
              </div>
              <p class="errors-search-hint">群管操作记录</p>

              <template v-if="logMode === 'detail' && logDetail">
                <div class="errors-detail-bar">
                  <button class="ghost-button" @click="backToLogList">返回列表</button>
                  <span class="errors-detail-crumb">操作详情</span>
                </div>

                <article class="errors-surface errors-detail">
                  <header class="errors-detail-head">
                    <span class="gm-log-type" :class="logTypeClass(logDetail.category)">{{ logCategoryText(logDetail.category) }}</span>
                    <h3 class="errors-detail-message">{{ logActionText(logDetail.action) }}</h3>
                    <span class="errors-detail-time">{{ formatTime(logDetail.createdAt) }}</span>
                  </header>

                  <dl class="errors-fields">
                    <div class="errors-field">
                      <dt class="errors-field-label">日志 id</dt>
                      <dd class="errors-field-value errors-mono">{{ logDetail.id }}</dd>
                    </div>
                    <div class="errors-field">
                      <dt class="errors-field-label">分类</dt>
                      <dd class="errors-field-value">{{ logCategoryText(logDetail.category) }}（{{ logDetail.category }}）</dd>
                    </div>
                    <div class="errors-field">
                      <dt class="errors-field-label">处理动作</dt>
                      <dd class="errors-field-value">{{ logActionText(logDetail.action) }}（{{ logDetail.action }}）</dd>
                    </div>
                    <div class="errors-field">
                      <dt class="errors-field-label">时间</dt>
                      <dd class="errors-field-value">{{ formatTime(logDetail.createdAt) }}（{{ relativeTime(logDetail.createdAt) }}）</dd>
                    </div>
                    <div class="errors-field">
                      <dt class="errors-field-label">成员 OpenID</dt>
                      <dd class="errors-field-value errors-mono">{{ logDetail.targetMemberOpenId || '-' }}</dd>
                    </div>
                  </dl>

                  <section class="errors-block">
                    <h4 class="errors-block-title">详情</h4>
                    <p class="gm-log-detail-full">{{ logDetail.detail || '(无详情)' }}</p>
                  </section>
                </article>
              </template>

              <template v-else>
                <div v-if="logsLoading" class="empty-state">加载中...</div>
                <div v-else-if="logsError" class="empty-state error">{{ logsError }}</div>
                <div v-else-if="logItems.length === 0" class="empty-state">暂无操作日志</div>

                <div v-else class="errors-surface">
                  <div class="sendlogs-grid gm-log-thead">
                    <span>分类</span>
                    <span>处理</span>
                    <span>成员</span>
                    <span>详情</span>
                    <span>时间</span>
                  </div>

                  <div class="errors-list">
                    <article v-for="row in logItems" :key="row.id" class="sendlogs-grid gm-log-row"
                             @click="openLogDetail(row)">
                      <span>
                        <span class="gm-log-type" :class="logTypeClass(row.category)">{{ logCategoryText(row.category) }}</span>
                      </span>
                      <span class="gm-log-action">{{ logActionText(row.action) }}</span>
                      <span class="gm-log-member" :title="row.targetMemberOpenId">{{ row.targetMemberOpenId || '-' }}</span>
                      <span class="gm-log-detail" :title="row.detail">{{ row.detail || '-' }}</span>
                      <span class="sendlogs-time" :title="formatTime(row.createdAt)">{{ relativeTime(row.createdAt) }}</span>
                    </article>
                  </div>
                </div>

                <div v-if="!logsLoading && !logsError && logTotalPages > 1" class="errors-pagination">
                  <button class="ghost-button" :disabled="logPage <= 1" @click="goLogPage(logPage - 1)">上一页</button>
                  <span class="errors-pagination-label">第 {{ logPage }} / {{ logTotalPages }} 页</span>
                  <button class="ghost-button" :disabled="logPage >= logTotalPages" @click="goLogPage(logPage + 1)">下一页</button>
                </div>
              </template>
            </div>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {API_BASE} from '../router.js'
import AppSidebar from '../components/AppSidebar.vue'
import ActionEditor from '../components/GroupModerationActionEditor.vue'
import {formatTime, relativeTime} from '../lib/time.js'

const router = useRouter()

const botName = ref('AtriBot')
const appId = ref('')
const botOpenId = ref('')
const sidebarOpen = ref(false)

const tab = ref('keyword')
const groups = ref([])
const groupOpenId = ref('')

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)

const settings = reactive(emptySettings())

const logItems = ref([])
const logsLoading = ref(false)
const logsError = ref('')
const logPage = ref(1)
const logPageSize = 20
const logTotal = ref(0)
const logCategory = ref('ALL')
const logSearchInput = ref('')
const logKeyword = ref('')
const logStats = reactive({all: 0, today: 0, last24h: 0, keywordRecall: 0, aiRecall: 0, joinReview: 0})
const logTabs = [
  {value: 'ALL', label: '全部'},
  {value: 'KEYWORD_RECALL', label: '关键词撤回'},
  {value: 'AI_RECALL', label: 'AI 撤回'},
  {value: 'JOIN_REVIEW', label: '入群审核'}
]
const logTotalPages = computed(() => Math.max(1, Math.ceil(logTotal.value / logPageSize)))

// 日志二级详情（行数据已含全部字段，直接展示，无需再请求详情接口）
const logMode = ref('list')
const logDetail = ref(null)

function openLogDetail(row) {
  logDetail.value = row
  logMode.value = 'detail'
}

function backToLogList() {
  logMode.value = 'list'
  logDetail.value = null
}

const expandedRuleId = ref('')
const weekDays = [
  {value: 1, short: '一'}, {value: 2, short: '二'}, {value: 3, short: '三'},
  {value: 4, short: '四'}, {value: 5, short: '五'}, {value: 6, short: '六'},
  {value: 7, short: '日'}
]

const DEFAULT_JOIN_REVIEW_PROMPT = `你是一个QQ群入群审核助手，负责根据申请人填写的验证消息或问答内容，判断是否应当同意其加入本群。
需要判定为拒绝的情形包括但不限于：
- 验证内容包含广告、引流、推广等营销信息
- 验证内容为空、无意义乱码，或明显是批量注册的机器人号特征
- 验证内容与群主题严重不符
- 验证内容包含辱骂、色情、政治敏感等违规信息
如果验证内容正常、态度诚恳，符合入群要求，请判定为通过。
请只根据申请人提供的验证内容判断，不要臆测，信息不足时倾向于判定为通过，交由管理员人工复核。`

const presetEditor = reactive({open: false, id: '', name: '', description: '', prompt: ''})
const scheduleSummary = computed(() => {
  const schedule = settings.aiRecall.schedule
  if (!schedule?.enabled) return '全天生效'
  const selected = weekDays.filter(day => schedule.daysOfWeek.includes(day.value)).map(day => `周${day.short}`)
  const days = selected.length === 7 ? '每天' : (selected.join('、') || '未选择重复日期')
  const dates = schedule.startDate || schedule.endDate
    ? `${schedule.startDate || '不限'} 至 ${schedule.endDate || '不限'}`
    : '长期'
  const overnight = schedule.startTime > schedule.endTime && schedule.startTime !== schedule.endTime ? '（次日结束）' : ''
  return `${dates} · ${days} · ${schedule.startTime}–${schedule.endTime}${overnight}`
})

function emptySettings() {
  return {
    keywordRecall: {enabled: false, rules: [], action: emptyAction()},
    aiRecall: {enabled: false, systemPrompt: '', action: emptyAction(), promptPresets: [], schedule: emptySchedule()},
    joinReview: {
      enabled: false,
      rules: [],
      mode: 'DISABLED',
      keywordRule: {matchMode: 'CONTAINS', keywords: [], onHit: 'REJECT'},
      aiSystemPrompt: '',
      rejectReason: '',
      notifyDebugGroup: false
    }
  }
}

function emptyAction() {
  return {remind: true, remindMessage: '你的消息违规了哦', recall: true, mute: false, muteSeconds: 0, notifyDebugGroup: false}
}

function emptySchedule() {
  return {enabled: false, startDate: '', endDate: '', startTime: '00:00', endTime: '23:59', daysOfWeek: [1, 2, 3, 4, 5, 6, 7]}
}

function emptyJoinRule(type) {
  return {
    id: crypto.randomUUID(),
    name: type === 'AI' ? 'AI 审核' : '关键词审核',
    enabled: true,
    type,
    matchMode: 'CONTAINS',
    keywords: [],
    onMatch: 'REJECT',
    aiSystemPrompt: type === 'AI' ? DEFAULT_JOIN_REVIEW_PROMPT : '',
    onViolation: 'REJECT',
    onPass: 'CONTINUE',
    rejectReason: ''
  }
}

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

async function fetchGroups() {
  try {
    const all = await api('/groups') || []
    groups.value = all.filter(g => g.memberRole === 'OWNER' || g.memberRole === 'ADMIN')
  } catch (e) {
    // 群列表失败不阻断页面
  }
}

async function loadSettings() {
  if (!groupOpenId.value) return
  loading.value = true
  loadError.value = ''
  try {
    const data = await api(`/group-moderation/${groupOpenId.value}`)
    Object.assign(settings, emptySettings(), data)
    if (!settings.keywordRecall.action) settings.keywordRecall.action = emptyAction()
    if (!settings.aiRecall.action) settings.aiRecall.action = emptyAction()
    if (!Array.isArray(settings.aiRecall.promptPresets)) settings.aiRecall.promptPresets = []
    if (!settings.aiRecall.schedule) settings.aiRecall.schedule = emptySchedule()
    else settings.aiRecall.schedule = {...emptySchedule(), ...settings.aiRecall.schedule}
    if (!Array.isArray(settings.aiRecall.schedule.daysOfWeek)) settings.aiRecall.schedule.daysOfWeek = []
    if (!Array.isArray(settings.keywordRecall.rules)) settings.keywordRecall.rules = []
    // 旧配置没有规则级 action，用原全局 action 兜底，保存时即完成迁移
    const fallbackAction = settings.keywordRecall.action
    for (const rule of settings.keywordRecall.rules) {
      if (!rule.action) rule.action = fallbackAction ? {...fallbackAction} : emptyAction()
    }
    migrateJoinReviewSettings()
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
  if (tab.value === 'logs') {
    logPage.value = 1
    fetchLogStats()
    fetchLogs()
  }
}

async function saveSettings() {
  if (!groupOpenId.value) return
  saving.value = true
  try {
    await api(`/group-moderation/${groupOpenId.value}`, {method: 'PUT', body: JSON.stringify(settings)})
  } catch (e) {
    alert('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

function applyPreset(preset) {
  settings.aiRecall.systemPrompt = preset.prompt
}

function toggleScheduleDay(day) {
  const days = settings.aiRecall.schedule.daysOfWeek
  settings.aiRecall.schedule.daysOfWeek = days.includes(day)
    ? days.filter(value => value !== day)
    : [...days, day].sort((a, b) => a - b)
}

function setScheduleDays(days) {
  settings.aiRecall.schedule.daysOfWeek = [...days]
}

function migrateJoinReviewSettings() {
  const config = settings.joinReview
  if (!Array.isArray(config.rules)) config.rules = []
  if (config.rules.length > 0 || !config.mode || config.mode === 'DISABLED') return

  config.enabled = true
  if (config.mode === 'KEYWORD' || config.mode === 'ALL') {
    const rule = emptyJoinRule('KEYWORD')
    rule.matchMode = config.keywordRule?.matchMode || 'CONTAINS'
    rule.keywords = Array.isArray(config.keywordRule?.keywords) ? [...config.keywordRule.keywords] : []
    rule.onMatch = config.keywordRule?.onHit || 'REJECT'
    rule.rejectReason = config.rejectReason || ''
    config.rules.push(rule)
  }
  if (config.mode === 'AI' || config.mode === 'ALL') {
    const rule = emptyJoinRule('AI')
    rule.aiSystemPrompt = config.aiSystemPrompt || DEFAULT_JOIN_REVIEW_PROMPT
    rule.rejectReason = config.rejectReason || ''
    config.rules.push(rule)
  }
}

function addJoinRule(type) {
  settings.joinReview.rules.push(emptyJoinRule(type))
}

function removeJoinRule(index) {
  const rule = settings.joinReview.rules[index]
  if (!rule || !confirm(`确定删除规则“${rule.name || index + 1}”吗？`)) return
  settings.joinReview.rules.splice(index, 1)
}

function moveJoinRule(index, offset) {
  const target = index + offset
  if (target < 0 || target >= settings.joinReview.rules.length) return
  const [rule] = settings.joinReview.rules.splice(index, 1)
  settings.joinReview.rules.splice(target, 0, rule)
}

function joinRuleCanReject(rule) {
  return rule.type === 'KEYWORD'
    ? rule.onMatch === 'REJECT'
    : rule.onViolation === 'REJECT' || rule.onPass === 'REJECT'
}

function startCreatePreset() {
  Object.assign(presetEditor, {open: true, id: '', name: '', description: '', prompt: ''})
}

function startEditPreset(preset) {
  Object.assign(presetEditor, {
    open: true,
    id: preset.id,
    name: preset.name,
    description: preset.description || '',
    prompt: preset.prompt
  })
}

function closePresetEditor() {
  Object.assign(presetEditor, {open: false, id: '', name: '', description: '', prompt: ''})
}

function savePreset() {
  const name = presetEditor.name.trim()
  const prompt = presetEditor.prompt.trim()
  if (!name || !prompt) {
    alert('请填写配置名称和提示词')
    return
  }
  const presets = settings.aiRecall.promptPresets
  const value = {
    id: presetEditor.id || crypto.randomUUID(),
    name,
    description: presetEditor.description.trim(),
    prompt
  }
  const index = presets.findIndex(preset => preset.id === value.id)
  if (index >= 0) presets.splice(index, 1, value)
  else presets.push(value)
  closePresetEditor()
}

function deletePreset(id) {
  const preset = settings.aiRecall.promptPresets.find(item => item.id === id)
  if (!preset || !confirm(`确定删除预选配置“${preset.name}”吗？`)) return
  settings.aiRecall.promptPresets = settings.aiRecall.promptPresets.filter(item => item.id !== id)
  if (presetEditor.id === id) closePresetEditor()
}

function addRule() {
  const rule = {
    ruleId: crypto.randomUUID(),
    type: 'KEYWORD',
    matchMode: 'CONTAINS',
    keyword: '',
    remark: '',
    action: emptyAction()
  }
  settings.keywordRecall.rules.push(rule)
  expandedRuleId.value = rule.ruleId
}

function removeRule(index) {
  const [removed] = settings.keywordRecall.rules.splice(index, 1)
  if (removed && expandedRuleId.value === removed.ruleId) {
    expandedRuleId.value = ''
  }
}

function toggleRuleAction(ruleId) {
  expandedRuleId.value = expandedRuleId.value === ruleId ? '' : ruleId
}

function actionSummary(action) {
  if (!action) return '无处理动作'
  const parts = []
  if (action.recall) parts.push('撤回')
  if (action.remind) parts.push('提醒')
  if (action.mute) parts.push(`禁言 ${action.muteSeconds}s`)
  if (action.notifyDebugGroup) parts.push('通知开发组')
  return parts.length > 0 ? parts.join(' · ') : '无处理动作'
}

function switchToLogs() {
  tab.value = 'logs'
  logMode.value = 'list'
  if (!groupOpenId.value) return
  logPage.value = 1
  fetchLogStats()
  fetchLogs()
}

async function fetchLogStats() {
  if (!groupOpenId.value) return
  try {
    const data = await api(`/group-moderation/${groupOpenId.value}/logs/stats`)
    logStats.all = data.all || 0
    logStats.today = data.today || 0
    logStats.last24h = data.last24h || 0
    logStats.keywordRecall = data.keywordRecall || 0
    logStats.aiRecall = data.aiRecall || 0
    logStats.joinReview = data.joinReview || 0
  } catch {
    // 统计失败不阻断列表
  }
}

async function fetchLogs() {
  if (!groupOpenId.value) return
  logsLoading.value = true
  logsError.value = ''
  try {
    const params = new URLSearchParams()
    params.set('page', String(logPage.value))
    params.set('pageSize', String(logPageSize))
    if (logCategory.value !== 'ALL') params.set('category', logCategory.value)
    if (logKeyword.value) params.set('keyword', logKeyword.value)
    const data = await api(`/group-moderation/${groupOpenId.value}/logs?${params.toString()}`)
    logItems.value = data.items || []
    logTotal.value = data.total || 0
  } catch (e) {
    logsError.value = e.message
    logItems.value = []
    logTotal.value = 0
  } finally {
    logsLoading.value = false
  }
}

function logTabCount(value) {
  if (value === 'KEYWORD_RECALL') return logStats.keywordRecall
  if (value === 'AI_RECALL') return logStats.aiRecall
  if (value === 'JOIN_REVIEW') return logStats.joinReview
  return logStats.all
}

function selectLogCategory(value) {
  logMode.value = 'list'
  if (logCategory.value === value) return
  logCategory.value = value
  logPage.value = 1
  fetchLogs()
}

function doLogSearch() {
  logKeyword.value = logSearchInput.value.trim()
  logMode.value = 'list'
  logPage.value = 1
  fetchLogs()
}

function resetLogSearch() {
  logSearchInput.value = ''
  logKeyword.value = ''
  logMode.value = 'list'
  logPage.value = 1
  fetchLogs()
}

function goLogPage(p) {
  if (p < 1 || p > logTotalPages.value) return
  logPage.value = p
  fetchLogs()
}

function parseLines(text) {
  if (!text) return []
  return text.split('\n').map(s => s.trim()).filter(Boolean)
}

function logCategoryText(category) {
  if (category === 'KEYWORD_RECALL') return '关键词撤回'
  if (category === 'AI_RECALL') return 'AI 撤回'
  if (category === 'JOIN_REVIEW') return '入群审核'
  return category
}

function logTypeClass(category) {
  return {
    'gm-log-type--keyword': category === 'KEYWORD_RECALL',
    'gm-log-type--ai': category === 'AI_RECALL',
    'gm-log-type--join': category === 'JOIN_REVIEW'
  }
}

function logActionText(action) {
  if (action === 'recall') return '撤回'
  if (action === 'mute') return '禁言'
  if (action === 'approve') return '通过'
  if (action === 'reject') return '拒绝'
  return action || '-'
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
  await fetchGroups()
})
</script>

