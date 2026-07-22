export function downloadUrl(url, filename) {
  // 通过临时 a 标签触发浏览器下载，兼容 OSS URL、视频 URL 和普通图片 URL。
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.target = '_blank'
  link.rel = 'noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
