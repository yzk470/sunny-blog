import { httpGet, httpPost } from './http'
import type {
  ArchiveItem,
  Category,
  Comment,
  CommentPayload,
  Guestbook,
  GuestbookPayload,
  PageResult,
  PostDetail,
  PostSummary,
  SiteProfile,
  SiteStats,
  Tag,
} from './types'

export interface PostQuery {
  page?: number
  size?: number
  category?: string
  tag?: string
  keyword?: string
  sort?: 'latest' | 'popular'
}

export const blogApi = {
  listPosts(params: PostQuery = {}) {
    return httpGet<PageResult<PostSummary>>('/posts', params)
  },
  latest(limit = 4) {
    return httpGet<PostSummary[]>('/posts/latest', { limit })
  },
  featured(limit = 3) {
    return httpGet<PostSummary[]>('/posts/featured', { limit })
  },
  detail(slug: string) {
    return httpGet<PostDetail>(`/posts/${encodeURIComponent(slug)}`)
  },
  archive() {
    return httpGet<ArchiveItem[]>('/posts/archive')
  },
  recordView(slug: string) {
    return httpPost<{ viewCount: number }>(`/posts/${encodeURIComponent(slug)}/view`)
  },
  like(slug: string) {
    return httpPost<{ likeCount: number }>(`/posts/${encodeURIComponent(slug)}/like`)
  },
  categories() {
    return httpGet<Category[]>('/categories')
  },
  tags() {
    return httpGet<Tag[]>('/tags')
  },
  site() {
    return httpGet<SiteProfile>('/site')
  },
  stats() {
    return httpGet<SiteStats>('/stats')
  },
  comments(slug: string, page = 1, size = 20) {
    return httpGet<PageResult<Comment>>(`/posts/${encodeURIComponent(slug)}/comments`, { page, size })
  },
  createComment(slug: string, payload: CommentPayload) {
    return httpPost<Comment>(`/posts/${encodeURIComponent(slug)}/comments`, payload)
  },
  guestbook(page = 1, size = 20) {
    return httpGet<PageResult<Guestbook>>('/guestbook', { page, size })
  },
  createGuestbook(payload: GuestbookPayload) {
    return httpPost<Guestbook>('/guestbook', payload)
  },
}
