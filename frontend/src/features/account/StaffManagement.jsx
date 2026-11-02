import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createStaff,
  getToken,
  grantStaffRole,
  listStaff,
  revokeStaffRole,
  setStaffStatus,
} from '../../services/accountApi'

// Giu nguyen contract cua accountApi. Khong can them thu vien UI/icon.
const ROLE_OPTIONS = [
  { code: 'DOCTOR', label: 'Bác sĩ', description: 'Khám và điều trị' },
  { code: 'LAB_STAFF', label: 'Nhân viên CLS', description: 'Thực hiện cận lâm sàng' },
  { code: 'DEPARTMENT_HEAD', label: 'Trưởng khoa', description: 'Điều phối khoa và lịch làm việc' },
  { code: 'CASHIER', label: 'Thu ngân', description: 'Thu phí và quản lý thanh toán' },
]
const ROLE_LABELS = {
  ...Object.fromEntries(ROLE_OPTIONS.map(({ code, label }) => [code, label])),
  SYSTEM_ADMIN: 'Quản trị viên',
}
const ROLE_FILTERS = [
  { value: 'ALL', label: 'Tất cả vai trò' },
  ...ROLE_OPTIONS.map(({ code, label }) => ({ value: code, label })),
  { value: 'SYSTEM_ADMIN', label: 'Quản trị viên' },
]
const PAGE_SIZE = 10
const EMPTY_FORM = {
  loginName: '',
  password: '',
  fullName: '',
  phone: '',
  email: '',
  professionalLicense: '',
  qualification: '',
  roles: [],
}

const primaryButton =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-suncare-400 px-5 py-2.5 text-sm font-bold text-clinical-ink shadow-sm transition hover:bg-suncare-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-200 disabled:cursor-not-allowed disabled:opacity-50'
const outlineButton =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-clinical-border-strong bg-white px-4 py-2.5 text-sm font-semibold text-clinical-ink transition hover:border-amber-300 hover:bg-amber-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100 disabled:cursor-not-allowed disabled:opacity-50'
const fieldClass =
  'mt-1.5 block min-h-11 w-full min-w-0 rounded-xl border border-clinical-border-strong bg-white px-3.5 py-2.5 text-sm text-clinical-ink outline-none transition placeholder:text-slate-400 focus:border-amber-500 focus:ring-4 focus:ring-amber-100 aria-[invalid=true]:border-rose-400'

function Icon({ name, size = 19, className = '' }) {
  const paths = {
    plus: <path d="M12 5v14M5 12h14" />,
    search: <><circle cx="10.8" cy="10.8" r="6.3" /><path d="m16 16 4.2 4.2" /></>,
    refresh: <><path d="M20 11a8 8 0 0 0-14-5L4 8" /><path d="M4 4v4h4M4 13a8 8 0 0 0 14 5l2-2" /><path d="M20 20v-4h-4" /></>,
    users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    lock: <><rect x="4.5" y="10" width="15" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></>,
    doctor: <><path d="M6 3v6a5 5 0 0 0 10 0V3M4 3h4M14 3h4" /><path d="M11 14v2a4 4 0 0 0 8 0v-1" /><circle cx="19" cy="13" r="2" /></>,
    chevronLeft: <path d="m15 18-6-6 6-6" />,
    chevronRight: <path d="m9 18 6-6-6-6" />,
    close: <path d="M18 6 6 18M6 6l12 12" />,
    shield: <><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" /><path d="m9 12 2 2 4-4" /></>,
    filter: <><path d="M4 7h16M7 12h10M10 17h4" /></>,
    arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
    info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></>,
  }
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      width={size}
      height={size}
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {paths[name] || paths.info}
    </svg>
  )
}

function getRoles(person) {
  return Array.isArray(person?.roles) ? person.roles : []
}

function nameOf(person) {
  return person?.fullName?.trim() || person?.loginName || 'Chưa cập nhật tên'
}

function initials(person) {
  const pieces = nameOf(person).split(/\s+/).filter(Boolean)
  return (pieces.length > 1
    ? `${pieces[0][0]}${pieces[pieces.length - 1][0]}`
    : pieces[0]?.slice(0, 2) || 'NV').toLocaleUpperCase('vi')
}

function searchable(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
}

function messageOf(error, fallback) {
  return typeof error?.message === 'string' && error.message.trim()
    ? error.message
    : fallback
}

function Avatar({ person, large = false }) {
  return (
    <div className={`${large ? 'h-16 w-16 text-xl' : 'h-11 w-11 text-sm'} flex shrink-0 items-center justify-center rounded-2xl border border-amber-200 bg-amber-100 font-extrabold text-amber-900`}>
      {initials(person)}
    </div>
  )
}

function StatusBadge({ status }) {
  const active = status === 'ACTIVE'
  const locked = status === 'LOCKED'
  return (
    <span className={`inline-flex w-fit items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold ${active
      ? 'bg-emerald-50 text-emerald-800'
      : locked ? 'bg-rose-50 text-rose-800' : 'bg-slate-100 text-slate-700'}`}>
      <span aria-hidden="true" className={`h-1.5 w-1.5 rounded-full ${active ? 'bg-emerald-500' : locked ? 'bg-rose-500' : 'bg-slate-500'}`} />
      {active ? 'Hoạt động' : locked ? 'Đã khóa' : status || 'Chưa xác định'}
    </span>
  )
}

