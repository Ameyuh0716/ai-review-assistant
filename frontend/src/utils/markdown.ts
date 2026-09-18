import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  breaks: true,
  gfm: true,
  // @ts-ignore
  headerIds: false,
  mangle: false
})

export function renderMarkdown(text: string): string {
  if (!text) return ''
  const raw = marked.parse(text) as string
  return DOMPurify.sanitize(raw, { ADD_ATTR: ['target'] })
}

/**
 * 流式渲染 Markdown。
 * <p>与最终渲染共用同一渲染管线（marked + DOMPurify），保证「流式累积结果」与
 * 「历史消息重渲染结果」完全一致；marked 能安全处理未闭合的语法片段
 * （如只收到 `**` 半截时按字面量渲染）。</p>
 *
 * @param text 截至当前的累积文本
 * @returns 渲染后的安全 HTML
 */
export function renderStreamingMarkdown(text: string): string {
  return renderMarkdown(text)
}
