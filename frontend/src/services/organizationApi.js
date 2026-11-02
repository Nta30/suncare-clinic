import { getToken } from './accountApi'

async function request(path, method = 'GET', data) {
  let response
  try {
    response = await fetch(`/api/admin/organization${path}`, {
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
  if (!body || !Array.isArray(body.departments) || !Array.isArray(body.specialties) || !Array.isArray(body.links)) {
    throw new Error('Máy chủ trả về danh mục không hợp lệ.')
  }
  return body
}

export const getOrganization = () => request('')
export const createDepartment = (data) => request('/departments', 'POST', data)
export const updateDepartment = (id, data) => request(`/departments/${id}`, 'PUT', data)
export const createSpecialty = (data) => request('/specialties', 'POST', data)
export const updateSpecialty = (id, data) => request(`/specialties/${id}`, 'PUT', data)
export const setDepartmentSpecialty = (departmentId, specialtyId, active) =>
  request(`/departments/${departmentId}/specialties/${specialtyId}`, active ? 'PUT' : 'DELETE')
