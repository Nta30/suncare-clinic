import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowLeft, faChevronRight, faNotesMedical, faRotateRight } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import EncounterClinical from './EncounterClinical'
import EncounterInvoices from './EncounterInvoices'
import PatientEncounterHistory from './PatientEncounterHistory'
import { getEncounterWorklist, getEncounter, startEncounter, savePatientHistory, savePatientAllergy } from '../../services/encounterApi'

const inputClass = 'mt-1 min-h-11 w-full rounded-lg border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline-2 focus-visible:outline-suncare-700 disabled:bg-clinical-canvas'
const historyTypes = { PERSONAL: 'Bản thân', FAMILY: 'Gia đình', SURGERY: 'Phẫu thuật' }
const severities = { UNKNOWN: 'Chưa xác định', MILD: 'Nhẹ', MODERATE: 'Vừa', SEVERE: 'Nặng' }
const statuses = { PLANNED: 'Chưa bắt đầu', IN_PROGRESS: 'Đang khám', WAITING_CLS: 'Chờ cận lâm sàng', COMPLETED: 'Đã hoàn tất', CANCELLED: 'Đã hủy', NO_SHOW: 'Vắng mặt' }
const encounterSections = [['overview', 'Tổng quan'], ['visit-history', 'Lịch sử khám'], ['history', 'Tiền sử bệnh'], ['allergy', 'Dị ứng'], ['vitals', 'Sinh hiệu'], ['notes', 'Ghi chú'], ['diagnosis', 'Chẩn đoán'], ['invoices', 'Hóa đơn']]
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Saigon', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const time = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
const dateTime = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
const snapshotText = (value) => value.replace(/^(PERSONAL|FAMILY|SURGERY):/gm, (_, type) => `${historyTypes[type]}:`).replace(/\[(UNKNOWN|MILD|MODERATE|SEVERE)\]/g, (_, severity) => `[${severities[severity]}]`)

function ErrorBox({ message, onRetry }) {
  return <div role='alert' className='space-y-3 rounded-xl border border-clinical-danger/40 bg-white p-4'><p className='text-sm text-clinical-danger'>{message}</p>{onRetry && <Button onClick={onRetry}>Tải lại</Button>}</div>
}

function Field({ label, error, children }) {
  return <label className='block text-sm font-semibold'>{label}{children}{error && <span className='mt-1 block text-sm font-normal text-clinical-danger'>{error}</span>}</label>
}

