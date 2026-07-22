function readImageFile(file) {
  // 参考图先转成 data URL 预览和提交，后端会在厂商调用前按需下载或解码。
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

export function referenceImagePayload(items) {
  // 后端只关心图片地址和展示名，前端临时 id 不参与请求体。
  return items.map((item, index) => ({
    imageUrl: item.url,
    name: item.name.trim() || `参考图 ${index + 1}`
  }))
}

export async function appendReferenceFiles(event, targetList) {
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    const url = await readImageFile(file)
    targetList.push({
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      name: file.name,
      url
    })
  }
  event.target.value = ''
}

export function removeReference(targetList, id) {
  const index = targetList.findIndex((item) => item.id === id)
  if (index >= 0) {
    targetList.splice(index, 1)
  }
}
