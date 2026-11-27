import { getToken } from './accountApi'

async function request(path = '', data, method = 'GET') {
  let response
  try {
    response = await fetch(`/api/admin/drugs${path}`, {
      method, cache: 'no-store',
      headers: { Accept: 'application/json', Authorization: `Bearer ${getToken()}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      ...(data ? { body: JSON.stringify(data) } : {}),
    })
  } catch { throw new Error('Không nhận được phản hồi máy chủ. Nếu vừa lưu, hãy tải lại để kiểm tra trước khi lưu lại.') }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || `Không xử lý được danh mục thuốc (HTTP ${response.status}).`)
    error.status = response.status
    error.fieldErrors = body?.fieldErrors || {}
    throw error
  }
  if (!Array.isArray(body)) throw new Error('Phản hồi danh mục thuốc không hợp lệ. Hãy tải lại để kiểm tra.')
  return body
}

export const getDrugs = () => request()
export const saveDrug = (id, data) => request(id == null ? '' : `/${id}`, data, id == null ? 'POST' : 'PUT')
