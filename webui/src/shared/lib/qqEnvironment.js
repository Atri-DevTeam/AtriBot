const values = new URLSearchParams(window.location.search).getAll('qq')
export const isQqPage = values.length === 1 && values[0] === '1'

export function withQqMode(path) {
    if (!isQqPage) return path
    const url = new URL(path, window.location.origin)
    url.searchParams.set('qq', '1')
    return url.pathname + url.search + url.hash
}
