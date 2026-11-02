import { useCallback, useEffect, useMemo, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowRotateRight, faPlus } from '@fortawesome/free-solid-svg-icons'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import { getOrganization } from '../../services/organizationApi'
import { createRoom, getRooms, updateRoom } from '../../services/roomApi'

const emptyForm = { code: '', name: '', roomType: 'CONSULTATION', departmentId: '', isActive: true }
const inputClass = 'mt-1.5 min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline-suncare-700 disabled:bg-suncare-50 disabled:text-clinical-muted'
const roomTypes = { CONSULTATION: 'Phòng khám', LAB: 'Phòng cận lâm sàng' }

function RoomForm({ item, departments, busy, error, fieldErrors, onCancel, onSubmit }) {
  const [form, setForm] = useState(() => item ? { ...item } : { ...emptyForm })
  const expectedType = form.roomType === 'CONSULTATION' ? 'CLINICAL' : 'LAB'
  const choices = departments.filter((department) => department.isActive && department.departmentType === expectedType)

  function change(event) {
    const { name, value, type, checked } = event.target
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value,
      ...(name === 'roomType' ? { departmentId: '' } : {}) }))
  }

  return (
    <form onSubmit={(event) => { event.preventDefault(); onSubmit(form) }} noValidate className='rounded-2xl border border-suncare-200 bg-suncare-50 p-4 sm:p-5'>
      <h2 className='text-lg font-bold'>{item ? 'Chỉnh sửa phòng' : 'Thêm phòng'}</h2>
      {item && <p className='mt-1 text-sm text-clinical-muted'>Khoa và loại phòng được giữ nguyên để bảo toàn lịch sử sử dụng.</p>}
      {error && <p role='alert' className='mt-3 rounded-lg border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{error}</p>}
      <div className='mt-4 grid gap-4 sm:grid-cols-2'>
        <label className='block text-sm font-semibold'>Mã phòng <span className='text-clinical-danger'>*</span>
          <input name='code' value={form.code} onChange={change} maxLength={40} aria-invalid={Boolean(fieldErrors.code)} className={inputClass} />
          {fieldErrors.code && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.code}</span>}
        </label>
        <label className='block text-sm font-semibold'>Tên phòng <span className='text-clinical-danger'>*</span>
          <input name='name' value={form.name} onChange={change} maxLength={150} aria-invalid={Boolean(fieldErrors.name)} className={inputClass} />
          {fieldErrors.name && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.name}</span>}
        </label>
        <label className='block text-sm font-semibold'>Loại phòng
          <select name='roomType' value={form.roomType} onChange={change} disabled={Boolean(item)} className={inputClass}>
            <option value='CONSULTATION'>Phòng khám</option>
            <option value='LAB'>Phòng cận lâm sàng</option>
          </select>
        </label>
        <label className='block text-sm font-semibold'>Khoa phụ trách <span className='text-clinical-danger'>*</span>
          <select name='departmentId' value={form.departmentId} onChange={change} disabled={Boolean(item)} aria-invalid={Boolean(fieldErrors.departmentId)} className={inputClass}>
            <option value=''>Chọn khoa phù hợp</option>
            {item && !choices.some((department) => department.id === item.departmentId) &&
              <option value={item.departmentId}>{item.departmentName} (ngừng hoạt động)</option>}
            {choices.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
          </select>
          {fieldErrors.departmentId && <span className='mt-1 block text-xs text-clinical-danger'>{fieldErrors.departmentId}</span>}
        </label>
        <label className='flex min-h-11 items-center gap-3 self-end rounded-xl border border-clinical-border bg-white px-3 py-2 text-sm font-semibold'>
          <input name='isActive' type='checkbox' checked={form.isActive} onChange={change} className='h-4 w-4 accent-suncare-700' /> Đang hoạt động
        </label>
      </div>
      {!item && choices.length === 0 && <p className='mt-3 text-sm text-clinical-warning'>Chưa có khoa đang hoạt động phù hợp với loại phòng này. Hãy tạo khoa trước.</p>}
      <div className='mt-5 flex flex-wrap justify-end gap-2'>
        <Button disabled={busy} onClick={onCancel}>Hủy</Button>
        <Button type='submit' variant='primary' disabled={busy || (!item && choices.length === 0)}>{busy ? 'Đang lưu...' : 'Lưu phòng'}</Button>
      </div>
    </form>
  )
}

