import { getToken } from './accountApi'

async function request(path = '', method = 'GET', data, idempotencyKey, signal) {
  const token = getToken()
  if (!token) {
    const error = new Error('Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.')
    error.status = 401
    throw error
  }
  let response
  try {
    response = await fetch(path.startsWith('/api/') ? path : `/api/doctor/encounters${path}`, {
      method,
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(data ? { 'Content-Type': 'application/json' } : {}), ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}) },
      ...(data ? { body: JSON.stringify(data) } : {}),
      cache: 'no-store',
      signal,
    })
  } catch (failure) {
    if (failure.name === 'TimeoutError') throw new Error('Tra cứu danh mục quá thời gian chờ. Hãy thử lại.', { cause: failure })
    throw new Error('Không kết nối được máy chủ. Hãy thử lại sau.', { cause: failure })
  }
  let body
  try {
    body = (path.includes('/invoices') || path.endsWith('/billing-summary'))
      ? JSON.parse(await response.text(), (key, value, context) => {
        if (!['amount', 'totalAmount', 'unitPrice'].includes(key) || typeof value !== 'number') return value
        if (context?.source) return context.source
        if (Number.isSafeInteger(value)) return String(value)
        throw new Error('Trình duyệt không đọc chính xác số tiền này. Hãy cập nhật trình duyệt.')
      })
      : await response.json()
  } catch (failure) {
    if (!(failure instanceof SyntaxError)) throw failure
    body = null
  }
  if (!response.ok) {
    const error = new Error(body?.message || `Không xử lý được yêu cầu (HTTP ${response.status}).`)
    error.status = response.status
    error.code = body?.code
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

export function completeEncounter(id) {
  return request(`/${id}/complete`, 'POST', { completionConfirmed: true })
}

export function savePatientHistory(encounterId, itemId, data) {
  return request(`/${encounterId}/histories${itemId == null ? '' : `/${itemId}`}`, itemId == null ? 'POST' : 'PUT', data)
}

export function savePatientAllergy(encounterId, itemId, data) {
  return request(`/${encounterId}/allergies${itemId == null ? '' : `/${itemId}`}`, itemId == null ? 'POST' : 'PUT', data)
}

export const getEncounterClinical = (id) => request(`/${id}/clinical`)
export const searchSymptoms = (search, signal) => request(`/api/doctor/symptoms?${new URLSearchParams({ search })}`, 'GET', undefined, undefined,
  signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000))
export const getEncounterInvoices = (id) => request(`/${id}/invoices`)
export const getEncounterBillingSummary = (id) => request(`/${id}/billing-summary`)
export const createExaminationInvoice = (id, key) => request(`/${id}/invoices`, 'POST', undefined, key)
export const issueExaminationInvoice = (id, invoiceId, version, key) => request(`/${id}/invoices/${invoiceId}/issue`, 'POST', { version }, key)
export const saveEncounterNotes = (id, data) => request(`/${id}/clinical/notes`, 'PUT', data)
export const recordVitalSign = (id, data, key) => request(`/${id}/clinical/vitals`, 'POST', data, key)
export const saveDiagnosis = (id, diagnosisId, data, key) => request(`/${id}/clinical/diagnoses${diagnosisId == null ? '' : `/${diagnosisId}`}`, diagnosisId == null ? 'POST' : 'PUT', data, key)
export const searchDiagnosisConcepts = (search, page = 1, groups = false, signal) => request(
  `/api/doctor/diagnosis-concepts/search?${new URLSearchParams({ search, page, groups })}`,
  'GET', undefined, undefined,
  signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000),
)
export const getPreviousEncounters = (contextId, page = 1) => request(`/${contextId}/history?${new URLSearchParams({ page })}`)
export const getPreviousEncounter = (contextId, id) => request(`/${contextId}/history/${id}`)
