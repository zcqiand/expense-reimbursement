/**
 * 票据 OCR API 封装——第 37 章。
 *
 * 上传是 multipart/form-data：请求**不能**走 expense.ts 的统一 request()
 * （那会给请求体套 JSON Content-Type），但响应仍是统一 ApiResponse<Receipt>
 * 包装，所以解析逻辑保持同款。
 *
 * 端点对应后端 OcrReceiptController：
 *   POST /api/v1/expense-reports/{id}/receipts（字段名固定 file）
 */
import type { ApiResponse } from '../types'
import type { Receipt } from '../types/receipt'

const BASE = '/api/v1/expense-reports'

export const receiptApi = {
  /** 上传票据图片并触发 OCR 识别；识别失败不抛错，落 FAILED 记录返回。 */
  upload(reportId: number, file: File): Promise<Receipt> {
    const form = new FormData()
    form.append('file', file)
    return fetch(`${BASE}/${reportId}/receipts`, {
      method: 'POST',
      body: form, // 不设 Content-Type，让浏览器自带 multipart boundary
    }).then(async (res) => {
      const body: ApiResponse<Receipt> = await res.json()
      if (!body.success || body.data === null) {
        throw new Error(body.error?.message ?? '未知错误')
      }
      return body.data
    })
  },
}
