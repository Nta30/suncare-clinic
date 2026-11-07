async function request(path, token, requestOptions = {}) {
  let response
  try {
    response = await fetch(path, {
      ...requestOptions,
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        ...(requestOptions.headers || {}),
      },
      cache: 'no-store',
    })
  } catch {
    throw new Error('Không kết nối được máy chủ. Hãy thử lại sau.')
  }

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(response.status === 502 || response.status === 503
      ? 'Không kết nối được backend. Hãy kiểm tra Spring Boot đang chạy trên cổng 8080 rồi thử lại.'
      : body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`)
    error.status = response.status
    throw error
  }
  if (!body) throw new Error('Máy chủ trả về dữ liệu không hợp lệ.')
  return body
}

export function getOnsiteAppointmentOptions(token) {
  return request('/api/appointments/onsite/options', token)
}

export function searchOnsiteAppointmentAvailability(token, filters) {
  const query = new URLSearchParams()
  if (filters.specialtyId) query.set('specialtyId', filters.specialtyId)
  if (filters.dateFrom) query.set('dateFrom', filters.dateFrom)
  if (filters.dateTo) query.set('dateTo', filters.dateTo)
  if (filters.shiftId) query.set('shiftId', filters.shiftId)
  if (filters.doctorName?.trim()) query.set('doctorName', filters.doctorName.trim())
  return request(`/api/appointments/onsite/availability?${query}`, token)
}

export function bookOnsiteAppointment(token, booking, idempotencyKey) {
  return request('/api/appointments/onsite/bookings', token, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(booking),
  })
}
