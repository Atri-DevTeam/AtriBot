<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ApiError} from '../session'
import Icon from './Icon.vue'
import goldIngot from '../assets/dock-prices.png'
import DropdownSelect from './DropdownSelect.vue'
import PriceItemIcon from './PriceItemIcon.vue'
import {createPriceIconLoader} from '../priceIcons'

type Item = {id: string; key: string; name: string; tier?: string; markets?: string[]; variant?: Record<string, unknown>;
  bazaar?: Record<string, number | null>; auction?: Record<string, number | null>}
type Quote = {item: Item; sources: Record<string, {available: boolean; stale: boolean; last_updated: number}>}
type Rule = {id: string; revision: string; itemId: string; key: string; name: string; variant: string;
  metric: string; direction: string; threshold: number; enabled: boolean; completedAt: number;
  lastPrice: number | null; lastSnapshot: number; sending: boolean; error?: string}
type Settings = {available: boolean; enabled: boolean; canPush: boolean; canTest?: boolean; limit: number; rules: Rule[]}
type Search = {items: Item[]; next_cursor: string | null; pre_cursor: string | null; page: number; total_pages: number}
const props = defineProps<{
  request: <T>(path: string) => Promise<T | null>
  post: <T>(path: string, body: unknown) => Promise<T | null>
  image: (path: string) => Promise<string | null>
}>()
const settings = ref<Settings | null>(null)
const loading = ref(true)
const error = ref('')
const notice = ref('')
const busy = ref(false)
const query = ref('')
const results = ref<Search | null>(null)
const searching = ref(false)
const selected = ref<Item | null>(null)
const editing = ref<Rule | null>(null)
const quote = ref<Quote | null>(null)
const itemTiers = ref<Record<string, string>>({})
const icons = createPriceIconLoader(props.post, props.image, (item, tier) => { itemTiers.value[item.key] = tier })
const detailLoading = ref(false)
const metricChanged = ref(false)
const metric = ref('instant_buy')
const direction = ref('below')
const threshold = ref('')
const ruleEnabled = ref(true)
const deleting = ref('')
const testing = ref(new Set<string>())
let searchVersion = 0
let detailVersion = 0
let destroyed = false
watch(query, () => { ++searchVersion; results.value = null; searching.value = false })
const labels: Record<string, string> = {
  instant_buy: 'Instant Buy', instant_sell: 'Instant Sell', buy_order: 'Buy Order', sell_offer: 'Sell Offer', lowest_bin: 'Lowest Bin'
}
const rarityColors: Record<string, string> = {
  COMMON: '#746b7e', UNCOMMON: '#3b8b37', RARE: '#5555ff', EPIC: '#aa00aa', LEGENDARY: '#b17a0b',
  MYTHIC: '#c139b6', DIVINE: '#168b9c', SPECIAL: '#d65353', VERY_SPECIAL: '#d65353', SUPREME: '#a8363a'
}
const marketName = (item: Item) => {
  const markets = item.markets || [item.bazaar && 'bazaar', item.auction && 'auction'].filter(Boolean)
  return markets.map(market => market === 'bazaar' ? 'Bazaar' : market === 'auction' ? 'Auction House' : 'NPC').join(' · ')
}
const ruleMarket = (rule: Rule) => rule.metric === 'lowest_bin' ? 'Auction House' : 'Bazaar'
const itemColor = (key: string, tier?: string) => rarityColors[itemTiers.value[key] || tier || '']
const prices = computed(() => Object.entries(labels).map(([key, label]) => {
  const market = key === 'lowest_bin' ? 'auction' : 'bazaar'
  const value = quote.value?.item[market]?.[key]
  const source = quote.value?.sources[market === 'auction' ? 'auctions' : market]
  const updated = source?.last_updated || 0
  const fresh = !!source?.available && !source.stale && updated > 0 &&
    Date.now() - updated <= (market === 'auction' ? 1_200_000 : 300_000)
  return {key, label, value, updated, available: fresh && typeof value === 'number' && value > 0}
}))
const current = computed(() => prices.value.find(price => price.key === metric.value))
const quoteOptions = computed(() => prices.value.map(price => ({value: price.key, label: price.label,
  detail: price.available ? `${format(price.value)} Coins` : '暂无报价'})))
