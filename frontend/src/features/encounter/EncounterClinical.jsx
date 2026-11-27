import { cloneElement, useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCircleCheck, faXmark } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import DiagnosisLookup from './DiagnosisLookup'
import SymptomMultiSelect from './SymptomMultiSelect'
import EncounterFooter from './EncounterFooter'
import { getEncounterClinical, saveEncounterNotes, recordVitalSign, saveDiagnosis } from '../../services/encounterApi'

const inputClass = 'mt-2 min-h-11 w-full min-w-0 rounded-lg border border-clinical-border-strong bg-white px-3 py-2 text-sm disabled:bg-clinical-canvas'
const box = 'min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'
const noteFields = [['chiefComplaint', 'Lý do vào khám', 2000], ['historyOfPresentIllness', 'Bệnh sử', 20000], ['examinationNote', 'Kết quả khám thực thể', 20000], ['treatmentPlan', 'Hướng xử trí và điều trị', 20000], ['followupNote', 'Tư vấn và hẹn tái khám', 2000]]
const vitalFields = [['temperatureC', 'Nhiệt độ (°C)', '0.01', '999.99'], ['pulseBpm', 'Mạch (lần/phút)', '1', '32767'], ['systolicMmhg', 'Huyết áp tâm thu (mmHg)', '1', '32767'], ['diastolicMmhg', 'Huyết áp tâm trương (mmHg)', '1', '32767'], ['respiratoryRate', 'Nhịp thở (lần/phút)', '1', '32767'], ['spo2Percent', 'SpO₂ (%)', '0.01', '100'], ['weightKg', 'Cân nặng (kg)', '0.01', '99999.99'], ['heightCm', 'Chiều cao (cm)', '0.01', '9999.99']]
const localNow = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Saigon', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date()).replace(' ', 'T')
const showTime = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))

function Field({ label, error, children }) {
  const id = useId()
  return <div className='min-w-0 text-sm'><label htmlFor={id} className='block font-semibold'>{label}</label>{cloneElement(children, { id })}{error && <p className='mt-1 text-clinical-danger'>{error}</p>}</div>
}

function useCommandKey() {
  const pending = useRef(null)
  const getKey = (data) => {
    const payload = JSON.stringify(data)
    if (pending.current?.payload !== payload) pending.current = { payload, key: crypto.randomUUID() }
    return pending.current.key
  }
  return [getKey, () => { pending.current = null }]
}

