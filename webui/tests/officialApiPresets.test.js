import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {officialApiPresets} from '../src/features/debug/data/officialApiPresets.js'
import {messageExamples} from '../src/features/debug/data/messageExamples.js'

const inventory = JSON.parse(readFileSync(new URL('./fixtures/officialApiInventory.json', import.meta.url), 'utf8'))
const endpoint = item => `${item.method} ${item.path.split('?')[0]}`
const preset = key => officialApiPresets.find(item => item.key === key)

test('covers the reviewed autogen and channel documentation with source links', () => {
  assert.equal(inventory.apis.length, 96)
  assert.equal(officialApiPresets.length, inventory.apis.length)
  assert.deepEqual(new Set(officialApiPresets.map(endpoint)), new Set(inventory.apis.map(endpoint)))
  for (const api of inventory.apis) {
    const item = officialApiPresets.find(item => endpoint(item) === endpoint(api))
    assert.equal(item.documentation, api.documentation, endpoint(api))
    for (const documentation of api.relatedDocumentation || []) {
      assert.ok(item.samples.some(sample => sample.documentation === documentation), documentation)
    }
  }
  assert.equal(new Set(officialApiPresets.map(item => item.key)).size, officialApiPresets.length)
})

test('channel requests preserve channel-specific message, role and permission structures', () => {
  for (const key of ['channel-message', 'guild-dm-message']) {
    const item = preset(key)
    for (const request of [item, ...item.samples]) assert.equal(request.body.msg_type, undefined)
    assert.ok(item.samples.some(sample => sample.body.message_reference?.message_id))
  }
  assert.ok(preset('guild-dm-create').body.source_guild_id)
  for (const key of ['guild-role-member-add', 'guild-role-member-remove']) {
    assert.ok(preset(key).samples.some(sample => sample.body.channel?.id))
  }
  for (const key of ['channel-member-permissions-put', 'channel-role-permissions-put']) {
    assert.equal(typeof preset(key).body.add, 'string')
    assert.equal(typeof preset(key).body.remove, 'string')
  }
  assert.equal(preset('guild-mute').body.user_ids, undefined)
  assert.ok(preset('guild-mute').samples.some(sample => sample.body.user_ids?.length))
})

test('forum rich text is a JSON string and deprecated announcements are identified', () => {
  const richText = preset('channel-thread-create').samples.find(sample => sample.body.format === 4).body.content
  assert.equal(JSON.parse(richText).paragraphs[0].elems[0].type, 1)
  const deprecated = officialApiPresets.filter(item => item.deprecated)
  assert.equal(deprecated.length, 2)
  for (const item of deprecated) {
    assert.equal(item.category, '已废弃接口')
    assert.match(item.label, /已废弃/)
  }
})

test('stream samples distinguish the first, continuation and final chunks', () => {
  const stream = preset('c2c-stream-message')
  assert.equal(stream.body.index, 0)
  assert.equal(stream.body.input_state, 1)
  assert.equal(stream.body.stream_msg_id, undefined)
  assert.ok(stream.samples.some(sample => sample.body.input_state === 10))
  for (const sample of stream.samples) {
    assert.ok(sample.body.stream_msg_id)
    assert.ok(sample.body.index > 0)
    assert.ok(sample.body.content_raw)
    assert.equal(sample.body.stream, undefined)
  }
})

test('chunk upload samples include complete checksums and string byte counts', () => {
  for (const scene of ['group', 'user']) {
    const prepare = preset(`${scene}-upload-prepare`).body
    for (const key of ['file_name', 'file_size', 'md5', 'sha1', 'md5_10m']) {
      assert.equal(typeof prepare[key], 'string', key)
      assert.ok(prepare[key])
    }
    const finish = preset(`${scene}-upload-finish`).body
    assert.equal(finish.part_index, 0)
    assert.equal(typeof finish.block_size, 'string')
    assert.ok(finish.upload_id && finish.md5)
  }
})

test('moderation and panel operations use documented request structures', () => {
  assert.equal(preset('approve-join-request').body.op, 'approve')
  assert.equal(preset('blacklist-set').body.op, 'add')
  assert.ok(Array.isArray(preset('strategy-whitelist').body.whitelist_users))
  const mute = preset('restrict-chat-set')
  assert.equal(mute.body.members[0].op, 'add')
  assert.ok(mute.samples.some(sample => sample.body.members[0].op === 'del'))
  const target = preset('panel-target')
  for (const sample of [target, ...target.samples]) {
    assert.ok(['add', 'del'].includes(sample.body.op))
    assert.ok(Array.isArray(sample.body.user_openids) !== Array.isArray(sample.body.group_openids))
  }
})

test('advanced message examples refer to existing APIs and carry required upload filenames', () => {
  const endpoints = new Set(inventory.apis.map(endpoint))
  for (const example of messageExamples) {
    assert.ok(endpoints.has(endpoint(example)), example.key)
    if (example.body?.file_data) assert.ok(example.body.file_name, example.key)
  }
  assert.equal(messageExamples.find(item => item.key === 'group-upload-file-url').body.file_type, 4)
})
