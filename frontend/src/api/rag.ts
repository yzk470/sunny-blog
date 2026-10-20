import { httpDelete, httpGet, httpPost } from './http'
import { TOKEN_KEY } from './http'

export interface RagDatabase {
  id: string
  name: string
  description: string
  createdAt: string
  updatedAt: string
  files: RagFile[]
}

export interface RagFile {
  id: string
  name: string
  size: number
  type: string
  strategy: string
  chunks: number
  createdAt: string
}

export interface RetrievedChunk {
  databaseId: string
  databaseName: string
  source: string
  content: string
  score: number
}

export const ragApi = {
  databases() {
    return httpGet<Array<{ id: string; name: string; description: string; files: number }>>('/agent/rag/databases')
  },
  adminDatabases() {
    return httpGet<RagDatabase[]>('/admin/rag/databases')
  },
  createDatabase(payload: { name: string; description: string }) {
    return httpPost<RagDatabase>('/admin/rag/databases', payload)
  },
  deleteDatabase(id: string) {
    return httpDelete<void>(`/admin/rag/databases/${encodeURIComponent(id)}`)
  },
  files(id: string) {
    return httpGet<RagFile[]>(`/admin/rag/databases/${encodeURIComponent(id)}/files`)
  },
  async upload(id: string, file: File, strategy: string) {
    const form = new FormData()
    form.append('file', file)
    form.append('strategy', strategy)
    const token = localStorage.getItem(TOKEN_KEY)
    const response = await fetch(`/api/admin/rag/databases/${encodeURIComponent(id)}/files`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    })
    const body = await response.json()
    if (!response.ok || body.code !== 0) throw new Error(body.message || '上传失败')
    return body.data as RagFile
  },
  deleteFile(id: string, fileId: string) {
    return httpDelete<void>(`/admin/rag/databases/${encodeURIComponent(id)}/files/${encodeURIComponent(fileId)}`)
  },
  search(id: string, query: string, topK = 5) {
    return httpPost<RetrievedChunk[]>(`/admin/rag/databases/${encodeURIComponent(id)}/search`, { query, topK })
  },
}
