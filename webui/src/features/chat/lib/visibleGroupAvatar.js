const observers = new WeakMap()
const callbacks = new WeakMap()

export const vVisibleGroupAvatar = {
  mounted(element, {value}) {
    if (!value) return
    const root = element.closest('.chatnt-items')
    if (!root || typeof IntersectionObserver === 'undefined') {
      value()
      return
    }
    let state = observers.get(root)
    if (!state) {
      const observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          observer.unobserve(entry.target)
          callbacks.get(entry.target)?.()
        }
      }, {root, rootMargin: '60px'})
      state = {observer, count: 0}
      observers.set(root, state)
    }
    state.count++
    callbacks.set(element, value)
    state.observer.observe(element)
  },
  unmounted(element) {
    callbacks.delete(element)
  },
  beforeUnmount(element) {
    const root = element.closest('.chatnt-items')
    const state = root && observers.get(root)
    if (!state || !callbacks.has(element)) return
    state.observer.unobserve(element)
    if (--state.count === 0) {
      state.observer.disconnect()
      observers.delete(root)
    }
  }
}
