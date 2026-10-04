import { onBeforeUnmount } from 'vue'

export function createLatestRequest() {
  let version = 0
  let controller = null
  let disposed = false

  function invalidate() {
    version++
    controller?.abort()
    controller = null
  }

  function begin() {
    invalidate()
    const currentVersion = version
    const current = controller = new AbortController()
    if (disposed) current.abort()
    return {
      signal: current.signal,
      isCurrent: () => !disposed && currentVersion === version && !current.signal.aborted
    }
  }

  return { begin, invalidate, dispose() { disposed = true; invalidate() } }
}

export function useLatestRequest() {
  const request = createLatestRequest()
  onBeforeUnmount(request.dispose)
  return request
}
