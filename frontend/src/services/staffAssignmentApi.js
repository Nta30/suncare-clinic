import { getToken } from './accountApi'

async function request(path = '', method = 'GET', data) {
  let response
  try {
    response = await fetch(`/api/admin/staff-assignments${path}`, {
      method,
      headers: { Accept: 'application/json', Authorization: `Bearer ${getToken()}`, ...(data ? { 'Content-Type': 'application/json' } : {}) },
      ...(data ? { body: JSON.stringify(data) } : {}),
      cache: 'no-store',
    })
  } catch {
    throw new Error('Không kết nối được máy chủ.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`)
    error.status = response.status
    throw error
  }
  return body
}

export const listAssignments = () => request()
export const assignDepartment = (data) => request('/departments', 'POST', data)
export const endDepartment = (id, validTo) => request(`/departments/${id}/end`, 'PUT', { validTo })
export const assignHead = (data) => request('/heads', 'POST', data)
export const endHead = (id, validTo) => request(`/heads/${id}/end`, 'PUT', { validTo })
export const setStaffSpecialty = (data) => request('/specialties', 'PUT', data)
