import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import DiagnosisLookup from './DiagnosisLookup'
import { getEncounterClinical, saveEncounterNotes, recordVitalSign, saveDiagnosis } from '../../services/encounterApi'

const inputClass = 'mt-1 min-h-11 w-full rounded-lg border border-clinical-border-strong bg-white px-3 py-2 text-sm disabled:bg-clinical-canvas focus-visible:outline-2 focus-visible:outline-suncare-700'
const sectionClass = 'min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'
const vitalFields = [
  ['temperatureC', 'Nhiệt độ (°C)', '0.01', '999.99'],
  ['pulseBpm', 'Mạch (lần/phút)', '1', '32767'],
  ['systolicMmhg', 'Huyết áp tâm thu (mmHg)', '1', '32767'],
  ['diastolicMmhg', 'Huyết áp tâm trương (mmHg)', '1', '32767'],
  ['respiratoryRate', 'Nhịp thở (lần/phút)', '1', '32767'],
  ['spo2Percent', 'SpO₂ (%)', '0.01', '100'],
  ['weightKg', 'Cân nặng (kg)', '0.01', '99999.99'],
  ['heightCm', 'Chiều cao (cm)', '0.01', '9999.99'],
]
const noteFields = [['chiefComplaint', 'Lý do khám / triệu chứng chính', 2000], ['examinationNote', 'Ghi chú thăm khám', 20000], ['treatmentPlan', 'Kế hoạch điều trị', 20000], ['followupNote', 'Dặn dò / tái khám', 2000]]
const emptyDiagnosis = { diagnosisConceptId: null, description: '', diagnosisType: 'PRELIMINARY', isPrimary: false }
const localNow = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Saigon', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date()).replace(' ', 'T')
const showTime = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))

function Field({ label, error, children }) {
  return <label className='block min-w-0 text-sm font-semibold'>{label}{children}{error && <span className='mt-1 block text-sm font-normal text-clinical-danger'>{error}</span>}</label>
}

function NotesForm({ notes, disabled, errors, onSave }) {
  const [form, setForm] = useState(() => Object.fromEntries(noteFields.map(([name]) => [name, notes[name] || ''])))
  return <form onSubmit={(event) => { event.preventDefault(); onSave({ ...form, version: notes.version }) }} className={sectionClass}>
    <h2 className='text-lg font-bold'>Ghi chú khám</h2>
    <fieldset disabled={disabled} className='grid gap-4 lg:grid-cols-2'>{noteFields.map(([name, label, max]) => <Field key={name} label={label} error={errors[name]}><textarea name={name} rows={4} maxLength={max} value={form[name]} onChange={(event) => setForm((previous) => ({ ...previous, [name]: event.target.value }))} aria-invalid={Boolean(errors[name])} className={inputClass} /></Field>)}<div className='lg:col-span-2'><Button type='submit' variant='primary'>Lưu ghi chú</Button></div></fieldset>
  </form>
}

