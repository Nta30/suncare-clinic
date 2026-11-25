import { getToken } from './accountApi'

async function request(path = '', data, method = 'GET') {
  const token = getToken()
  let response
  try {
    response = await fetch(`/api/admin/allergens${path}`, { method, cache: 'no-store',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      ...(data ? { body: JSON.stringify(data) } : {}),
    })
  } catch { throw new Error('Không kết nối được máy chủ. Hãy thử lại.') }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || `Không xử lý được danh mục (HTTP ${response.status}).`)
    error.status = response.status
    error.fieldErrors = body?.fieldErrors || {}
    throw error
  }
  if (!Array.isArray(body)) throw new Error('Danh mục trả về không hợp lệ.')
  return body
}
export const getAllergens = () => request()
export const saveAllergen = (id, data) => request(id == null ? '' : `/${id}`, data, id == null ? 'POST' : 'PUT')
