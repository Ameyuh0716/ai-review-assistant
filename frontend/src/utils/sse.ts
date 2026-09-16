export interface SseMessage {
  type: 'meta' | 'content' | 'error' | 'done'
  data: string
}

export function createEventSource(url: string, token?: string): EventSource {
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  // EventSource 不支持自定义 headers，需要通过 query 传递 token 或在服务端支持 cookie
  // 这里使用原生 EventSource，服务端允许匿名访问该接口，token 放 query 更安全
  return new EventSource(url)
}
