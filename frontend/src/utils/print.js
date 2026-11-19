/**
 * 打印工具。
 *
 * 后端 /api/print/** 接口返回的是整页 HTML（已内联打印样式），且需要 JWT，
 * 因此不能用 window.open('/api/print/...') 直接打开，必须先通过 axios 取回 HTML，
 * 再写入新窗口由浏览器打印。
 */

/**
 * 判断后端返回的内容是否是可直接写入的 HTML 页面。
 * @param {unknown} html
 * @returns {html is string}
 */
function isHtmlDocument(html) {
  if (typeof html !== 'string') return false
  const text = html.trim()
  if (!text) return false
  // 打印接口应返回 <!DOCTYPE html> 或 <html>，不应是统一响应体的 JSON
  return text.startsWith('<') && !text.startsWith('{"')
}

/**
 * 把后端返回的打印 HTML 写入新窗口。
 * 后端 HTML 已内置 window.onload = () => window.print()，这里只负责写入；
 * 若 HTML 未内置自动打印脚本，则在页面加载完成后调用一次 window.print() 兜底。
 *
 * @param {string} html 后端打印接口返回的 HTML 字符串
 * @returns {Window|null} 新窗口对象；被浏览器拦截或内容异常时返回 null
 */
export function openPrintWindow(html) {
  if (!isHtmlDocument(html)) return null
  const win = window.open('', '_blank')
  if (!win) return null

  win.document.open()
  win.document.write(html)
  win.document.close()

  const hasAutoPrint = /window\.print\s*\(|\bprint\s*\(\s*\)/.test(html)
  if (!hasAutoPrint) {
    const trigger = () => {
      try {
        win.focus()
        win.print()
      } catch {
        /* 忽略打印调用异常（例如用户已手动关闭窗口） */
      }
    }
    if (win.document.readyState === 'complete') {
      window.setTimeout(trigger, 200)
    } else {
      win.addEventListener('load', () => window.setTimeout(trigger, 200))
    }
  }
  return win
}
