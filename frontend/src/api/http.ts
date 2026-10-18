import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'

export const TOKEN_KEY = 'sunnyblog_token'
export const USER_KEY = 'sunnyblog_user'

export class ApiError extends Error {
  status: number

  constructor(message: string, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json; charset=utf-8' },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await client.request<ApiResponse<T>>(config)
    const body = response.data
    if (!body || typeof body.code !== 'number') {
      throw new ApiError('服务器返回了无法识别的数据', 500)
    }
    if (body.code !== 0) {
      throw new ApiError(body.message || '请求失败', body.code)
    }
    return body.data
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 0
      const body = error.response?.data as ApiResponse<unknown> | undefined
      const message =
        body?.message ||
        (status === 0 ? '无法连接服务器，请确认后端已经启动' : error.message || '请求失败')
      if (
        status === 401 &&
        !config.url?.includes('/admin/auth/login')
      ) {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
        window.dispatchEvent(new CustomEvent('sunny:auth-expired'))
      }
      throw new ApiError(message, status)
    }
    throw error
  }
}

export function httpGet<T>(url: string, params?: object): Promise<T> {
  return request<T>({ url, method: 'GET', params })
}

export function httpPost<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'POST', data })
}

export function httpPostLong<T>(url: string, data?: unknown, timeout = 180000): Promise<T> {
  return request<T>({ url, method: 'POST', data, timeout })
}

export function httpPut<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'PUT', data })
}

export function httpPatch<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'PATCH', data })
}

export function httpDelete<T>(url: string): Promise<T> {
  return request<T>({ url, method: 'DELETE' })
}