function Vitals({ data, disabled, perform, encounterId, errors, onGuardChange }) {
  const initial = () => ({ measuredAt: localNow(), ...Object.fromEntries(vitalFields.map(([name]) => [name, ''])) })
  const [form, setForm] = useState(initial)
  const [error, setError] = useState('')
  const [key, resetKey] = useCommandKey()
  const latest = data.vitals[0]
  const dirty = vitalFields.some(([name]) => form[name] !== '')
  useEffect(() => {
    onGuardChange?.('vitals', dirty)
    return () => onGuardChange?.('vitals', false)
  }, [dirty, onGuardChange])
  async function submit(event) {
    event.preventDefault()
    const body = { measuredAt: `${form.measuredAt}+07:00`, ...Object.fromEntries(vitalFields.map(([name]) => [name, form[name] === '' ? null : Number(form[name])])) }
    if (vitalFields.every(([name]) => body[name] == null)) { setError('Nhập ít nhất một số đo sinh hiệu.'); return }
    setError('')
    if (await perform('vitals', () => recordVitalSign(encounterId, body, key(body)), 'Đã lưu dấu hiệu sinh tồn.')) { resetKey(); setForm(initial()) }
  }
  return <details open className='encounter-fold min-w-0 rounded-xl border border-clinical-border bg-white'>
    <summary className='cursor-pointer p-4 text-sm font-bold'>Dấu hiệu sinh tồn</summary>
    <div className='space-y-4 border-t border-clinical-border p-4'>
      <dl className='grid grid-cols-2 gap-2'>{vitalFields.map(([name, label]) => <div key={name} className='min-w-0 rounded-lg bg-clinical-canvas p-3'><dt className='text-xs text-clinical-muted'>{label}</dt><dd className='mt-1 break-words text-lg font-semibold tabular-nums'>{latest?.[name] ?? '—'}</dd></div>)}</dl>
      <p className='text-xs text-clinical-muted'>{latest ? `Lần đo gần nhất: ${showTime(latest.measuredAt)}` : 'Chưa ghi nhận số đo; không suy diễn là bình thường.'}</p>
      <details className='encounter-fold'><summary className='cursor-pointer rounded-lg border border-clinical-border-strong px-3 py-2.5 text-center text-sm font-semibold'>Ghi nhận sinh hiệu</summary>
        <form onSubmit={submit} className='mt-4 space-y-3'><fieldset disabled={disabled} className='space-y-3'>
          <Field label='Thời điểm đo (giờ Việt Nam) *' error={errors.measuredAt}><input type='datetime-local' step='1' required value={form.measuredAt} onChange={(event) => setForm({ ...form, measuredAt: event.target.value })} className={inputClass} /></Field>
          <div className='grid grid-cols-2 gap-3'>{vitalFields.map(([name, label, step, max]) => <Field key={name} label={label} error={errors[name]}><input type='number' min={step} max={max} step={step} value={form[name]} onChange={(event) => setForm({ ...form, [name]: event.target.value })} className={inputClass} aria-invalid={Boolean(errors[name])} /></Field>)}</div>
          <div className='flex flex-wrap gap-2'><Button type='submit' variant='primary'>Lưu lần đo</Button><Button onClick={() => { setForm(initial()); setError('') }}>Bỏ số đo chưa lưu</Button></div>
        </fieldset>{error && <p role='alert' className='text-sm text-clinical-danger'>{error}</p>}</form>
      </details>
      <details className='encounter-fold'><summary className='cursor-pointer text-sm font-semibold'>Các lần đo trước ({data.vitals.length})</summary><ul className='mt-3 space-y-3 text-sm'>{data.vitals.map((item) => <li key={item.id} className='border-t border-clinical-border pt-2'><p className='font-semibold'>{showTime(item.measuredAt)}</p>{vitalFields.filter(([name]) => item[name] != null).map(([name, label]) => <p key={name}>{label}: {item[name]}</p>)}</li>)}</ul></details>
    </div>
  </details>
}

