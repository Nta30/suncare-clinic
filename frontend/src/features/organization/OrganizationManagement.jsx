import { useCallback, useEffect, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowRotateRight, faPlus } from '@fortawesome/free-solid-svg-icons'
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
    <form onSubmit={submit} noValidate className='rounded-2xl border border-suncare-200 bg-suncare-50 p-4 sm:p-5'>
      <h3 className='text-lg font-bold'>{item ? 'Chỉnh sửa' : 'Thêm'} {isDepartment ? 'khoa' : 'chuyên khoa'}</h3>
      <p className='mt-1 text-sm text-clinical-muted'>Mã cần duy nhất; ngừng hoạt động để giữ lịch sử đã sử dụng.</p>
      {error && <p role='alert' className='mt-3 rounded-lg border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{error}</p>}
      <div className='mt-4 grid gap-4 sm:grid-cols-2'>
        <label className='block text-sm font-semibold'>Mã <span className='text-clinical-danger'>*</span>
          <input name='code' value={form.code} onChange={change} maxLength={40} className={inputClass} aria-invalid={Boolean(fieldErrors.code)} />
          {fieldErrors.code && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.code}</span>}
        </label>
        <label className='block text-sm font-semibold'>Tên <span className='text-clinical-danger'>*</span>
          <input name='name' value={form.name} onChange={change} maxLength={150} className={inputClass} aria-invalid={Boolean(fieldErrors.name)} />
          {fieldErrors.name && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.name}</span>}
        </label>
        {isDepartment && <label className='block text-sm font-semibold'>Loại khoa
          <select name='departmentType' value={form.departmentType} onChange={change} className={inputClass}>
            <option value='CLINICAL'>Khoa khám</option>
            <option value='LAB'>Khoa cận lâm sàng</option>
          </select>
        </label>}
        <label className='flex min-h-11 items-center gap-3 self-end rounded-xl border border-clinical-border bg-white px-3 py-2 text-sm font-semibold'>
          <input name='isActive' type='checkbox' checked={form.isActive} onChange={change} className='h-4 w-4 accent-suncare-700' /> Đang hoạt động
        </label>
      </div>
      <div className='mt-5 flex flex-wrap justify-end gap-2'>
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
  const [busy, setBusy] = useState(false)
  const [linkChoices, setLinkChoices] = useState({})

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
    if (await run(command, 'Đã lưu danh mục thành công.')) setEditing(null)
  }

  function openForm(kind, item = null) {
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
          <button key={kind} type='button' aria-pressed={tab === kind} onClick={() => { setTab(kind); setEditing(null); setActionError('') }}
            className={`min-h-11 rounded-xl px-4 text-sm font-semibold ${tab === kind ? 'bg-suncare-400 text-clinical-ink' : 'bg-white text-clinical-muted hover:bg-suncare-50'}`}>
            {label} ({kind === 'department' ? catalog.departments.length : catalog.specialties.length})
          </button>
        ))}
      </div>
      {editing && <CatalogForm key={`${editing.kind}-${editing.item?.id || 'new'}`} kind={editing.kind} item={editing.item} busy={busy} error={actionError} fieldErrors={fieldErrors} onCancel={() => { setEditing(null); setActionError('') }} onSubmit={(form) => void save(form)} />}
      <div className='flex justify-end'><Button variant='primary' onClick={() => openForm(tab)}><FontAwesomeIcon icon={faPlus} aria-hidden='true' /> Thêm {tab === 'department' ? 'khoa' : 'chuyên khoa'}</Button></div>
      {loading ? <p role='status' className='rounded-xl border border-clinical-border bg-white p-6'>Đang tải danh mục...</p> :
        tab === 'department' ? (
          <div className='space-y-3'>
            {catalog.departments.length === 0 && <p className='rounded-xl border border-clinical-border bg-white p-6 text-sm text-clinical-muted'>Chưa có khoa. Chọn “Thêm khoa” để bắt đầu.</p>}
            {catalog.departments.map((department) => {
              const links = catalog.links.filter((link) => link.departmentId === department.id)
              const available = catalog.specialties.filter((specialty) => specialty.isActive && !links.some((link) => link.specialtyId === specialty.id && link.isActive))
              return (
                <article key={department.id} className='rounded-2xl border border-clinical-border bg-white p-4 sm:p-5'>
                  <div className='flex flex-wrap items-start justify-between gap-3'>
                    <div><div className='flex flex-wrap items-center gap-2'><h2 className='font-bold'>{department.name}</h2><CatalogStatus active={department.isActive} /></div>
                      <p className='mt-1 text-xs text-clinical-muted'>{department.code} · {department.departmentType === 'CLINICAL' ? 'Khoa khám' : 'Khoa cận lâm sàng'}</p></div>
                    <Button onClick={() => openForm('department', department)}>Chỉnh sửa</Button>
                  </div>
                  {department.departmentType === 'CLINICAL' && <div className='mt-4 border-t border-clinical-border pt-4'>
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
                  </div>}
                </article>
              )
            })}
          </div>
        ) : (
          <div className='grid gap-3 sm:grid-cols-2'>
            {catalog.specialties.length === 0 && <p className='rounded-xl border border-clinical-border bg-white p-6 text-sm text-clinical-muted sm:col-span-2'>Chưa có chuyên khoa. Chọn “Thêm chuyên khoa” để bắt đầu.</p>}
            {catalog.specialties.map((specialty) => <article key={specialty.id} className='rounded-2xl border border-clinical-border bg-white p-4'>
              <div className='flex flex-wrap items-center gap-2'><h2 className='font-bold'>{specialty.name}</h2><CatalogStatus active={specialty.isActive} /></div>
              <p className='mt-1 text-xs text-clinical-muted'>{specialty.code}</p>
              <Button className='mt-4' onClick={() => openForm('specialty', specialty)}>Chỉnh sửa</Button>
            </article>)}
          </div>
        )}
    </section>
  )
}