function HistorySection({ kind, items, editable, busy, onSave, onDirtyChange, allergenOptions = [] }) {
  const allergy = kind === 'allergy'
  const initial = allergy ? { allergenId: '', otherNote: '', reaction: '', severity: 'UNKNOWN', isActive: true } : { historyType: 'PERSONAL', description: '', isActive: true }
  const [form, setForm] = useState(initial)
  const [editing, setEditing] = useState(null)
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  function change(event) {
    const { name, value, checked, type } = event.target
    setForm((previous) => ({ ...previous, [name]: type === 'checkbox' ? checked : value, ...(name === 'allergenId' ? { otherNote: '' } : {}) }))
    onDirtyChange(kind, true)
    setErrors((previous) => ({ ...previous, [name]: undefined }))
    setMessage('')
  }
  function reset() {
    setForm(initial)
    setEditing(null)
    setErrors({})
    setMessage('')
    onDirtyChange(kind, false)
  }
  function edit(item) {
    setEditing(item)
    setForm(allergy ? { allergenId: item.allergenId == null ? 'OTHER' : String(item.allergenId), otherNote: item.otherNote || '', reaction: item.reaction || '', severity: item.severity, isActive: item.isActive } : { historyType: item.historyType, description: item.description, isActive: item.isActive })
    setErrors({})
    setMessage('')
    onDirtyChange(kind, false)
  }
  async function submit(event) {
    event.preventDefault()
    const required = allergy ? (form.allergenId === 'OTHER' ? 'otherNote' : 'allergenId') : 'description'
    if (!form[required].trim()) { setErrors({ [required]: 'Không được để trống.' }); return }
    const data = allergy ? { ...form, allergenId: form.allergenId === 'OTHER' ? null : Number(form.allergenId), otherNote: form.allergenId === 'OTHER' ? form.otherNote.trim() : null } : form
    const result = await onSave(kind, editing?.id, { ...data, ...(editing ? { version: editing.version } : {}) })
    if (result.ok) { reset(); setMessage('Đã lưu thông tin.') }
    else setErrors(result.fieldErrors || {})
  }
  return <section className='min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5' aria-labelledby={`${kind}-title`}>
    <h2 id={`${kind}-title`} className='text-lg font-bold'>{allergy ? 'Dị ứng hiện tại' : 'Tiền sử hiện tại'}</h2>
    {allergy && <p className='text-sm text-clinical-muted'>Mỗi chất là một dòng riêng với phản ứng và mức độ tương ứng. Có thể lưu thêm nhiều chất cho cùng bệnh nhân.</p>}
    {items.length === 0 ? <p className='text-sm text-clinical-muted'>Chưa ghi nhận thông tin {allergy ? 'dị ứng' : 'tiền sử'}. Điều này chưa xác nhận bệnh nhân không có {allergy ? 'dị ứng' : 'tiền sử'}.</p> : <ul className='divide-y divide-clinical-border'>{items.map((item) => <li key={item.id} className='py-3 first:pt-0'>
      <div className='flex flex-wrap items-start justify-between gap-2'><div className='min-w-0 flex-1'><p className='break-words font-semibold'>{allergy ? (item.allergenId == null ? 'Chất khác / chưa đối chiếu danh mục' : `${item.allergenCode} · ${item.substance}`) : historyTypes[item.historyType]}</p>
        {allergy && item.otherNote && <p className='mt-1 whitespace-pre-wrap break-words text-sm'>{item.otherNote}</p>}
        <p className='mt-1 whitespace-pre-wrap break-words text-sm'>{allergy ? `${severities[item.severity]}${item.reaction ? ` · ${item.reaction}` : ''}` : item.description}</p>
        <p className={`mt-1 text-xs font-semibold ${item.isActive ? 'text-clinical-leaf' : 'text-clinical-muted'}`}>{item.isActive ? 'Đang áp dụng' : 'Đã ngừng áp dụng'}</p>
      </div>{editable && <Button disabled={busy} onClick={() => edit(item)}>Sửa</Button>}</div>
    </li>)}</ul>}
    {editable && <form onSubmit={submit} className='space-y-3 border-t border-clinical-border pt-4'>
      <h3 className='text-sm font-bold'>{editing ? 'Chỉnh sửa thông tin' : `Thêm ${allergy ? 'dị ứng' : 'tiền sử'}`}</h3>
      <fieldset disabled={busy} className='space-y-3'>
        {allergy ? <>
          <Field label='Chất gây dị ứng *' error={errors.allergenId}><select name='allergenId' value={form.allergenId} onChange={change} required className={inputClass} aria-invalid={Boolean(errors.allergenId)}>
            <option value=''>Chọn mã và tên chất</option>
            {allergenOptions.map((option) => <option key={option.id} value={option.id} disabled={items.some((item) => item.isActive && item.allergenId === option.id && item.id !== editing?.id)}>{option.code} · {option.name}</option>)}
            {editing?.allergenId != null && !allergenOptions.some((item) => item.id === editing.allergenId) && <option value={editing.allergenId}>{editing.allergenCode} · {editing.substance} (đã ngừng trong danh mục)</option>}
            <option value='OTHER'>Chất khác / chưa có trong danh mục</option>
          </select></Field>
          {allergenOptions.length === 0 && <p className='text-sm text-clinical-muted'>Danh mục chưa có chất đang áp dụng. Người quản lý danh mục có thể bổ sung tại “Chất gây dị ứng”.</p>}
          {form.allergenId === 'OTHER' && <Field label='Ghi rõ chất chưa có trong danh mục *' error={errors.otherNote}><textarea name='otherNote' value={form.otherNote} onChange={change} maxLength={1000} required rows={2} className={inputClass} aria-invalid={Boolean(errors.otherNote)} /></Field>}
          <Field label='Phản ứng' error={errors.reaction}><textarea name='reaction' value={form.reaction} onChange={change} maxLength={1000} rows={2} className={inputClass} aria-invalid={Boolean(errors.reaction)} /></Field>
          <Field label='Mức độ *' error={errors.severity}><select name='severity' value={form.severity} onChange={change} className={inputClass}>{Object.entries(severities).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></Field>
        </> : <>
          <Field label='Loại tiền sử *' error={errors.historyType}><select name='historyType' value={form.historyType} onChange={change} className={inputClass}>{Object.entries(historyTypes).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></Field>
          <Field label='Nội dung *' error={errors.description}><textarea name='description' value={form.description} onChange={change} maxLength={2000} required rows={3} className={inputClass} aria-invalid={Boolean(errors.description)} /></Field>
        </>}
        <label className='flex min-h-11 items-center gap-2 text-sm'><input type='checkbox' name='isActive' checked={form.isActive} onChange={change} className='h-4 w-4 accent-suncare-700' />Đang áp dụng</label>
        {errors.version && <p role='alert' className='text-sm text-clinical-danger'>{errors.version}</p>}
        <div className='flex flex-wrap gap-2'><Button type='submit' variant='primary'>{busy ? 'Đang lưu...' : allergy && !editing ? 'Lưu và thêm chất khác' : 'Lưu thông tin'}</Button><Button onClick={reset}>{editing ? 'Bỏ chỉnh sửa' : 'Xóa nội dung chưa lưu'}</Button></div>
      </fieldset>
    </form>}
    {message && <p role='status' className='text-sm font-semibold text-clinical-success'>{message}</p>}
  </section>
}

function EncounterPanel({ encounterId, onSessionExpired, onChanged }) {
  const [activeSection, setActiveSection] = useState('overview')
  const [state, setState] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [message, setMessage] = useState('')
  const [present, setPresent] = useState(false)
  const [dirty, setDirty] = useState({})
  const hasUnsaved = Object.values(dirty).some(Boolean)
  function onDirtyChange(kind, value) { setDirty((previous) => ({ ...previous, [kind]: value })) }
  function navigateSection(event, index) {
    let next
    if (event.key === 'ArrowRight') next = (index + 1) % encounterSections.length
    else if (event.key === 'ArrowLeft') next = (index + encounterSections.length - 1) % encounterSections.length
    else if (event.key === 'Home') next = 0
    else if (event.key === 'End') next = encounterSections.length - 1
    else return
    event.preventDefault()
    setActiveSection(encounterSections[next][0])
    document.getElementById(`encounter-tab-${encounterSections[next][0]}`)?.focus()
  }
  useEffect(() => {
    let active = true
    getEncounter(encounterId).then((data) => { if (active) setState({ data }) }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired(error.message)
      else setState({ error: error.message })
    })
    return () => { active = false }
  }, [encounterId, reload, onSessionExpired])
  function refresh() {
    setState({ loading: true })
    setActionError('')
    setMessage('')
    setPresent(false)
    setDirty({})
    setReload((value) => value + 1)
  }
  async function save(kind, itemId, data) {
    if (busy) return { ok: false }
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      const result = kind === 'allergy' ? await savePatientAllergy(encounterId, itemId, data) : await savePatientHistory(encounterId, itemId, data)
      setState({ data: result })
      return { ok: true }
    } catch (error) {
      if (error.status === 401) onSessionExpired(error.message)
      else setActionError(error.status === 409 ? `${error.message} Chọn “Tải lại hồ sơ” để lấy dữ liệu mới; nội dung chưa lưu sẽ được bỏ.` : error.message)
      return { ok: false, fieldErrors: error.fieldErrors }
    } finally { setBusy(false) }
  }
  async function begin() {
    if (!present || busy || hasUnsaved) return
    setBusy(true)
    setActionError('')
    setMessage('')
    try {
      const started = await startEncounter(encounterId)
      // Preserve the successful command result even if refreshing the remaining profile fails.
      setState((previous) => ({ data: { ...previous.data, encounter: { ...previous.data.encounter, status: started.status }, snapshot: { historyText: started.historyText, allergyText: started.allergyText, recordedAt: started.startedAt } } }))
      setPresent(false)
      setMessage(started.replayed ? 'Lượt khám đã được bắt đầu trước đó; giữ nguyên bản chốt.' : 'Đã bắt đầu khám và chốt tiền sử/dị ứng.')
      onChanged()
    } catch (error) {
      if (error.status === 401) onSessionExpired(error.message)
      else setActionError(error.message)
    } finally { setBusy(false) }
  }
  if (state.loading) return <p role='status' className='p-5'>Đang tải hồ sơ tiền sử/dị ứng...</p>
  if (state.error) return <ErrorBox message={state.error} onRetry={refresh} />
  const data = state.data
  const item = data.encounter
  return <div className='min-w-0 space-y-4'>
    <section className='rounded-xl border border-clinical-border bg-white p-4 sm:p-5'>
      <div className='flex flex-wrap items-start justify-between gap-3'><div><h2 className='break-words text-xl font-bold'>{item.patientName}</h2><p className='mt-1 text-sm text-clinical-muted'>{item.patientCode} · {item.encounterCode}</p></div><Button disabled={busy} onClick={refresh}>Tải lại hồ sơ</Button></div>
      <dl className='mt-4 grid gap-3 text-sm sm:grid-cols-2'>
        <div><dt className='text-clinical-muted'>Ngày và ca khám</dt><dd>{dateTime(item.startsAt)} – {time(item.endsAt)}</dd></div>
        <div><dt className='text-clinical-muted'>Phòng · chuyên khoa</dt><dd>{item.roomName} · {item.specialtyName}</dd></div>
        <div><dt className='text-clinical-muted'>Trạng thái lượt khám</dt><dd className='font-semibold'>{statuses[item.status] || item.status}</dd></div>
      </dl>
      {!data.editable && <p className='mt-3 text-sm text-clinical-muted'>Lượt khám này chỉ cho phép xem thông tin.</p>}
    </section>
    {actionError && <ErrorBox message={actionError} />}
    {message && <p role='status' className='rounded-lg border border-clinical-success/30 bg-white p-3 text-sm font-semibold text-clinical-success'>{message}</p>}
    <div role='tablist' aria-label='Các mục trong lượt khám' className='flex flex-wrap gap-2 border-b border-clinical-border pb-3'>
      {encounterSections.map(([code, label], index) => <button type='button' key={code} role='tab' id={`encounter-tab-${code}`} aria-controls={`encounter-panel-${code}`} aria-selected={activeSection === code} tabIndex={activeSection === code ? 0 : -1} onClick={() => setActiveSection(code)} onKeyDown={(event) => navigateSection(event, index)} className={`min-h-11 rounded-lg border px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 ${activeSection === code ? 'border-suncare-700 bg-suncare-100 text-suncare-900' : 'border-clinical-border bg-white hover:bg-suncare-50'}`}>{label}{dirty[code] ? ' *' : ''}</button>)}
    </div>
    <div id='encounter-panel-history' role='tabpanel' aria-labelledby='encounter-tab-history' hidden={activeSection !== 'history'}>
      <p className='mb-3 text-sm text-clinical-muted'>Bệnh nền, bệnh đã mắc, yếu tố gia đình và phẫu thuật trước đây. Các lần khám trước nằm trong mục Lịch sử khám.</p>
      <HistorySection key={`history-${reload}`} kind='history' items={data.histories} editable={data.editable} busy={busy} onSave={save} onDirtyChange={onDirtyChange} />
    </div>
    <div id='encounter-panel-visit-history' role='tabpanel' aria-labelledby='encounter-tab-visit-history' hidden={activeSection !== 'visit-history'}>
      <PatientEncounterHistory encounterId={encounterId} active={activeSection === 'visit-history'} onSessionExpired={onSessionExpired} />
    </div>
    <div id='encounter-panel-allergy' role='tabpanel' aria-labelledby='encounter-tab-allergy' hidden={activeSection !== 'allergy'}>
      <HistorySection key={`allergy-${reload}`} kind='allergy' items={data.allergies} editable={data.editable} busy={busy} onSave={save} onDirtyChange={onDirtyChange} allergenOptions={data.allergenOptions} />
    </div>
    <section id='encounter-panel-overview' role='tabpanel' hidden={activeSection !== 'overview'} className='space-y-3 rounded-xl border border-clinical-border bg-white p-4 sm:p-5' aria-labelledby='encounter-tab-overview'>
      <h2 id='history-snapshot-title' className='text-lg font-bold'>Bản chốt khi bắt đầu khám</h2>
      {data.snapshot ? <>
        <p className='text-sm text-clinical-muted'>Chốt lúc {dateTime(data.snapshot.recordedAt)}. Thay đổi tiền sử/dị ứng hiện tại không sửa bản đã chốt của lượt khám này.</p>
        <dl className='grid gap-4 text-sm sm:grid-cols-2'><div><dt className='font-semibold'>Tiền sử</dt><dd className='mt-1 whitespace-pre-wrap break-words'>{snapshotText(data.snapshot.historyText) || 'Chưa có thông tin được ghi nhận lúc chốt.'}</dd></div><div><dt className='font-semibold'>Dị ứng</dt><dd className='mt-1 whitespace-pre-wrap break-words'>{snapshotText(data.snapshot.allergyText) || 'Chưa có thông tin được ghi nhận lúc chốt.'}</dd></div></dl>
      </> : <p className='text-sm text-clinical-muted'>Chưa có bản chốt. Hãy kiểm tra và cập nhật tiền sử/dị ứng trước khi bắt đầu khám.</p>}
      {item.status === 'PLANNED' && data.editable && <div className='space-y-3 border-t border-clinical-border pt-3'>
        <label className='flex min-h-11 items-start gap-2 text-sm'><input type='checkbox' checked={present} onChange={(event) => setPresent(event.target.checked)} disabled={busy} className='mt-1 h-4 w-4 shrink-0 accent-suncare-700' />Tôi xác nhận bệnh nhân đã có mặt và đã kiểm tra tiền sử/dị ứng được ghi nhận.</label>
        {hasUnsaved && <p role='status' className='text-sm text-clinical-warning'>Hãy lưu hoặc bỏ nội dung đang sửa trước khi bắt đầu khám.</p>}
        <Button variant='primary' disabled={!present || busy || hasUnsaved} onClick={begin}>{busy ? 'Đang xử lý...' : 'Bắt đầu khám và chốt thông tin'}</Button>
      </div>}
    </section>
    <div hidden={!['vitals', 'notes', 'diagnosis'].includes(activeSection)}>
      <EncounterClinical key={`${encounterId}-${item.status}`} encounterId={encounterId} onSessionExpired={onSessionExpired} section={activeSection} />
    </div>
    <div id='encounter-panel-invoices' role='tabpanel' aria-labelledby='encounter-tab-invoices' hidden={activeSection !== 'invoices'}>
      <EncounterInvoices encounterId={encounterId} active={activeSection === 'invoices'} writable={data.editable && ['IN_PROGRESS', 'WAITING_CLS'].includes(item.status)} onSessionExpired={onSessionExpired} />
    </div>
  </div>
}

