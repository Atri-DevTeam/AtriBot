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
