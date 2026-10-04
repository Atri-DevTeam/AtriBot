<script setup>
import {computed, ref} from 'vue'
import ActionEditor from './GroupModerationActionEditor.vue'
import {newKeywordRule} from '../lib/moderationSettings.js'
const props = defineProps({settings: {type: Object, required: true}})
const domains = computed({get: () => props.settings.aiRecall.allowedDomains.join('\n'), set: value => { props.settings.aiRecall.allowedDomains = value.split(/\r?\n/) }})
const allowAll = computed({
  get: () => props.settings.aiRecall.allowedDomains.includes('.*'),
  set: enabled => { props.settings.aiRecall.allowedDomains = [...props.settings.aiRecall.allowedDomains.filter(value => value !== '.*'), ...(enabled ? ['.*'] : [])] }
})
const expandedRuleId = ref('')
function addRule() {
  const rule = newKeywordRule()
  props.settings.keywordRecall.rules.push(rule)
  expandedRuleId.value = rule.ruleId
}
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
function toggleDay(day) {
  const schedule = props.settings.aiRecall.schedule
  schedule.daysOfWeek = schedule.daysOfWeek.includes(day) ? schedule.daysOfWeek.filter(value => value !== day) : [...schedule.daysOfWeek, day].sort()
}
</script>

<template>
  <div class="mod-sections">
    <p class="mod-description">先匹配内容规则，未命中时再进行 AI 审查。两项可以分别启用。</p>
    <section class="mod-card">
      <header class="mod-card-head">
        <div><h3>内容规则</h3><p>关键词、链接与小程序</p></div>
        <label class="mod-toggle"><input v-model="settings.keywordRecall.enabled" type="checkbox" aria-label="启用内容规则"/><span/></label>
      </header>
      <div class="mod-card-body">
        <div v-if="!settings.keywordRecall.rules.length" class="mod-empty">暂无规则，添加需要处理的内容。</div>
        <details v-for="(rule, index) in settings.keywordRecall.rules" :key="rule.ruleId" class="mod-rule" :open="expandedRuleId === rule.ruleId">
          <summary><span class="mod-order">{{ index + 1 }}</span><span>{{ rule.remark || rule.keyword || ({LINK: '链接规则', MINI_PROGRAM: '小程序规则'}[rule.type]) || '新规则' }}</span><small>{{ {KEYWORD: '关键词', LINK: '链接', MINI_PROGRAM: '小程序'}[rule.type] }}</small></summary>
          <div class="mod-rule-body">
            <div class="mod-grid">
              <label>规则类型<select v-model="rule.type"><option value="KEYWORD">关键词</option><option value="LINK">链接</option><option value="MINI_PROGRAM">小程序</option></select></label>
              <label v-if="rule.type === 'KEYWORD'">匹配方式<select v-model="rule.matchMode"><option value="CONTAINS">包含</option><option value="EQUALS">完全相等</option></select></label>
            </div>
            <label v-if="rule.type === 'KEYWORD'">关键词<input v-model="rule.keyword" placeholder="输入需要匹配的内容"/></label>
            <label>备注<input v-model="rule.remark" placeholder="用于识别这条规则"/></label>
            <div class="mod-field-title">命中后处理</div><ActionEditor v-model="rule.action"/>
            <button class="mod-button danger" type="button" @click="settings.keywordRecall.rules.splice(index, 1)">删除规则</button>
          </div>
        </details>
        <button class="mod-button mod-add" type="button" @click="addRule">＋ 添加内容规则</button>
      </div>
    </section>

    <section class="mod-card">
      <header class="mod-card-head"><div><h3>AI 审查</h3><p>按提示词判断未命中规则的消息</p></div><label class="mod-toggle"><input v-model="settings.aiRecall.enabled" type="checkbox" aria-label="启用 AI 审查"/><span/></label></header>
      <div class="mod-card-body">
        <label>审查模式<select v-model.number="settings.aiRecall.type"><option :value="0">仅接口词库</option><option :value="1">接口词库与 AI 同时审查</option><option :value="2">接口词库优先，未命中再审查 AI</option><option :value="3">仅 AI</option></select></label>
        <label>审核提示词<textarea v-model="settings.aiRecall.systemPrompt" rows="8" placeholder="填写违规判断标准和放行条件"/></label>
        <details class="mod-disclosure"><summary>提醒内容与域名放行</summary><div class="mod-rule-body">
          <label>自定义输出<textarea v-model="settings.aiRecall.customOutput" rows="3" placeholder="可选，指定审核结果的输出要求"/></label>
          <label class="mod-check"><input v-model="settings.aiRecall.useCustomOutputAsReminder" type="checkbox"/>将自定义输出作为提醒内容</label>
          <p class="mod-muted">需同时开启违规提醒；未返回自定义内容时使用固定提醒。</p>
          <label>放行域名<textarea v-model="domains" rows="3" placeholder="每行一个正则表达式"/></label>
          <label class="mod-check"><input v-model="allowAll" type="checkbox"/>全部放行</label>
          <p class="mod-muted">勾选后添加 .*，放行 AI 审查中的所有链接。</p>
        </div></details>
        <details class="mod-disclosure"><summary>生效时间<small>{{ settings.aiRecall.schedule.enabled ? '已设置时段' : '全天' }}</small></summary><div class="mod-rule-body">
          <label class="mod-check"><input v-model="settings.aiRecall.schedule.enabled" type="checkbox"/>仅在指定时间审查</label>
          <template v-if="settings.aiRecall.schedule.enabled">
            <div class="mod-grid"><label>开始日期<input v-model="settings.aiRecall.schedule.startDate" type="date"/></label><label>结束日期<input v-model="settings.aiRecall.schedule.endDate" type="date"/></label></div>
            <div class="mod-days"><button v-for="(day, index) in weekdays" :key="day" type="button" :class="{selected: settings.aiRecall.schedule.daysOfWeek.includes(index + 1)}" :aria-pressed="settings.aiRecall.schedule.daysOfWeek.includes(index + 1)" @click="toggleDay(index + 1)">{{ day }}</button></div>
            <div class="mod-grid"><label>开始时间<input v-model="settings.aiRecall.schedule.startTime" type="time"/></label><label>结束时间<input v-model="settings.aiRecall.schedule.endTime" type="time"/></label></div>
            <p class="mod-muted">日期留空表示不限；结束时间早于开始时间时跨天生效。</p>
          </template>
        </div></details>
        <details class="mod-disclosure"><summary>违规处理</summary><div class="mod-rule-body"><ActionEditor v-model="settings.aiRecall.action" :reminder-disabled="settings.aiRecall.useCustomOutputAsReminder"/></div></details>
      </div>
    </section>
  </div>
</template>
