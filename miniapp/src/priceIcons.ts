type Item = {id: string; key: string}
type Post = <T>(path: string, body: unknown) => Promise<T | null>
type Image = (path: string) => Promise<string | null>

export function createPriceIconLoader(post: Post, image: Image, onTier?: (item: Item, tier: string) => void) {
  const cache = new Map<string, {promise: Promise<string | null>; settled: boolean}>()
  const queue: Array<{item: Item; resolve: (url: string | null) => void}> = []
  let active = 0
  let disposed = false

  async function render(item: Item) {
    try {
      const result = await post<{hash: string; placeholder?: boolean; tier?: string}>('price-alerts/icon', item)
      if (!result || disposed) return null
      if (result.tier) onTier?.(item, result.tier)
      return {url: await image(`price-alerts/image/${result.hash}`), placeholder: result.placeholder === true}
    } catch { return null }
  }

  function pump() {
    while (!disposed && active < 2 && queue.length) {
      const job = queue.shift()!
      active++
      void render(job.item).then(result => {
        const url = result?.url || null
        const entry = cache.get(job.item.key)
        if (entry) entry.settled = true
        if (!url || result?.placeholder) cache.delete(job.item.key)
        job.resolve(disposed ? null : url)
        for (const [key, value] of cache) {
          if (cache.size <= 64) break
          if (value.settled) cache.delete(key)
        }
      }).finally(() => { active--; pump() })
    }
  }

  return {
    load(item: Item): Promise<string | null> {
      if (disposed) return Promise.resolve(null)
      const cached = cache.get(item.key)
      if (cached) return cached.promise
      let resolve!: (url: string | null) => void
      const promise = new Promise<string | null>(complete => { resolve = complete })
      cache.set(item.key, {promise, settled: false})
      queue.push({item: {id: item.id, key: item.key}, resolve})
      pump()
      return promise
    },
    dispose() {
      disposed = true
      for (const job of queue.splice(0)) job.resolve(null)
      cache.clear()
    }
  }
}
