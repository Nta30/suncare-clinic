import { getToken } from './accountApi'

async function request(path = '', method = 'GET', data) {
  const token = getToken()
  if (!token) {
    const error = new Error('Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.')
    error.status = 401
    throw error
  }
  let response
  try {
    response = await fetch(`/api/doctor/encounters${path}`, {
      method,
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      ...(data ? { body: JSON.stringify(data) } : {}),
      cache: 'no-store',
    })
  } catch {
    throw new Error('Không kết nối được máy chủ. Hãy thử lại sau.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || `Không xử lý được yêu cầu (HTTP ${response.status}).`)
    error.status = response.status
    error.fieldErrors = body?.fieldErrors || {}
    throw error
  }
  if (!body || typeof body !== 'object') throw new Error('Máy chủ trả về dữ liệu lượt khám không hợp lệ.')
  return body
}

export function getEncounterWorklist(filters) {
  return request(`?${new URLSearchParams(filters)}`).then((body) => {
    if (!Array.isArray(body.items) || !Number.isInteger(body.page) || !Number.isInteger(body.totalElements)) throw new Error('Danh sách lượt khám không hợp lệ.')
    return body
  })
}

export function getEncounter(id) {
  return request(`/${id}`)
}

export function startEncounter(id) {
  return request(`/${id}/start`, 'POST', { presenceConfirmed: true })
}

export function savePatientHistory(encounterId, itemId, data) {
  return request(`/${encounterId}/histories${itemId == null ? '' : `/${itemId}`}`, itemId == null ? 'POST' : 'PUT', data)
}

export function savePatientAllergy(encounterId, itemId, data) {
  return request(`/${encounterId}/allergies${itemId == null ? '' : `/${itemId}`}`, itemId == null ? 'POST' : 'PUT', data)
}
