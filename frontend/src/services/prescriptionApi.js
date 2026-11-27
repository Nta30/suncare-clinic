import { getToken } from './accountApi'

async function request(path, method = 'GET', body, key) {
  let response
  try {
    response = await fetch(path, {
      method, cache: 'no-store',
      headers: { Accept: 'application/json', Authorization: `Bearer ${getToken()}`,
        ...(body ? { 'Content-Type': 'application/json' } : {}), ...(key ? { 'Idempotency-Key': key } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
  } catch { throw new Error('Chưa nhận được phản hồi máy chủ. Hãy thử lại yêu cầu đang chờ để kiểm tra kết quả.') }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(data?.message || `Không xử lý được đơn thuốc (HTTP ${response.status}).`)
    error.status = response.status
    error.fieldErrors = data?.fieldErrors || {}
    throw error
  }
  if (!data || typeof data !== 'object') throw new Error('Chưa đọc được kết quả đơn thuốc. Hãy thử lại cùng yêu cầu.')
  return data
}

const root = (encounterId) => `/api/doctor/encounters/${encounterId}/prescriptions`
export const getPrescriptions = (encounterId) => request(root(encounterId))
export const getPrescribingDrugs = () => request('/api/doctor/drugs')
export function sendPrescriptionCommand(encounterId, command) {
  const { action, id, body, key } = command
  const path = root(encounterId) + (id ? `/${id}` : '') + (action === 'issue' ? '/issue' : '')
  return request(path, action === 'save' ? 'PUT' : action === 'discard' ? 'DELETE' : 'POST', body, key)
}
