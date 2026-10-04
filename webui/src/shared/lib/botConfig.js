import { computed, readonly, shallowRef } from 'vue'

export function createBotConfigStore() {
  const config = shallowRef({})
  let loaded = false
  let pending = null
  let generation = 0

  async function loadBotConfig(request, { force = false } = {}) {
    if (pending) {
      const current = pending
      try { return await current.promise }
      catch (error) {
        // 页面请求可能在卸载时取消；新页面使用自己的请求重新读取。
        if (current.request === request) throw error
        return loadBotConfig(request, { force })
      }
    }
    if (loaded && !force) return config.value
    const version = generation
    const current = { request, promise: null }
    pending = current
    current.promise = Promise.resolve().then(() => request('/config')).then(value => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('机器人配置响应无效')
      if (version === generation) {
        config.value = value
        loaded = true
      }
      return value
    }).finally(() => {
      if (pending === current) pending = null
    })
    return current.promise
  }

  function resetBotConfig() {
    generation++
    pending = null
    loaded = false
    config.value = {}
  }

  return {
    config: readonly(config),
    appId: computed(() => config.value.appId || ''),
    botOpenId: computed(() => config.value.botOpenId || ''),
    botName: computed(() => config.value.botName || 'AtriBot'),
    loadBotConfig,
    resetBotConfig
  }
}

const store = createBotConfigStore()
export const useBotConfig = () => store
export const resetBotConfig = () => store.resetBotConfig()
