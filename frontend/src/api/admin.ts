import { httpDelete, httpGet, httpPatch, httpPost, httpPut } from './http'
import type {
  AdminPost,
  AuthUser,
  Category,
  CategoryPayload,
  Comment,
  Dashboard,
  Guestbook,
  LoginResponse,
  PageResult,
  PostPayload,
  PostSummary,
  SiteProfile,
  SiteProfilePayload,
  Tag,
  TagPayload,
} from './types'

export const adminApi = {
  login(username: string, password: string) {
    return httpPost<LoginResponse>('/admin/auth/login', { username, password })
  },
  me() {
    return httpGet<AuthUser>('/admin/auth/me')
  },
  dashboard() {
    return httpGet<Dashboard>('/admin/dashboard')
  },

  posts(params: { page?: number; size?: number; status?: number; keyword?: string } = {}) {
    return httpGet<PageResult<PostSummary>>('/admin/posts', params)
  },
  post(id: number) {
    return httpGet<AdminPost>(`/admin/posts/${id}`)
  },
  createPost(payload: PostPayload) {
    return httpPost<AdminPost>('/admin/posts', payload)
  },
  updatePost(id: number, payload: PostPayload) {
    return httpPut<AdminPost>(`/admin/posts/${id}`, payload)
  },
  changePostStatus(id: number, status: number) {
    return httpPatch<void>(`/admin/posts/${id}/status`, { status })
  },
  deletePost(id: number) {
    return httpDelete<void>(`/admin/posts/${id}`)
  },

  comments(params: { page?: number; size?: number; status?: number } = {}) {
    return httpGet<PageResult<Comment>>('/admin/comments', params)
  },
  moderateComment(id: number, status: number) {
    return httpPatch<void>(`/admin/comments/${id}/status`, { status })
  },
  deleteComment(id: number) {
    return httpDelete<void>(`/admin/comments/${id}`)
  },

  guestbook(params: { page?: number; size?: number; status?: number } = {}) {
    return httpGet<PageResult<Guestbook>>('/admin/guestbook', params)
  },
  moderateGuestbook(id: number, status: number) {
    return httpPatch<void>(`/admin/guestbook/${id}/status`, { status })
  },
  replyGuestbook(id: number, reply: string) {
    return httpPut<void>(`/admin/guestbook/${id}/reply`, { reply })
  },
  deleteGuestbook(id: number) {
    return httpDelete<void>(`/admin/guestbook/${id}`)
  },

  categories() {
    return httpGet<Category[]>('/admin/categories')
  },
  createCategory(payload: CategoryPayload) {
    return httpPost<Category>('/admin/categories', payload)
  },
  updateCategory(id: number, payload: CategoryPayload) {
    return httpPut<Category>(`/admin/categories/${id}`, payload)
  },
  deleteCategory(id: number) {
    return httpDelete<void>(`/admin/categories/${id}`)
  },

  tags() {
    return httpGet<Tag[]>('/admin/tags')
  },
  createTag(payload: TagPayload) {
    return httpPost<Tag>('/admin/tags', payload)
  },
  updateTag(id: number, payload: TagPayload) {
    return httpPut<Tag>(`/admin/tags/${id}`, payload)
  },
  deleteTag(id: number) {
    return httpDelete<void>(`/admin/tags/${id}`)
  },

  site() {
    return httpGet<SiteProfile>('/admin/site')
  },
  updateSite(payload: SiteProfilePayload) {
    return httpPut<SiteProfile>('/admin/site', payload)
  },
}
