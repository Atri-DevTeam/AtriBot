type QQWindow = Window & {mqq?: {ui?: {popBack?: () => void}}}

export async function closeCurrentPage(): Promise<boolean> {
    const host = window as QQWindow
    const flags = new URLSearchParams(window.location.search).getAll('qq')
    const qq = flags.length === 1 && flags[0] === '1'
    if (qq && !host.mqq?.ui?.popBack && /\bQQ\/[\d.]+/i.test(navigator.userAgent)) {
        await new Promise<void>(resolve => {
            const script = document.createElement('script')
            const finish = () => {
                clearTimeout(timeout)
                script.onload = null
                script.onerror = null
                script.remove()
                resolve()
            }
            const timeout = setTimeout(finish, 3_000)
            script.src = 'https://open.mobile.qq.com/sdk/qqapi.js'
            script.async = true
            script.referrerPolicy = 'no-referrer'
            script.onload = finish
            script.onerror = finish
            document.head.append(script)
        })
    }
    if (qq && host.mqq?.ui?.popBack) {
        try {
            host.mqq.ui.popBack()
            return true
        } catch {}
    }
    try { window.close() } catch {}
    return window.closed
}
