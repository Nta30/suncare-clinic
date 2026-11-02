const tokenKey = 'suncareAccessToken'

export function getToken() {
  return sessionStorage.getItem(tokenKey)
}

export function saveToken(token) {
  sessionStorage.setItem(tokenKey, token)
}

export function clearToken() {
  sessionStorage.removeItem(tokenKey)
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(path, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      },
      cache: 'no-store',
    })
  } catch {
    throw new Error('Không kết nối được máy chủ. Hãy thử lại sau.')
  }

  if (response.status === 204) return null

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const message = response.status === 502 || response.status === 503
      ? 'Không kết nối được backend. Hãy kiểm tra Spring Boot đang chạy trên cổng 8080 rồi thử lại.'
      : body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`
    const error = new Error(message)
    error.status = response.status
    error.fieldErrors = body?.fieldErrors || {}
    throw error
  }
  if (!body) throw new Error('Máy chủ trả về dữ liệu không hợp lệ.')
  return body
}

export function registerPatient(data) {
  return request('/api/accounts/register', { method: 'POST', body: JSON.stringify(data) })
}

export function login(data) {
  return request('/api/auth/login', { method: 'POST', body: JSON.stringify(data) })
}

export function getCurrentAccount(token) {
  return request('/api/auth/me', { method: 'GET', token })
}

export function logout(token) {
  return request('/api/auth/logout', { method: 'POST', token })
}

export function getPersonalProfile(token) {
  return request('/api/accounts/profile', { method: 'GET', token })
}

export function updatePersonalProfile(token, data) {
  return request('/api/accounts/profile', { method: 'PUT', token, body: JSON.stringify(data) })
}

export function listStaff(token) {
  return request('/api/admin/staff', { method: 'GET', token })
}

export function createStaff(token, data) {
  return request('/api/admin/staff', { method: 'POST', token, body: JSON.stringify(data) })
}

export function updateStaff(token, accountId, data) {
  return request(`/api/admin/staff/${accountId}`, {
    method: 'PUT', token, body: JSON.stringify(data),
  })
}

export function grantStaffRole(token, accountId, roleCode) {
  return request(`/api/admin/staff/${accountId}/roles`, {
    method: 'POST', token, body: JSON.stringify({ roleCode }),
  })
}

export function revokeStaffRole(token, accountId, roleCode) {
  return request(`/api/admin/staff/${accountId}/roles/${roleCode}`, { method: 'DELETE', token })
}

export function setStaffStatus(token, accountId, status) {
  return request(`/api/admin/staff/${accountId}/status`, {
    method: 'PATCH', token, body: JSON.stringify({ status }),
  })
}
