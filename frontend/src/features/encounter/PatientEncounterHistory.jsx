import { useEffect, useState } from 'react'
import Button from '../../components/ui/Button'
import { getPreviousEncounters, getPreviousEncounter } from '../../services/encounterApi'

const time = (value) => value ? new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'
const box = 'min-w-0 space-y-3 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'
const notes = [['chiefComplaint', 'Lý do khám'], ['examinationNote', 'Thăm khám'], ['treatmentPlan', 'Kế hoạch điều trị'], ['followupNote', 'Dặn dò']]
const vitals = [['temperatureC', 'Nhiệt độ', '°C'], ['pulseBpm', 'Mạch', 'lần/phút'], ['systolicMmhg', 'Huyết áp tâm thu', 'mmHg'], ['diastolicMmhg', 'Huyết áp tâm trương', 'mmHg'], ['respiratoryRate', 'Nhịp thở', 'lần/phút'], ['spo2Percent', 'SpO₂', '%'], ['weightKg', 'Cân nặng', 'kg'], ['heightCm', 'Chiều cao', 'cm']]

function Text({ children }) { return <p className='whitespace-pre-wrap break-words text-sm'>{children || 'Chưa ghi nhận.'}</p> }
function Snapshot({ value }) {
  if (!value) return <Text />
  let rows
  try { rows = JSON.parse(value) } catch { return <Text>{value}</Text> }
  if (!Array.isArray(rows)) return <Text>{value}</Text>
  return <ul className='space-y-2 text-sm'>{rows.map((row, index) => <li key={index} className='whitespace-pre-wrap break-words'>{Object.entries(row).filter(([key, content]) => !['id', 'version', 'allergenId'].includes(key) && content != null).map(([, content]) => String(content)).join(' · ')}</li>)}</ul>
}

function PreviousDetail({ data }) {
  const { clinical, snapshot, prescriptions, results } = data
  return <div className='space-y-4'>
    <section className={box}><h3 className='text-lg font-bold'>Chẩn đoán</h3>{clinical.diagnoses.length === 0 && <Text />}{clinical.diagnoses.map((item) => <div key={item.id} className='border-t border-clinical-border pt-3'><p className='text-sm font-semibold'>{item.diagnosisType === 'FINAL' ? 'Xác định' : 'Sơ bộ'}{item.isPrimary ? ' · Chính' : ''}</p><Text>{item.description}</Text>{item.diagnosisCode && <Text>{`${item.codeSystem} ${item.codeVersion || '(phiên bản chưa xác định)'} · ${item.diagnosisCode} · ${item.diagnosisName || ''}`}</Text>}</div>)}</section>
    <section className={box}><h3 className='text-lg font-bold'>Ghi chú khám</h3>{notes.map(([key, label]) => <div key={key}><h4 className='text-sm font-semibold'>{label}</h4><Text>{clinical.notes[key]}</Text></div>)}</section>
    <section className={box}><h3 className='text-lg font-bold'>Sinh hiệu</h3>{clinical.vitals.length === 0 && <Text />}{clinical.vitals.map((item) => <div key={item.id} className='border-t border-clinical-border pt-3'><p className='mb-2 text-sm font-semibold'>{time(item.measuredAt)}</p><dl className='grid gap-2 text-sm sm:grid-cols-2'>{vitals.filter(([key]) => item[key] != null).map(([key, label, unit]) => <div key={key}><dt className='text-clinical-muted'>{label}</dt><dd>{item[key]} {unit}</dd></div>)}</dl></div>)}</section>
    <section className={box}><h3 className='text-lg font-bold'>Tiền sử và dị ứng tại lần khám đó</h3><p className='text-sm text-clinical-muted'>Bản chốt {time(snapshot?.recordedAt)}</p><h4 className='font-semibold'>Tiền sử bệnh</h4><Snapshot value={snapshot?.historyText} /><h4 className='font-semibold'>Dị ứng</h4><Snapshot value={snapshot?.allergyText} /></section>
    <section className={box}><h3 className='text-lg font-bold'>Đơn thuốc đã phát hành</h3>{prescriptions.length === 0 && <Text />}{prescriptions.map((rx) => <article key={rx.id} className='space-y-2 border-t border-clinical-border pt-3'><h4 className='font-semibold'>{rx.code} · {time(rx.issuedAt)}</h4><Text>{rx.instruction}</Text><ul className='space-y-3'>{rx.items.map((item, index) => <li key={index}><p className='text-sm font-semibold'>{item.drugName} {item.strength}</p><Text>{`${item.dose} · ${item.route} · ${item.frequency} · ${item.durationDays} ngày · ${item.quantity} ${item.unit}`}</Text>{item.instruction && <Text>{item.instruction}</Text>}</li>)}</ul></article>)}</section>
    <section className={box}><h3 className='text-lg font-bold'>Kết quả CLS đã công bố</h3>{results.length === 0 && <Text />}{results.map((result) => <article key={result.id} className='space-y-2 border-t border-clinical-border pt-3'><h4 className='font-semibold'>{result.serviceName} · Phiên bản {result.versionNo}</h4><p className='text-sm text-clinical-muted'>{time(result.publishedAt)}</p><Text>{result.description}</Text><Text>{result.conclusion}</Text><ul className='space-y-2 text-sm'>{result.values.map((value, index) => <li key={index} className='break-words'>{value.analyteName}: {value.numericValue ?? value.textValue} {value.unit}{value.referenceText ? ` · Tham chiếu: ${value.referenceText}` : ''}{value.abnormalFlag ? ` · ${value.abnormalFlag}` : ''}</li>)}</ul></article>)}</section>
  </div>
}

