import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCircleCheck, faCircleXmark, faCircleExclamation, faXmark } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import { completeEncounter, getEncounterClinical, getEncounterBillingSummary, getClinicalOrders } from '../../services/encounterApi'
import { getPrescriptions } from '../../services/prescriptionApi'
import { completionChecks } from './completionChecks'

const statusStyle = {
  pass: { label: 'Đạt', icon: faCircleCheck, className: 'text-clinical-success' },
  fail: { label: 'Không đạt', icon: faCircleXmark, className: 'text-clinical-danger' },
  warning: { label: 'Cảnh báo', icon: faCircleExclamation, className: 'text-clinical-warning' },
}

export default function EncounterCompletion({ encounterId, patientName, encounterCode, encounterStatus, blockers = [], onLockChange, onCompleted, onSessionExpired }) {
  const dialog = useRef(null)
  const sending = useRef(false)
  const closing = useRef(false)
  const timer = useRef(null)
  const sequence = useRef(0)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [rejectionCode, setRejectionCode] = useState('')
  const [check, setCheck] = useState({ loading: false })
  useEffect(() => { onLockChange(open || pending || busy) }, [open, pending, busy, onLockChange])
  useEffect(() => {
    if (open) { closing.current = false; dialog.current?.showModal() }
  }, [open])
  useEffect(() => () => { window.clearTimeout(timer.current); sequence.current += 1 }, [])
  useEffect(() => {
    if (!pending) return undefined
    const warn = (event) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [pending])
  async function load() {
    const requestId = ++sequence.current
    setCheck({ loading: true })
    const results = await Promise.allSettled([getEncounterClinical(encounterId), getEncounterBillingSummary(encounterId), getPrescriptions(encounterId), getClinicalOrders(encounterId)])
    if (requestId !== sequence.current) return
    const expired = results.find((result) => result.status === 'rejected' && result.reason.status === 401)
    if (expired) { onSessionExpired(expired.reason.message); return }
    const value = (index) => results[index].status === 'fulfilled' ? results[index].value : null
    const clinical = value(0)
    const billing = value(1)
    const prescriptions = value(2)
    const orders = value(3)
    const failures = results.flatMap((result, index) => result.status === 'rejected' ? [`${['Chẩn đoán', 'Viện phí', 'Đơn thuốc', 'Chỉ định CLS'][index]}: ${result.reason.message}`] : [])
    if ((orders && !Array.isArray(orders)) || (clinical && !Array.isArray(clinical.diagnoses)) || (billing && (!Array.isArray(billing.invoices) || billing.invoices.some(({ invoice }) => !Array.isArray(invoice?.lines)))) || (prescriptions && !Array.isArray(prescriptions))) {
      setCheck({ error: 'Dữ liệu kiểm tra không hợp lệ. Hãy kiểm tra lại.' })
      return
    }
    setCheck({ clinical, billing, prescriptions, orders, error: failures.join(' ') })
  }
  function launch() {
    setOpen(true)
    if (!pending) { setError(''); setRejectionCode(''); load() }
  }
  function close() {
    if (sending.current || closing.current || !dialog.current?.open) return
    closing.current = true
    sequence.current += 1
    dialog.current.classList.add('logout-dialog--closing')
    timer.current = window.setTimeout(() => {
      dialog.current?.close()
      dialog.current?.classList.remove('logout-dialog--closing')
      setOpen(false)
    }, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180)
  }
  const rows = completionChecks({ ...check, blockers }).map((row) => row.name === 'Chỉ định cận lâm sàng' && rejectionCode === 'ENCOUNTER_CLS_NOT_SUPPORTED'
    ? { ...row, status: 'fail', detail: 'Hệ thống xác nhận lượt khám có chỉ định CLS; chưa hỗ trợ hoàn tất lượt này tại đây.' } : row)
  const ready = !check.loading && !check.error && !rejectionCode && check.clinical && check.billing && check.prescriptions && rows.every((row) => row.status !== 'fail') && encounterStatus === 'IN_PROGRESS'
  async function complete() {
    if (sending.current || closing.current || (!pending && !ready)) return
    sending.current = true
    setBusy(true)
    setPending(true)
    setError('')
    try {
      const result = await completeEncounter(encounterId)
      if (result.encounterId !== encounterId || result.status !== 'COMPLETED' || !Number.isFinite(Date.parse(result.completedAt))) throw new Error('Chưa đọc được kết quả hoàn tất lượt khám.')
      setPending(false)
      dialog.current?.close()
      onCompleted(result)
    } catch (failure) {
      const knownFailure = [400, 401, 403, 404, 409, 422].includes(failure.status)
      if (knownFailure) setPending(false)
      if (failure.status === 401) onSessionExpired(failure.message)
      else setError(knownFailure ? failure.message : 'Chưa xác minh được kết quả hoàn tất. Hồ sơ tạm khóa; thử lại để lấy kết quả từ máy chủ.')
      if (failure.status === 409) { setRejectionCode(failure.code || 'REJECTED'); load() }
    } finally { sending.current = false; setBusy(false) }
  }
  return <>
    <Button variant='primary' onClick={launch} aria-label='Hoàn tất lượt khám' title='Hoàn tất lượt khám' disabled={busy} className='px-2! sm:px-3! xl:px-4!'><FontAwesomeIcon icon={faCircleCheck} aria-hidden='true' /><span className='hidden xl:inline'>Hoàn tất lượt khám</span></Button>
    {createPortal(<dialog ref={dialog} onCancel={(event) => { event.preventDefault(); close() }} aria-labelledby='complete-title' aria-describedby='complete-description'
      className='logout-dialog m-auto max-h-[calc(100dvh-2rem)] w-[min(40rem,calc(100%-2rem))] overflow-y-auto rounded-2xl border border-clinical-border bg-white p-0 text-clinical-ink shadow-xl backdrop:bg-clinical-ink/60'>
      <header className='flex items-start justify-between gap-4 border-b border-clinical-border p-5 sm:p-6'><div className='min-w-0'><h2 id='complete-title' className='text-xl font-bold'>Hoàn tất lượt khám</h2><p id='complete-description' className='mt-2 text-sm text-clinical-muted'>Kiểm tra các điều kiện trước khi kết thúc lượt khám.</p><p className='mt-2 break-words text-xs text-clinical-muted'>{patientName} · {encounterCode}</p></div><Button autoFocus disabled={busy} onClick={close} aria-label='Đóng hộp kiểm tra hoàn tất' className='shrink-0 border-transparent px-3'><FontAwesomeIcon icon={faXmark} /></Button></header>
      <div className='space-y-4 p-5 sm:p-6' aria-busy={check.loading || busy}>
        {check.loading && <p role='status' className='text-sm text-clinical-muted'>Đang kiểm tra dữ liệu mới nhất…</p>}
        <ul className='divide-y divide-clinical-border'>{rows.map((row) => {
          const status = statusStyle[check.loading ? 'warning' : row.status]
          return <li key={row.name} className='grid grid-cols-[minmax(0,1fr)_auto] items-start gap-x-4 gap-y-1 py-3'><h3 className='text-sm font-semibold'>{row.name}</h3><span className={`flex items-center gap-1.5 text-xs font-bold ${status.className}`}><FontAwesomeIcon icon={status.icon} aria-hidden='true' />{check.loading ? 'Đang kiểm tra' : status.label}</span><p className='col-span-2 text-xs leading-5 text-clinical-muted'>{check.loading ? 'Đang lấy dữ liệu từ máy chủ.' : row.detail}</p></li>
        })}</ul>
        {encounterStatus !== 'IN_PROGRESS' && <p role='alert' className='text-sm text-clinical-warning'>Lượt khám hiện không ở trạng thái Đang khám, chưa thể gửi lệnh hoàn tất.</p>}
        {check.error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{check.error}</p><Button onClick={load} disabled={busy || pending}>Kiểm tra lại</Button></div>}
        {error && <p role='alert' className='text-sm text-clinical-danger'>{error}</p>}
        {pending && !busy && <p role='status' className='text-sm text-clinical-warning'>Thử lại sẽ xác minh cùng lượt khám, không tạo lịch sử hoàn tất trùng.</p>}
        <p className='text-xs leading-5 text-clinical-muted'>Máy chủ kiểm tra lại toàn bộ điều kiện khi xác nhận. Hoàn tất không tự lưu nội dung đang soạn; hồ sơ sau hoàn tất chuyển sang chỉ đọc.</p>
      </div>
      <footer className='flex flex-wrap justify-end gap-2 border-t border-clinical-border bg-clinical-canvas p-4 sm:px-6'><Button disabled={busy} onClick={close}>Đóng</Button><Button variant='primary' disabled={busy || (!pending && !ready)} onClick={complete} aria-busy={busy}><FontAwesomeIcon icon={faCircleCheck} aria-hidden='true' />{busy ? 'Đang hoàn tất…' : pending ? 'Thử lại hoàn tất' : 'Hoàn tất lượt khám'}</Button></footer>
    </dialog>, document.body)}
  </>
}