export default function EncounterClinical({ encounterId, onSessionExpired, section = 'vitals' }) {
  const [state, setState] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const [vital, setVital] = useState(() => ({ measuredAt: localNow(), ...Object.fromEntries(vitalFields.map(([name]) => [name, ''])) }))
  const [diagnosis, setDiagnosis] = useState(emptyDiagnosis)
  const [editing, setEditing] = useState(null)
  const [concept, setConcept] = useState(null)
  const pendingVital = useRef(null)
  const pendingDiagnosis = useRef(null)
  useEffect(() => {
    let active = true
    getEncounterClinical(encounterId).then((data) => { if (active) setState({ data }) }).catch((failure) => {
      if (!active) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else setState({ error: failure.message })
    })
    return () => { active = false }
  }, [encounterId, reload, onSessionExpired])
  function refresh() {
    setState({ loading: true })
    setError('')
    setErrors({})
    setMessage('')
    setEditing(null)
    setDiagnosis(emptyDiagnosis)
    setConcept(null)
    setReload((value) => value + 1)
  }
  function requestKey(ref, data) {
    const payload = JSON.stringify(data)
    if (ref.current?.payload !== payload) ref.current = { payload, key: crypto.randomUUID() }
    return ref.current.key
  }
  async function perform(kind, operation, success) {
    if (busy) return false
    setBusy(kind)
    setError('')
    setErrors({})
    setMessage('')
    try {
      const data = await operation()
      setState({ data })
      setMessage(data.replayed ? 'Yêu cầu đã được lưu trước đó; không tạo bản ghi trùng.' : success)
      return true
    } catch (failure) {
      if (failure.status === 401) onSessionExpired(failure.message)
      else {
        setError(failure.status === 409 ? `${failure.message} Tải lại dữ liệu khám để lấy phiên bản mới; ghi chú chưa lưu sẽ được bỏ.` : failure.message)
        setErrors({ kind, fields: failure.fieldErrors || {} })
      }
      return false
    } finally { setBusy('') }
  }
  async function saveVital(event) {
    event.preventDefault()
    const data = { measuredAt: `${vital.measuredAt}+07:00`, ...Object.fromEntries(vitalFields.map(([name]) => [name, vital[name] === '' ? null : Number(vital[name])])) }
    if (vitalFields.every(([name]) => data[name] == null)) { setError('Nhập ít nhất một số đo sinh hiệu.'); return }
    const saved = await perform('vitals', () => recordVitalSign(encounterId, data, requestKey(pendingVital, data)), 'Đã lưu số đo sinh hiệu.')
    if (saved) {
      pendingVital.current = null
      setVital({ measuredAt: localNow(), ...Object.fromEntries(vitalFields.map(([name]) => [name, ''])) })
    }
  }
  async function saveDiagnosisForm(event) {
    event.preventDefault()
    if (!diagnosis.description.trim()) { setError('Nhập nội dung chẩn đoán.'); return }
    if (diagnosis.diagnosisType === 'FINAL' && !diagnosis.diagnosisConceptId) { setError('Chẩn đoán FINAL phải chọn mã bệnh từ danh mục.'); return }
    const data = { ...diagnosis, ...(editing ? { version: editing.version } : {}) }
    const saved = await perform('diagnosis', () => saveDiagnosis(encounterId, editing?.id, data, editing ? undefined : requestKey(pendingDiagnosis, data)), 'Đã lưu chẩn đoán.')
    if (saved) {
      setEditing(null)
      setDiagnosis(emptyDiagnosis)
      setConcept(null)
      pendingDiagnosis.current = null
    }
  }
  function editDiagnosis(item) {
    setEditing(item)
    setDiagnosis({ diagnosisConceptId: item.diagnosisConceptId, description: item.description, diagnosisType: item.diagnosisType, isPrimary: item.isPrimary })
    setConcept(item.diagnosisConceptId ? { id: item.diagnosisConceptId, codeSystem: item.codeSystem, codeVersion: item.codeVersion, code: item.diagnosisCode, name: item.diagnosisName, primaryAllowed: item.primaryAllowed !== false } : null)
    setErrors({})
    setError('')
  }
  function cancelDiagnosis() {
    setEditing(null)
    setDiagnosis(emptyDiagnosis)
    setConcept(null)
  }
  function chooseConcept(item) {
    setConcept(item)
    setDiagnosis((previous) => ({ ...previous, diagnosisConceptId: item?.id || null,
      description: !previous.description.trim() || previous.description === concept?.name ? item?.name || '' : previous.description,
      isPrimary: item && !item.primaryAllowed ? false : previous.isPrimary }))
  }
  if (state.loading) return <p role='status' className='p-4'>Đang tải dữ liệu khám...</p>
  if (state.error) return <div className={sectionClass}><p role='alert' className='text-clinical-danger'>{state.error}</p><Button onClick={refresh}>Thử lại dữ liệu khám</Button></div>
  const data = state.data
  const disabled = Boolean(busy) || !data.editable
  const fieldErrors = (kind) => errors.kind === kind ? errors.fields : {}
  return <section className='min-w-0 space-y-4' aria-label='Sinh hiệu, ghi chú và chẩn đoán'>
    <div className='flex flex-wrap items-center justify-between gap-3'><h2 className='text-xl font-bold'>Thông tin khám</h2><Button disabled={Boolean(busy)} onClick={refresh}>Tải lại dữ liệu khám</Button></div>
    {!data.editable && <p className='text-sm text-clinical-muted'>Dữ liệu khám chỉ đọc. Chỉ ghi sinh hiệu, ghi chú và chẩn đoán khi lượt khám đã bắt đầu và đang diễn ra.</p>}
    {error && <p role='alert' className='rounded-lg border border-clinical-danger/40 bg-white p-3 text-sm text-clinical-danger'>{error}</p>}
    {message && <p role='status' className='rounded-lg border border-clinical-success/30 bg-white p-3 text-sm text-clinical-success'>{message}</p>}
    {busy && <p role='status' className='text-sm text-clinical-muted'>Đang lưu dữ liệu...</p>}
    <form id='encounter-panel-vitals' role='tabpanel' aria-labelledby='encounter-tab-vitals' hidden={section !== 'vitals'} onSubmit={saveVital} className={sectionClass}>
      <h3 className='text-lg font-bold'>Sinh hiệu</h3>
      <p className='text-sm text-clinical-muted'>Mỗi lần đo là một bản ghi riêng. Nếu cần điều chỉnh, ghi lần đo mới để giữ các số đo trước.</p>
      <fieldset disabled={disabled} className='space-y-4'><Field label='Thời điểm đo (giờ Việt Nam) *' error={fieldErrors('vitals').measuredAt}><input type='datetime-local' step='1' required value={vital.measuredAt} onChange={(event) => setVital((previous) => ({ ...previous, measuredAt: event.target.value }))} className={inputClass} /></Field>
        <div className='grid gap-3 sm:grid-cols-2 xl:grid-cols-4'>{vitalFields.map(([name, label, step, max]) => <Field key={name} label={label} error={fieldErrors('vitals')[name]}><input type='number' min={step} max={max} step={step} value={vital[name]} onChange={(event) => setVital((previous) => ({ ...previous, [name]: event.target.value }))} aria-invalid={Boolean(fieldErrors('vitals')[name])} className={inputClass} /></Field>)}</div><Button type='submit' variant='primary'>Lưu lần đo</Button>
      </fieldset>
      {data.vitals.length === 0 ? <p className='text-sm text-clinical-muted'>Chưa có số đo sinh hiệu.</p> : <div className='overflow-x-auto' role='region' tabIndex={0} aria-label='Lịch sử số đo sinh hiệu'><table className='w-full min-w-[1080px] text-left text-sm'><thead><tr><th className='border-b border-clinical-border p-2'>Thời điểm đo</th>{vitalFields.map(([name, label]) => <th key={name} className='border-b border-clinical-border p-2'>{label}</th>)}</tr></thead><tbody>{data.vitals.map((item) => <tr key={item.id}><td className='border-b border-clinical-border p-2'>{showTime(item.measuredAt)}</td>{vitalFields.map(([name]) => <td key={name} className='border-b border-clinical-border p-2'>{item[name] ?? '—'}</td>)}</tr>)}</tbody></table></div>}
    </form>
    <div id='encounter-panel-notes' role='tabpanel' aria-labelledby='encounter-tab-notes' hidden={section !== 'notes'}>
      <NotesForm key={data.notes.version} notes={data.notes} disabled={disabled} errors={fieldErrors('notes')} onSave={(form) => perform('notes', () => saveEncounterNotes(encounterId, form), 'Đã lưu ghi chú khám.')} />
    </div>
    <section id='encounter-panel-diagnosis' role='tabpanel' aria-labelledby='encounter-tab-diagnosis' hidden={section !== 'diagnosis'} className={sectionClass}><h3 id='diagnosis-title' className='text-lg font-bold'>Chẩn đoán</h3><p className='text-sm text-clinical-muted'>FINAL là chẩn đoán xác định, không tự hoàn tất lượt khám. Chỉ có một chẩn đoán chính FINAL cho mỗi lượt.</p>
      {data.diagnoses.length === 0 ? <p className='text-sm text-clinical-muted'>Chưa ghi nhận chẩn đoán.</p> : <ul className='divide-y divide-clinical-border'>{data.diagnoses.map((item) => <li key={item.id} className='flex flex-wrap items-start justify-between gap-3 py-3'><div className='min-w-0 flex-1'><p className='whitespace-pre-wrap break-words font-semibold'>{item.description}</p><p className='mt-1 text-sm'>{item.diagnosisType === 'FINAL' ? 'Xác định (FINAL)' : 'Sơ bộ'}{item.isPrimary ? ' · Chẩn đoán chính' : ''}</p>{item.diagnosisCode && <p className='mt-1 text-xs text-clinical-muted'>{item.codeSystem}: {item.diagnosisCode}</p>}</div>{data.editable && <Button disabled={Boolean(busy)} onClick={() => editDiagnosis(item)}>Sửa</Button>}</li>)}</ul>}
      <form onSubmit={saveDiagnosisForm} className='space-y-3 border-t border-clinical-border pt-4'><h4 className='font-semibold'>{editing ? 'Sửa chẩn đoán' : 'Thêm chẩn đoán'}</h4><fieldset disabled={disabled} className='space-y-3'>
        <p className='text-sm text-clinical-muted'>Chọn bệnh trong danh mục để điền tên chẩn đoán. Có thể bổ sung diễn giải lâm sàng bên dưới; chẩn đoán sơ bộ được phép chưa chọn mã.</p>
        <DiagnosisLookup key={editing?.id || 'new'} value={concept} onSelect={chooseConcept} disabled={disabled} onSessionExpired={onSessionExpired} />
        <Field label='Nội dung chẩn đoán *' error={fieldErrors('diagnosis').description}><textarea rows={3} required maxLength={1000} placeholder='Tên bệnh được điền sau khi chọn mã; bác sĩ có thể bổ sung diễn giải.' value={diagnosis.description} onChange={(event) => setDiagnosis((previous) => ({ ...previous, description: event.target.value }))} className={inputClass} /></Field>
        <Field label='Loại chẩn đoán' error={fieldErrors('diagnosis').diagnosisType}><select value={diagnosis.diagnosisType} onChange={(event) => setDiagnosis((previous) => ({ ...previous, diagnosisType: event.target.value }))} className={inputClass}><option value='PRELIMINARY'>Sơ bộ</option><option value='FINAL'>Xác định (FINAL)</option></select></Field>
        {editing?.diagnosisCode && !editing.diagnosisConceptId && <p className='text-sm text-clinical-warning'>Bản ghi cũ: {editing.codeSystem} / {editing.diagnosisCode}. Nếu lưu FINAL, cần chọn lại mã từ danh mục; hệ thống không tự đoán phiên bản của mã cũ.</p>}
        <label className='flex min-h-11 items-center gap-2 text-sm'><input type='checkbox' disabled={concept != null && !concept.primaryAllowed} checked={diagnosis.isPrimary} onChange={(event) => setDiagnosis((previous) => ({ ...previous, isPrimary: event.target.checked }))} />Chẩn đoán chính</label>
        <div className='flex flex-wrap gap-2'><Button type='submit' variant='primary'>Lưu chẩn đoán</Button>{editing && <Button onClick={cancelDiagnosis}>Bỏ chỉnh sửa</Button>}</div>
      </fieldset></form>
    </section>
  </section>
}
