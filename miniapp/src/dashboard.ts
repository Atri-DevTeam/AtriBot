export function takeDashboardEntry(url: URL) {
    const single = (key: string) => {
        const values = url.searchParams.getAll(key)
        return values.length === 1 ? values[0] : null
    }
    const binding = url.searchParams.has('userOpenId') || url.searchParams.has('ticket')
    const userOpenId = single('userOpenId')
    const ticket = single('ticket')
    const error = single('auth_error')
    const nav = single('_nav')
    const wwv = single('_wwv')
    const qq = single('qq') === '1'
    url.searchParams.delete('userOpenId')
    url.searchParams.delete('ticket')
    url.searchParams.delete('auth_error')
    const valid = !binding || !!userOpenId && userOpenId.length <= 128 && !!ticket && /^[\w-]{43}$/.test(ticket)
    return {binding, valid, userOpenId, ticket, error, nav, wwv, qq}
}
