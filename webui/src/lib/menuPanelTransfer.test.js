import test from 'node:test'
import assert from 'node:assert/strict'
import {createTransferDocument, parseTransferDocument} from './menuPanelTransfer.js'

const validDocument = {
  format: 'atribot-menu-panel',
  version: 1,
  menu: {items: [{name: '帮助', type: 'send_message', sendMessage: '/help', align: 'left'}]},
  panels: [{
    scope: 'c2c',
    targetType: 'specific',
    panel: {remark: '常用指令', items: [{name: 'help', desc: '帮助', type: 'command', onlyAdmin: false}]},
    userOpenIds: ['user-1']
  }]
}

test('normalizes a valid transfer document', () => {
  const parsed = parseTransferDocument(JSON.stringify(validDocument))
  assert.equal(parsed.menu.items[0].sendMessage, '/help')
  assert.deepEqual(parsed.panels[0].userOpenIds, ['user-1'])
  assert.deepEqual(parsed.panels[0].groupOpenIds, [])
})

test('rejects unsupported formats before import', () => {
  assert.throws(
    () => parseTransferDocument({...validDocument, format: 'unknown'}),
    /不是 AtriBot/
  )
})

test('rejects invalid nested panel data', () => {
  const invalid = structuredClone(validDocument)
  invalid.panels[0].panel.items[0].type = 'menu'
  assert.throws(() => parseTransferDocument(invalid), /type 不受支持/)
})

test('uses the existing double-width rule for Chinese menu names', () => {
  const invalid = structuredClone(validDocument)
  invalid.menu.items[0].name = '一二三四五六'
  assert.throws(() => parseTransferDocument(invalid), /name 过长/)
})

test('supports a panel-only configuration package', () => {
  const document = createTransferDocument({menu: null, panels: validDocument.panels, exportedAt: '2026-08-25T00:00:00.000Z'})
  assert.equal(document.exportedAt, '2026-08-25T00:00:00.000Z')
  assert.equal(document.menu, null)
  assert.equal(document.panels.length, 1)
})

test('preserves more than one target batch for large transfers', () => {
  const document = structuredClone(validDocument)
  document.panels[0].userOpenIds = Array.from({length: 45}, (_, index) => `user-${index}`)
  assert.equal(parseTransferDocument(document).panels[0].userOpenIds.length, 45)
})
