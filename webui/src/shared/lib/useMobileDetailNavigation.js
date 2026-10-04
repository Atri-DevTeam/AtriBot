import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createDetailHistory } from './detailHistory.js'

export function useMobileDetailNavigation({ open, close }) {
  const router = useRouter()
  const path = router.currentRoute.value.path
  let disposed = false
  const history = createDetailHistory(router, () => disposed)

  function listRoute(route) {
    const { detail, ...query } = route.query
    return { path: route.path, query, hash: route.hash }
  }

  function detailId(route) {
    return typeof route.query.detail === 'string' ? route.query.detail : ''
  }

  async function sync() {
    const route = router.currentRoute.value
    if (disposed || history.preparing || route.path !== path) return
    const id = detailId(route)
    if (!id) { close(); return }
    const list = listRoute(route)
    // 直接打开或刷新详情链接时，确保系统返回键有对应的列表可返回。
    if (window.matchMedia('(max-width: 768px)').matches && !await history.ensureList(route, list)) return
    open(id)
  }

  function openDetail(id) {
    if (!id || history.preparing || disposed) return
    const route = router.currentRoute.value
    if (!window.matchMedia('(max-width: 768px)').matches && !detailId(route)) return open(id)
    if (detailId(route) === String(id)) return open(id)
    const target = { path, query: { ...route.query, detail: String(id) }, hash: route.hash }
    return detailId(route) ? router.replace(target) : router.push(target)
  }

  function backToList() {
    close()
    const route = router.currentRoute.value
    if (route.path !== path || !detailId(route)) return
    const list = listRoute(route)
    history.backToList(list)
  }

  watch(() => router.currentRoute.value.fullPath, sync)
  onMounted(sync)
  onBeforeUnmount(() => { disposed = true })
  return { openDetail, backToList }
}