function RoleBadge({ code }) {
  return (
    <span className="inline-flex max-w-full items-center rounded-lg border border-amber-100 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-900">
      {ROLE_LABELS[code] || code}
    </span>
  )
}

function RoleList({ roles }) {
  return roles.length ? (
    <div className="flex flex-wrap gap-1.5">
      {roles.map((role) => <RoleBadge key={role} code={role} />)}
    </div>
  ) : <span className="text-sm text-slate-400">Chưa được cấp vai trò</span>
}

function StatCard({ label, value, icon, detail, highlight = false }) {
  return (
    <div className={`min-w-0 rounded-2xl border p-4 shadow-[0_8px_30px_rgba(50,70,54,0.035)] sm:p-5 ${highlight
      ? 'border-amber-200 bg-gradient-to-br from-amber-50 via-white to-white'
      : 'border-clinical-border bg-white'}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-semibold text-clinical-muted sm:text-sm">{label}</p>
          <p className="mt-2 text-2xl font-extrabold tabular-nums tracking-tight text-clinical-ink sm:text-3xl">{value}</p>
        </div>
        <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${highlight ? 'bg-amber-200 text-amber-900' : 'bg-[#F4F7EE] text-[#55715C]'}`}>
          <Icon name={icon} size={20} />
        </div>
      </div>
      <p className="mt-2 truncate text-xs text-clinical-muted">{detail}</p>
    </div>
  )
}

function FormField({ name, label, value, onChange, error, type = 'text', maxLength, required = false, placeholder, hint, adornment }) {
  return (
    <div className="min-w-0">
      <label htmlFor={`staff-${name}`} className="block text-sm font-semibold text-clinical-ink">
        {label}{required && <span className="ml-1 text-rose-600">*</span>}
      </label>
      <div className="relative">
        <input
          id={`staff-${name}`}
          name={name}
          type={type}
          value={value}
          onChange={onChange}
          maxLength={maxLength}
          placeholder={placeholder}
          autoComplete={name === 'password' ? 'new-password' : 'off'}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `staff-${name}-error` : hint ? `staff-${name}-hint` : undefined}
          className={`${fieldClass} ${adornment ? 'pr-20' : ''}`}
        />
        {adornment && <div className="absolute inset-y-0 right-2 flex items-center pt-1">{adornment}</div>}
      </div>
      {error ? (
        <p id={`staff-${name}-error`} className="mt-1.5 text-xs font-medium text-rose-700">{error}</p>
      ) : hint ? (
        <p id={`staff-${name}-hint`} className="mt-1.5 text-xs text-clinical-muted">{hint}</p>
      ) : null}
    </div>
  )
}

function DetailRow({ label, value }) {
  return (
    <div className="min-w-0 border-b border-clinical-border py-3 last:border-b-0">
      <dt className="mb-1 text-xs font-medium text-clinical-muted">{label}</dt>
      <dd className="break-words text-sm font-semibold text-clinical-ink">{value === undefined ? 'API chưa cung cấp' : (value || 'Chưa cập nhật')}</dd>
    </div>
  )
}

