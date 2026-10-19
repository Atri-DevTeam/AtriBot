export function emptyUserProfile() {
    return {role: 'USER', permissions: [], status: 'ACTIVE', c2cPush: true, c2cRegistered: false, account: null}
}

export const accountStatusOptions = [
    {key: 'ACTIVE', label: '正常'},
    {key: 'SUSPENDED', label: '拉黑'},
    {key: 'BANNED', label: '屏蔽'},
    {key: 'DELETED', label: '已注销'}
]

export function accountStatusLabel(status) {
    return accountStatusOptions.find(option => option.key === status)?.label || '未知状态'
}

function fromResponse(response) {
    const data = response?.user
    if (!data || !Array.isArray(data.permissions) || !['USER', 'ADMIN', 'OWNER'].includes(data.role)
        || !accountStatusOptions.some(option => option.key === data.status)) {
        throw new Error('用户档案响应无效，请重新加载')
    }
    return {
        role: data.role,
        permissions: [...data.permissions],
        status: data.status,
        c2cPush: data.c2cPush !== false,
        c2cRegistered: data.c2cRegistered === true,
        account: response.account || null
    }
}

export async function loadUserProfile(api, userId, options) {
    return fromResponse(await api(`/c2c/${encodeURIComponent(userId)}/permissions`, options))
}

export async function saveUserProfile(api, userId, profile) {
    const body = JSON.stringify({
        role: profile.role,
        permissions: [...profile.permissions],
        status: profile.status,
        c2cPush: profile.c2cPush
    })
    return fromResponse(await api(`/c2c/${encodeURIComponent(userId)}/profile`, {method: 'POST', body}))
}
