export function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

// 每次从当前位置续接；连续消息或图片撑高内容时也不会先跳到终点。
export function createChatBottomScroller(getElement) {
  let frame = null

  function cancel() {
    if (frame !== null) cancelAnimationFrame(frame)
    frame = null
  }

  function scroll(behavior = 'auto') {
    cancel()
    const el = getElement()
    if (!el) return
    const bottom = () => Math.max(0, el.scrollHeight - el.clientHeight)
    if (behavior !== 'smooth' || prefersReducedMotion() || !el.clientHeight) {
      el.scrollTop = bottom()
      return
    }
    const start = el.scrollTop
    const startedAt = performance.now()
    const tick = now => {
      if (getElement() !== el) { cancel(); return }
      const progress = Math.min(1, (now - startedAt) / 280)
      const eased = 1 - Math.pow(1 - progress, 3)
      el.scrollTop = start + (bottom() - start) * eased
      frame = progress < 1 ? requestAnimationFrame(tick) : null
    }
    frame = requestAnimationFrame(tick)
  }

  return { scroll, cancel, get running() { return frame !== null } }
}

// 用消息在视口中的位置作锚点；上方插入历史或图片变高时，只补偿布局位移。
export function createChatPositionKeeper(getElement) {
  let target = null
  let offset = 0
  let frame = null
  let start = 0
  let previousTop = 0

  function cancel() {
    if (frame !== null) cancelAnimationFrame(frame)
    frame = null
    target = null
  }

  function geometry() {
    const el = getElement()
    if (!el || !target?.isConnected || !el.contains(target)) { cancel(); return null }
    const rect = target.getBoundingClientRect()
    return {
      el,
      height: rect.height,
      top: el.scrollTop + rect.top - el.getBoundingClientRect().top - el.clientTop
    }
  }

  function destination(el, top) {
    // 页首、页尾按真实滚动范围定位，避免为居中添加消息尾部空白。
    return Math.min(Math.max(0, top - offset), Math.max(0, el.scrollHeight - el.clientHeight))
  }

  function sync() {
    const position = geometry()
    if (!position || !position.el.clientHeight) return
    const { el, top } = position
    if (frame !== null) {
      // 动画中的起点、当前位置一起平移，继续向同一条消息滑动。
      const shift = top - previousTop
      start += shift
      if (Math.abs(shift) > 0.5) el.scrollTop += shift
      previousTop = top
    } else {
      const end = destination(el, top)
      if (Math.abs(el.scrollTop - end) > 0.5) el.scrollTop = end
      offset = top - el.scrollTop
    }
  }

  function hold(node, viewportOffset) {
    cancel()
    target = node
    offset = viewportOffset
  }

  function scrollTo(node) {
    cancel()
    target = node
    const position = geometry()
    if (!position) return
    const { el, top, height } = position
    offset = (el.clientHeight - Math.min(height, el.clientHeight)) / 2
    const end = destination(el, top)
    start = el.scrollTop
    previousTop = top
    if (prefersReducedMotion() || Math.abs(end - start) <= 1) {
      el.scrollTop = end
      offset = top - el.scrollTop
      return
    }
    const duration = Math.min(1200, Math.max(280, Math.abs(end - start) * 0.18))
    const startedAt = performance.now()
    const tick = now => {
      if (getElement() !== el) { cancel(); return }
      sync()
      const current = geometry()
      if (!current) return
      offset = (el.clientHeight - Math.min(current.height, el.clientHeight)) / 2
      const end = destination(el, current.top)
      const progress = Math.min(1, (now - startedAt) / duration)
      const eased = 1 - Math.pow(1 - progress, 3)
      el.scrollTop = start + (end - start) * eased
      if (progress < 1) frame = requestAnimationFrame(tick)
      else {
        frame = null
        // 页首、页尾不能居中时记住实际位置，后续加载不会推走正在看的消息。
        offset = current.top - el.scrollTop
      }
    }
    frame = requestAnimationFrame(tick)
  }

  return { scrollTo, hold, sync, cancel, clear: cancel, get active() { return target !== null } }
}