const directions = [{value: 'below', label: '低于或等于'}, {value: 'above', label: '高于或等于'}]
const canSave = computed(() => !busy.value && settings.value?.available && selected.value && !!labels[metric.value] &&
  /^\d+(\.\d{1,2})?$/.test(threshold.value) && Number(threshold.value) > 0 && Number(threshold.value) <= 1e15)
const format = (value: number | null | undefined) => typeof value === 'number' ? new Intl.NumberFormat('en-US', {maximumFractionDigits: 2}).format(value) : '—'
const date = (value: number) => value ? new Date(value).toLocaleString('zh-CN', {hour12: false}) : '—'
function variant(value: Item['variant'] | string) {
  if (!value) return ''
  try {
    const data = typeof value === 'string' ? JSON.parse(value) : value
    if (data.pet_type) return [data.pet_type, data.pet_tier, data.pet_skin || '默认皮肤', '跨等级最低价'].filter(Boolean).join(' · ')
    if (data.enchantments) return Object.entries(data.enchantments).map(([key, level]) => `${key} ${level}`).join(' · ')
  } catch { /* 历史记录的变体说明不可用时保留物品名称 */ }
  return ''
}
function message(reason: unknown) {
  const messages: Record<string, string> = {
    PRICE_PERMISSION_REQUIRED: '你没有使用此功能的权限（bot.skyblock.price）',
    PRICE_ALERT_FRIEND_REQUIRED: '请先添加机器人为好友，再设置私聊提醒',
    PRICE_ALERT_PUSH_DISABLED: '私聊主动消息已关闭，请先在机器人设置中开启',
    PRICE_ALERT_PUSH_UNAVAILABLE: '无法发送私聊消息，请确认已允许机器人主动发送消息',
    PRICE_ALERT_GLOBALLY_DISABLED: '价格提醒暂时不可用，请稍后重试',
    PRICE_ALERT_LIMIT: '最多保存 20 条提醒，请先删除不需要的记录',
    PRICE_ALERT_CHANGED: '这条提醒已发生变化，请刷新后重试',
    PRICE_ALERT_SENDING: '该提醒正在发送，请稍后操作',
    PRICE_ALERT_NOT_FOUND: '该提醒已删除，请刷新列表',
    INVALID_PRICE_ALERT: '请检查目标价格，最多保留两位小数',
    PRICE_ALERT_UNAVAILABLE: '价格服务暂不可用，请稍后重试',
    PRICE_ALERT_TEST_FORBIDDEN: '仅管理员可以发送测试提醒',
    PRICE_ALERT_TEST_FAILED: '测试提醒发送失败'
  }
  return reason instanceof ApiError ? messages[reason.code] || '操作失败，请稍后重试' : '连接失败，请稍后重试'
}
async function load() {
  loading.value = true
  error.value = ''
  try { settings.value = await props.request<Settings>('price-alerts') }
  catch (reason) { error.value = message(reason) }
  finally { loading.value = false }
}
async function search(cursor?: string | null) {
  if (!query.value.trim() || !settings.value?.available) return
  const version = ++searchVersion
  searching.value = true
  error.value = ''
  try {
    const result = await props.request<Search>(`price-alerts/search?q=${encodeURIComponent(query.value.trim())}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`)
    if (!destroyed && version === searchVersion) {
      results.value = result
      for (const item of result?.items || []) if (item.tier) itemTiers.value[item.key] = item.tier
    }
  } catch (reason) { if (version === searchVersion) error.value = message(reason) }
  finally { if (version === searchVersion) searching.value = false }
}
async function open(item: Item, rule: Rule | null = null) {
  const version = ++detailVersion
  selected.value = item
  editing.value = rule
  quote.value = null
  detailLoading.value = true
  metricChanged.value = false
  threshold.value = rule ? String(rule.threshold) : ''
  metric.value = rule?.metric || 'instant_buy'
  direction.value = rule?.direction || 'below'
  ruleEnabled.value = rule ? rule.enabled : true
  error.value = ''
  notice.value = ''
  try {
    const detail = await props.post<Quote>('price-alerts/item', {id: item.id, key: item.key})
    if (version !== detailVersion || destroyed) return
    quote.value = detail
    if (detail?.item.tier) itemTiers.value[item.key] = detail.item.tier
    if (!rule && !metricChanged.value) metric.value = prices.value.find(price => price.available)?.key || 'instant_buy'
  } catch { /* 行情预览失败不影响提醒配置 */ }
  finally { if (version === detailVersion) detailLoading.value = false }
}
function close() { ++detailVersion; selected.value = null; editing.value = null }
async function mutate(path: string, body: unknown, success: string) {
  if (busy.value) return false
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await props.post<Settings>(`price-alerts/${path}`, body)
    if (!result) return false
    settings.value = result
    notice.value = success
    return true
  } catch (reason) { error.value = message(reason); return false }
  finally { busy.value = false }
}
async function save() {
  if (!selected.value || !canSave.value) return
  if (await mutate('save', {id: editing.value?.id || null, revision: editing.value?.revision || null,
    itemId: selected.value.id, key: selected.value.key, metric: metric.value, direction: direction.value,
    threshold: threshold.value, enabled: ruleEnabled.value, name: selected.value.name,
    variant: selected.value.variant ? JSON.stringify(selected.value.variant) : editing.value?.variant || ''}, '提醒已保存')) close()
}
async function action(rule: Rule, enabled?: boolean) {
  if (await mutate('action', {action: enabled === undefined ? 'delete' : 'toggle', id: rule.id,
    revision: rule.revision, enabled}, enabled === undefined ? '提醒已删除' : enabled ? '提醒已启用' : '提醒已暂停')) deleting.value = ''
}
async function testRule(rule: Rule) {
  if (!settings.value?.canTest || testing.value.has(rule.id)) return
  testing.value = new Set([...testing.value, rule.id])
  error.value = ''
  notice.value = ''
  try {
    const result = await props.post<{success: boolean}>('price-alerts/test', {id: rule.id, revision: rule.revision})
    if (result?.success && !destroyed) notice.value = '测试消息已发送'
  } catch (reason) { if (!destroyed) error.value = message(reason) }
  finally { const pending = new Set(testing.value); pending.delete(rule.id); testing.value = pending }
}
onMounted(load)
onBeforeUnmount(() => { destroyed = true; ++searchVersion; ++detailVersion; icons.dispose() })
</script>

