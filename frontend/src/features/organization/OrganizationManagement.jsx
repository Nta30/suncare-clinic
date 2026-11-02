import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowRotateRight, faPlus, faXmark } from '@fortawesome/free-solid-svg-icons'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import {
  createDepartment, createSpecialty, getOrganization, setDepartmentSpecialty,
  updateDepartment, updateSpecialty,
} from '../../services/organizationApi'

const emptyForm = { code: '', name: '', departmentType: 'CLINICAL', isActive: true }
const inputClass = 'mt-1.5 min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline-suncare-700'

function CatalogForm({ kind, item, busy, error, fieldErrors, onCancel, onSubmit }) {
  const [form, setForm] = useState(() => item ? { ...item } : { ...emptyForm })
  const isDepartment = kind === 'department'

  function change(event) {
    const { name, value, type, checked } = event.target
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }))
  }

  function submit(event) {
    event.preventDefault()
    onSubmit(form)
  }

  return (
    <form onSubmit={submit} noValidate>
      <div className='border-b border-clinical-border px-5 pb-4 pt-5 sm:px-6'>
        <p className='text-xs font-bold uppercase tracking-widest text-suncare-700'>Danh mục tổ chức</p>
        <h3 className='mt-1 text-xl font-bold text-clinical-ink'>{item ? 'Chỉnh sửa' : 'Thêm'} {isDepartment ? 'khoa' : 'chuyên khoa'}</h3>
        <p className='mt-1 text-sm text-clinical-muted'>Thông tin được dùng khi phân công nhân viên và đặt lịch.</p>
      </div>
      <div className='space-y-5 px-5 py-5 sm:px-6'>
      {error && <p role='alert' className='rounded-lg border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{error}</p>}
      <div className='grid gap-4 sm:grid-cols-2'>
        <label className='block text-sm font-semibold'>Mã <span className='text-clinical-danger'>*</span>
          <input name='code' value={form.code} onChange={change} maxLength={40} className={inputClass} aria-invalid={Boolean(fieldErrors.code)} />
          {fieldErrors.code && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.code}</span>}
        </label>
        <label className='block text-sm font-semibold sm:col-span-2'>Tên <span className='text-clinical-danger'>*</span>
          <input name='name' value={form.name} onChange={change} maxLength={150} className={inputClass} aria-invalid={Boolean(fieldErrors.name)} />
          {fieldErrors.name && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.name}</span>}
        </label>
        {isDepartment && <label className='block text-sm font-semibold'>Loại khoa
          <select name='departmentType' value={form.departmentType} onChange={change} className={inputClass}>
            <option value='CLINICAL'>Khoa khám</option>
            <option value='LAB'>Khoa cận lâm sàng</option>
          </select>
        </label>}
      </div>
      <label className='flex min-h-11 items-center gap-3 rounded-xl border border-clinical-border bg-clinical-canvas px-3 py-2 text-sm font-semibold'>
        <input name='isActive' type='checkbox' checked={form.isActive} onChange={change} className='h-4 w-4 accent-suncare-700' /> Đang hoạt động
      </label>
      <p className='text-xs text-clinical-muted'>Ngừng hoạt động để giữ lịch sử đã sử dụng. Mã phải duy nhất trong hệ thống.</p>
      </div>
      <div className='flex flex-wrap justify-end gap-2 border-t border-clinical-border bg-clinical-canvas px-5 py-4 sm:px-6'>
        <Button disabled={busy} onClick={onCancel}>Hủy</Button>
        <Button type='submit' variant='primary' disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu'}</Button>
      </div>
    </form>
  )
}

function CatalogStatus({ active }) {
  return <Badge tone={active ? 'success' : 'neutral'}>{active ? 'Hoạt động' : 'Ngừng hoạt động'}</Badge>
}

