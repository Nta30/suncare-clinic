import { getToken } from './accountApi'

async function request(path = '', method = 'GET', data) {
  let response
  try {
    response = await fetch(`/api/admin/services${path}`, {
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
    error.code = body?.code
    error.fieldErrors = body?.fieldErrors || {}
    throw error
  }
  return body
}

export const getServices = (params) => {
  const query = new URLSearchParams()
  if (params?.type) query.append('type', params.type)
  if (params?.active !== undefined && params?.active !== null && params?.active !== '') {
    query.append('active', params.active)
  }
  const qs = query.toString()
  return request(qs ? `?${qs}` : '')
}

export const getService = (id) => request(`/${id}`)
export const createService = (data) => request('', 'POST', data)
export const updateService = (id, data) => request(`/${id}`, 'PUT', data)
export const getServiceRooms = (serviceId) => request(`/${serviceId}/rooms`)
export const toggleServiceRoom = (serviceId, roomId, active) =>
  request(`/${serviceId}/rooms/${roomId}`, 'PUT', { active })
export const getServicePrices = (serviceId) => request(`/${serviceId}/prices`)
export const createServicePrice = (serviceId, data) => request(`/${serviceId}/prices`, 'POST', data)
export const endServicePrice = (serviceId, priceId, data) =>
  request(`/${serviceId}/prices/${priceId}/end`, 'PUT', data)
export const getEffectivePrice = (serviceId, targetTime) => {
  const qs = targetTime ? `?targetTime=${encodeURIComponent(targetTime)}` : ''
  return request(`/${serviceId}/effective-price${qs}`)
}