export default function DoctorEncounters({ onSessionExpired }) {
  const [filters, setFilters] = useState(() => ({ date: today(), status: 'ALL', search: '', pageSize: 10 }))
  const [query, setQuery] = useState(() => ({ date: today(), status: 'ALL', search: '', pageSize: 10, page: 1 }))
  const [state, setState] = useState({ loading: true, data: [] })
  const [reload, setReload] = useState(0)
  const [selected, setSelected] = useState(null)
  const searchTimer = useRef(null)
  useEffect(() => () => window.clearTimeout(searchTimer.current), [])
  useEffect(() => {
    let active = true
    getEncounterWorklist(query).then((result) => { if (active) setState({ ...result, data: result.items }) }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired(error.message)
      else setState({ data: [], error: error.message })
    })
    return () => { active = false }
  }, [query, reload, onSessionExpired])
  function refresh() {
    window.clearTimeout(searchTimer.current)
    setState({ loading: true, data: [] })
    if (filters.date) setQuery({ ...filters, search: filters.search.trim(), page: filters.search.trim() === query.search ? query.page : 1 })
    setReload((value) => value + 1)
  }
  function applyFilters(next) {
    window.clearTimeout(searchTimer.current)
    if (!next.date) return
    setState({ loading: true, data: [] })
    setQuery({ ...next, search: next.search.trim(), page: 1 })
  }
  function changeFilter(name, value) {
    const next = { ...filters, [name]: value }
    setFilters(next)
    window.clearTimeout(searchTimer.current)
    if (name === 'search') searchTimer.current = window.setTimeout(() => applyFilters(next), 350)
    else applyFilters(next)
  }
  function clearFilters() {
    const next = { ...filters, status: 'ALL', search: '' }
    setFilters(next)
    applyFilters(next)
  }
  function goToPage(page) {
    window.clearTimeout(searchTimer.current)
    setState({ loading: true, data: [] })
    setQuery((previous) => filters.date ? { ...filters, search: filters.search.trim(), page: filters.search.trim() === previous.search ? page : 1 } : { ...previous, page })
  }
  if (selected != null) {
    return <section className='w-full space-y-4 text-clinical-ink' aria-label='Chi tiết lượt khám'>
      <Button onClick={() => setSelected(null)}><FontAwesomeIcon icon={faArrowLeft} aria-hidden='true' />Quay lại danh sách lượt khám</Button>
      <EncounterPanel key={selected} encounterId={selected} onSessionExpired={onSessionExpired} onChanged={refresh} />
    </section>
  }
  return <section className='w-full space-y-5 text-clinical-ink' aria-labelledby='encounter-page-title'>
    <header><h1 id='encounter-page-title' className='flex items-center gap-2 text-2xl font-bold'><FontAwesomeIcon icon={faNotesMedical} className='text-clinical-leaf' />Danh sách lượt khám trực tiếp</h1><p className='mt-1 text-sm text-clinical-muted'>Chọn Chi tiết để xem hồ sơ và thực hiện khám.</p></header>
    <form onSubmit={(event) => { event.preventDefault(); applyFilters(filters) }} className='space-y-3 rounded-xl border border-clinical-border bg-white p-4' aria-label='Tìm và lọc lượt khám'>
      <div className='grid items-end gap-3 sm:grid-cols-2 xl:grid-cols-[auto_auto_minmax(15rem,1fr)_auto]'>
        <Field label='Ngày khám'><input type='date' required value={filters.date} onChange={(event) => changeFilter('date', event.target.value)} className={inputClass} /></Field>
        <Field label='Trạng thái lượt khám'><select value={filters.status} onChange={(event) => changeFilter('status', event.target.value)} className={inputClass}><option value='ALL'>Tất cả trạng thái</option>{Object.entries(statuses).map(([code, label]) => <option value={code} key={code}>{label}</option>)}</select></Field>
        <Field label='Tìm bệnh nhân hoặc lượt khám'><input type='search' value={filters.search} onChange={(event) => changeFilter('search', event.target.value)} placeholder='Tên, mã bệnh nhân, mã lượt khám hoặc lịch hẹn' maxLength={100} aria-describedby='encounter-search-help' className={inputClass} /></Field>
        <Field label='Số dòng mỗi trang'><select value={filters.pageSize} onChange={(event) => changeFilter('pageSize', Number(event.target.value))} className={inputClass}>{[10, 20, 50].map((size) => <option key={size} value={size}>{size} dòng</option>)}</select></Field>
      </div>
      <p id='encounter-search-help' className='text-xs text-clinical-muted'>Tìm trong ngày khám đã chọn. Tên hỗ trợ không dấu, không phân biệt hoa thường; có thể nhập một phần mã.</p>
      <div className='flex flex-wrap gap-2'><Button onClick={clearFilters}>Xóa lọc trạng thái/từ khóa</Button><Button onClick={refresh}><FontAwesomeIcon icon={faRotateRight} aria-hidden='true' />Tải lại</Button></div>
    </form>
    {state.loading && <p role='status' className='p-4 text-sm'>Đang tải lượt khám...</p>}
    {state.error && <ErrorBox message={state.error} onRetry={refresh} />}
    {!state.loading && !state.error && <p role='status' className='text-sm text-clinical-muted'>{state.totalElements === 0 ? 'Không tìm thấy lượt khám phù hợp. Hãy kiểm tra ngày, đổi từ khóa hoặc xóa bộ lọc.' : `${state.totalElements} lượt khám · Hiển thị ${(state.page - 1) * state.pageSize + 1}–${Math.min(state.page * state.pageSize, state.totalElements)}`}</p>}
    {!state.loading && !state.error && state.data.length > 0 && <ul className='divide-y divide-clinical-border rounded-xl border border-clinical-border bg-white' aria-label='Danh sách lượt khám'>{state.data.map((item) => <li key={item.encounterId} className='grid min-w-0 items-center gap-4 p-4 sm:p-5 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_minmax(0,1fr)_auto]'>
      <div className='min-w-0'><p className='break-words font-bold'>{item.patientName}</p><p className='mt-1 break-all text-sm text-clinical-muted'>Bệnh nhân: {item.patientCode}</p><p className='mt-1 break-all text-sm'>Lượt khám: {item.encounterCode}</p></div>
      <div className='min-w-0 text-sm'><p className='font-semibold'>{time(item.startsAt)} – {time(item.endsAt)}</p><p className='mt-1 break-words'>{item.roomName}</p><p className='mt-1 break-words text-clinical-muted'>{item.specialtyName}</p></div>
      <p className='text-sm font-semibold'>{statuses[item.status] || item.status}</p>
      <Button className='justify-self-start lg:justify-self-end' onClick={() => setSelected(item.encounterId)} aria-label={`Xem chi tiết lượt khám ${item.encounterCode} của ${item.patientName}`}>Chi tiết<FontAwesomeIcon icon={faChevronRight} aria-hidden='true' /></Button>
    </li>)}</ul>}
    {!state.loading && !state.error && state.totalPages > 1 && <nav className='flex flex-wrap items-center justify-between gap-3' aria-label='Phân trang lượt khám'>
      <p className='text-sm'>Trang {state.page} / {state.totalPages}</p>
      <div className='flex flex-wrap gap-2'><Button disabled={state.page <= 1} onClick={() => goToPage(1)}>Đầu</Button><Button disabled={state.page <= 1} onClick={() => goToPage(state.page - 1)}>Trước</Button><Button disabled={state.page >= state.totalPages} onClick={() => goToPage(state.page + 1)}>Sau</Button><Button disabled={state.page >= state.totalPages} onClick={() => goToPage(state.totalPages)}>Cuối</Button></div>
    </nav>}
  </section>
}