export default function RoomManagement({ onSessionExpired }) {
  const [rooms, setRooms] = useState([])
  const [departments, setDepartments] = useState([])
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [actionError, setActionError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [message, setMessage] = useState('')
  const [editing, setEditing] = useState(null)
  const [busy, setBusy] = useState(false)
  const [departmentFilter, setDepartmentFilter] = useState('ALL')
  const [typeFilter, setTypeFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const load = useCallback(async () => {
    setLoading(true)
    setListError('')
    try {
      const [roomList, catalog] = await Promise.all([getRooms(), getOrganization()])
      setRooms(roomList)
      setDepartments(catalog.departments)
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

  const filtered = useMemo(() => rooms.filter((room) =>
    (departmentFilter === 'ALL' || room.departmentId === Number(departmentFilter))
    && (typeFilter === 'ALL' || room.roomType === typeFilter)
    && (statusFilter === 'ALL' || room.isActive === (statusFilter === 'ACTIVE')),
  ), [rooms, departmentFilter, typeFilter, statusFilter])

  function openForm(item = null) {
    setEditing({ item })
    setFieldErrors({})
    setActionError('')
  }

  async function save(form) {
    if (busy) return
    const errors = {}
    if (!/^[A-Za-z0-9_-]{1,40}$/.test(form.code.trim())) errors.code = 'Mã 1–40 ký tự, chỉ gồm chữ, số, _ hoặc -.'
    if (!form.name.trim() || form.name.trim().length > 150) errors.name = 'Nhập tên phòng, tối đa 150 ký tự.'
    if (!Number(form.departmentId)) errors.departmentId = 'Chọn khoa phụ trách.'
    setFieldErrors(errors)
    if (Object.keys(errors).length) return
    const data = { code: form.code.trim().toUpperCase(), name: form.name.trim(), roomType: form.roomType,
      departmentId: Number(form.departmentId), isActive: form.isActive }
    if (editing.item) data.version = editing.item.version
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      setRooms(await (editing.item ? updateRoom(editing.item.id, data) : createRoom(data)))
      setEditing(null)
      setMessage('Đã lưu phòng thành công.')
    } catch (error) {
      if (error.status === 401) onSessionExpired?.()
      else {
        setActionError(error.status === 409 ? `${error.message} Hãy tải lại danh sách nếu dữ liệu đã đổi.` : error.message)
        setFieldErrors(error.fieldErrors || {})
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-labelledby='room-title' className='space-y-5 pb-10'>
      <header className='flex flex-wrap items-start justify-between gap-3'>
        <div><p className='text-xs font-bold uppercase tracking-widest text-suncare-700'>Quản trị hệ thống / Danh mục</p>
          <h1 id='room-title' className='mt-2 text-2xl font-bold sm:text-3xl'>Quản lý phòng</h1>
          <p className='mt-2 text-sm text-clinical-muted'>Phòng khám thuộc khoa khám; phòng CLS thuộc khoa cận lâm sàng.</p></div>
        <Button onClick={() => void load()} disabled={loading || busy}><FontAwesomeIcon icon={faArrowRotateRight} aria-hidden='true' /> Tải lại</Button>
      </header>
      {message && <p role='status' className='rounded-xl border border-clinical-leaf bg-clinical-leaf-soft p-3 text-sm text-clinical-success'>{message}</p>}
      {listError && <p role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{listError} <button type='button' onClick={() => void load()} className='font-bold underline'>Thử lại</button></p>}
      {actionError && !editing && <p role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{actionError}</p>}
      {editing && <RoomForm key={editing.item?.id || 'new'} item={editing.item} departments={departments} busy={busy} error={actionError} fieldErrors={fieldErrors} onCancel={() => setEditing(null)} onSubmit={(form) => void save(form)} />}
      <div className='flex justify-end'><Button variant='primary' onClick={() => openForm()}><FontAwesomeIcon icon={faPlus} aria-hidden='true' /> Thêm phòng</Button></div>
      <div className='grid gap-3 rounded-2xl border border-clinical-border bg-white p-4 sm:grid-cols-3'>
        <label className='text-sm font-semibold'>Khoa
          <select value={departmentFilter} onChange={(event) => setDepartmentFilter(event.target.value)} className={inputClass}>
            <option value='ALL'>Tất cả khoa</option>
            {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
          </select>
        </label>
        <label className='text-sm font-semibold'>Loại phòng
          <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)} className={inputClass}>
            <option value='ALL'>Mọi loại phòng</option><option value='CONSULTATION'>Phòng khám</option><option value='LAB'>Phòng CLS</option>
          </select>
        </label>
        <label className='text-sm font-semibold'>Trạng thái
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className={inputClass}>
            <option value='ALL'>Mọi trạng thái</option><option value='ACTIVE'>Hoạt động</option><option value='INACTIVE'>Ngừng hoạt động</option>
          </select>
        </label>
      </div>
      {loading ? <p role='status' className='rounded-xl border border-clinical-border bg-white p-6'>Đang tải danh sách phòng...</p> :
        filtered.length === 0 ? <p className='rounded-xl border border-clinical-border bg-white p-6 text-sm text-clinical-muted'>
          {rooms.length ? 'Không có phòng phù hợp bộ lọc.' : 'Chưa có phòng. Chọn “Thêm phòng” để bắt đầu.'}</p> :
          <div className='grid gap-3 sm:grid-cols-2'>
            {filtered.map((room) => <article key={room.id} className='rounded-2xl border border-clinical-border bg-white p-4 sm:p-5'>
              <div className='flex flex-wrap items-center gap-2'><h2 className='font-bold'>{room.name}</h2><Badge tone={room.isActive ? 'success' : 'neutral'}>{room.isActive ? 'Hoạt động' : 'Ngừng hoạt động'}</Badge></div>
              <p className='mt-1 text-sm text-clinical-muted'>{room.code} · {roomTypes[room.roomType] || room.roomType}</p>
              <p className='mt-2 text-sm'>Khoa: <strong>{room.departmentName}</strong>{!room.departmentActive && <span className='ml-2 text-clinical-warning'>(khoa ngừng hoạt động)</span>}</p>
              <Button className='mt-4' onClick={() => openForm(room)}>Chỉnh sửa</Button>
            </article>)}
          </div>}
    </section>
  )
}
