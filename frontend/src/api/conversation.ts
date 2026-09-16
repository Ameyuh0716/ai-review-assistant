import { get, post, put, del } from './request'

export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  intent?: string
  createdAt: string
}

export function listConversations() {
  return get<Conversation[]>('/api/conversations')
}

export function createConversation(title: string = '新对话') {
  return post<Conversation>('/api/conversations', { title })
}

export function updateConversation(id: number, title: string) {
  return put<Conversation>(`/api/conversations/${id}`, { title })
}

export function deleteConversation(id: number) {
  return del<boolean>(`/api/conversations/${id}`)
}

export function listMessages(conversationId: number) {
  return get<Message[]>(`/api/messages?conversationId=${conversationId}`)
}
