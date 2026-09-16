// 带图消息按图片等比缩放后的宽度排版，避免声明的大宽度留下空白。
export function chatImageSize(image, attachment = false) {
  if (image.complete && !image.naturalWidth) return null
  const declaredWidth = Number(image.getAttribute('width')) || 0
  const declaredHeight = Number(image.getAttribute('height')) || 0
  const sourceWidth = image.naturalWidth || declaredWidth
  const sourceHeight = image.naturalHeight || declaredHeight
  if (!(sourceWidth > 0 && sourceHeight > 0)) return null

  const scale = Math.min(1, 320 / sourceHeight,
    declaredWidth > 0 ? declaredWidth / sourceWidth : 1,
    declaredHeight > 0 ? declaredHeight / sourceHeight : 1,
    attachment ? 320 / sourceWidth : 1)
  const width = sourceWidth * scale
  const height = sourceHeight * scale
  return { width, height, inline: width <= 48 && height <= 48 }
}

function fitImages(bubble) {
  let width = 0
  if (!bubble.classList.contains('qm-bubble--forward')) {
    for (const image of bubble.querySelectorAll(':scope > .md-body img, :scope > .qm-attach > img')) {
      const size = chatImageSize(image, image.parentElement.classList.contains('qm-attach'))
      image.classList.toggle('md-inline-icon', !!size?.inline)
      if (size) {
        image.style.width = `${size.width}px`
        // 行内图标不应把整条文字消息压成窄列。
        if (!size.inline && size.width > 48 && size.height > 48) width = Math.max(width, size.width)
      } else {
        image.style.removeProperty('width')
      }
    }
  }
  bubble.classList.toggle('qm-bubble--image-sized', width > 0)
  if (width > 0) bubble.style.setProperty('--qm-image-width', `${width}px`)
  else bubble.style.removeProperty('--qm-image-width')
}

const states = new WeakMap()

export const vChatImageLayout = {
  mounted(bubble) {
    const state = { queued: false, disposed: false, update: null }
    state.update = () => {
      if (state.queued || state.disposed) return
      state.queued = true
      queueMicrotask(() => {
        state.queued = false
        // 等 Vue 更新及失效图片替换完成后再计算。
        if (!state.disposed) fitImages(bubble)
      })
    }
    states.set(bubble, state)
    bubble.addEventListener('load', state.update, true)
    bubble.addEventListener('error', state.update, true)
    state.update()
  },
  updated(bubble) {
    states.get(bubble)?.update()
  },
  beforeUnmount(bubble) {
    const state = states.get(bubble)
    if (!state) return
    state.disposed = true
    bubble.removeEventListener('load', state.update, true)
    bubble.removeEventListener('error', state.update, true)
    states.delete(bubble)
  }
}