export default function StaffManagement({ currentAccountId, onSessionExpired }) {
  const [staff, setStaff] = useState([])
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [actionError, setActionError] = useState('')
  const [message, setMessage] = useState('')
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [page, setPage] = useState(1)
  const [panel, setPanel] = useState(null) // null | {kind:'create'} | {kind:'detail', accountId}
  const [form, setForm] = useState(() => ({ ...EMPTY_FORM, roles: [] }))
  const [formErrors, setFormErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)
  const [creating, setCreating] = useState(false)
  const [selectedRole, setSelectedRole] = useState('')
  const [pending, setPending] = useState(null)
  const [busy, setBusy] = useState(false)
  const drawerRef = useRef(null)
  const confirmRef = useRef(null)
  const onExpiredRef = useRef(onSessionExpired)
  const busyRef = useRef(false)
  const latestRequestRef = useRef(0)

  useEffect(() => { onExpiredRef.current = onSessionExpired }, [onSessionExpired])
  useEffect(() => { busyRef.current = busy || creating }, [busy, creating])

  const load = useCallback(async () => {
    const requestId = ++latestRequestRef.current
    setLoading(true)
    setListError('')
    try {
      const data = await listStaff(getToken())
      if (!Array.isArray(data)) {
        throw new Error('Danh sách nhân viên không đúng định dạng. Cần API trả về một mảng nhân viên.')
      }
      if (requestId === latestRequestRef.current) setStaff(data)
      return true
    } catch (failure) {
      if (requestId === latestRequestRef.current) {
        if (failure?.status === 401) onExpiredRef.current?.()
        else setListError(messageOf(failure, 'Không thể tải danh sách nhân viên.'))
      }
      return false
    } finally {
      if (requestId === latestRequestRef.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
    return () => { latestRequestRef.current += 1 }
  }, [load])

  const stats = useMemo(() => ({
    total: staff.length,
    active: staff.filter((person) => person.status === 'ACTIVE').length,
    locked: staff.filter((person) => person.status === 'LOCKED').length,
    doctors: staff.filter((person) => getRoles(person).includes('DOCTOR')).length,
  }), [staff])

  const filteredStaff = useMemo(() => {
    const query = searchable(search.trim())
    return staff.filter((person) => {
      if (statusFilter !== 'ALL' && person.status !== statusFilter) return false
      if (roleFilter !== 'ALL' && !getRoles(person).includes(roleFilter)) return false
      if (!query) return true
      const text = searchable([
        person.fullName, person.staffCode, person.loginName, person.phone, person.email,
      ].join(' '))
      return text.includes(query)
    }).sort((first, second) => nameOf(first).localeCompare(nameOf(second), 'vi'))
  }, [staff, search, roleFilter, statusFilter])

  const totalPages = Math.max(1, Math.ceil(filteredStaff.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const visibleStaff = filteredStaff.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)
  const showingFrom = filteredStaff.length ? (currentPage - 1) * PAGE_SIZE + 1 : 0
  const showingTo = Math.min(currentPage * PAGE_SIZE, filteredStaff.length)
  const hasFilters = search !== '' || roleFilter !== 'ALL' || statusFilter !== 'ALL'
  const selectedStaff = panel?.kind === 'detail'
    ? staff.find((person) => String(person.accountId) === String(panel.accountId))
    : null
  const pendingStaff = pending
    ? staff.find((person) => String(person.accountId) === String(pending.accountId))
    : null
  const panelIsOpen = Boolean(panel)
  const confirmIsOpen = Boolean(pending)

  useEffect(() => { setPage(1) }, [search, roleFilter, statusFilter])
  useEffect(() => { setPage((previous) => Math.min(previous, totalPages)) }, [totalPages])

  // Focus/keyboard management cho drawer va modal xac nhan.
  useEffect(() => {
    if (!panelIsOpen && !confirmIsOpen) return undefined
    const previouslyFocused = document.activeElement
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const dialog = confirmIsOpen ? confirmRef.current : drawerRef.current
    const focusables = () => Array.from(dialog?.querySelectorAll(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ) || []).filter((element) => element.getClientRects().length > 0)
    const first = focusables()[0]
    first?.focus()

    function handleKeys(event) {
      if (event.key === 'Escape') {
        if (busyRef.current) return
        event.preventDefault()
        if (confirmIsOpen) setPending(null)
        else setPanel(null)
      }
      if (event.key !== 'Tab') return
      const nodes = focusables()
      if (!nodes.length) return
      const firstNode = nodes[0]
      const lastNode = nodes[nodes.length - 1]
      if (event.shiftKey && document.activeElement === firstNode) {
        event.preventDefault()
        lastNode.focus()
      } else if (!event.shiftKey && document.activeElement === lastNode) {
        event.preventDefault()
        firstNode.focus()
      }
    }

    document.addEventListener('keydown', handleKeys)
    return () => {
      document.removeEventListener('keydown', handleKeys)
      document.body.style.overflow = oldOverflow
      if (previouslyFocused instanceof HTMLElement && previouslyFocused.isConnected) previouslyFocused.focus()
    }
  }, [panelIsOpen, panel?.kind, panel?.accountId, confirmIsOpen])

  function isProtected(person) {
    return (currentAccountId != null && String(person.accountId) === String(currentAccountId))
      || getRoles(person).includes('SYSTEM_ADMIN')
  }

  function openCreate() {
    setForm({ ...EMPTY_FORM, roles: [] })
    setFormErrors({})
    setShowPassword(false)
    setActionError('')
    setPanel({ kind: 'create' })
  }

  function openDetails(person) {
    setSelectedRole('')
    setActionError('')
    setPanel({ kind: 'detail', accountId: person.accountId })
  }

  function closePanel() {
    if (busy || creating) return
    setPanel(null)
    setForm({ ...EMPTY_FORM, roles: [] })
    setFormErrors({})
    setActionError('')
    setShowPassword(false)
  }

  function clearFilters() {
    setSearch('')
    setRoleFilter('ALL')
    setStatusFilter('ALL')
  }

  function change(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    setFormErrors((current) => ({ ...current, [name]: undefined }))
  }

  function toggleRole(code) {
    setForm((current) => ({
      ...current,
      roles: current.roles.includes(code)
        ? current.roles.filter((role) => role !== code)
        : [...current.roles, code],
    }))
    setFormErrors((current) => ({ ...current, roles: undefined }))
  }

  function validate() {
    const errors = {}
    if (!/^[A-Za-z0-9._@-]{3,100}$/.test(form.loginName.trim())) {
      errors.loginName = 'Tên đăng nhập từ 3–100 ký tự, không dấu hoặc khoảng trắng.'
    }
    if (form.password.length < 12 || form.password.length > 72) {
      errors.password = 'Mật khẩu phải có từ 12 đến 72 ký tự.'
    }
    if (!form.fullName.trim() || form.fullName.trim().length > 150) {
      errors.fullName = 'Nhập họ và tên, tối đa 150 ký tự.'
    }
    if (!form.roles.length) errors.roles = 'Chọn ít nhất một vai trò.'
    if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      errors.email = 'Email không đúng định dạng.'
    }
    for (const [name, max] of Object.entries({
      phone: 20, email: 254, professionalLicense: 100, qualification: 150,
    })) {
      if (form[name].trim().length > max) errors[name] = `Tối đa ${max} ký tự.`
    }
    return errors
  }

  async function submitCreate(event) {
    event.preventDefault()
    setActionError('')
    setMessage('')
    const errors = validate()
    setFormErrors(errors)
    if (Object.keys(errors).length) return
    setCreating(true)
    try {
      await createStaff(getToken(), {
        loginName: form.loginName.trim(),
        password: form.password, // Khong trim password: giu nguyen gia tri nguoi dung nhap.
        fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        professionalLicense: form.professionalLicense.trim() || null,
        qualification: form.qualification.trim() || null,
        roles: [...form.roles],
      })
      setPanel(null)
      setForm({ ...EMPTY_FORM, roles: [] })
      setShowPassword(false)
      setMessage('Đã tạo tài khoản nhân viên và cấp vai trò thành công.')
      await load()
    } catch (failure) {
      if (failure?.status === 401) onExpiredRef.current?.()
      else {
        setActionError(messageOf(failure, 'Không thể tạo nhân viên.'))
        setFormErrors(failure?.fieldErrors && typeof failure.fieldErrors === 'object' ? failure.fieldErrors : {})
      }
    } finally {
      setCreating(false)
    }
  }

  async function confirmAction() {
    if (!pending || busy) return
    const target = staff.find((person) => String(person.accountId) === String(pending.accountId))
    if (!target || isProtected(target)) {
      setPending(null)
      setActionError('Không thể thay đổi tài khoản này. Hãy tải lại danh sách để kiểm tra quyền thao tác.')
      return
    }
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      const token = getToken()
      if (pending.type === 'grant') await grantStaffRole(token, pending.accountId, pending.roleCode)
      else if (pending.type === 'revoke') await revokeStaffRole(token, pending.accountId, pending.roleCode)
      else if (pending.type === 'status') await setStaffStatus(token, pending.accountId, pending.status)
      else throw new Error('Thao tác không hợp lệ.')
      if (pending.type === 'grant') setSelectedRole('')
      setPending(null)
      setMessage('Đã cập nhật tài khoản nhân viên thành công.')
      await load()
    } catch (failure) {
      setPending(null)
      if (failure?.status === 401) onExpiredRef.current?.()
      else setActionError(messageOf(failure, 'Không thể cập nhật tài khoản nhân viên.'))
    } finally {
      setBusy(false)
    }
  }

  const confirmDescription = pending
    ? pending.type === 'grant'
      ? `Cấp vai trò ${ROLE_LABELS[pending.roleCode] || pending.roleCode} cho ${nameOf(pendingStaff)}?`
      : pending.type === 'revoke'
        ? `Thu hồi vai trò ${ROLE_LABELS[pending.roleCode] || pending.roleCode} của ${nameOf(pendingStaff)}?`
        : pending.status === 'LOCKED'
          ? `Khóa tài khoản của ${nameOf(pendingStaff)}? Nhân viên sẽ không thể đăng nhập bằng tài khoản này.`
          : `Mở khóa tài khoản của ${nameOf(pendingStaff)}?`
    : ''

  return (
    <section aria-labelledby="staff-title" className="min-w-0 space-y-6 pb-10 text-clinical-ink">
      {/* Page heading */}
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-suncare-700">Quản trị hệ thống / Nhân sự</p>
          <h1 id="staff-title" className="mt-2 text-2xl font-extrabold tracking-tight sm:text-3xl">Quản lý nhân viên</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-clinical-muted">
            Theo dõi tài khoản nhân viên, phân quyền và quản lý trạng thái truy cập hệ thống.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => void load()} disabled={loading} className={outlineButton}>
            <Icon name="refresh" size={17} /> Tải lại
          </button>
          <button type="button" onClick={openCreate} className={primaryButton}>
            <Icon name="plus" size={18} /> Thêm nhân viên
          </button>
        </div>
      </header>

      {/* Summary */}
      <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        <StatCard label="Tổng nhân viên" value={stats.total} icon="users" detail="Tất cả tài khoản nhân viên" highlight />
        <StatCard label="Đang hoạt động" value={stats.active} icon="check" detail="Có quyền đăng nhập" />
        <StatCard label="Đã khóa" value={stats.locked} icon="lock" detail="Không thể đăng nhập" />
        <StatCard label="Bác sĩ" value={stats.doctors} icon="doctor" detail="Có vai trò bác sĩ" />
      </div>

      {message && (
        <div role="status" className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800">
          <Icon name="check" size={18} /> <span>{message}</span>
        </div>
      )}
      {listError && (
        <div role="alert" className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
          <Icon name="info" size={18} /> <span>{listError}</span>
        </div>
      )}

      {/* Full staff directory */}
      <section aria-labelledby="staff-list-title" className="min-w-0 overflow-hidden rounded-2xl border border-clinical-border bg-white shadow-[0_10px_34px_rgba(50,70,54,0.04)]">
        <div className="border-b border-clinical-border px-4 py-5 sm:px-6">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 id="staff-list-title" className="text-lg font-extrabold">Danh sách nhân viên</h2>
              <p className="mt-1 text-sm text-clinical-muted">Tra cứu và quản lý tất cả tài khoản nhân sự.</p>
            </div>
            <span className="rounded-full bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-900">
              {filteredStaff.length} kết quả
            </span>
          </div>
          <div className="mt-5 grid gap-3 lg:grid-cols-[minmax(0,1fr)_190px_165px_auto]">
            <label className="relative min-w-0">
              <span className="sr-only">Tìm kiếm nhân viên</span>
              <Icon name="search" size={18} className="pointer-events-none absolute left-3.5 top-3.5 text-slate-400" />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Tìm tên, mã nhân viên, tài khoản..."
                className="min-h-11 w-full min-w-0 rounded-xl border border-clinical-border-strong bg-white py-2.5 pl-10 pr-3 text-sm outline-none focus:border-amber-500 focus:ring-4 focus:ring-amber-100"
              />
            </label>
            <label className="min-w-0">
              <span className="sr-only">Lọc theo vai trò</span>
              <select
                value={roleFilter}
                onChange={(event) => setRoleFilter(event.target.value)}
                className="min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 text-sm outline-none focus:border-amber-500 focus:ring-4 focus:ring-amber-100"
              >
                {ROLE_FILTERS.map((role) => <option key={role.value} value={role.value}>{role.label}</option>)}
              </select>
            </label>
            <label className="min-w-0">
              <span className="sr-only">Lọc theo trạng thái</span>
              <select
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
                className="min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 text-sm outline-none focus:border-amber-500 focus:ring-4 focus:ring-amber-100"
              >
                <option value="ALL">Mọi trạng thái</option>
                <option value="ACTIVE">Hoạt động</option>
                <option value="LOCKED">Đã khóa</option>
              </select>
            </label>
            <button type="button" onClick={clearFilters} disabled={!hasFilters} className={`${outlineButton} px-3.5`}>
              <Icon name="filter" size={17} /> Đặt lại
            </button>
          </div>
        </div>

        {loading ? (
          <div role="status" className="space-y-3 p-5 sm:p-6" aria-label="Đang tải danh sách nhân viên">
            {[0, 1, 2, 3].map((index) => (
              <div key={index} className="flex animate-pulse items-center gap-4 rounded-xl bg-slate-50 p-3">
                <div className="h-11 w-11 rounded-xl bg-slate-200" />
                <div className="flex-1 space-y-2"><div className="h-3 w-1/3 rounded bg-slate-200" /><div className="h-3 w-1/2 rounded bg-slate-100" /></div>
              </div>
            ))}
            <p className="text-sm text-clinical-muted">Đang tải danh sách nhân viên...</p>
          </div>
        ) : filteredStaff.length === 0 ? (
          <div className="flex flex-col items-center px-6 py-16 text-center">
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-100 text-amber-900">
              <Icon name={hasFilters ? 'search' : 'users'} size={27} />
            </div>
            <h3 className="text-base font-bold">{hasFilters ? 'Không tìm thấy nhân viên phù hợp' : 'Chưa có nhân viên'}</h3>
            <p className="mt-2 max-w-sm text-sm leading-6 text-clinical-muted">
              {hasFilters ? 'Thử từ khóa khác hoặc đặt lại bộ lọc để xem toàn bộ nhân viên.' : 'Tạo tài khoản đầu tiên để bắt đầu quản lý nhân sự.'}
            </p>
            <button type="button" className={`${outlineButton} mt-5`} onClick={hasFilters ? clearFilters : openCreate}>
              {hasFilters ? 'Xóa bộ lọc' : 'Thêm nhân viên'}
            </button>
          </div>
        ) : (
          <>
            {/* Desktop table: thay vi moi nguoi la mot form khong lo */}
            <div className="hidden overflow-x-auto lg:block">
              <table className="w-full min-w-[850px] text-left text-sm">
                <thead className="bg-[#FBFAF6] text-xs font-bold uppercase tracking-wider text-slate-500">
                  <tr>
                    <th scope="col" className="px-6 py-4">Nhân viên</th>
                    <th scope="col" className="px-5 py-4">Tài khoản</th>
                    <th scope="col" className="px-5 py-4">Vai trò</th>
                    <th scope="col" className="px-5 py-4">Trạng thái</th>
                    <th scope="col" className="px-6 py-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-clinical-border">
                  {visibleStaff.map((person) => (
                    <tr key={person.accountId} className="transition hover:bg-[#FFFCF5]">
                      <td className="px-6 py-4">
                        <div className="flex min-w-0 items-center gap-3">
                          <Avatar person={person} />
                          <div className="min-w-0">
                            <p className="max-w-[220px] truncate font-bold" title={nameOf(person)}>{nameOf(person)}</p>
                            <p className="mt-1 text-xs text-clinical-muted">{person.staffCode || 'Chưa có mã'}</p>
                          </div>
                        </div>
                      </td>
                      <td className="max-w-[190px] px-5 py-4">
                        <span className="block truncate font-medium" title={person.loginName || ''}>{person.loginName || '—'}</span>
                      </td>
                      <td className="max-w-[300px] px-5 py-4"><RoleList roles={getRoles(person)} /></td>
                      <td className="whitespace-nowrap px-5 py-4"><StatusBadge status={person.status} /></td>
                      <td className="px-6 py-4 text-right">
                        <button type="button" onClick={() => openDetails(person)} className="inline-flex min-h-10 items-center gap-1 rounded-lg border border-clinical-border-strong bg-white px-3 py-2 text-xs font-bold transition hover:border-amber-300 hover:bg-amber-50 focus-visible:ring-4 focus-visible:ring-amber-100">
                          Quản lý <Icon name="chevronRight" size={15} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Mobile cards */}
            <div className="divide-y divide-clinical-border lg:hidden">
              {visibleStaff.map((person) => (
                <article key={person.accountId} className="space-y-3 px-4 py-4 sm:px-6">
                  <div className="flex items-start gap-3">
                    <Avatar person={person} />
                    <div className="min-w-0 flex-1">
                      <h3 className="break-words font-bold">{nameOf(person)}</h3>
                      <p className="mt-0.5 break-all text-xs text-clinical-muted">{person.staffCode || 'Chưa có mã'} · {person.loginName || '—'}</p>
                    </div>
                    <button type="button" onClick={() => openDetails(person)} aria-label={`Quản lý ${nameOf(person)}`} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-clinical-border-strong text-clinical-ink hover:bg-amber-50">
                      <Icon name="chevronRight" />
                    </button>
                  </div>
                  <div className="flex flex-wrap items-center gap-2"><StatusBadge status={person.status} /><RoleList roles={getRoles(person)} /></div>
                </article>
              ))}
            </div>
          </>
        )}

        {!loading && filteredStaff.length > 0 && (
          <footer className="flex flex-col items-start justify-between gap-3 border-t border-clinical-border px-4 py-4 sm:flex-row sm:items-center sm:px-6">
            <p aria-live="polite" className="text-sm text-clinical-muted">
              Hiển thị <strong className="text-clinical-ink">{showingFrom}–{showingTo}</strong> trong <strong className="text-clinical-ink">{filteredStaff.length}</strong> nhân viên
            </p>
            <nav aria-label="Phân trang nhân viên" className="flex items-center gap-2">
              <button type="button" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={currentPage === 1} aria-label="Trang trước" className={`${outlineButton} min-w-11 px-3`}><Icon name="chevronLeft" size={17} /></button>
              <span className="min-w-24 text-center text-sm font-semibold tabular-nums">{currentPage} / {totalPages}</span>
              <button type="button" onClick={() => setPage((value) => Math.min(totalPages, value + 1))} disabled={currentPage === totalPages} aria-label="Trang sau" className={`${outlineButton} min-w-11 px-3`}><Icon name="chevronRight" size={17} /></button>
            </nav>
          </footer>
        )}
      </section>

      {/* Shared side drawer: create or account details */}
      {panel && (
        <div className="fixed inset-0 z-[60] flex" role="presentation">
          <button type="button" aria-label="Đóng bảng điều khiển" onClick={closePanel} className="absolute inset-0 h-full w-full cursor-default bg-slate-950/45 backdrop-blur-[2px]" />
          <aside
            ref={drawerRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="staff-panel-title"
            className="relative ml-auto flex h-full w-full max-w-[570px] flex-col overflow-hidden border-l border-amber-100 bg-[#FFFCF7] shadow-2xl"
          >
            <div className="flex items-start justify-between gap-4 border-b border-clinical-border bg-white px-5 py-5 sm:px-7">
              <div className="min-w-0">
                <p className="text-xs font-bold uppercase tracking-[0.14em] text-suncare-700">Quản lý nhân sự</p>
                <h2 id="staff-panel-title" className="mt-1 text-xl font-extrabold sm:text-2xl">
                  {panel.kind === 'create' ? 'Thêm nhân viên mới' : 'Thông tin nhân viên'}
                </h2>
                <p className="mt-1 text-sm text-clinical-muted">
                  {panel.kind === 'create' ? 'Tạo tài khoản và thiết lập vai trò ban đầu.' : 'Xem hồ sơ, cập nhật vai trò và quyền truy cập.'}
                </p>
              </div>
              <button type="button" onClick={closePanel} disabled={busy || creating} aria-label="Đóng" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-clinical-border bg-white hover:bg-slate-50 disabled:opacity-50">
                <Icon name="close" size={20} />
              </button>
            </div>

            {panel.kind === 'create' ? (
              <form onSubmit={submitCreate} className="flex min-h-0 flex-1 flex-col" noValidate>
                <div className="min-h-0 flex-1 space-y-7 overflow-y-auto px-5 py-6 sm:px-7">
                  {actionError && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{actionError}</p>}
                  <div className="space-y-4">
                    <div>
                      <h3 className="font-extrabold">01. Thông tin đăng nhập</h3>
                      <p className="mt-1 text-xs text-clinical-muted">Bàn giao mật khẩu ban đầu qua kênh an toàn, không gửi công khai.</p>
                    </div>
                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField name="loginName" label="Tên đăng nhập" value={form.loginName} onChange={change} error={formErrors.loginName} maxLength={100} required placeholder="vd: nguyenvana" />
                      <FormField
                        name="password" label="Mật khẩu ban đầu" value={form.password} onChange={change}
                        error={formErrors.password} maxLength={72} required hint="Từ 12 đến 72 ký tự"
                        type={showPassword ? 'text' : 'password'}
                        adornment={<button type="button" className="rounded-md px-2 py-1 text-xs font-bold text-amber-900 hover:bg-amber-50" onClick={() => setShowPassword((value) => !value)}>{showPassword ? 'Ẩn' : 'Hiện'}</button>}
                      />
                    </div>
                  </div>

                  <div className="space-y-4 border-t border-clinical-border pt-6">
                    <h3 className="font-extrabold">02. Thông tin cá nhân</h3>
                    <FormField name="fullName" label="Họ và tên" value={form.fullName} onChange={change} error={formErrors.fullName} maxLength={150} required placeholder="Nguyễn Văn A" />
                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField name="phone" label="Số điện thoại" value={form.phone} onChange={change} error={formErrors.phone} maxLength={20} type="tel" placeholder="09xxxxxxxx" />
                      <FormField name="email" label="Email" value={form.email} onChange={change} error={formErrors.email} maxLength={254} type="email" placeholder="name@example.com" />
                    </div>
                  </div>

                  <div className="space-y-4 border-t border-clinical-border pt-6">
                    <h3 className="font-extrabold">03. Thông tin chuyên môn</h3>
                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField name="professionalLicense" label="Giấy phép hành nghề" value={form.professionalLicense} onChange={change} error={formErrors.professionalLicense} maxLength={100} placeholder="Số giấy phép (nếu có)" />
                      <FormField name="qualification" label="Trình độ chuyên môn" value={form.qualification} onChange={change} error={formErrors.qualification} maxLength={150} placeholder="VD: Bác sĩ CKI" />
                    </div>
                  </div>

                  <fieldset className="space-y-4 border-t border-clinical-border pt-6">
                    <legend className="pt-6 font-extrabold">04. Vai trò trong hệ thống <span className="text-rose-600">*</span></legend>
                    <p className="text-xs text-clinical-muted">Có thể chọn nhiều vai trò cho cùng một nhân viên.</p>
                    <div className="grid gap-3 sm:grid-cols-2">
                      {ROLE_OPTIONS.map((role) => {
                        const checked = form.roles.includes(role.code)
                        return (
                          <label key={role.code} className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3.5 transition ${checked ? 'border-amber-400 bg-amber-50' : 'border-clinical-border bg-white hover:border-amber-300'}`}>
                            <input type="checkbox" checked={checked} onChange={() => toggleRole(role.code)} className="mt-1 h-4 w-4 shrink-0 accent-amber-600" />
                            <span className="min-w-0">
                              <span className="block text-sm font-bold">{role.label}</span>
                              <span className="mt-1 block text-xs leading-5 text-clinical-muted">{role.description}</span>
                            </span>
                          </label>
                        )
                      })}
                    </div>
                    {formErrors.roles && <p role="alert" className="text-xs font-medium text-rose-700">{formErrors.roles}</p>}
                  </fieldset>
                </div>
                <div className="flex shrink-0 items-center justify-end gap-3 border-t border-clinical-border bg-white px-5 py-4 sm:px-7">
                  <button type="button" onClick={closePanel} disabled={creating} className={outlineButton}>Hủy</button>
                  <button type="submit" disabled={creating} className={primaryButton}>
                    <Icon name="plus" size={17} /> {creating ? 'Đang tạo...' : 'Tạo tài khoản'}
                  </button>
                </div>
              </form>
            ) : (
              <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-5 py-6 sm:px-7">
                {!selectedStaff ? (
                  <p className="rounded-xl bg-amber-50 p-4 text-sm">Nhân viên không còn trong danh sách. Hãy tải lại dữ liệu.</p>
                ) : (
                  <>
                    {actionError && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{actionError}</p>}
                    <div className="flex items-center gap-4 rounded-2xl border border-clinical-border bg-white p-4">
                      <Avatar person={selectedStaff} large />
                      <div className="min-w-0 flex-1">
                        <h3 className="break-words text-lg font-extrabold">{nameOf(selectedStaff)}</h3>
                        <p className="mt-1 break-all text-xs text-clinical-muted">{selectedStaff.staffCode || 'Chưa có mã nhân viên'}</p>
                        <div className="mt-2"><StatusBadge status={selectedStaff.status} /></div>
                      </div>
                    </div>

                    <div className="rounded-2xl border border-clinical-border bg-white p-5">
                      <h3 className="mb-3 font-extrabold">Thông tin tài khoản</h3>
                      <dl className="grid gap-x-5 sm:grid-cols-2">
                        <DetailRow label="Tên đăng nhập" value={selectedStaff.loginName} />
                        <DetailRow label="Mã nhân viên" value={selectedStaff.staffCode} />
                        <DetailRow label="Số điện thoại" value={selectedStaff.phone} />
                        <DetailRow label="Email" value={selectedStaff.email} />
                        <DetailRow label="Giấy phép hành nghề" value={selectedStaff.professionalLicense} />
                        <DetailRow label="Trình độ chuyên môn" value={selectedStaff.qualification} />
                      </dl>
                    </div>

                    {isProtected(selectedStaff) && (
                      <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                        <Icon name="shield" size={20} className="shrink-0" />
                        <p>Đây là tài khoản của bạn hoặc tài khoản quản trị hệ thống. Không thể đổi vai trò hay trạng thái tại màn hình này.</p>
                      </div>
                    )}

                    <div className="rounded-2xl border border-clinical-border bg-white p-5">
                      <h3 className="font-extrabold">Phân quyền</h3>
                      <p className="mt-1 text-xs text-clinical-muted">Vai trò quyết định những chức năng nhân viên được phép sử dụng.</p>
                      <div className="mt-4 space-y-2">
                        {getRoles(selectedStaff).length ? getRoles(selectedStaff).map((role) => (
                          <div key={role} className="flex min-w-0 items-center justify-between gap-3 rounded-xl border border-clinical-border bg-[#FFFCF7] px-3 py-2.5">
                            <span className="min-w-0 break-words text-sm font-semibold">{ROLE_LABELS[role] || role}</span>
                            {!isProtected(selectedStaff) && role !== 'SYSTEM_ADMIN' && (
                              <button type="button" onClick={() => setPending({ type: 'revoke', accountId: selectedStaff.accountId, roleCode: role })} className="shrink-0 rounded-lg px-2.5 py-2 text-xs font-bold text-rose-700 hover:bg-rose-50">Thu hồi</button>
                            )}
                          </div>
                        )) : <p className="text-sm text-clinical-muted">Tài khoản chưa được cấp vai trò nào.</p>}
                      </div>
                      {!isProtected(selectedStaff) && ROLE_OPTIONS.some((role) => !getRoles(selectedStaff).includes(role.code)) && (
                        <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                          <label className="min-w-0 flex-1">
                            <span className="sr-only">Chọn vai trò cần gán</span>
                            <select value={selectedRole} onChange={(event) => setSelectedRole(event.target.value)} className="min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 text-sm outline-none focus:border-amber-500 focus:ring-4 focus:ring-amber-100">
                              <option value="">Chọn vai trò muốn thêm</option>
                              {ROLE_OPTIONS.filter((role) => !getRoles(selectedStaff).includes(role.code)).map((role) => (
                                <option key={role.code} value={role.code}>{role.label}</option>
                              ))}
                            </select>
                          </label>
                          <button
                            type="button"
                            disabled={!selectedRole || getRoles(selectedStaff).includes(selectedRole)}
                            onClick={() => setPending({ type: 'grant', accountId: selectedStaff.accountId, roleCode: selectedRole })}
                            className={primaryButton}
                          >
                            <Icon name="plus" size={17} /> Gán vai trò
                          </button>
                        </div>
                      )}
                    </div>

                    <div className="rounded-2xl border border-clinical-border bg-white p-5">
                      <h3 className="font-extrabold">Trạng thái tài khoản</h3>
                      <p className="mt-1 text-sm leading-6 text-clinical-muted">{selectedStaff.status === 'ACTIVE'
                        ? 'Tài khoản đang hoạt động và có thể đăng nhập theo quyền được cấp.'
                        : 'Tài khoản hiện không ở trạng thái hoạt động.'}</p>
                      {!isProtected(selectedStaff) && ['ACTIVE', 'LOCKED'].includes(selectedStaff.status) && (
                        <button
                          type="button"
                          onClick={() => setPending({ type: 'status', accountId: selectedStaff.accountId, status: selectedStaff.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE' })}
                          className={`${outlineButton} mt-4 ${selectedStaff.status === 'ACTIVE' ? 'border-rose-200 text-rose-700 hover:bg-rose-50' : ''}`}
                        >
                          <Icon name={selectedStaff.status === 'ACTIVE' ? 'lock' : 'check'} size={17} />
                          {selectedStaff.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>
            )}
          </aside>
        </div>
      )}

      {/* Confirmation dialog: prevents accidental privilege/status changes */}
      {pending && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-slate-950/55" aria-hidden="true" />
          <div ref={confirmRef} role="alertdialog" aria-modal="true" aria-labelledby="staff-confirm-title" aria-describedby="staff-confirm-description" className="relative w-full max-w-md rounded-2xl border border-clinical-border bg-white p-6 shadow-2xl sm:p-7">
            <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-amber-100 text-amber-900"><Icon name="shield" size={23} /></div>
            <h2 id="staff-confirm-title" className="text-xl font-extrabold">Xác nhận thay đổi</h2>
            <p id="staff-confirm-description" className="mt-2 text-sm leading-6 text-clinical-muted">{confirmDescription}</p>
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <button type="button" disabled={busy} onClick={() => setPending(null)} className={outlineButton}>Hủy</button>
              <button type="button" disabled={busy} onClick={() => void confirmAction()} className={primaryButton}>{busy ? 'Đang xử lý...' : 'Xác nhận'}</button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
