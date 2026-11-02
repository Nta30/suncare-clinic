/**
 * Badge — Các nhãn trạng thái và vai trò tái sử dụng.
 *
 * StatusBadge: hiển thị ACTIVE / LOCKED / trạng thái khác.
 * RoleBadge:   hiển thị một vai trò nhân viên bằng nhãn tiếng Việt.
 * RoleList:    danh sách RoleBadge hoặc text "Chưa được cấp vai trò".
 *
 * Dùng token CSS từ Sunflower Clinical design system:
 *   trạng thái hoạt động  → emerald (không phải clinical-success vì nền nhạt cần emerald-50)
 *   trạng thái khóa       → rose
 *   trạng thái khác       → slate
 *   vai trò               → amber (suncare brand)
 *
 * Không dùng màu làm thông tin duy nhất; mỗi badge có text kèm theo.
 */

export const ROLE_LABELS = {
  DOCTOR: 'Bác sĩ',
  LAB_STAFF: 'Nhân viên CLS',
  DEPARTMENT_HEAD: 'Trưởng khoa',
  CASHIER: 'Thu ngân',
  SYSTEM_ADMIN: 'Quản trị viên',
  PATIENT: 'Bệnh nhân',
}

const STATUS_CONFIG = {
  ACTIVE: { dot: 'bg-emerald-500', chip: 'bg-emerald-50 text-emerald-800', label: 'Hoạt động' },
  LOCKED: { dot: 'bg-rose-500', chip: 'bg-rose-50 text-rose-800', label: 'Đã khóa' },
}

/**
 * @param {{ status: string }} props
 */
export function StatusBadge({ status }) {
  const cfg = STATUS_CONFIG[status] || {
    dot: 'bg-slate-500',
    chip: 'bg-slate-100 text-slate-700',
    label: status || 'Chưa xác định',
  }
  return (
    <span className={`inline-flex w-fit items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold ${cfg.chip}`}>
      <span aria-hidden='true' className={`h-1.5 w-1.5 rounded-full ${cfg.dot}`} />
      {cfg.label}
    </span>
  )
}

/**
 * @param {{ code: string, label?: string }} props
 */
export function RoleBadge({ code, label }) {
  return (
    <span className='inline-flex max-w-full items-center rounded-lg border border-amber-100 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-900'>
      {label || ROLE_LABELS[code] || code}
    </span>
  )
}

/**
 * @param {{ roles: string[] }} props
 */
export function RoleList({ roles }) {
  if (!roles?.length) {
    return <span className='text-sm text-clinical-muted'>Chưa được cấp vai trò</span>
  }
  return (
    <div className='flex flex-wrap gap-1.5'>
      {roles.map((role) => <RoleBadge key={role} code={role} />)}
    </div>
  )
}
