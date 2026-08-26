/**
 * Loads and renders a group's first nine member avatars without blocking the
 * conversation list. Jobs are queued and limited so a large list of groups
 * cannot start an unbounded number of requests at once.
 */
export default class GroupAvatarRenderer {
  constructor({
    loadMembers,
    avatarUrl,
    maxConcurrent = 2,
    imageConcurrent = 3,
    size = 128,
    gap = 2,
    cacheLimit = 100
  } = {}) {
    if (typeof loadMembers !== 'function') throw new TypeError('loadMembers must be a function')
    if (typeof avatarUrl !== 'function') throw new TypeError('avatarUrl must be a function')

    this.loadMembers = loadMembers
    this.avatarUrl = avatarUrl
    this.maxConcurrent = Math.max(1, Number(maxConcurrent) || 1)
    this.imageConcurrent = Math.max(1, Number(imageConcurrent) || 1)
    this.size = Math.max(32, Number(size) || 128)
    this.gap = Math.max(0, Number(gap) || 0)
    this.cacheLimit = Math.max(1, Number(cacheLimit) || 100)
    this.cache = new Map()
    this.inflight = new Map()
    this.queue = []
    this.running = 0
  }

  /** Enqueue a group and resolve with a PNG data URL (or null on failure). */
  enqueue(groupOpenId) {
    const id = String(groupOpenId || '').trim()
    if (!id) return Promise.resolve(null)
    if (this.cache.has(id)) return Promise.resolve(this.cache.get(id))
    if (this.inflight.has(id)) return this.inflight.get(id)

    const promise = new Promise(resolve => {
      this.queue.push({ id, resolve })
      this.drain()
    })
    this.inflight.set(id, promise)
    return promise
  }

  clear(groupOpenId) {
    if (groupOpenId == null) {
      this.cache.clear()
      return
    }
    this.cache.delete(String(groupOpenId))
  }

  dispose() {
    this.queue.splice(0).forEach(job => job.resolve(null))
    this.cache.clear()
    this.inflight.clear()
  }

  drain() {
    while (this.running < this.maxConcurrent && this.queue.length) {
      const job = this.queue.shift()
      this.running += 1
      this.process(job).finally(() => {
        this.running -= 1
        this.inflight.delete(job.id)
        this.drain()
      })
    }
  }

  async process({ id, resolve }) {
    try {
      const members = await this.loadMembers(id)
      const ids = this.memberIds(members)
      const result = ids.length ? await this.render(id, ids) : null
      if (result) this.remember(id, result)
      resolve(result)
    } catch {
      // A failed group falls back to the normal placeholder in the view.
      resolve(null)
    }
  }

  memberIds(members) {
    if (!Array.isArray(members)) return []
    const ids = []
    const seen = new Set()
    for (const member of members) {
      const id = String(
        member?.unionOpenId || member?.memberOpenId || member?.openId || member?.userOpenId || ''
      ).trim()
      if (!id || seen.has(id)) continue
      seen.add(id)
      ids.push(id)
      if (ids.length === 9) break
    }
    return ids
  }

  async render(groupOpenId, ids) {
    if (typeof document === 'undefined') return null
    const canvas = document.createElement('canvas')
    canvas.width = this.size
    canvas.height = this.size
    const context = canvas.getContext('2d')
    if (!context) return null

    context.clearRect(0, 0, this.size, this.size)
    context.save()
    context.beginPath()
    context.arc(this.size / 2, this.size / 2, this.size / 2, 0, Math.PI * 2)
    context.clip()
    // The unused portions of a partial group collage are intentionally white.
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, this.size, this.size)

    const images = await this.loadImages(ids)
    const tiles = this.layoutForCount(ids.length)
    images.forEach((image, index) => {
      if (!image) return
      const tile = tiles[index]
      if (!tile) return
      this.drawCover(
        context,
        image,
        tile.x * this.size,
        tile.y * this.size,
        tile.width * this.size,
        tile.height * this.size
      )
    })
    context.restore()

    try {
      return canvas.toDataURL('image/png')
    } catch {
      // Cross-origin images without CORS headers can taint a canvas.
      return null
    }
  }

  /** Return normalized tile rectangles with equal-sized cells per count. */
  layoutForCount(count) {
    // Keep every member tile the same size for a given count. Unused cells
    // remain white, which matches the native QQ/WeChat collage appearance.
    const gridByCount = {
      1: [1, 1],
      2: [2, 1],
      3: [2, 2],
      4: [2, 2],
      5: [3, 2],
      6: [3, 2],
      7: [3, 3],
      8: [3, 3],
      9: [3, 3]
    }
    const [columns, rows] = gridByCount[Math.max(1, Math.min(9, count))] || [3, 3]
    const gap = Math.min(0.2, this.gap / this.size)
    const cellWidth = (1 - gap * (columns - 1)) / columns
    const cellHeight = (1 - gap * (rows - 1)) / rows
    const tiles = []
    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        tiles.push({
          x: column * (cellWidth + gap),
          y: row * (cellHeight + gap),
          width: cellWidth,
          height: cellHeight
        })
      }
    }
    return tiles
  }

  loadImages(ids) {
    const results = new Array(ids.length)
    let next = 0
    let active = 0

    return new Promise(resolve => {
      const pump = () => {
        if (next >= ids.length && active === 0) {
          resolve(results)
          return
        }
        while (active < this.imageConcurrent && next < ids.length) {
          const index = next++
          active += 1
          this.loadImage(this.avatarUrl(ids[index]))
            .then(image => { results[index] = image })
            .catch(() => { results[index] = null })
            .finally(() => {
              active -= 1
              pump()
            })
        }
      }
      pump()
    })
  }

  loadImage(url) {
    return new Promise((resolve, reject) => {
      if (!url || typeof Image === 'undefined') {
        reject(new Error('Avatar URL unavailable'))
        return
      }
      const image = new Image()
      image.crossOrigin = 'anonymous'
      image.referrerPolicy = 'no-referrer'
      image.onload = () => resolve(image)
      image.onerror = () => reject(new Error('Avatar load failed'))
      image.src = url
    })
  }

  drawCover(context, image, x, y, width, height) {
    const sourceWidth = image.naturalWidth || image.width
    const sourceHeight = image.naturalHeight || image.height
    if (!sourceWidth || !sourceHeight) return
    const targetRatio = width / height
    const sourceRatio = sourceWidth / sourceHeight
    let cropWidth = sourceWidth
    let cropHeight = sourceHeight
    if (sourceRatio > targetRatio) cropWidth = sourceHeight * targetRatio
    else if (sourceRatio < targetRatio) cropHeight = sourceWidth / targetRatio
    const sourceX = (sourceWidth - cropWidth) / 2
    const sourceY = (sourceHeight - cropHeight) / 2
    context.drawImage(image, sourceX, sourceY, cropWidth, cropHeight, x, y, width, height)
  }

  remember(id, value) {
    this.cache.delete(id)
    this.cache.set(id, value)
    while (this.cache.size > this.cacheLimit) this.cache.delete(this.cache.keys().next().value)
  }
}
