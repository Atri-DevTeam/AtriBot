export const ARK_TEMPLATES = [
  { id: 23, label: '链接与文本列表' },
  { id: 24, label: '文本与缩略图' },
  { id: 37, label: '大图' }
]

const arkFields = {
  description: { label: '描述', placeholder: '卡片描述', required: true },
  prompt: { label: '通知预览', placeholder: '消息列表和通知中显示的文字', required: true },
  title: { label: '标题', placeholder: '卡片标题', required: true },
  metaDescription: { label: '详情描述', placeholder: '卡片正文', required: false },
  picUrl: { label: '图片链接', placeholder: 'https://…', required: true, url: true },
  jumpUrl: { label: '跳转链接', placeholder: 'https://…', required: true, url: true },
  subTitle: { label: '来源', placeholder: '来源名称', required: false }
}
const templateFields = {
  23: ['description', 'prompt'],
  24: ['description', 'prompt', 'title', 'metaDescription', 'picUrl', 'jumpUrl', 'subTitle'],
  37: ['prompt', 'title', 'subTitle', 'picUrl', 'jumpUrl']
}

export const CARD_FIELDS = [
  { key: 'title', label: '标题', placeholder: '卡片标题', required: true },
  { key: 'description', label: '描述', placeholder: '卡片描述', required: true },
  { key: 'picUrl', label: '图片链接', placeholder: 'https://…', required: true, url: true },
  { key: 'jumpUrl', label: '跳转链接', placeholder: 'https://…', required: true, url: true }
]

export function createArkDraft(templateId = 23) {
  return { templateId, description: '', prompt: '', title: '', metaDescription: '', picUrl: '',
    jumpUrl: '', subTitle: '', items: [{ description: '', link: '' }] }
}

export function createCardDraft() {
  return { title: '', description: '', picUrl: '', jumpUrl: '' }
}

export function getArkFields(templateId) {
  return (templateFields[templateId] || []).map(key => ({ key, ...arkFields[key],
    ...(Number(templateId) === 37 && key === 'subTitle' ? { label: '子标题', placeholder: '补充说明' } : {}) }))
}

export function isArkDraftValid(draft) {
  const fields = getArkFields(draft.templateId)
  if (!fields.length || !fields.every(field => !field.required || hasText(draft[field.key]))) return false
  return Number(draft.templateId) !== 23 || (Array.isArray(draft.items) && draft.items.length > 0
    && draft.items.every(item => hasText(item?.description)))
}

export function buildArkPayload(draft) {
  const payload = { templateId: Number(draft.templateId) }
  for (const field of getArkFields(draft.templateId)) payload[field.key] = trim(draft[field.key])
  if (payload.templateId === 23) {
    payload.items = draft.items.map(item => ({ description: trim(item.description), link: trim(item.link) || null }))
  }
  return payload
}

export function isCardDraftValid(draft) {
  return CARD_FIELDS.every(field => hasText(draft[field.key]))
}

export function buildCardPayload(draft) {
  return { type: 'tuwen', ...Object.fromEntries(CARD_FIELDS.map(field => [field.key, trim(draft[field.key])])) }
}

function hasText(value) { return typeof value === 'string' && !!value.trim() }
function trim(value) { return typeof value === 'string' ? value.trim() : '' }