export default function OrganizationManagement({ onSessionExpired }) {
  const [catalog, setCatalog] = useState({ departments: [], specialties: [], links: [] })
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [actionError, setActionError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [message, setMessage] = useState('')
  const [tab, setTab] = useState('department')
  const [editing, setEditing] = useState(null)
  const [isClosing, setIsClosing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [linkChoices, setLinkChoices] = useState({})
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [expandedDepartmentId, setExpandedDepartmentId] = useState(null)
  const dialogRef = useRef(null)
  const openerRef = useRef(null)
  const closeTimerRef = useRef(null)

  const closeForm = useCallback(() => {
    if (!editing || isClosing) return
    setIsClosing(true)
    setActionError('')
    const delay = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180
    closeTimerRef.current = window.setTimeout(() => {
      setEditing(null)
      setIsClosing(false)
      openerRef.current?.focus()
    }, delay)
  }, [editing, isClosing])

  useEffect(() => () => window.clearTimeout(closeTimerRef.current), [])

  useEffect(() => {
    if (editing) dialogRef.current?.querySelector('input[name="code"]')?.focus()
  }, [editing])

  useEffect(() => {
    if (!editing) return undefined
    function onKeyDown(event) {
      if (event.key === 'Escape' && !busy) {
        closeForm()
      }
      if (event.key !== 'Tab') return
      const controls = [...dialogRef.current.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled)')]
      if (!controls.length) return
      const first = controls[0]
      const last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [editing, busy, isClosing, closeForm])

  const items = (tab === 'department' ? catalog.departments : catalog.specialties).filter((item) => {
    const query = search.trim().toLocaleLowerCase('vi')
    return (!query || `${item.code} ${item.name}`.toLocaleLowerCase('vi').includes(query))
      && (statusFilter === 'all' || item.isActive === (statusFilter === 'active'))
  })

  const load = useCallback(async () => {
    setLoading(true)
    setListError('')
    try {
      setCatalog(await getOrganization())
    } catch (error) {
      if (error.status === 401) onSessionExpired?.()
      else setListError(error.message)
    } finally {
      setLoading(false)
    }
  }, [onSessionExpired])

  useEffect(() => {
    let active = true
    Promise.resolve().then(() => { if (active) void load() })
    return () => { active = false }
  }, [load])

  async function run(command, success) {
    if (busy) return false
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      setCatalog(await command())
      setMessage(success)
      return true
    } catch (error) {
      if (error.status === 401) onSessionExpired?.()
      else {
        setActionError(error.status === 409 ? `${error.message} Tải lại danh mục nếu dữ liệu đã đổi.` : error.message)
        setFieldErrors(error.fieldErrors || {})
      }
      return false
    } finally {
      setBusy(false)
    }
  }

  async function save(form) {
    const errors = {}
    if (!/^[A-Za-z0-9_-]{1,40}$/.test(form.code.trim())) errors.code = 'Mã 1–40 ký tự, chỉ gồm chữ, số, _ hoặc -.'
    if (!form.name.trim() || form.name.trim().length > 150) errors.name = 'Nhập tên, tối đa 150 ký tự.'
    setFieldErrors(errors)
    if (Object.keys(errors).length) return
    const data = { code: form.code.trim().toUpperCase(), name: form.name.trim(), isActive: form.isActive }
    if (editing.kind === 'department') data.departmentType = form.departmentType
    if (editing.item) data.version = editing.item.version
    const command = editing.kind === 'department'
      ? editing.item ? () => updateDepartment(editing.item.id, data) : () => createDepartment(data)
      : editing.item ? () => updateSpecialty(editing.item.id, data) : () => createSpecialty(data)
    if (await run(command, 'Đã lưu danh mục thành công.')) {
      closeForm()
    }
  }

  function openForm(kind, item = null) {
    window.clearTimeout(closeTimerRef.current)
    openerRef.current = document.activeElement
    setIsClosing(false)
    setTab(kind)
    setEditing({ kind, item })
    setFieldErrors({})
    setActionError('')
  }

  async function changeLink(departmentId, specialtyId, active) {
    if (!specialtyId) return
    const done = await run(
      () => setDepartmentSpecialty(departmentId, specialtyId, active),
      active ? 'Đã liên kết chuyên khoa.' : 'Đã ngừng liên kết chuyên khoa.',
    )
    if (done) setLinkChoices((current) => ({ ...current, [departmentId]: '' }))
  }

  return (
    <section aria-labelledby='organization-title' className='space-y-5 pb-10'>
      <header className='flex flex-wrap items-start justify-between gap-3'>
        <div>
          <p className='text-xs font-bold uppercase tracking-widest text-suncare-700'>Quản trị hệ thống / Danh mục</p>
          <h1 id='organization-title' className='mt-2 text-2xl font-bold sm:text-3xl'>Khoa và chuyên khoa</h1>
          <p className='mt-2 text-sm text-clinical-muted'>Quản lý danh mục và liên kết sử dụng trong phân công, đặt lịch.</p>
        </div>
        <Button onClick={() => void load()} disabled={loading || busy}><FontAwesomeIcon icon={faArrowRotateRight} aria-hidden='true' /> Tải lại</Button>
      </header>
      {message && <p role='status' className='rounded-xl border border-clinical-leaf bg-clinical-leaf-soft p-3 text-sm text-clinical-success'>{message}</p>}
      {listError && <p role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{listError} <button type='button' onClick={() => void load()} className='font-bold underline'>Thử lại</button></p>}
      {actionError && !editing && <p role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{actionError}</p>}
      <div aria-label='Loại danh mục' className='flex gap-2 border-b border-clinical-border pb-2'>
        {[['department', 'Khoa'], ['specialty', 'Chuyên khoa']].map(([kind, label]) => (
          <button key={kind} type='button' aria-pressed={tab === kind} onClick={() => { setTab(kind); closeForm() }}
            className={`min-h-11 rounded-xl px-4 text-sm font-semibold ${tab === kind ? 'bg-suncare-400 text-clinical-ink' : 'bg-white text-clinical-muted hover:bg-suncare-50'}`}>
            {label} ({kind === 'department' ? catalog.departments.length : catalog.specialties.length})
          </button>
        ))}
      </div>
      <div className='flex justify-end'><Button variant='primary' onClick={() => openForm(tab)}><FontAwesomeIcon icon={faPlus} aria-hidden='true' /> Thêm {tab === 'department' ? 'khoa' : 'chuyên khoa'}</Button></div>
      <div className='flex flex-wrap gap-3 rounded-xl border border-clinical-border bg-white p-3'>
        <label className='min-w-48 flex-1 text-sm font-semibold'>Tìm theo mã hoặc tên
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder='Nhập mã hoặc tên' className={inputClass} />
        </label>
        <label className='min-w-40 text-sm font-semibold'>Trạng thái
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className={inputClass}>
            <option value='all'>Tất cả</option><option value='active'>Hoạt động</option><option value='inactive'>Ngừng hoạt động</option>
          </select>
        </label>
      </div>
      {loading ? <p role='status' className='rounded-xl border border-clinical-border bg-white p-6'>Đang tải danh mục...</p> :
        items.length === 0 ? <p className='rounded-xl border border-clinical-border bg-white p-6 text-sm text-clinical-muted'>{search || statusFilter !== 'all' ? 'Không có mục nào khớp bộ lọc.' : `Chưa có ${tab === 'department' ? 'khoa' : 'chuyên khoa'}. Chọn nút Thêm để bắt đầu.`}</p> :
        <div className='overflow-x-auto rounded-xl border border-clinical-border bg-white'>
          <table className='w-full min-w-[42rem] border-collapse text-left text-sm'>
            <thead className='bg-suncare-50 text-clinical-muted'><tr>
              <th scope='col' className='px-4 py-3'>Mã</th><th scope='col' className='px-4 py-3'>Tên {tab === 'department' ? 'khoa' : 'chuyên khoa'}</th>
              {tab === 'department' && <th scope='col' className='px-4 py-3'>Loại khoa</th>}
              <th scope='col' className='px-4 py-3'>Trạng thái</th><th scope='col' className='px-4 py-3 text-right'>Thao tác</th>
            </tr></thead>
            <tbody className='divide-y divide-clinical-border'>
            {tab === 'department' ? items.map((department) => {
              const links = catalog.links.filter((link) => link.departmentId === department.id)
              const available = catalog.specialties.filter((specialty) => specialty.isActive && !links.some((link) => link.specialtyId === specialty.id && link.isActive))
              const expanded = expandedDepartmentId === department.id
              return (
                <Fragment key={department.id}>
                  <tr className='hover:bg-suncare-50/50'>
                    <td className='px-4 py-3 font-mono text-xs'>{department.code}</td>
                    <td className='px-4 py-3 font-semibold'>{department.name}</td>
                    <td className='px-4 py-3'>{department.departmentType === 'CLINICAL' ? 'Khoa khám' : 'Khoa cận lâm sàng'}</td>
                    <td className='px-4 py-3'><CatalogStatus active={department.isActive} /></td>
                    <td className='space-x-3 whitespace-nowrap px-4 py-3 text-right'>
                      {department.departmentType === 'CLINICAL' && <button type='button' aria-expanded={expanded} onClick={() => setExpandedDepartmentId(expanded ? null : department.id)} className='font-semibold text-suncare-700 underline'>Liên kết ({links.filter((link) => link.isActive).length})</button>}
                      <button type='button' onClick={() => openForm('department', department)} className='font-semibold text-suncare-700 underline'>Chỉnh sửa</button>
                    </td>
                  </tr>
                  {expanded && <tr><td colSpan={5} className='bg-clinical-canvas px-4 py-4'>
                    <h3 className='text-sm font-bold'>Chuyên khoa liên kết</h3>
                    <div className='mt-2 flex flex-wrap gap-2'>{links.filter((link) => link.isActive).map((link) => {
                      const specialty = catalog.specialties.find((item) => item.id === link.specialtyId)
                      return <span key={link.specialtyId} className='inline-flex items-center gap-2 rounded-lg border border-suncare-200 bg-suncare-50 px-2 py-1 text-sm'>{specialty?.name || link.specialtyId}
                        <button type='button' disabled={busy} onClick={() => void changeLink(department.id, link.specialtyId, false)} className='min-h-9 font-semibold text-suncare-700 underline disabled:opacity-50' aria-label={`Ngừng liên kết ${specialty?.name || link.specialtyId}`}>Ngừng</button></span>
                    })}{links.every((link) => !link.isActive) && <p className='text-sm text-clinical-muted'>Chưa có liên kết đang hoạt động.</p>}</div>
                    {department.isActive && available.length > 0 && <div className='mt-3 flex flex-wrap gap-2'>
                      <label className='min-w-[12rem] flex-1 text-sm'><span className='sr-only'>Chọn chuyên khoa liên kết với {department.name}</span>
                        <select value={linkChoices[department.id] || ''} onChange={(event) => setLinkChoices((current) => ({ ...current, [department.id]: event.target.value }))} className={inputClass}>
                          <option value=''>Chọn chuyên khoa</option>{available.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                        </select></label>
                      <Button disabled={busy || !linkChoices[department.id]} onClick={() => void changeLink(department.id, Number(linkChoices[department.id]), true)}>Liên kết</Button>
                    </div>}
                  </td></tr>}
                </Fragment>
              )
            }) : items.map((specialty) => <tr key={specialty.id} className='hover:bg-suncare-50/50'>
              <td className='px-4 py-3 font-mono text-xs'>{specialty.code}</td><td className='px-4 py-3 font-semibold'>{specialty.name}</td>
              <td className='px-4 py-3'><CatalogStatus active={specialty.isActive} /></td>
              <td className='px-4 py-3 text-right'><button type='button' onClick={() => openForm('specialty', specialty)} className='font-semibold text-suncare-700 underline'>Chỉnh sửa</button></td>
            </tr>)}
            </tbody>
          </table>
        </div>}
      {editing && <div className={`catalog-modal-backdrop fixed inset-0 z-50 flex items-center justify-center bg-clinical-ink/60 p-3 ${isClosing ? 'catalog-modal-closing pointer-events-none' : ''}`} onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) closeForm() }}>
        <div ref={dialogRef} role='dialog' aria-modal='true' aria-label={`${editing.item ? 'Chỉnh sửa' : 'Thêm'} ${editing.kind === 'department' ? 'khoa' : 'chuyên khoa'}`} className='catalog-modal-panel relative max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl border border-clinical-border bg-white shadow-2xl'>
          <button type='button' aria-label='Đóng hộp thoại' disabled={busy} onClick={closeForm} className='absolute right-4 top-4 rounded-lg p-2 text-clinical-muted hover:bg-suncare-50 focus-visible:outline-suncare-700'><FontAwesomeIcon icon={faXmark} /></button>
          <CatalogForm key={`${editing.kind}-${editing.item?.id || 'new'}`} kind={editing.kind} item={editing.item} busy={busy} error={actionError} fieldErrors={fieldErrors} onCancel={closeForm} onSubmit={(form) => void save(form)} />
        </div>
      </div>}
    </section>
  )
}
