<script setup>
import {ref} from 'vue'
import {newJoinRule} from '../lib/moderationSettings.js'
const props = defineProps({config: {type: Object, required: true}})
const expandedRuleId = ref('')
function addRule(type) {
  const rule = newJoinRule(type)
  props.config.rules.push(rule)
  expandedRuleId.value = rule.id
}
const outcomes = [{value: 'APPROVE', label: '通过申请'}, {value: 'REJECT', label: '拒绝申请'}, {value: 'CONTINUE', label: '继续下一条'}]
function move(index, offset) {
  const target = index + offset
  if (target < 0 || target >= props.config.rules.length) return
  props.config.rules.splice(target, 0, ...props.config.rules.splice(index, 1))
}
</script>
<template>
  <div class="mod-sections">
    <section class="mod-card">
      <header class="mod-card-head"><div><h3>入群审核</h3><p>按规则顺序处理新申请</p></div><label class="mod-toggle"><input v-model="config.enabled" type="checkbox" aria-label="启用入群审核"/><span/></label></header>
      <div class="mod-card-body">
        <p class="mod-muted">产生通过或拒绝结果后停止；所有规则都继续时，交由管理员处理。</p>
        <div v-if="!config.rules.length" class="mod-empty">暂无审核规则。</div>
        <details v-for="(rule, index) in config.rules" :key="rule.id" class="mod-rule" :open="expandedRuleId === rule.id">
          <summary><span class="mod-order">{{ index + 1 }}</span><span>{{ rule.name || '未命名规则' }}</span><small>{{ !rule.enabled ? '已停用' : rule.type === 'AI' ? 'AI' : '关键词' }}</small></summary>
          <div class="mod-rule-body">
            <div class="mod-row"><label class="mod-check"><input v-model="rule.enabled" type="checkbox"/>启用规则</label><div class="mod-tools"><button class="mod-button" type="button" :disabled="index === 0" @click="move(index, -1)">上移</button><button class="mod-button" type="button" :disabled="index === config.rules.length - 1" @click="move(index, 1)">下移</button></div></div>
            <label>规则名称<input v-model="rule.name"/></label>
            <template v-if="rule.type === 'KEYWORD'">
              <label>匹配方式<select v-model="rule.matchMode"><option value="CONTAINS">包含</option><option value="EQUALS">完全相等</option></select></label>
              <label>关键词<textarea :value="(rule.keywords || []).join('\n')" rows="4" placeholder="每行一个关键词" @input="rule.keywords = $event.target.value.split(/\r?\n/)"/></label>
              <label>命中后<select v-model="rule.onMatch"><option v-for="item in outcomes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            </template>
            <template v-else>
              <label>审核提示词<textarea v-model="rule.aiSystemPrompt" rows="6"/></label>
              <div class="mod-grid"><label>判定违规<select v-model="rule.onViolation"><option v-for="item in outcomes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><label>判定正常<select v-model="rule.onPass"><option v-for="item in outcomes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label></div>
            </template>
            <label v-if="rule.onMatch === 'REJECT' && rule.type === 'KEYWORD' || rule.type === 'AI' && [rule.onViolation, rule.onPass].includes('REJECT')">拒绝理由<input v-model="rule.rejectReason" placeholder="留空使用默认理由"/></label>
            <button class="mod-button danger" type="button" @click="config.rules.splice(index, 1)">删除规则</button>
          </div>
        </details>
        <div class="mod-grid"><button class="mod-button" type="button" @click="addRule('KEYWORD')">＋ 关键词规则</button><button class="mod-button" type="button" @click="addRule('AI')">＋ AI 规则</button></div>
      </div>
    </section>
    <section class="mod-card"><div class="mod-card-body"><label>默认拒绝理由<input v-model="config.rejectReason" placeholder="规则未单独设置时使用"/></label><label class="mod-check"><input v-model="config.notifyDebugGroup" type="checkbox"/>通知到开发组</label></div></section>
  </div>
</template>