function DiagnosisEditor({ type, data, disabled, encounterId, perform, errors, onSessionExpired, onGuardChange }) {
  const initial = { diagnosisConceptId: null, description: '', diagnosisType: type, isPrimary: false }
  const [form, setForm] = useState(initial)
  const [editing, setEditing] = useState(null)
  const [concept, setConcept] = useState(null)
  const [error, setError] = useState('')
  const [key, resetKey] = useCommandKey()
  const dirty = Boolean(editing || form.description || form.diagnosisConceptId || form.isPrimary)
  useEffect(() => {
    onGuardChange?.(`diagnosis-${type}`, dirty)
    return () => onGuardChange?.(`diagnosis-${type}`, false)
  }, [dirty, type, onGuardChange])
  function clear() { setEditing(null); setForm(initial); setConcept(null); setError('') }
  function edit(item) {
    setEditing(item)
    setForm({ diagnosisConceptId: item.diagnosisConceptId, description: item.description, diagnosisType: item.diagnosisType, isPrimary: item.isPrimary })
    setConcept(item.diagnosisConceptId ? { id: item.diagnosisConceptId, codeSystem: item.codeSystem, codeVersion: item.codeVersion, code: item.diagnosisCode, name: item.diagnosisName, primaryAllowed: item.primaryAllowed !== false } : null)
  }
  async function submit(event) {
    event.preventDefault()
    if (!form.description.trim()) { setError('Nhập nội dung chẩn đoán.'); return }
    if (form.diagnosisType === 'FINAL' && !form.diagnosisConceptId) { setError('Chẩn đoán xác định phải chọn mã bệnh từ danh mục ICD-10.'); return }
    const body = { ...form, ...(editing ? { version: editing.version } : {}) }
    if (await perform('diagnosis', () => saveDiagnosis(encounterId, editing?.id, body, editing ? undefined : key(body)), 'Đã lưu chẩn đoán.')) { resetKey(); clear() }
  }
  const title = type === 'FINAL' ? 'Chẩn đoán xác định' : 'Chẩn đoán sơ bộ'
  const items = data.diagnoses.filter((item) => item.diagnosisType === type)
  return <section className={box} aria-label={title}>
    <div className='flex items-center justify-between gap-3 border-b border-clinical-border pb-3'><h3 className='font-bold'>{title}</h3><span className='text-xs text-clinical-muted'>ICD-10</span></div>
    <form onSubmit={submit} className='space-y-3'><fieldset disabled={disabled} className='space-y-3'>
      <DiagnosisLookup key={editing?.id || 'new'} value={concept} onSessionExpired={onSessionExpired} disabled={disabled} onSelect={(item) => {
        setConcept(item)
        setForm((previous) => ({ ...previous, diagnosisConceptId: item?.id || null, description: !previous.description.trim() || previous.description === concept?.name ? item?.name || '' : previous.description, isPrimary: item && !item.primaryAllowed ? false : previous.isPrimary }))
      }} />
      <Field label='Diễn giải chẩn đoán *' error={errors.description}><textarea rows={3} required maxLength={1000} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} className={inputClass} /></Field>
      {editing && <Field label='Phân loại chẩn đoán'><select className={inputClass} value={form.diagnosisType} onChange={(event) => setForm({ ...form, diagnosisType: event.target.value })}><option value='PRELIMINARY'>Sơ bộ</option><option value='FINAL'>Xác định</option></select></Field>}
      <label className='flex min-h-11 items-center gap-2 text-sm'><input type='checkbox' disabled={concept != null && !concept.primaryAllowed} checked={form.isPrimary} onChange={(event) => setForm({ ...form, isPrimary: event.target.checked })} className='h-4 w-4 accent-suncare-700' />Chẩn đoán chính</label>
      {editing?.diagnosisCode && !editing.diagnosisConceptId && <p className='text-sm text-clinical-warning'>Mã cũ: {editing.diagnosisCode}. Cần chọn lại mã từ danh mục nếu lưu chẩn đoán xác định.</p>}
      <div className='flex flex-wrap gap-2'><Button type='submit' variant='primary'>{editing ? 'Lưu chẩn đoán' : 'Thêm chẩn đoán'}</Button>{dirty && <Button onClick={clear}>Bỏ chỉnh sửa</Button>}</div>
    </fieldset></form>
    {error && <p role='alert' className='text-sm text-clinical-danger'>{error}</p>}
    {items.length === 0 ? <p className='rounded-lg bg-clinical-canvas p-3 text-sm text-clinical-muted'>Chưa ghi nhận {title.toLowerCase()}.</p> : <ul className='divide-y divide-clinical-border'>{items.map((item) => <li key={item.id} className='flex flex-wrap items-start justify-between gap-3 py-3'><div className='min-w-0 flex-1'><p className='whitespace-pre-wrap break-words font-semibold'>{item.description}</p><p className='mt-1 text-sm'>{item.diagnosisCode || 'Chưa chọn mã'} · {item.isPrimary ? 'Bệnh chính' : 'Bệnh kèm theo'}</p></div>{data.editable && <Button disabled={disabled} onClick={() => edit(item)}>Sửa</Button>}</li>)}</ul>}
  </section>
}

