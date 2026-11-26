import { getToken } from './accountApi'

const moneyFields = new Set(['amount', 'totalAmount', 'unitPrice', 'allocated', 'refunded', 'outstanding', 'paymentAmount', 'receivedToday'])

async function request(path, options = {}) {
  const token = getToken()
  if (!token) throw Object.assign(new Error('Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.'), { status: 401 })
  let response
  try {
    response = await fetch(`/api/cashier/invoices${path}`, {
      ...options,
      signal: AbortSignal.timeout(20000),
      cache: 'no-store',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...options.headers },
    })
  } catch (cause) {
    throw new Error('Không nhận được phản hồi máy chủ. Hãy thử lại.', { cause })
  }
  // Preserve the original integer digits before JSON numbers lose precision.
  let body
  try {
    body = JSON.parse(await response.text(), (key, value, context) => {
      if (!moneyFields.has(key) || typeof value !== 'number') return value
      if (context?.source) return context.source
      if (Number.isSafeInteger(value)) return String(value)
      throw new Error('Trình duyệt không đọc chính xác số tiền này. Hãy cập nhật trình duyệt.')
    })
  } catch (cause) {
    throw Object.assign(new Error(cause instanceof SyntaxError ? 'Máy chủ trả về dữ liệu không hợp lệ. Hãy thử lại.' : cause.message, { cause }), {
      status: response.ok ? undefined : response.status,
    })
  }
  if (!response.ok) throw Object.assign(new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`), {
    status: response.status, code: body?.code, fieldErrors: body?.fieldErrors || {},
  })
  if (!body || typeof body !== 'object') throw new Error('Máy chủ trả về dữ liệu không hợp lệ. Hãy thử lại.')
  return body
}

export const findCashInvoice = (code) => request(`?${new URLSearchParams({ code })}`)
export const getCashInvoice = (id) => request(`/${id}`)
export const getCashierWorkspace = (filters) => request(`/search?${new URLSearchParams(filters)}`)
export const getCashInvoiceDetail = (id) => request(`/${id}/detail`)
export const collectCashPayment = (id, amount, key) => request(`/${id}/cash-payments`, {
  method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
  body: JSON.stringify({ amount }),
})