<template>
  <div class="price-alerts">
    <section class="price-intro">
      <span class="price-heading-icon"><img :src="goldIngot" alt="" width="24" height="24"/></span>
      <div><h2>价格提醒</h2><p>设置价格条件，接收 QQ 私聊通知</p></div>
    </section>
    <div v-if="error" class="price-message error" role="alert">{{ error }}</div>
    <div v-if="notice" class="price-message success" role="status">{{ notice }}</div>
    <div v-if="loading" class="price-card price-empty" role="status">正在读取提醒…</div>
    <template v-else-if="settings">
      <section class="price-card push-settings">
        <div><strong>私聊价格提醒</strong><p>{{ settings.enabled ? '已开启' : '已关闭' }}</p></div>
        <button class="price-switch" role="switch" :aria-checked="settings.enabled" aria-label="私聊价格提醒"
                :disabled="busy || !settings.available || !settings.enabled && !settings.canPush" @click="mutate('switch', {enabled: !settings.enabled}, settings.enabled ? '私聊提醒已关闭' : '私聊提醒已开启')"><span/></button>
        <p v-if="!settings.available" class="price-warning">请先添加机器人为好友，再设置私聊提醒</p>
        <p v-else-if="!settings.canPush" class="price-warning">私聊主动消息已关闭，请先开启主动消息权限</p>
      </section>
      <section v-if="selected" class="price-card price-editor" aria-label="配置价格提醒">
        <button class="price-back" :disabled="busy" @click="close"><Icon name="left"/> 返回物品列表</button>
        <div class="selected-item">
          <PriceItemIcon :item="selected" :load="icons.load" :size="80"/>
          <div><h3 :style="{color: itemColor(selected.key, selected.tier)}">{{ selected.name }}</h3><p>{{ editing ? ruleMarket(editing) : marketName(quote?.item || selected) }}</p><small v-if="variant(selected.variant || editing?.variant || '')">{{ variant(selected.variant || editing?.variant || '') }}</small></div>
        </div>
        <p v-if="detailLoading" class="price-muted" role="status">正在读取当前报价…</p>
        <form @submit.prevent="save">
          <div class="price-field"><span>报价类型</span><DropdownSelect v-model="metric" label="报价类型" :options="quoteOptions" @update:model-value="metricChanged = true"/></div>
          <div class="price-fields">
            <div class="price-field"><span>提醒条件</span><DropdownSelect v-model="direction" label="提醒条件" :options="directions"/></div>
            <label class="price-field"><span>目标价格 / Coins</span><input v-model="threshold" inputmode="decimal" placeholder="输入目标价格" maxlength="20" required/></label>
          </div>
          <div class="quote-status"><span>当前报价 <strong>{{ current?.available ? format(current.value) + ' Coins' : '暂不可用' }}</strong></span><small>行情更新 {{ date(current?.updated || 0) }}</small></div>
          <label class="rule-checkbox"><input v-model="ruleEnabled" type="checkbox"/> 启用提醒</label>
          <button class="price-primary" type="submit" :disabled="!canSave">{{ busy ? '正在保存…' : editing ? '保存修改' : '创建提醒' }}</button>
        </form>
      </section>
      <section v-else class="price-card price-search">
        <h3>添加提醒</h3>
        <form class="price-search-form" @submit.prevent="search()">
          <div class="price-search-control">
            <Icon name="search"/>
            <input v-model="query" class="price-search-input" type="search" aria-label="搜索物品"
                   placeholder="物品英文名或 ID" maxlength="128" :disabled="!settings.available"/>
          </div>
          <button class="price-search-submit" type="submit" :disabled="searching || !query.trim() || !settings.available">
            {{ searching ? '搜索中…' : '搜索' }}
          </button>
        </form>
        <div v-if="results" class="search-results" aria-label="搜索结果">
          <button v-for="item in results.items" :key="item.key" class="search-item" @click="open(item)">
            <PriceItemIcon :item="item" :load="icons.load"/>
            <div class="search-item-text"><strong :style="{color: itemColor(item.key, item.tier)}">{{ item.name }}</strong><span>{{ marketName(item) }}</span><small v-if="variant(item.variant)">{{ variant(item.variant) }}</small></div>
            <Icon name="chevron"/>
          </button>
          <p v-if="!results.items.length" class="price-empty">没有匹配的物品</p>
          <div class="price-pagination"><button :disabled="!results.pre_cursor || searching" @click="search(results.pre_cursor)">上一页</button><span>{{ results.page }} / {{ results.total_pages }}</span><button :disabled="!results.next_cursor || searching" @click="search(results.next_cursor)">下一页</button></div>
        </div>
      </section>
      <section class="price-rule-list" aria-label="我的提醒">
        <div class="price-list-title"><h3>我的提醒 <small>{{ settings.rules.length }}/{{ settings.limit }}</small></h3><button class="price-icon-button" aria-label="刷新提醒" :disabled="busy" @click="load"><Icon name="refresh"/></button></div>
        <div v-if="!settings.rules.length" class="price-card price-empty"><Icon name="broadcast"/><p>暂无价格提醒</p></div>
        <article v-for="rule in settings.rules" :key="rule.id" class="price-card rule-card">
          <div class="rule-heading">
            <div class="rule-item">
              <PriceItemIcon :item="{id: rule.itemId, key: rule.key}" :load="icons.load"/>
              <div><h4 :style="{color: itemColor(rule.key)}">{{ rule.name }}</h4><p class="rule-source">{{ ruleMarket(rule) }}</p><p v-if="variant(rule.variant)" class="rule-variant">{{ variant(rule.variant) }}</p></div>
            </div>
            <button v-if="settings.canTest" type="button" class="rule-state rule-test" :disabled="busy || rule.sending || testing.has(rule.id) || !settings.available" @click="testRule(rule)">{{ testing.has(rule.id) ? '测试中' : '测试' }}</button>
            <span v-else-if="rule.sending || !rule.enabled" class="rule-state">{{ rule.sending ? '发送中' : '已暂停' }}</span>
          </div>
          <p class="rule-threshold">{{ labels[rule.metric] }} <strong>{{ rule.direction === 'below' ? '≤' : '≥' }} {{ format(rule.threshold) }}</strong><span> Coins</span></p>
          <p class="rule-latest">最近报价 {{ format(rule.lastPrice) }}<template v-if="rule.lastSnapshot"> · {{ date(rule.lastSnapshot) }}</template></p>
          <p v-if="rule.error" class="price-warning">{{ rule.error }}</p>
          <div class="rule-actions"><button :disabled="busy || rule.sending || !settings.available" @click="open({id: rule.itemId, key: rule.key, name: rule.name}, rule)">编辑</button><button :disabled="busy || rule.sending || !settings.available" @click="action(rule, !rule.enabled)">{{ rule.enabled ? '暂停' : '启用' }}</button><button v-if="deleting !== rule.id" class="price-danger" :disabled="busy || rule.sending || !settings.available" @click="deleting = rule.id">删除</button><template v-else><button class="price-danger" :disabled="busy" @click="action(rule)">确认删除</button><button :disabled="busy" @click="deleting = ''">取消</button></template></div>
        </article>
      </section>
    </template>
    <button v-else class="price-primary" @click="load">重新加载</button>
  </div>