function ClinicalWorkspace({ data, encounterId, section, footerProps, locked, sidebar, invoices, prescriptions, onSessionExpired, perform, busy, errors, onGuardChange }) {
  const [notes, setNotes] = useState(() => Object.fromEntries(noteFields.map(([name]) => [name, data.notes[name] || ''])))
  const [savedNotes, setSavedNotes] = useState(data.notes)
  const [symptoms, setSymptoms] = useState(data.notes.symptoms || [])
  const dirty = noteFields.some(([name]) => notes[name] !== (savedNotes[name] || '')) || JSON.stringify(symptoms) !== JSON.stringify(savedNotes.symptoms || [])
  useEffect(() => {
    onGuardChange?.('notes', dirty)
    return () => onGuardChange?.('notes', false)
  }, [dirty, onGuardChange])
  useEffect(() => {
    if (!dirty) return undefined
    const warn = (event) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
  const disabled = Boolean(busy) || !data.editable
  const fields = (kind) => errors.kind === kind ? errors.fields : {}
  function noteField(name) {
    const [, label, max] = noteFields.find(([key]) => key === name)
    return <Field label={label} error={fields('notes')[name]}><textarea form='encounter-notes-form' name={name} rows={name === 'chiefComplaint' ? 3 : 5} disabled={disabled} maxLength={max} value={notes[name]} onChange={(event) => setNotes({ ...notes, [name]: event.target.value })} className={inputClass} aria-invalid={Boolean(fields('notes')[name])} /></Field>
  }
  async function saveNotes(event) {
    event.preventDefault()
    let result
    const saved = await perform('notes', async () => {
      result = await saveEncounterNotes(encounterId, { ...notes, symptoms, version: savedNotes.version })
      return result
    }, 'Đã lưu nội dung hồ sơ khám.')
    if (saved) {
      setSavedNotes(result.notes)
      setNotes(Object.fromEntries(noteFields.map(([name]) => [name, result.notes[name] || ''])))
      setSymptoms(result.notes.symptoms || [])
    }
  }
  return <>
    <fieldset disabled={locked} className='min-w-0'>
    <div className='encounter-columns grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,7fr)]'>
      <aside className='order-2 min-w-0 space-y-3 lg:order-1' aria-label='Thông tin lâm sàng liên tục'><Vitals data={data} disabled={disabled} perform={perform} encounterId={encounterId} errors={fields('vitals')} onGuardChange={onGuardChange} />{sidebar}</aside>
      <div className='order-1 min-w-0 space-y-4 lg:order-2'>
        <div id='encounter-panel-clinical' role='tabpanel' aria-labelledby='encounter-tab-clinical' hidden={section !== 'clinical'} className='space-y-4'>
          <section className={box} aria-label='Hỏi bệnh'><h3 className='border-b border-clinical-border pb-3 font-bold'>Hỏi bệnh</h3>{noteField('chiefComplaint')}<SymptomMultiSelect value={symptoms} onChange={setSymptoms} disabled={disabled} onSessionExpired={onSessionExpired} />{fields('notes').symptoms && <p role='alert' className='text-sm text-clinical-danger'>{fields('notes').symptoms}</p>}{noteField('historyOfPresentIllness')}<p className='text-xs text-clinical-muted'>Bệnh sử ghi nhận thời điểm khởi phát, diễn biến, đặc điểm triệu chứng và điều trị trước đó. Chọn “Lưu hồ sơ khám” để lưu thay đổi.</p></section>
          <section className={box}><h3 className='border-b border-clinical-border pb-3 font-bold'>Khám thực thể</h3>{noteField('examinationNote')}</section>
          <DiagnosisEditor type='PRELIMINARY' {...{ data, encounterId, disabled, perform, onSessionExpired, onGuardChange }} errors={fields('diagnosis')} />
        </div>
        <div id='encounter-panel-treatment' role='tabpanel' aria-labelledby='encounter-tab-treatment' hidden={section !== 'treatment'} className='space-y-4'>
          <DiagnosisEditor type='FINAL' {...{ data, encounterId, disabled, perform, onSessionExpired, onGuardChange }} errors={fields('diagnosis')} />
          <section className={box}><h3 className='border-b border-clinical-border pb-3 font-bold'>Hướng xử trí và điều trị</h3>{noteField('treatmentPlan')}</section>
          {prescriptions}
          <section className={box}><h3 className='border-b border-clinical-border pb-3 font-bold'>Tư vấn và hẹn tái khám</h3>{noteField('followupNote')}<p className='text-xs text-clinical-muted'>Ghi dặn dò và đề nghị tái khám; không tạo lịch hẹn mới.</p></section>
        </div>
        <div id='encounter-panel-invoices' role='tabpanel' aria-labelledby='encounter-tab-invoices' hidden={section !== 'invoices'}>{invoices}</div>
      </div>
    </div>
    <form id='encounter-notes-form' onSubmit={saveNotes} />
    </fieldset>
    <EncounterFooter {...footerProps} section={section} locked={locked} saving={busy === 'notes'} saveDisabled={disabled}
      saveReason={locked ? 'Hồ sơ đang khóa để kiểm tra hoặc xác minh hoàn tất lượt khám.' : busy ? 'Đang xử lý dữ liệu khám, vui lòng chờ.' : !data.editable ? 'Hồ sơ chỉ đọc; hiện không được phép lưu.' : 'Lưu ghi chú khám, bệnh sử, khám thực thể, hướng điều trị và tư vấn.'} />
  </>
}

export default function EncounterClinical({ encounterId, onSessionExpired, section = 'clinical', footerProps, locked, sidebar, invoices, prescriptions, onGuardChange }) {
  const [state, setState] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const commandBusy = useRef(false)
  useEffect(() => {
    if (!message) return undefined
    const timer = window.setTimeout(() => setMessage(''), 8000)
    return () => window.clearTimeout(timer)
  }, [message])
  useEffect(() => {
    onGuardChange?.('clinical-request', Boolean(busy || state.loading || state.error))
    return () => onGuardChange?.('clinical-request', false)
  }, [busy, state.loading, state.error, onGuardChange])
  useEffect(() => {
    let active = true
    getEncounterClinical(encounterId).then((data) => { if (active) setState({ data }) }).catch((failure) => {
      if (!active) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else setState({ error: failure.message })
    })
    return () => { active = false }
  }, [encounterId, reload, onSessionExpired])
  function refresh() { setState({ loading: true }); setError(''); setErrors({}); setMessage(''); setReload((value) => value + 1) }
  async function perform(kind, operation, success) {
    if (commandBusy.current) return false
    commandBusy.current = true
    setBusy(kind); setError(''); setErrors({}); setMessage('')
    try { const data = await operation(); setState({ data }); setMessage(data.replayed ? 'Yêu cầu đã được lưu trước đó; không tạo bản ghi trùng.' : success); return true }
    catch (failure) {
      if (failure.status === 401) onSessionExpired(failure.message)
      else { setError(failure.status === 409 ? `${failure.message} Nội dung nhập được giữ lại; đọc lại bản mới nếu cần.` : failure.message); setErrors({ kind, fields: failure.fieldErrors || {}, conflict: failure.status === 409 }) }
      return false
    } finally { commandBusy.current = false; setBusy('') }
  }
  if (state.loading) return <p role='status' className='p-4'>Đang tải hồ sơ lâm sàng...</p>
  if (state.error) return <div className={box}><p role='alert' className='text-clinical-danger'>{state.error}</p><Button onClick={refresh}>Thử lại dữ liệu khám</Button></div>
  return <div className='min-w-0 space-y-4'>
    {error && <div role='alert' className='space-y-2 rounded-lg border border-clinical-danger/40 bg-white p-3 text-sm text-clinical-danger'><p>{error}</p>{errors.conflict && <Button disabled={Boolean(busy) || locked} onClick={() => { if (window.confirm('Đọc lại sẽ bỏ nội dung khám chưa lưu. Tiếp tục?')) refresh() }}>Đọc lại bản mới</Button>}</div>}
    {message && createPortal(<div className='fixed right-4 top-4 z-50 flex max-w-[calc(100%-2rem)] items-center gap-3 rounded-xl border border-clinical-success/30 bg-white p-3 text-sm font-semibold text-clinical-success shadow-lg'>
      <p role='status' className='flex min-w-0 items-center gap-2'><FontAwesomeIcon icon={faCircleCheck} aria-hidden='true' /><span>{message}</span></p>
      <Button onClick={() => setMessage('')} aria-label='Đóng thông báo lưu thành công' className='shrink-0 border-transparent px-3'><FontAwesomeIcon icon={faXmark} aria-hidden='true' /></Button>
    </div>, document.body)}
    {!state.data.editable && <p className='text-sm text-clinical-muted'>Hồ sơ chỉ đọc. Chỉ được ghi nhận khi lượt khám đang diễn ra.</p>}
    <ClinicalWorkspace key={reload} data={state.data} {...{ encounterId, section, footerProps, locked, sidebar, invoices, prescriptions, onSessionExpired, perform, busy, errors, onGuardChange }} />
  </div>
}
