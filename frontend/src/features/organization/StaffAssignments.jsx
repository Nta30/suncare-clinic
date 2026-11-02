import { useCallback, useEffect, useState } from 'react'
import { getToken, listStaff } from '../../services/accountApi'
import { getOrganization } from '../../services/organizationApi'
import {
  assignDepartment, assignHead, endDepartment, endHead, listAssignments, setStaffSpecialty,
} from '../../services/staffAssignmentApi'

const field = 'min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm'
const empty = { staffId: '', departmentId: '', specialtyId: '', validFrom: '', validTo: '', primary: true }
const utc = (value) => value ? new Date(`${value}+07:00`).toISOString().slice(0, 23) : null
const localNow = () => new Date(Date.now() + 7 * 60 * 60 * 1000).toISOString().slice(0, 16)
const displayDate = (value) => value ? new Date(`${value}Z`).toLocaleString('vi-VN', { timeZone: 'Asia/Saigon' }) : 'Không giới hạn'
const canEnd = (value) => !value || new Date(`${value}Z`).getTime() > Date.now()

export default function StaffAssignments({ onSessionExpired }) {
  const [staff, setStaff] = useState([])
  const [catalog, setCatalog] = useState({ departments: [], specialties: [] })
  const [assignments, setAssignments] = useState({ departments: [], specialties: [], heads: [] })
  const [form, setForm] = useState(empty)
  const [kind, setKind] = useState('department')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

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

  function change(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function run(action, success) {
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const result = await action()
      setAssignments(result)
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
    const data = { staffId, departmentId: Number(form.departmentId), validFrom: utc(form.validFrom), validTo: utc(form.validTo) }
    void run(() => kind === 'head' ? assignHead(data) : assignDepartment({ ...data, primary: form.primary }), 'Đã lưu phân công.')
  }

  function finish(type, item) {
    const answer = window.prompt('Thời điểm kết thúc giờ Việt Nam (YYYY-MM-DDTHH:mm)', localNow())
    if (!answer) return
    void run(() => type === 'head' ? endHead(item.id, utc(answer)) : endDepartment(item.id, utc(answer)), 'Đã kết thúc phân công.')
  }

  const selected = staff.find((person) => String(person.staffId) === form.staffId)

  return (
    <section className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8 sm:px-8">
      <div>
        <h1 className="text-2xl font-extrabold">Phân công nhân viên</h1>
        <p className="mt-1 text-sm text-clinical-muted">Quản lý khoa, chuyên khoa và nhiệm kỳ trưởng khoa. Thời điểm nhập theo giờ Việt Nam.</p>
      </div>
      {error && <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-800">{error}</p>}
      {message && <p role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-800">{message}</p>}
      {loading ? <p role="status">Đang tải phân công...</p> : <>
        <form onSubmit={submit} className="space-y-4 rounded-2xl border border-clinical-border bg-white p-5">
          <h2 className="font-bold">Thêm phân công</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <label className="text-sm font-semibold">Loại phân công
              <select className={field} value={kind} onChange={(event) => setKind(event.target.value)}>
                <option value="department">Phân khoa</option><option value="specialty">Chuyên khoa</option><option value="head">Trưởng khoa</option>
              </select>
            </label>
            <label className="text-sm font-semibold">Nhân viên
              <select className={field} required value={form.staffId} onChange={change} name="staffId">
                <option value="">Chọn nhân viên</option>
                {staff.filter((person) => person.status === 'ACTIVE').map((person) =>
                  <option key={person.staffId} value={person.staffId}>{person.fullName} ({person.staffCode})</option>)}
              </select>
            </label>
            {kind === 'specialty' ? <label className="text-sm font-semibold">Chuyên khoa
              <select className={field} required value={form.specialtyId} onChange={change} name="specialtyId">
                <option value="">Chọn chuyên khoa</option>
                {catalog.specialties.filter((item) => item.isActive).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
              </select>
            </label> : <>
              <label className="text-sm font-semibold">Khoa
                <select className={field} required value={form.departmentId} onChange={change} name="departmentId">
                  <option value="">Chọn khoa</option>
                  {catalog.departments.filter((item) => item.isActive && (kind !== 'head' || item.departmentType === 'CLINICAL')).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                </select>
              </label>
              <label className="text-sm font-semibold">Bắt đầu (giờ Việt Nam)
                <input className={field} required type="datetime-local" value={form.validFrom} onChange={change} name="validFrom" />
              </label>
              <label className="text-sm font-semibold">Kết thúc (giờ Việt Nam, tùy chọn)
                <input className={field} type="datetime-local" value={form.validTo} onChange={change} name="validTo" />
              </label>
              {kind === 'department' && <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.primary} onChange={(event) => setForm((current) => ({ ...current, primary: event.target.checked }))} /> Khoa chính</label>}
            </>}
          </div>
          {selected && <p className="text-xs text-clinical-muted">Vai trò: {selected.roles.join(', ') || 'Chưa có'}</p>}
          <button disabled={busy} className="rounded-xl bg-suncare-400 px-5 py-2.5 text-sm font-bold disabled:opacity-50">{busy ? 'Đang lưu...' : 'Lưu phân công'}</button>
        </form>
        {[['departments', 'Lịch sử phân khoa', 'department'], ['specialties', 'Chuyên khoa', 'specialty'], ['heads', 'Nhiệm kỳ trưởng khoa', 'head']].map(([key, title, type]) =>
          <section key={key} className="rounded-2xl border border-clinical-border bg-white p-5">
            <h2 className="font-bold">{title}</h2>
            {!assignments[key]?.length ? <p className="mt-3 text-sm text-clinical-muted">Chưa có dữ liệu.</p> :
              <div className="mt-3 divide-y divide-clinical-border">{assignments[key].map((item) =>
                <div key={item.id ?? `${item.staffId}-${item.specialtyId}`} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                  <div><strong>{item.staffName}</strong> · {item.departmentName || item.specialtyName}
                    <p className="text-xs text-clinical-muted">{type === 'specialty' ? (item.isActive ? 'Đang hoạt động' : 'Đã ngừng') : `${displayDate(item.validFrom)} → ${displayDate(item.validTo)}`}</p>
                  </div>
                  {type === 'specialty' ? <button disabled={busy} onClick={() => void run(() => setStaffSpecialty({ staffId: item.staffId, specialtyId: item.specialtyId, active: !item.isActive }), 'Đã cập nhật chuyên khoa.')} className="rounded-lg border px-3 py-2">{item.isActive ? 'Ngừng' : 'Bật lại'}</button> :
                    canEnd(item.validTo) && <button disabled={busy} onClick={() => finish(type, item)} className="rounded-lg border px-3 py-2">Kết thúc</button>}
                </div>)}</div>}
          </section>)}
      </>}
    </section>
  )
}