</template>

<style scoped>
.price-alerts {
  --price-text: #354339;
  --price-muted: #7e8977;
  --price-subtle: #98a18f;
  --price-soft: #ffffff70;
  --price-border: #b5c3a740;
  --price-accent: #829678;
  --price-accent-hover: #637857;
  --price-accent-soft: #dfe9d49c;
  --price-focus: #a9ba9b;
  padding-bottom: 24px;
  color: var(--price-text);
}

.price-intro {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 23px;
}

.price-heading-icon {
  display: grid;
  place-items: center;
  width: 45px;
  height: 45px;
  flex: none;
  border: 1px solid #fff;
  border-radius: 15px;
  background: #ffffff80;
}

.price-heading-icon img {
  display: block;
  width: 24px;
  height: 24px;
  image-rendering: pixelated;
}

.price-intro h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 550;
}

.price-intro p {
  margin: 5px 0 0;
  color: #8b9683;
  font-size: 10px;
  line-height: 1.7;
}

.push-settings p {
  margin: 0;
  color: var(--price-muted);
  font-size: 12px;
  line-height: 1.7;
}

.price-card {
  padding: 25px 27px;
  margin-bottom: 22px;
  border: 1px solid #ffffffe0;
  border-radius: 23px;
  background: #ffffff6e;
  box-shadow: 0 8px 32px #44573c05, inset 0 1px 0 #ffffffa6;
  -webkit-backdrop-filter: blur(24px);
  backdrop-filter: blur(24px);
}

