async function requestAppointments(path, token) {
  let response
  try {
    response = await fetch(path, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
      cache: 'no-store',
    })
  } catch {
    throw new Error('Không kết nối được máy chủ. Hãy thử tải lại sau.')
  }

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(response.status === 502 || response.status === 503
      ? 'Không kết nối được backend. Hãy kiểm tra Spring Boot đang chạy rồi thử lại.'
      : body?.message || `Không tải được lịch hẹn (HTTP ${response.status}).`)
    error.status = response.status
    throw error
  }
  if (body == null || typeof body !== 'object') throw new Error('Máy chủ trả về dữ liệu lịch khám không hợp lệ.')
  return body
}

function appointmentList(path, token) {
  return requestAppointments(path, token).then((body) => {
    if (!Array.isArray(body)) throw new Error('Máy chủ trả về danh sách lịch không hợp lệ.')
    return body
  })
}

export function getPatientAppointments(token) {
  return appointmentList('/api/patient/appointments', token)
}

export function getDoctorAppointments(token) {
  return appointmentList('/api/doctor/appointments', token)
}

export function getPatientAppointment(token, appointmentId) {
  return requestAppointments(`/api/patient/appointments/${appointmentId}`, token)
}

export function getDoctorAppointment(token, appointmentId) {
  return requestAppointments(`/api/doctor/appointments/${appointmentId}`, token)
}
