import { useEffect, useState } from 'react'
import { getPersonalProfile, getToken, updatePersonalProfile } from '../../services/accountApi'

const editableFields = ['fullName', 'birthDate', 'sex', 'phone', 'email', 'address']
const patientFields = ['identityNumber', 'emergencyName', 'emergencyPhone']
const labels = {
  fullName: 'Họ và tên', birthDate: 'Ngày sinh', sex: 'Giới tính',
  phone: 'Số điện thoại', email: 'Email', address: 'Địa chỉ',
  identityNumber: 'Số giấy tờ tùy thân', emergencyName: 'Người liên hệ khẩn cấp',
  emergencyPhone: 'Số điện thoại khẩn cấp',
  professionalLicense: 'Số giấy phép hành nghề', qualification: 'Trình độ chuyên môn',
  departments: 'Khoa công tác', specialties: 'Chuyên khoa',
}
const sexLabels = { UNKNOWN: 'Chưa xác định', MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' }
const limits = {
  fullName: 150, phone: 20, email: 254, address: 500,
  identityNumber: 30, emergencyName: 150, emergencyPhone: 20,
}

function toForm(profile) {
  return Object.fromEntries([...editableFields, ...patientFields, 'version']
    .map((name) => [name, profile[name] ?? '']))
}

function validate(form, isPatient) {
  const errors = {}
  const name = form.fullName.trim()
  if (!name || name.length > 150) errors.fullName = 'Nhập họ tên, tối đa 150 ký tự.'
  if (!['UNKNOWN', 'MALE', 'FEMALE', 'OTHER'].includes(form.sex)) errors.sex = 'Chọn giới tính hợp lệ.'
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Saigon' })
  if (form.birthDate && form.birthDate > today) errors.birthDate = 'Ngày sinh không được ở tương lai.'
  for (const field of isPatient ? [...editableFields, ...patientFields] : editableFields) {
    if (limits[field] && form[field].length > limits[field]) errors[field] = `Tối đa ${limits[field]} ký tự.`
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errors.email = 'Email không đúng định dạng.'
  return errors
}

export default function PersonalProfile({ account, onSessionExpired }) {
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [revision, setRevision] = useState(0)
  const [editing, setEditing] = useState(false)
  const isPatient = account.accountType === 'PATIENT'

  useEffect(() => {
    let active = true
    getPersonalProfile(getToken()).then((data) => {
      if (!active) return
      setProfile(data)
      setForm(toForm(data))
      setEditing(false)
      setError('')
    }).catch((failure) => {
      if (!active) return
      if (failure.status === 401) onSessionExpired()
      else setError(failure.message)
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setSuccess('')
    setFieldErrors({})
    setEditing(false)
    setRevision((value) => value + 1)
  }

  function change(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    setFieldErrors((current) => ({ ...current, [name]: undefined }))
    setSuccess('')
  }

  async function save(event) {
    event.preventDefault()
    setError('')
    setSuccess('')
    const errors = validate(form, isPatient)
    setFieldErrors(errors)
    if (Object.keys(errors).length) return
    const fields = isPatient ? [...editableFields, ...patientFields] : editableFields
    const payload = Object.fromEntries(fields.map((name) => [name,
      name === 'fullName' ? form[name].trim() : form[name] || null]))
    payload.version = form.version
    setSaving(true)
    try {
      const updated = await updatePersonalProfile(getToken(), payload)
      setProfile(updated)
      setForm(toForm(updated))
      setEditing(false)
      setSuccess('Đã lưu hồ sơ cá nhân.')
    } catch (failure) {
      if (failure.status === 401) onSessionExpired()
      else {
        setFieldErrors(failure.fieldErrors || {})
        setError(failure.status === 409
          ? 'Hồ sơ đã được thay đổi ở nơi khác. Hãy tải lại trước khi lưu.'
          : failure.message)
      }
    } finally { setSaving(false) }
  }

  function field(name, type = 'text') {
    return <label key={name} className="block min-w-0 text-sm font-semibold text-clinical-ink">
      <span>{labels[name]}</span>
      <input name={name} type={type} value={form[name]} onChange={change}
        maxLength={limits[name]} autoComplete={name === 'fullName' ? 'name' : name === 'email' ? 'email' : name === 'phone' ? 'tel' : 'off'}
        aria-invalid={Boolean(fieldErrors[name])} aria-describedby={fieldErrors[name] ? `${name}-error` : undefined}
        className="mt-2 w-full rounded-xl border border-clinical-border-strong bg-white px-4 py-3 font-normal outline-none focus:border-suncare-700 focus:ring-2 focus:ring-suncare-100" />
      {fieldErrors[name] && <span id={`${name}-error`} className="mt-1 block text-xs font-medium text-rose-700">{fieldErrors[name]}</span>}
    </label>
  }

  function detail(name, value) {
    return <div key={name} className="min-w-0 border-b border-clinical-border pb-3">
      <dt className="text-sm font-semibold text-clinical-muted">{labels[name]}</dt>
      <dd className="mt-1 break-words text-base text-clinical-ink">{value || 'Chưa cập nhật'}</dd>
    </div>
  }

  function displayDate(value) {
    if (!value) return ''
    const [year, month, day] = value.split('-')
    return `${day}/${month}/${year}`
  }

  return <section aria-labelledby="profile-title" className="mx-auto max-w-4xl">
    <div className="mb-6">
      <p className="text-sm font-semibold text-suncare-700">Tài khoản của tôi</p>
      <h1 id="profile-title" className="mt-2 text-3xl font-bold">Hồ sơ cá nhân</h1>
      <p className="mt-2 text-sm leading-6 text-clinical-muted">Thông tin hồ sơ của chính bạn.</p>
    </div>
    {loading ? <p role="status" className="rounded-xl border border-clinical-border bg-white p-6">Đang tải hồ sơ...</p> :
      !form ? <div className="rounded-xl border border-clinical-border bg-white p-6">
        <p role="alert" className="text-rose-700">{error || 'Chưa có hồ sơ để hiển thị.'}</p>
        <button type="button" onClick={reload} className="mt-4 rounded-xl bg-suncare-400 px-5 py-3 font-semibold text-clinical-ink">Thử lại</button>
      </div> : <div className="rounded-2xl border border-clinical-border bg-white p-5 sm:p-8">
        <div className="mb-7 flex flex-wrap items-start justify-between gap-3 border-b border-clinical-border pb-5">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-clinical-muted">Mã hồ sơ</p>
            <p className="mt-1 font-semibold">{profile.code}</p>
            <p className="mt-2 text-sm text-clinical-muted">{isPatient ? 'Bệnh nhân' : 'Nhân viên'} · {account.loginName}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={reload} disabled={saving} className="rounded-lg border border-clinical-border-strong px-4 py-2 text-sm font-semibold hover:bg-suncare-50 disabled:opacity-60">Tải lại</button>
            {!editing && <button type="button" onClick={() => { setError(''); setSuccess(''); setEditing(true) }}
              className="rounded-lg bg-suncare-400 px-4 py-2 text-sm font-semibold text-clinical-ink hover:bg-suncare-500">Chỉnh sửa hồ sơ</button>}
          </div>
        </div>
        {error && <p role="alert" className="mb-5 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
        {success && <p role="status" className="mb-5 rounded-lg border border-clinical-leaf bg-clinical-leaf-soft p-3 text-sm text-clinical-success">{success}</p>}
        {!editing ? <>
          <dl className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
            {detail('fullName', profile.fullName)}
            {detail('sex', sexLabels[profile.sex] || profile.sex)}
            {detail('birthDate', displayDate(profile.birthDate))}
            {detail('phone', profile.phone)}
            {detail('email', profile.email)}
            {detail('address', profile.address)}
          </dl>
          {isPatient ? <section className="mt-8 border-t border-clinical-border pt-6">
            <h2 className="text-lg font-bold">Thông tin bổ sung</h2>
            <dl className="mt-4 grid gap-x-8 gap-y-5 sm:grid-cols-2">
              {detail('identityNumber', profile.identityNumber)}
              {detail('emergencyName', profile.emergencyName)}
              {detail('emergencyPhone', profile.emergencyPhone)}
            </dl>
          </section> : <section className="mt-8 border-t border-clinical-border pt-6">
            <h2 className="text-lg font-bold">Thông tin chuyên môn</h2>
            <p className="mt-1 text-sm text-clinical-muted">Thông tin chuyên môn do quản trị viên quản lý.</p>
            <dl className="mt-4 grid gap-x-8 gap-y-5 sm:grid-cols-2">
              {detail('departments', profile.departments?.join(' · ') || 'Chưa phân khoa')}
              {detail('specialties', profile.specialties?.join(' · ') || 'Chưa gán chuyên khoa')}
              {detail('professionalLicense', profile.professionalLicense)}
              {detail('qualification', profile.qualification)}
            </dl>
          </section>}
        </> : <form onSubmit={save}>
        <div className="grid gap-5 sm:grid-cols-2">
          {field('fullName')}
          <label className="block text-sm font-semibold">Giới tính
            <select name="sex" value={form.sex} onChange={change} aria-invalid={Boolean(fieldErrors.sex)}
              className="mt-2 w-full rounded-xl border border-clinical-border-strong bg-white px-4 py-3 font-normal outline-none focus:border-suncare-700 focus:ring-2 focus:ring-suncare-100">
              <option value="UNKNOWN">Chưa xác định</option><option value="MALE">Nam</option>
              <option value="FEMALE">Nữ</option><option value="OTHER">Khác</option>
            </select>
            {fieldErrors.sex && <span className="mt-1 block text-xs text-rose-700">{fieldErrors.sex}</span>}
          </label>
          {field('birthDate', 'date')}
          {field('phone', 'tel')}
          {field('email', 'email')}
          {field('address')}
        </div>
        {isPatient && <fieldset className="mt-8 border-t border-clinical-border pt-6">
          <legend className="text-lg font-bold">Thông tin bổ sung</legend>
          <div className="mt-4 grid gap-5 sm:grid-cols-2">
            {field('identityNumber')}{field('emergencyName')}{field('emergencyPhone', 'tel')}
          </div>
        </fieldset>}
        <div className="mt-8 flex flex-wrap gap-3 border-t border-clinical-border pt-5">
          <button type="submit" disabled={saving} className="min-h-11 rounded-xl bg-suncare-400 px-6 font-semibold text-clinical-ink hover:bg-suncare-500 disabled:opacity-60">{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
          <button type="button" disabled={saving} onClick={() => { setForm(toForm(profile)); setFieldErrors({}); setError(''); setSuccess(''); setEditing(false) }}
            className="min-h-11 rounded-xl border border-clinical-border-strong px-6 font-semibold hover:bg-suncare-50 disabled:opacity-60">Bỏ thay đổi</button>
        </div>
      </form>}
      </div>}
  </section>
}