.push-settings {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.push-settings strong {
  font-size: 14px;
}

.push-settings p {
  margin-top: 5px;
}

.price-switch {
  flex: none;
  width: 42px;
  height: 25px;
  padding: 3px;
  border: 0;
  border-radius: 20px;
  background: #d3dacd;
  transition: background .2s;
}

.price-switch span {
  display: block;
  width: 19px;
  height: 19px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 1px 3px #0002;
  transition: transform .2s;
}

.price-switch[aria-checked=true] {
  background: var(--price-accent);
}

.price-switch[aria-checked=true] span {
  transform: translateX(17px);
}

.price-warning {
  width: 100%;
  font-size: 12px;
  color: #b57548;
  line-height: 1.6;
  margin: 8px 0 0;
}

.price-card h3,.price-list-title h3 {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 550;
}

.price-message {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 14px;
}

.price-message.error {
  color: #b05252;
  background: #fff0ed;
}

.price-message.success {
  color: var(--price-accent-hover);
  background: var(--price-accent-soft);
}

.price-search-form {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.price-search-control {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  padding: 0 13px;
  border: 1px solid var(--price-border);
  background: var(--price-soft);
  border-radius: 12px;
  transition: border-color .18s, box-shadow .18s;
}

.price-search-control:focus-within {
  border-color: var(--price-focus);
  box-shadow: 0 0 0 3px #647f660d;
}

.price-search-control > svg {
  width: 18px;
  height: 18px;
  color: var(--price-muted);
  flex: none;
}

.price-search-input {
  flex: 1;
  width: 0;
  min-width: 0;
  height: 44px;
  padding: 0 0 0 10px;
  background: transparent;
  border: 0;
  border-radius: 0;
  font: inherit;
  font-size: 14px;
  color: var(--price-text);
  appearance: none;
}

.price-search-input:focus-visible {
  outline: none;
}

.price-search-input::placeholder {
  color: var(--price-subtle);
}

.price-search-input::-webkit-search-cancel-button {
  cursor: pointer;
}

.price-search-submit,
.price-primary {
  border: 1px solid #ffffffc9;
  background: var(--price-accent-soft);
  color: var(--price-accent-hover);
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  border-radius: 12px;
  padding: 11px 18px;
  white-space: nowrap;
}

.price-search-submit {
  min-width: 76px;
  flex: none;
}

.price-primary {
  width: 100%;
  min-height: 43px;
}

.price-hint {
  font-size: 11px;
  line-height: 1.8;
  color: var(--price-muted);
  margin: 12px 0 0;
}

.search-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  border: 0;
  border-bottom: 1px solid #b5c3a726;
  background: none;
  width: 100%;
  padding: 15px 0;
  color: inherit;
  font: inherit;
}

.search-item div {
  min-width: 0;
}

.search-item strong {
  display: block;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.search-item-text {
  flex: 1;
}

.search-item-text > span,.search-item-text > small {
  display: block;
  margin-top: 4px;
  color: var(--price-muted);
  font-size: 10px;
  overflow-wrap: anywhere;
}

.search-item>svg {
  width: 16px;
  flex: none;
  color: var(--price-subtle);
}

.price-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 15px;
  font-size: 11px;
  color: var(--price-muted);
}

.price-pagination button,.rule-actions button,.price-back {
  border: 0;
  background: var(--price-accent-soft);
  color: var(--price-accent-hover);
  padding: 7px 11px;
  border-radius: 8px;
  font: inherit;
  font-size: 11px;
}

.price-list-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 24px 4px 12px;
}

