/**
 * 票据类型——与后端 entity.Receipt 字段对齐（第 37 章 OCR）。
 */

/** OCR 识别状态——与后端 Receipt.STATUS_* 常量一致（表里 VARCHAR(20)） */
export type OcrStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

/** 票据——与后端 entity.Receipt 字段一致 */
export interface Receipt {
  id: number
  expenseReportId: number
  filePath: string
  /** Tesseract 识别正文；引擎失败时为 null */
  ocrText: string | null
  ocrStatus: OcrStatus
  createdAt: string
}
