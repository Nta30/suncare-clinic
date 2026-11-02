import { getCurrentAccount, getToken } from './accountApi'

const schedulePermission = 'SCHEDULE_PUBLISH_DEPARTMENT'

async function explainAccessDenied(error) {
  try {
    const account = await getCurrentAccount(getToken())
    const loginName = account.loginName || 'tài khoản hiện tại'
    error.accountLoginName = account.loginName

    if (!account.permissions?.includes(schedulePermission)) {
      error.code = 'SCHEDULE_PERMISSION_REQUIRED'
      error.message = `Tài khoản ${loginName} đang đăng nhập chưa được cấp quyền điều phối lịch khoa. Hãy nhờ quản trị viên gán quyền ${schedulePermission}.`
      return
    }

    error.code = 'SCHEDULE_AUTHORITY_MISMATCH'
    error.message = `Tài khoản ${loginName} có quyền ${schedulePermission} theo API tài khoản nhưng ${error.endpoint} trả HTTP 403. Backend cổng 8080 có thể đang chạy phiên bản cũ hoặc cấu hình quyền khác; hãy khởi động lại Spring Boot từ thư mục backend của checkout này.`
  } catch (authFailure) {
    if (authFailure.status === 401) {
      error.status = 401
      error.code = 'UNAUTHENTICATED'
      error.message = 'Phiên đăng nhập không còn hợp lệ. Hãy đăng nhập lại rồi mở Lịch khoa.'
      return
    }

    error.message = `API ${error.endpoint} trả HTTP 403 và không xác minh được quyền của phiên hiện tại. Hãy kiểm tra kết nối backend rồi thử đăng nhập lại.`
  }
}

async function request(path = '', method = 'GET', data) {
  const endpoint = `/api/head/schedules${path}`
  const token = getToken()
  let response
  try {
    response = await fetch(endpoint, {
      method,
      headers: {
        Accept: 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(data ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(data ? { body: JSON.stringify(data) } : {}),
      cache: 'no-store',
    })
  } catch {
    const error = new Error('Không kết nối được máy chủ. Hãy kiểm tra Spring Boot trên cổng 8080 rồi thử lại.')
    error.code = 'NETWORK_ERROR'
    error.endpoint = `${method} ${endpoint}`
    throw error
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`)
    error.status = response.status
    error.code = body?.code
    error.endpoint = `${method} ${endpoint}`
    if (response.status === 403 && error.code === 'ACCESS_DENIED') {
      await explainAccessDenied(error)
    }
    throw error
  }
  return body
}

export const listHeadSchedules = () => request()
export const getHeadScheduleOptions = (departmentId) =>
  request(departmentId ? `/options?departmentId=${departmentId}` : '/options')
export const createHeadSchedule = (data) => request('', 'POST', data)
export const getHeadScheduleAssignments = (scheduleId) => request(`/${scheduleId}/assignments`)
export const addHeadScheduleAssignment = (scheduleId, data) =>
  request(`/${scheduleId}/assignments`, 'POST', data)
export const removeHeadScheduleAssignment = (scheduleId, assignmentId) =>
  request(`/${scheduleId}/assignments/${assignmentId}`, 'DELETE')
