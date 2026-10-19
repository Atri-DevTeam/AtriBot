import {test} from 'node:test'
import assert from 'node:assert/strict'
import {createPriceIconLoader} from '../src/priceIcons.ts'

test('shares pending icons and limits concurrent rendering requests', async () => {
  const pending: Array<(value: unknown) => void> = []
  let requests = 0
  const loader = createPriceIconLoader(<T>() => {
    requests++
    return new Promise<T | null>(resolve => { pending.push(resolve as (value: unknown) => void) })
  }, async path => `blob:${path}`)
  const first = loader.load({id: 'A', key: 'A'})
  assert.equal(first, loader.load({id: 'A', key: 'A'}))
  const second = loader.load({id: 'B', key: 'B'})
  const third = loader.load({id: 'C', key: 'C'})
  assert.equal(requests, 2)
  pending[0]!({hash: 'a'})
  assert.equal(await first, 'blob:price-alerts/image/a')
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(requests, 3)
  pending[1]!({hash: 'b'})
  pending[2]!({hash: 'c'})
  await Promise.all([second, third])
  assert.equal(await loader.load({id: 'A', key: 'A'}), 'blob:price-alerts/image/a')
  assert.equal(requests, 3)
  loader.dispose()
})

test('disposal resolves queued requests without starting more network work', async () => {
  const pending: Array<(value: unknown) => void> = []
  let requests = 0
  const loader = createPriceIconLoader(<T>() => {
    requests++
    return new Promise<T | null>(resolve => { pending.push(resolve as (value: unknown) => void) })
  }, async () => { throw new Error('Disposed requests must not fetch image bytes') })
  const first = loader.load({id: 'A', key: 'A'})
  const second = loader.load({id: 'B', key: 'B'})
  const queued = loader.load({id: 'C', key: 'C'})
  loader.dispose()
  assert.equal(await queued, null)
  pending.forEach(resolve => resolve({hash: 'example'}))
  assert.deepEqual(await Promise.all([first, second]), [null, null])
  assert.equal(requests, 2)
})

test('paper placeholders remain retryable until the real material is available', async () => {
  let requests = 0
  const loader = createPriceIconLoader(async <T>() => {
    requests++
    return (requests === 1 ? {hash: 'paper', placeholder: true} : {hash: 'real'}) as T
  }, async path => `blob:${path}`)
  const item = {id: 'A', key: 'A'}
  assert.equal(await loader.load(item), 'blob:price-alerts/image/paper')
  assert.equal(await loader.load(item), 'blob:price-alerts/image/real')
  assert.equal(await loader.load(item), 'blob:price-alerts/image/real')
  assert.equal(requests, 2)
  loader.dispose()
})

test('delivers rarity before image bytes finish and skips callbacks after disposal', async () => {
  const tiers: string[] = []
  const pending: Array<(value: unknown) => void> = []
  let releaseImage!: (value: string | null) => void
  const loader = createPriceIconLoader(<T>() => new Promise<T | null>(resolve => {
    pending.push(resolve as (value: unknown) => void)
  }), () => new Promise(resolve => { releaseImage = resolve }), (item, tier) => tiers.push(`${item.key}:${tier}`))
  const first = loader.load({id: 'A', key: 'A'})
  pending[0]!({hash: 'paper', placeholder: true, tier: 'EPIC'})
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(tiers, ['A:EPIC'])
  releaseImage(null)
  assert.equal(await first, null)
  const second = loader.load({id: 'B', key: 'B'})
  loader.dispose()
  pending[1]!({hash: 'b', tier: 'RARE'})
  assert.equal(await second, null)
  assert.deepEqual(tiers, ['A:EPIC'])
})