.price-list-title h3 {
  margin: 0;
}

.price-list-title small {
  font-weight: 400;
  color: var(--price-muted);
  margin-left: 4px;
  font-size: 11px;
}

.price-icon-button {
  border: 0;
  background: none;
  color: var(--price-muted);
  padding: 5px;
  display: flex;
}

.price-icon-button svg {
  width: 16px;
  height: 16px;
}

.price-empty {
  text-align: center;
  padding: 30px 18px;
  color: var(--price-muted);
  font-size: 12px;
}

.price-empty>svg {
  width: 27px;
  height: 27px;
  opacity: .5;
}

.price-empty p {
  margin: 10px 0 6px;
}

.price-empty small {
  font-size: 11px;
  color: var(--price-subtle);
}

.rule-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.rule-item > div { min-width: 0; }

.rule-heading h4 {
  font-size: 14px;
  margin: 0;
  overflow-wrap: anywhere;
  line-height: 1.6;
}

.rule-state {
  flex: none;
  font-size: 10px;
  border-radius: 6px;
  padding: 4px 7px;
  background: #e9eddf80;
  color: var(--price-muted);
}

.rule-test {
  border: 0;
  color: #bf565e;
  background: #fae9ec;
}

.rule-source,.rule-variant {
  font-size: 10px;
  color: var(--price-subtle);
  overflow-wrap: anywhere;
  margin: 4px 0 0;
  line-height: 1.6;
}

