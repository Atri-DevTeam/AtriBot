export async function prepareLootImage(file) {
  if (!/^image\/(webp|gif)$/i.test(file.type) && !/\.(webp|gif)$/i.test(file.name)) return file

  let bitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error('无法读取图片，请确认 WebP / GIF 文件未损坏')
  }

  try {
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    const context = canvas.getContext('2d', { alpha: true })
    if (!context) throw new Error('当前浏览器无法转换图片')
    context.drawImage(bitmap, 0, 0)
    const png = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!png) throw new Error('图片转 PNG 失败，请尝试其他图片')
    const name = file.name.replace(/\.[^.]+$/, '') || 'card'
    return new File([png], `${name}.png`, { type: 'image/png', lastModified: file.lastModified })
  } finally {
    bitmap.close()
  }
}
