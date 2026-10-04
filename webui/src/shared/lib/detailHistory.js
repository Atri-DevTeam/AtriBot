export function createDetailHistory(router, isDisposed = () => false) {
  let preparing = false

  async function ensureList(route, list) {
    if (preparing || isDisposed()) return false
    const listPath = router.resolve(list).fullPath
    if (router.options.history.state.back === listPath) return true
    preparing = true
    try {
      await router.replace(list)
      if (isDisposed() || router.currentRoute.value.fullPath !== listPath) return false
      await router.push({ path: route.path, query: route.query, hash: route.hash })
      return !isDisposed() && router.currentRoute.value.fullPath === route.fullPath
    } finally { preparing = false }
  }

  function backToList(list) {
    if (isDisposed()) return
    if (router.options.history.state.back === router.resolve(list).fullPath) router.back()
    else router.replace(list)
  }

  return { ensureList, backToList, get preparing() { return preparing } }
}
