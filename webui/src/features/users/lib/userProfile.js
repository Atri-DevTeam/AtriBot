export function emptyUserProfile() {
  return { role: 'USER', permissions: [], blocked: false, ignored: false, c2cPush: true }
}

function fromResponse(data) {
  if (!data || !Array.isArray(data.permissions) || !['USER', 'ADMIN', 'OWNER'].includes(data.role)) {
    throw new Error('用户档案响应无效，请重新加载')
  }
  return {
    role: data.role,
    permissions: [...data.permissions],
    blocked: !!data.isBlocked,
    ignored: !!data.isIgnored,
    c2cPush: data.c2cPush !== false
  }
}

export async function loadUserProfile(api, userId, options) {
  return fromResponse(await api(`/c2c/${encodeURIComponent(userId)}/permissions`, options))
}

export async function saveUserProfile(api, userId, profile) {
  const body = JSON.stringify({
    role: profile.role,
    permissions: [...profile.permissions],
    blocked: profile.blocked,
    ignored: profile.ignored,
    c2cPush: profile.c2cPush
  })
  return fromResponse(await api(`/c2c/${encodeURIComponent(userId)}/profile`, { method: 'POST', body }))
}