.rule-threshold {
  font-size: 12px;
  margin: 0;
  color: var(--price-muted);
  line-height: 1.9;
}

.rule-threshold strong {
  font-size: 18px;
  color: var(--price-text);
  margin-left: 7px;
}

.rule-threshold>span {
  font-size: 10px;
}

.rule-latest {
  font-size: 10px;
  color: var(--price-subtle);
  margin: 8px 0 0;
  line-height: 1.8;
}

.rule-actions {
  display: flex;
  gap: 7px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.rule-actions .price-danger {
  background: #fbefec;
  color: #b58277;
}

.price-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  padding: 0;
  color: var(--price-muted);
}

.price-back svg {
  width: 14px;
  height: 14px;
}

.selected-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 0;
}

.selected-item>div:last-child {
  min-width: 0;
}

.selected-item h3 {
  margin: 0;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.selected-item p,.selected-item small {
  font-size: 10px;
  color: var(--price-muted);
  margin: 5px 0 0;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.price-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  min-width: 0;
}

.price-field>span {
  font-size: 11px;
  color: var(--price-muted);
}

.price-field input {
  font: inherit;
  font-size: 12px;
  color: var(--price-text);
  background: var(--price-soft);
  border: 1px solid var(--price-border);
  border-radius: 10px;
  width: 100%;
  min-width: 0;
  padding: 11px 12px;
  box-sizing: border-box;
}

.price-fields {
  display: grid;
  grid-template-columns: 1fr 1.4fr;
  gap: 12px;
}

.quote-status {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--price-soft);
  border-radius: 12px;
  padding: 13px;
  margin-bottom: 16px;
  font-size: 11px;
  color: var(--price-muted);
}

.quote-status strong {
  color: var(--price-accent-hover);
  margin-left: 6px;
}

.quote-status small {
  font-size: 10px;
  color: var(--price-subtle);
}

.rule-checkbox {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--price-muted);
}

.rule-checkbox input {
  accent-color: var(--price-accent);
}

.price-editor .price-primary {
  margin-top: 18px;
}

.price-muted {
  font-size: 12px;
  color: var(--price-muted);
}

.price-alerts button {
  cursor: pointer;
  transition: opacity .18s,background .18s;
}

.price-alerts button:disabled {
  opacity: .45;
  cursor: default;
}

.price-field input:focus-visible,
.rule-checkbox input:focus-visible,
.price-alerts button:focus-visible {
  outline: 2px solid var(--price-focus);
  outline-offset: 2px;
}

@media (hover: hover) {
  .price-search-submit:hover:not(:disabled),
  .price-primary:hover:not(:disabled) {
    background: #d5e2cbbf;
  }

  .search-item:hover {
    background: var(--price-soft);
  }
}

@media(max-width:760px) {
  .price-card {
    padding: 20px;
  }
}

@media(max-width:420px) {
  .price-card {
    padding: 16px;
  }
  .price-fields {
    gap: 8px;
  }
  .price-field input {
    padding: 11px 9px;
    font-size: 11px;
  }
  .rule-threshold strong {
    font-size: 17px;
  }
}

@media(prefers-reduced-motion:reduce) {
  .price-alerts * {
    transition: none!important;
  }
}
</style>
