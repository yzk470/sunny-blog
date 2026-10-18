export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

export interface Category {
  id: number
  name: string
  slug: string
  description: string | null
  color: string | null
}

export interface Tag {
  id: number
  name: string
  slug: string
  color: string | null
}

export interface PostSummary {
  id: number
  title: string
  slug: string
  summary: string | null
  coverImage: string | null
  category: Category | null
  tags: Tag[]
  isTop: boolean
  status: number
  viewCount: number
  likeCount: number
  commentCount: number
  readingTime: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

export interface PostDetail extends Omit<PostSummary, 'status'> {
  content: string
}

export interface AdminPost {
  id: number
  title: string
  slug: string
  summary: string | null
  content: string
  coverImage: string | null
  categoryId: number | null
  tagIds: number[]
  status: number
  isTop: boolean
  viewCount: number
  likeCount: number
  commentCount: number
  readingTime: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
}

export interface Comment {
  id: number
  postId: number
  postTitle: string | null
  author: string
  email: string | null
  website: string | null
  content: string
  status: number | null
  isAdmin: boolean
  parentId: number | null
  createdAt: string
}

export interface Guestbook {
  id: number
  author: string
  email: string | null
  content: string
  reply: string | null
  status: number | null
  createdAt: string
}

export interface SiteProfile {
  id: number
  nickname: string
  avatar: string | null
  bio: string | null
  slogan: string | null
  location: string | null
  email: string | null
  github: string | null
  aboutContent: string | null
  footerText: string | null
  updatedAt: string | null
}

export interface SiteStats {
  posts: number
  categories: number
  tags: number
  comments: number
  views: number
}

export interface ArchiveItem {
  id: number
  title: string
  slug: string
  publishedAt: string
}

export interface Dashboard {
  postCount: number
  publishedCount: number
  draftCount: number
  commentCount: number
  pendingCommentCount: number
  guestbookCount: number
  categoryCount: number
  tagCount: number
  totalViews: number
  recentComments: Comment[]
  popularPosts: PostSummary[]
}

export interface AuthUser {
  id: number
  username: string
  nickname: string
  avatar: string | null
  role: string
}

export interface LoginResponse {
  token: string
  expiresIn: number
  user: AuthUser
}

export interface PostPayload {
  title: string
  slug?: string
  summary?: string
  content: string
  coverImage?: string
  categoryId: number
  tagIds: number[]
  status: number
  isTop: boolean
}

export interface CommentPayload {
  author: string
  email?: string
  website?: string
  content: string
  parentId?: number | null
}

export interface GuestbookPayload {
  author: string
  email?: string
  content: string
}

export interface CategoryPayload {
  name: string
  slug?: string
  description?: string
  color?: string
  sortOrder?: number
}

export interface TagPayload {
  name: string
  slug?: string
  color?: string
}

export interface SiteProfilePayload {
  nickname: string
  avatar?: string
  bio?: string
  slogan?: string
  location?: string
  email?: string
  github?: string
  aboutContent?: string
  footerText?: string
}
