export const TRANSFER_FORMAT = 'atribot-menu-panel'
export const TRANSFER_VERSION = 1

const MENU_TYPES = new Set(['send_message', 'link', 'switch', 'menu'])
const SUB_MENU_TYPES = new Set(['send_message', 'link'])
const PANEL_TYPES = new Set(['command', 'link'])
const PANEL_SCOPES = new Set(['c2c', 'group', 'channel', 'dm'])

function fail(message) {
  throw new Error(message)
}

function object(value, path) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(`${path} 必须是对象`)
  return value
}

function array(value, path, max) {
  if (!Array.isArray(value)) fail(`${path} 必须是数组`)
  if (value.length > max) fail(`${path} 最多允许 ${max} 项`)
  return value
}

function text(value, path, max = Infinity) {
  if (value == null) return ''
  if (typeof value !== 'string') fail(`${path} 必须是字符串`)
  if (value.length > max) fail(`${path} 过长`)
  return value
}

function menuName(value, path, max) {
  const result = text(value, path)
  const length = Array.from(result).reduce(
    (total, char) => total + (char.codePointAt(0) <= 0x7f ? 1 : 2),
    0
  )
  if (length > max) fail(`${path} 过长`)
  return result
}

function boolean(value, path) {
  if (value == null) return false
  if (typeof value !== 'boolean') fail(`${path} 必须是布尔值`)
  return value
}

function stringList(value, path) {
  if (value == null) return []
  const result = array(value, path, 10000).map((item, index) => {
    const result = text(item, `${path}[${index}]`, 256).trim()
    if (!result) fail(`${path}[${index}] 不能为空`)
    return result
  })
  return [...new Set(result)]
}

function normalizeSubMenuItem(value, path) {
  const item = object(value, path)
  const type = text(item.type, `${path}.type`)
  if (!SUB_MENU_TYPES.has(type)) fail(`${path}.type 不受支持`)
  const result = {name: menuName(item.name, `${path}.name`, 14) || null, type}
  if (type === 'send_message') result.sendMessage = text(item.sendMessage, `${path}.sendMessage`) || null
  if (type === 'link') result.link = text(item.link, `${path}.link`) || null
  return result
}

function normalizeMenuItem(value, path) {
  const item = object(value, path)
  const type = text(item.type, `${path}.type`)
  if (!MENU_TYPES.has(type)) fail(`${path}.type 不受支持`)
  const align = item.align == null ? '' : text(item.align, `${path}.align`)
  if (align && align !== 'left' && align !== 'right') fail(`${path}.align 不受支持`)

  const result = {name: menuName(item.name, `${path}.name`, 10) || null, type, align: align || null}
  if (type === 'send_message') result.sendMessage = text(item.sendMessage, `${path}.sendMessage`) || null
  if (type === 'link') result.link = text(item.link, `${path}.link`) || null
  if (type === 'switch') {
    const config = object(item.switchConfig, `${path}.switchConfig`)
    result.switchConfig = {
      switchId: text(config.switchId, `${path}.switchConfig.switchId`, 256) || null,
      defaultOn: boolean(config.defaultOn, `${path}.switchConfig.defaultOn`)
    }
  }
  if (type === 'menu') {
    result.subMenuItems = array(item.subMenuItems || [], `${path}.subMenuItems`, 5)
      .map((sub, index) => normalizeSubMenuItem(sub, `${path}.subMenuItems[${index}]`))
  }
  return result
}

function normalizeMenu(value) {
  if (value == null) return null
  const menu = object(value, 'menu')
  const items = array(menu.items, 'menu.items', 10)
  if (items.length === 0) fail('menu.items 不能为空；没有菜单时请使用 null')
  return {items: items.map((item, index) => normalizeMenuItem(item, `menu.items[${index}]`))}
}

function normalizePanelItem(value, path) {
  const item = object(value, path)
  const type = text(item.type, `${path}.type`)
  if (!PANEL_TYPES.has(type)) fail(`${path}.type 不受支持`)
  const result = {
    name: text(item.name, `${path}.name`, 14) || null,
    desc: text(item.desc, `${path}.desc`, 30) || null,
    type,
    onlyAdmin: boolean(item.onlyAdmin, `${path}.onlyAdmin`)
  }
  if (type === 'link') result.link = text(item.link, `${path}.link`) || null
  return result
}

function normalizePanel(value, index) {
  const path = `panels[${index}]`
  const entry = object(value, path)
  const scope = text(entry.scope, `${path}.scope`)
  if (!PANEL_SCOPES.has(scope)) fail(`${path}.scope 不受支持`)
  const targetType = entry.targetType == null ? 'all' : text(entry.targetType, `${path}.targetType`)
  if (targetType !== 'all' && targetType !== 'specific') fail(`${path}.targetType 不受支持`)
  if (targetType === 'specific' && scope !== 'c2c' && scope !== 'group') {
    fail(`${path} 的 ${scope} 场景不支持指定对象`)
  }

  const panel = object(entry.panel, `${path}.panel`)
  const items = array(panel.items, `${path}.panel.items`, 20)
  if (items.length === 0) fail(`${path}.panel.items 不能为空`)

  return {
    scope,
    targetType,
    panel: {
      items: items.map((item, itemIndex) => normalizePanelItem(item, `${path}.panel.items[${itemIndex}]`)),
      remark: text(panel.remark, `${path}.panel.remark`, 255) || null
    },
    userOpenIds: targetType === 'specific' && scope === 'c2c'
      ? stringList(entry.userOpenIds, `${path}.userOpenIds`)
      : [],
    groupOpenIds: targetType === 'specific' && scope === 'group'
      ? stringList(entry.groupOpenIds, `${path}.groupOpenIds`)
      : []
  }
}

export function parseTransferDocument(raw) {
  let value
  try {
    value = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    fail('文件不是有效的 JSON')
  }
  const document = object(value, '根节点')
  if (document.format !== TRANSFER_FORMAT) fail('不是 AtriBot 菜单与面板配置文件')
  if (document.version !== TRANSFER_VERSION) fail(`不支持的配置版本：${document.version ?? '未知'}`)
  const menu = normalizeMenu(document.menu)
  const panels = array(document.panels, 'panels', 200).map(normalizePanel)
  if (!menu && panels.length === 0) fail('配置文件中没有可导入的菜单或面板')
  return {format: TRANSFER_FORMAT, version: TRANSFER_VERSION, menu, panels}
}

export function createTransferDocument({menu, panels, exportedAt = new Date().toISOString()}) {
  const normalized = parseTransferDocument({
    format: TRANSFER_FORMAT,
    version: TRANSFER_VERSION,
    menu,
    panels
  })
  return {...normalized, exportedAt}
}
