import apiClient from './index'
import type { CreatePasteRequest, CreatePasteResponse, PasteData } from '@/types/paste'

/**
 * 创建 Paste
 */
export const createPaste = (data: CreatePasteRequest): Promise<CreatePasteResponse> => {
  return apiClient.post('/pastes', data)
}

/**
 * 获取 Paste
 */
export const getPaste = (key: string): Promise<PasteData> => {
  return apiClient.get(`/pastes/${key}`)
}

/**
 * 获取 Raw 内容 URL
 */
export const getRawUrl = (key: string): string => {
  return `/api/pastes/${key}/raw`
}

/**
 * 获取下载 URL
 */
export const getDownloadUrl = (key: string): string => {
  return `/api/pastes/${key}/download`
}