export default function PatientEncounterHistory({ encounterId, active, onSessionExpired }) {
  const [page, setPage] = useState(1)
  const [reload, setReload] = useState(0)
  const [state, setState] = useState({ loading: true })
  const [selected, setSelected] = useState(null)
  const [detail, setDetail] = useState({ loading: true })
  useEffect(() => {
    if (!active) return
    let live = true
    getPreviousEncounters(encounterId, page).then((data) => { if (live) setState({ data }) }).catch((error) => {
      if (!live) return
      if (error.status === 401) onSessionExpired(error.message)
      else setState({ error: error.message })
    })
    return () => { live = false }
  }, [active, encounterId, page, reload, onSessionExpired])
  useEffect(() => {
    if (!active || selected == null) return
    let live = true
    getPreviousEncounter(encounterId, selected).then((data) => { if (live) setDetail({ data }) }).catch((error) => {
      if (!live) return
      if (error.status === 401) onSessionExpired(error.message)
      else setDetail({ error: error.message })
    })
    return () => { live = false }
  }, [active, encounterId, selected, reload, onSessionExpired])
  function retry() { setState({ loading: true }); setDetail({ loading: true }); setReload((value) => value + 1) }
  const current = selected == null ? state : detail
  return <div className='space-y-4'>
    <div className={box}><h2 className='text-lg font-bold'>Lịch sử khám</h2><p className='text-sm text-clinical-muted'>Các lần khám đã hoàn tất của bệnh nhân, gồm cả lần khám với bác sĩ khác. Nội dung chỉ để xem.</p>{selected != null && <Button onClick={() => setSelected(null)}>Quay lại lịch sử khám</Button>}</div>
    {current.loading && <p role='status'>Đang tải lịch sử khám...</p>}
    {current.error && <div role='alert' className={box}><p className='text-clinical-danger'>{current.error}</p><Button onClick={retry}>Thử lại</Button></div>}
    {selected == null && state.data && <><p role='status' className='text-sm'>{state.data.totalElements} lần khám đã hoàn tất</p><ul className='space-y-3'>{state.data.items.map((item) => <li key={item.encounterId} className={box}><p className='font-bold'>{time(item.completedAt)} · {item.encounterCode}</p><p className='text-sm'>{item.doctorName} · {item.specialtyName} · {item.departmentName} · {item.visitMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}</p><Text>{item.primaryDiagnosis}</Text><Button onClick={() => { setDetail({ loading: true }); setSelected(item.encounterId) }}>Xem lần khám này</Button></li>)}</ul>{state.data.totalPages > 1 && <nav aria-label='Phân trang lịch sử khám' className='flex flex-wrap items-center gap-3'><Button disabled={state.data.page <= 1} onClick={() => { setState({ loading: true }); setPage(state.data.page - 1) }}>Trước</Button><span>Trang {state.data.page}/{state.data.totalPages}</span><Button disabled={state.data.page >= state.data.totalPages} onClick={() => { setState({ loading: true }); setPage(state.data.page + 1) }}>Sau</Button></nav>}</>}
    {selected != null && detail.data && <><div className={box}><h3 className='font-bold'>{detail.data.encounter.encounterCode} · {time(detail.data.encounter.completedAt)}</h3><p className='text-sm'>{detail.data.encounter.doctorName} · {detail.data.encounter.specialtyName}</p></div><PreviousDetail data={detail.data} /></>}
  </div>
}
