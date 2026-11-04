import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faMagnifyingGlass, faRotateRight } from '@fortawesome/free-solid-svg-icons'
import { getToken, listStaff } from '../../services/accountApi'
import { getOrganization } from '../../services/organizationApi'
import {
  assignDepartment, assignHead, endDepartment, endHead, listAssignments, setStaffSpecialty,
} from '../../services/staffAssignmentApi'

const inputClass = 'min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700'
const emptyForm = { staffId: '', departmentId: '', specialtyId: '', validFrom: '', validTo: '', primary: true }
const tabs = [
  { key: 'departments', label: 'Phân khoa' },
  { key: 'heads', label: 'Trưởng khoa' },
  { key: 'specialties', label: 'Chuyên khoa' },
]
const roleLabels = {
  DOCTOR: 'Bác sĩ',
  LAB_STAFF: 'Nhân viên CLS',
  DEPARTMENT_HEAD: 'Quyền trưởng khoa',
  CASHIER: 'Thu ngân',
}
const localToUtc = (value) => value ? new Date(`${value}+07:00`).toISOString().slice(0, 23) : null
const utcTime = (value) => value ? new Date(`${value}Z`).getTime() : null
const displayDate = (value) => value
  ? new Date(`${value}Z`).toLocaleString('vi-VN', { timeZone: 'Asia/Saigon' })
  : 'Không thời hạn'
const isCurrentPeriod = (item, now) => utcTime(item.validFrom) <= now && (!item.validTo || utcTime(item.validTo) > now)
const periodState = (item, now) => {
  if (isCurrentPeriod(item, now)) return 'current'
  if (utcTime(item.validFrom) > now) return 'future'
  return 'ended'
}
const stateLabel = { current: 'Đang hiệu lực', future: 'Sắp bắt đầu', ended: 'Đã kết thúc' }
const stateClass = {
  current: 'border-clinical-leaf bg-clinical-leaf-soft text-clinical-success',
  future: 'border-suncare-200 bg-suncare-50 text-suncare-800',
  ended: 'border-clinical-border bg-clinical-canvas text-clinical-muted',
}
const suggestedEnd = (item) => {
  const startMs = utcTime(item.validFrom)
  const latestEnd = item.validTo ? utcTime(item.validTo) - 1000 : Infinity
  return new Date(Math.min(Math.max(Date.now(), startMs + 1000), latestEnd) + 7 * 60 * 60 * 1000).toISOString().slice(0, 19)
}

function withinDepartmentMembership(form, memberships) {
  if (!form.staffId || !form.departmentId || !form.validFrom) return false
  const department = memberships.find((item) => String(item.staffId) === form.staffId
    && String(item.departmentId) === form.departmentId
    && utcTime(item.validFrom) <= new Date(`${localToUtc(form.validFrom)}Z`).getTime()
    && (!item.validTo || (form.validTo && utcTime(item.validTo) >= new Date(`${localToUtc(form.validTo)}Z`).getTime())))
  return Boolean(department)
}

