/**
 * @Author YZ_Ljc_
 * @ClassName welcomeEditor
 * @Created_at 2026/09/07
 * @Project AtriMeow
 * @Package webui.src.lib
 */
export const buttonStyles = {
  GRAY: '灰色', BLUE: '蓝色', ICON_BUTTON: '图标按钮', RED: '红色', BLUE_WITH_BACKGROUND: '蓝色填充'
}

export function createWelcomeButton(id) {
  return {
    button_id: id, display_text: '新按钮', visited_display_text: '', data: '/help',
    type: 'COMMAND', style: 'BLUE', enter: true, reply: false,
    permission: 'ALL', allowed_open_ids: [], button_group_id: '',
    modal: { content: '', confirm_text: '', cancel_text: '' }
  }
}

export function readWelcomeDraft(config = {}) {
  return {
    text: config.text ?? '',
    button_size: config.button_size || 'UNDEFINED',
    keyboard: (config.keyboard || []).map(row => row.map(button => ({
      ...createWelcomeButton(button.button_id), ...button,
      allowed_open_ids: [...(button.allowed_open_ids || [])],
      modal: { content: '', confirm_text: '', cancel_text: '', ...button.modal }
    })))
  }
}

export function serializeWelcomeDraft(draft) {
  return {
    text: draft.text,
    button_size: draft.button_size,
    keyboard: draft.keyboard.map(row => row.map(button => {
      const result = { ...button }
      if (!result.visited_display_text) delete result.visited_display_text
      if (!result.button_group_id) delete result.button_group_id
      if (result.permission !== 'SPECIFIC_USER') delete result.allowed_open_ids
      if (!result.modal.content.trim()) delete result.modal
      else {
        result.modal = { ...result.modal }
        if (!result.modal.confirm_text) delete result.modal.confirm_text
        if (!result.modal.cancel_text) delete result.modal.cancel_text
      }
      return result
    }))
  }
}

export function imageMarkdown(url, width, height) {
  let parsed
  try { parsed = new URL(url.trim()) } catch { throw new Error('请填写有效的图片 URL') }
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password) {
    throw new Error('图片仅支持不含账号密码的 HTTP / HTTPS 地址')
  }
  if (![width, height].every(value => Number.isInteger(Number(value)) && Number(value) > 0 && Number(value) <= 4096)) {
    throw new Error('图片宽高必须是 1 至 4096 的整数')
  }
  const address = parsed.href.replace(/\(/g, '%28').replace(/\)/g, '%29')
  return `![img #${Number(width)}px #${Number(height)}px](${address})`
}
