import { getToken } from './accountApi'

async function request(path, signal, data, key) {
  let response
  try {
    response = await fetch(`/api/lab${path}`, {
      signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(20000)]) : AbortSignal.timeout(20000),
      method: data ? 'POST' : 'GET',
      headers: {
        Accept: 'application/json', Authorization: `Bearer ${getToken()}`,
        ...(data ? { 'Content-Type': 'application/json', 'Idempotency-Key': key } : {})
      },
      ...(data ? { body: JSON.stringify(data) } : {}),
      cache: 'no-store'
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new Error('Không nhận được phản hồi. Hãy tải lại để kiểm tra trạng thái trước khi thử lại.', { cause: error })
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || 'Không tải được dữ liệu cận lâm sàng.')
    error.status = response.status
    throw error
  }
  return body
}

export const getLabAssignments = (signal, current = true) => request(`/assignments?current=${current}`, signal)
export const getLabQueue = (roomId, signal) => request(`/orders?roomId=${roomId}`, signal)
export const getLabCounts = (roomId, signal) => request(`/counts?roomId=${roomId}`, signal)
export const getLabOrder = (id, roomId, signal) => request(`/orders/${id}?roomId=${roomId}`, signal)
export const actOnLabOrder = (id, action, data, key) => request(`/orders/${id}/${action}`, null, data, key)