function AssignmentForm({ form, kind, staff, departments, specialties, assignments, busy, error, onChange, onKindChange, onSubmit }) {
  const selected = staff.find((person) => String(person.staffId) === form.staffId)
  const hasHeadRole = selected?.roles?.includes('DEPARTMENT_HEAD')
  const hasDoctorRole = selected?.roles?.includes('DOCTOR')
  const coversMembership = kind === 'head' && withinDepartmentMembership(form, assignments.departments)
  const disabled = busy || !form.staffId
    || (kind !== 'specialty' && (!form.departmentId || !form.validFrom))
    || (kind === 'specialty' && (!form.specialtyId || !hasDoctorRole))
    || (kind === 'head' && (!hasDoctorRole || !hasHeadRole || !coversMembership))
    || (kind !== 'specialty' && form.validTo && new Date(`${localToUtc(form.validTo)}Z`) <= new Date(`${localToUtc(form.validFrom)}Z`))

  return (
    <form onSubmit={onSubmit} className='rounded-2xl border border-clinical-border bg-white p-4 shadow-sm sm:p-5'>
      <div className='flex flex-wrap items-start justify-between gap-3'>
        <div>
          <h2 className='font-bold text-clinical-ink'>Tạo phân công</h2>
          <p className='mt-1 text-sm text-clinical-muted'>Chọn loại trước; các trường và điều kiện sẽ đổi theo nghiệp vụ.</p>
        </div>
        <div className='flex flex-wrap gap-1 rounded-xl bg-clinical-canvas p-1' aria-label='Loại phân công'>
          <button type='button' onClick={() => onKindChange('department')} aria-pressed={kind === 'department'} className={`min-h-10 rounded-lg px-3 text-sm font-semibold ${kind === 'department' ? 'bg-white text-clinical-ink shadow-sm' : 'text-clinical-muted'}`}>Phân khoa</button>
          <button type='button' onClick={() => onKindChange('head')} aria-pressed={kind === 'head'} className={`min-h-10 rounded-lg px-3 text-sm font-semibold ${kind === 'head' ? 'bg-white text-clinical-ink shadow-sm' : 'text-clinical-muted'}`}>Trưởng khoa</button>
          <button type='button' onClick={() => onKindChange('specialty')} aria-pressed={kind === 'specialty'} className={`min-h-10 rounded-lg px-3 text-sm font-semibold ${kind === 'specialty' ? 'bg-white text-clinical-ink shadow-sm' : 'text-clinical-muted'}`}>Chuyên khoa</button>
        </div>
      </div>

      {error && <p role='alert' className='mt-4 rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{error}</p>}

      <div className='mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4'>
        <label className='text-sm font-semibold'>Nhân viên
          <select required name='staffId' value={form.staffId} onChange={onChange} className={inputClass}>
            <option value=''>Chọn nhân viên</option>
            {staff.filter((person) => person.status === 'ACTIVE').map((person) => <option key={person.staffId} value={person.staffId}>{person.fullName} · {person.staffCode}</option>)}
          </select>
        </label>
        {kind === 'specialty' ? <label className='text-sm font-semibold'>Chuyên khoa
          <select required name='specialtyId' value={form.specialtyId} onChange={onChange} className={inputClass}>
            <option value=''>Chọn chuyên khoa</option>
            {specialties.filter((item) => item.isActive).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label> : <>
          <label className='text-sm font-semibold'>Khoa
            <select required name='departmentId' value={form.departmentId} onChange={onChange} className={inputClass}>
              <option value=''>Chọn khoa</option>
              {departments.filter((item) => item.isActive && (kind !== 'head' || item.departmentType === 'CLINICAL')).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <label className='text-sm font-semibold'>Bắt đầu · giờ Việt Nam
            <input required type='datetime-local' name='validFrom' value={form.validFrom} onChange={onChange} className={inputClass} />
          </label>
          <label className='text-sm font-semibold'>Kết thúc · tùy chọn
            <input type='datetime-local' name='validTo' value={form.validTo} onChange={onChange} className={inputClass} />
          </label>
          {kind === 'department' && <label className='flex min-h-11 items-center gap-2 self-end text-sm font-medium'>
            <input type='checkbox' name='primary' checked={form.primary} onChange={onChange} className='h-4 w-4 accent-suncare-700' /> Khoa chính
          </label>}
        </>}
      </div>

      {selected && <p className='mt-3 text-xs text-clinical-muted'>Vai trò tài khoản: {selected.roles.map((role) => roleLabels[role] || role).join(', ') || 'Chưa có'}</p>}
      {kind === 'head' && form.staffId && !hasDoctorRole && <p role='alert' className='mt-2 text-sm text-clinical-danger'>Trưởng khoa phải có vai trò bác sĩ. Hãy cấp role DOCTOR trước.</p>}
      {kind === 'head' && form.staffId && hasDoctorRole && !hasHeadRole && <p role='alert' className='mt-2 text-sm text-clinical-danger'>Bác sĩ cần có quyền trưởng khoa trước khi tạo phân công nhiệm kỳ.</p>}
      {kind === 'head' && form.staffId && hasDoctorRole && hasHeadRole && form.departmentId && !coversMembership && <p role='alert' className='mt-2 text-sm text-clinical-danger'>Nhiệm kỳ trưởng khoa phải nằm trọn trong phân khoa của nhân viên. Hãy tạo hoặc kiểm tra phân khoa trước.</p>}
      {kind === 'specialty' && form.staffId && !hasDoctorRole && <p role='alert' className='mt-2 text-sm text-clinical-danger'>Chỉ bác sĩ có role DOCTOR mới được gán chuyên khoa.</p>}

      <div className='mt-4 flex justify-end border-t border-clinical-border pt-4'>
        <button type='submit' disabled={disabled} className='min-h-11 rounded-xl bg-suncare-400 px-5 text-sm font-bold text-clinical-ink hover:bg-suncare-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-50'>
          {busy ? 'Đang lưu...' : 'Lưu phân công'}
        </button>
      </div>
    </form>
  )
}

export default function StaffAssignments({ onSessionExpired }) {
  const [staff, setStaff] = useState([])
  const [catalog, setCatalog] = useState({ departments: [], specialties: [] })
  const [assignments, setAssignments] = useState({ departments: [], specialties: [], heads: [] })
  const [form, setForm] = useState(emptyForm)
  const [kind, setKind] = useState('department')
  const [activeTab, setActiveTab] = useState('departments')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [search, setSearch] = useState('')
  const [staffFilter, setStaffFilter] = useState('')
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [now, setNow] = useState(0)
  const [ending, setEnding] = useState(null)
  const [endingAt, setEndingAt] = useState('')
  const [endingError, setEndingError] = useState('')
  const [isEndingClosing, setIsEndingClosing] = useState(false)
  const endingDialogRef = useRef(null)
  const endingOpenerRef = useRef(null)
  const endingTimerRef = useRef(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [people, organization, result] = await Promise.all([listStaff(getToken()), getOrganization(), listAssignments()])
      setStaff(people)
      setCatalog(organization)
      setAssignments(result)
    } catch (failure) {
      if (failure.status === 401) onSessionExpired?.()
      else setError(failure.message)
    } finally { setLoading(false) }
  }, [onSessionExpired])

  useEffect(() => { Promise.resolve().then(() => { void load() }) }, [load])
  useEffect(() => () => window.clearTimeout(endingTimerRef.current), [])
  useEffect(() => {
    const timer = window.setTimeout(() => setNow(Date.now()), 0)
    return () => window.clearTimeout(timer)
  }, [])

  const closeEnding = useCallback(() => {
    if (!ending || isEndingClosing) return
    setIsEndingClosing(true)
    const delay = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180
    endingTimerRef.current = window.setTimeout(() => {
      setEnding(null)
      setIsEndingClosing(false)
      endingOpenerRef.current?.focus()
    }, delay)
  }, [ending, isEndingClosing])

  useEffect(() => {
    if (!ending) return undefined
    endingDialogRef.current?.querySelector('input[name="endingAt"]')?.focus()
    function onKeyDown(event) {
      if (event.key === 'Escape' && !busy) closeEnding()
      if (event.key !== 'Tab') return
      const controls = [...endingDialogRef.current.querySelectorAll('button:not(:disabled), input:not(:disabled)')]
      if (!controls.length) return
      const first = controls[0]
      const last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [ending, busy, closeEnding])

  function changeForm(event) {
    const { name, value, type, checked } = event.target
    setError('')
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }))
  }

  async function run(action, success) {
    setBusy(true)
    setError('')
    setMessage('')
    try {
      setAssignments(await action())
      setMessage(success)
    } catch (failure) {
      if (failure.status === 401) onSessionExpired?.()
      else setError(failure.message)
    } finally { setBusy(false) }
  }

  function submit(event) {
    event.preventDefault()
    const staffId = Number(form.staffId)
    if (kind === 'specialty') {
      void run(() => setStaffSpecialty({ staffId, specialtyId: Number(form.specialtyId), active: true }), 'Đã gán chuyên khoa.')
      return
    }
    const validFrom = localToUtc(form.validFrom)
    const validTo = localToUtc(form.validTo)
    if (validTo && new Date(`${validTo}Z`) <= new Date(`${validFrom}Z`)) {
      setError('Thời điểm kết thúc phải sau thời điểm bắt đầu.')
      return
    }
    const data = { staffId, departmentId: Number(form.departmentId), validFrom, validTo }
    void run(() => kind === 'head' ? assignHead(data) : assignDepartment({ ...data, primary: form.primary }), 'Đã lưu phân công.')
  }

  function changeKind(value) {
    setKind(value)
    setError('')
    setForm(emptyForm)
  }

  const rows = useMemo(() => (assignments[activeTab] || []).filter((item) => {
    const query = search.trim().toLocaleLowerCase('vi')
    const subject = activeTab === 'specialties' ? item.specialtyName : item.departmentName
    const queryMatch = !query || `${item.staffName} ${item.staffCode || ''} ${subject || ''}`.toLocaleLowerCase('vi').includes(query)
    const staffMatch = !staffFilter || String(item.staffId) === staffFilter
    const departmentMatch = activeTab === 'specialties' || !departmentFilter || String(item.departmentId) === departmentFilter
    const state = activeTab === 'specialties' ? (item.isActive ? 'current' : 'ended') : periodState(item, now)
    const statusMatch = statusFilter === 'all' || state === statusFilter
    return queryMatch && staffMatch && departmentMatch && statusMatch
  }), [activeTab, assignments, departmentFilter, now, search, staffFilter, statusFilter])

  function setFilter(key, value) {
    if (key === 'staff') setStaffFilter(value)
    if (key === 'department') setDepartmentFilter(value)
    if (key === 'status') setStatusFilter(value)
  }

  function openEnding(type, item, event) {
    endingOpenerRef.current = event.currentTarget
    setEnding({ type, item })
    setEndingAt(suggestedEnd(item))
    setEndingError('')
    setIsEndingClosing(false)
  }

  async function submitEnding(event) {
    event.preventDefault()
    if (!ending || !endingAt) return
    const endUtc = localToUtc(endingAt)
    if (new Date(`${endUtc}Z`) <= new Date(`${ending.item.validFrom}Z`)) {
      setEndingError(`Thời điểm kết thúc phải sau khi bắt đầu (${displayDate(ending.item.validFrom)}).`)
      return
    }
    if (ending.item.validTo && new Date(`${endUtc}Z`) >= new Date(`${ending.item.validTo}Z`)) {
      setEndingError(`Thời điểm mới phải trước mốc kết thúc hiện tại (${displayDate(ending.item.validTo)}).`)
      return
    }
    setBusy(true)
    setEndingError('')
    setError('')
    try {
      const result = ending.type === 'head'
        ? await endHead(ending.item.id, endUtc)
        : await endDepartment(ending.item.id, endUtc)
      setAssignments(result)
      setMessage(ending.type === 'head' ? 'Đã kết thúc nhiệm kỳ trưởng khoa.' : 'Đã kết thúc phân khoa.')
      closeEnding()
    } catch (failure) {
      if (failure.status === 401) onSessionExpired?.()
      else setEndingError(failure.message)
    } finally { setBusy(false) }
  }

  const activeRows = assignments[activeTab] || []
  const isSpecialtyTab = activeTab === 'specialties'
  const visiblePeople = [...new Map(activeRows.map((item) => [item.staffId, { id: item.staffId, name: item.staffName, code: item.staffCode }])).values()]

  return (
    <section className='mx-auto w-full max-w-7xl space-y-5 px-4 py-6 sm:px-6 lg:px-8' aria-labelledby='assignment-title'>
      <header className='flex flex-wrap items-start justify-between gap-3'>
        <div>
          <p className='text-xs font-bold uppercase tracking-widest text-suncare-700'>Tổ chức nhân sự</p>
          <h1 id='assignment-title' className='mt-1 text-2xl font-bold text-clinical-ink sm:text-3xl'>Phân công nhân viên</h1>
          <p className='mt-1 max-w-3xl text-sm text-clinical-muted'>Quản lý khoa công tác, nhiệm kỳ trưởng khoa và chuyên khoa được giao. Khoảng thời gian lưu theo giờ Việt Nam.</p>
        </div>
        <button type='button' onClick={() => void load()} disabled={loading || busy} className='min-h-11 rounded-xl border border-clinical-border bg-white px-4 text-sm font-semibold text-clinical-ink hover:bg-suncare-50 disabled:opacity-50'>
          <FontAwesomeIcon icon={faRotateRight} className={loading ? 'animate-spin' : ''} /> <span className='ml-2'>Tải lại</span>
        </button>
      </header>

      {error && <p role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{error}</p>}
      {message && <p role='status' className='rounded-xl border border-clinical-leaf bg-clinical-leaf-soft p-3 text-sm text-clinical-success'>{message}</p>}
      <AssignmentForm form={form} kind={kind} staff={staff} departments={catalog.departments} specialties={catalog.specialties} assignments={assignments} busy={busy} error='' onChange={changeForm} onKindChange={changeKind} onSubmit={submit} />

      <section className='overflow-hidden rounded-2xl border border-clinical-border bg-white shadow-sm' aria-labelledby='history-title'>
        <div className='border-b border-clinical-border px-4 pt-4 sm:px-5'>
          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div>
              <h2 id='history-title' className='font-bold text-clinical-ink'>Lịch sử phân công</h2>
              <p className='mt-1 text-sm text-clinical-muted'>Tìm theo nhân viên, mã và khoa; lọc trạng thái để rà soát nhanh.</p>
            </div>
            <span className='text-xs text-clinical-muted'>{rows.length} / {activeRows.length} dòng</span>
          </div>
          <div role='tablist' aria-label='Loại lịch sử phân công' className='mt-4 flex gap-1 overflow-x-auto'>
            {tabs.map((tab) => <button key={tab.key} type='button' role='tab' aria-selected={activeTab === tab.key} onClick={() => { setActiveTab(tab.key); setStatusFilter('all') }} className={`min-h-11 shrink-0 border-b-2 px-4 text-sm font-semibold ${activeTab === tab.key ? 'border-suncare-700 text-suncare-700' : 'border-transparent text-clinical-muted hover:text-clinical-ink'}`}>
              {tab.label} <span className='ml-1 rounded-full bg-clinical-canvas px-2 py-0.5 text-xs'>{assignments[tab.key]?.length || 0}</span>
            </button>)}
          </div>
        </div>

        <div className='grid gap-3 border-b border-clinical-border bg-clinical-canvas/60 p-4 sm:grid-cols-2 xl:grid-cols-4'>
          <label className='text-xs font-semibold text-clinical-ink'>Tìm nhân viên / mã / {isSpecialtyTab ? 'chuyên khoa' : 'khoa'}
            <span className='relative mt-1 block'><FontAwesomeIcon icon={faMagnifyingGlass} className='absolute left-3 top-3.5 text-clinical-muted' /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder='Nhập từ khóa' className={`${inputClass} pl-9`} /></span>
          </label>
          <label className='text-xs font-semibold text-clinical-ink'>Nhân viên
            <select value={staffFilter} onChange={(event) => setFilter('staff', event.target.value)} className={`${inputClass} mt-1`}><option value=''>Tất cả nhân viên</option>{visiblePeople.map((person) => <option key={person.id} value={person.id}>{person.name} · {person.code}</option>)}</select>
          </label>
          {!isSpecialtyTab && <label className='text-xs font-semibold text-clinical-ink'>Khoa
            <select value={departmentFilter} onChange={(event) => setFilter('department', event.target.value)} className={`${inputClass} mt-1`}><option value=''>Tất cả khoa</option>{catalog.departments.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
          </label>}
          <label className='text-xs font-semibold text-clinical-ink'>Trạng thái
            <select value={statusFilter} onChange={(event) => setFilter('status', event.target.value)} className={`${inputClass} mt-1`}>
              <option value='all'>Tất cả</option>
              {isSpecialtyTab ? <><option value='current'>Đang hoạt động</option><option value='ended'>Đã ngừng</option></> : <><option value='current'>Đang hiệu lực</option><option value='future'>Sắp bắt đầu</option><option value='ended'>Đã kết thúc</option></>}
            </select>
          </label>
        </div>

        {loading ? <p role='status' className='p-6 text-sm text-clinical-muted'>Đang tải lịch sử phân công...</p> : activeRows.length === 0 ? <div className='p-8 text-center text-sm text-clinical-muted'>Chưa có lịch sử ở mục này.</div> : rows.length === 0 ? <div className='p-8 text-center text-sm text-clinical-muted'>Không có dòng nào khớp bộ lọc. Hãy đổi tiêu chí hoặc xóa bộ lọc.</div> : (
          <div className='overflow-x-auto'>
            <table className='w-full min-w-[54rem] text-left text-sm'>
              <thead className='bg-suncare-50 text-xs uppercase tracking-wide text-clinical-muted'><tr>
                <th className='px-4 py-3'>Nhân viên</th><th className='px-4 py-3'>{isSpecialtyTab ? 'Chuyên khoa' : 'Khoa'}</th><th className='px-4 py-3'>{isSpecialtyTab ? 'Trạng thái' : 'Hiệu lực'}</th>{activeTab === 'departments' && <th className='px-4 py-3'>Vai trò tại khoa</th>}<th className='px-4 py-3 text-right'>Thao tác</th>
              </tr></thead>
              <tbody className='divide-y divide-clinical-border'>
                {rows.map((item) => {
                  const state = isSpecialtyTab ? (item.isActive ? 'current' : 'ended') : periodState(item, now)
                  const canFinish = !isSpecialtyTab && state !== 'ended'
                  return <tr key={item.id ?? `${item.staffId}-${item.specialtyId}`} className='hover:bg-suncare-50/50'>
                    <td className='px-4 py-3'><div className='font-semibold text-clinical-ink'>{item.staffName}</div><div className='mt-0.5 font-mono text-xs text-clinical-muted'>{item.staffCode}</div></td>
                    <td className='px-4 py-3'>{isSpecialtyTab ? item.specialtyName : item.departmentName}</td>
                    <td className='px-4 py-3'>
                      {isSpecialtyTab ? <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${stateClass[state]}`}>{item.isActive ? 'Đang hoạt động' : 'Đã ngừng'}</span> : <><span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${stateClass[state]}`}>{stateLabel[state]}</span><div className='mt-1 text-xs text-clinical-muted'>{displayDate(item.validFrom)} → {displayDate(item.validTo)}</div></>}
                    </td>
                    {activeTab === 'departments' && <td className='px-4 py-3 text-clinical-muted'>{item.isPrimary ? 'Khoa chính' : 'Khoa phụ'}</td>}
                    <td className='px-4 py-3 text-right'>
                      {isSpecialtyTab ? <button type='button' disabled={busy} onClick={() => void run(() => setStaffSpecialty({ staffId: item.staffId, specialtyId: item.specialtyId, active: !item.isActive }), item.isActive ? 'Đã ngừng chuyên khoa.' : 'Đã bật lại chuyên khoa.')} className='min-h-10 rounded-lg border border-clinical-border px-3 font-semibold text-suncare-700 hover:bg-suncare-50 disabled:opacity-50'>{item.isActive ? 'Ngừng' : 'Bật lại'}</button> : canFinish && <button type='button' disabled={busy} onClick={(event) => openEnding(activeTab === 'heads' ? 'head' : 'department', item, event)} className='min-h-10 rounded-lg border border-clinical-border px-3 font-semibold text-suncare-700 hover:bg-suncare-50 disabled:opacity-50'>Kết thúc</button>}
                    </td>
                  </tr>
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {ending && <div className={`catalog-modal-backdrop fixed inset-0 z-50 flex items-center justify-center bg-clinical-ink/60 p-3 ${isEndingClosing ? 'catalog-modal-closing pointer-events-none' : ''}`} onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) closeEnding() }}>
        <form ref={endingDialogRef} role='dialog' aria-modal='true' aria-labelledby='end-assignment-title' onSubmit={submitEnding} className='catalog-modal-panel relative max-h-[90vh] w-full max-w-lg space-y-5 overflow-y-auto rounded-2xl border border-clinical-border bg-white p-5 shadow-2xl sm:p-6'>
          <div>
            <h2 id='end-assignment-title' className='text-lg font-bold'>Kết thúc {ending.type === 'head' ? 'nhiệm kỳ trưởng khoa' : 'phân khoa'}</h2>
            <p className='mt-1 text-sm text-clinical-muted'>{ending.item.staffName} · {ending.item.departmentName}</p>
            <p className='mt-1 text-xs text-clinical-muted'>Thời hạn hiện tại: {displayDate(ending.item.validFrom)} → {displayDate(ending.item.validTo)}</p>
          </div>
          {endingError && <p role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{endingError}</p>}
          <label className='block text-sm font-semibold'>Thời điểm kết thúc · giờ Việt Nam
            <input className={inputClass} required type='datetime-local' step='1' name='endingAt' value={endingAt} onChange={(event) => setEndingAt(event.target.value)} />
          </label>
          <div className='flex justify-end gap-2 border-t border-clinical-border pt-4'>
            <button type='button' disabled={busy} onClick={closeEnding} className='min-h-10 rounded-xl border border-clinical-border px-4 text-sm font-semibold'>Hủy</button>
            <button type='submit' disabled={busy} className='min-h-10 rounded-xl bg-suncare-700 px-4 text-sm font-bold text-white disabled:opacity-50'>{busy ? 'Đang lưu...' : 'Xác nhận kết thúc'}</button>
          </div>
        </form>
      </div>}
    </section>
  )
}
