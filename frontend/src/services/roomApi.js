import { getToken } from './accountApi'

async function request(path = '', method = 'GET', data) {
  let response
  try {
    response = await fetch(`/api/admin/organization/rooms${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${getToken()}`,
        ...(data ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(data ? { body: JSON.stringify(data) } : {}),
      cache: 'no-store',
    })
  } catch {
    throw new Error('Không kết nối được máy chủ. Hãy thử lại sau.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`)
    error.status = response.status
    error.fieldErrors = body?.fieldErrors || {}
    throw error
  }
  if (!Array.isArray(body)) throw new Error('Máy chủ trả về danh sách phòng không hợp lệ.')
  return body
}

export const getRooms = () => request()
export const createRoom = (data) => request('', 'POST', data)
export const updateRoom = (id, data) => request(`/${id}`, 'PUT', data)
