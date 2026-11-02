import { getToken } from './accountApi'

async function request(url, method = 'GET', data) {
  let response
  try {
    response = await fetch(url, {
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

// ── Ca làm việc (ShiftDefinition) ──

export const getShifts = () => request('/api/admin/scheduling/shifts')

export const createShift = (data) => request('/api/admin/scheduling/shifts', 'POST', data)

export const updateShift = (id, data) => request(`/api/admin/scheduling/shifts/${id}`, 'PUT', data)

export const initDefaultShifts = () => request('/api/admin/scheduling/shifts/init-defaults', 'POST')

// ── Bác sĩ & Dịch vụ khám ──

export const getDoctors = () => request('/api/admin/services/doctors')

export const getExaminationServices = () => request('/api/admin/services?type=EXAMINATION&active=true')

// ── Giá khám bệnh theo bác sĩ / hình thức ──

export const getAllConsultationPrices = (params) => {
  const query = new URLSearchParams()
  if (params?.serviceId) query.append('serviceId', params.serviceId)
  if (params?.doctorId) query.append('doctorId', params.doctorId)
  if (params?.visitMode) query.append('visitMode', params.visitMode)
  if (params?.activeOnly) query.append('activeOnly', 'true')
  const qs = query.toString()
  return request(`/api/admin/services/consultation-prices${qs ? `?${qs}` : ''}`)
}

export const createConsultationPrice = (serviceId, data) =>
  request(`/api/admin/services/${serviceId}/consultation-prices`, 'POST', data)

export const endConsultationPrice = (serviceId, priceId, data) =>
  request(`/api/admin/services/${serviceId}/consultation-prices/${priceId}/end`, 'PUT', data)
