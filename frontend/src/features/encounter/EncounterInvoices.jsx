import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import EncounterCostTable, { EncounterBillingMetrics } from './EncounterCostTable'
import { createExaminationInvoice, getEncounterBillingSummary, issueExaminationInvoice } from '../../services/encounterApi'

const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(BigInt(value))
const issuedTime = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
const paymentStatuses = { NOT_DUE: 'Chưa phát hành', NOT_APPLICABLE: 'Không áp dụng', UNPAID: 'Chưa thanh toán', PARTIAL: 'Chưa thanh toán đủ', PAID: 'Đã thanh toán' }
const statuses = { DRAFT: 'Bản nháp', ISSUED: 'Đã phát hành', VOID: 'Đã hủy nháp' }

export default function EncounterInvoices({ encounterId, active, writable, onSessionExpired, onGuardChange }) {
  const [state, setState] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [confirmed, setConfirmed] = useState({})
  const commands = useRef({})
  const busyRef = useRef(false)
  const reads = useRef(0)
  useEffect(() => {
    onGuardChange?.('invoice', Boolean(busy))
    return () => onGuardChange?.('invoice', false)
  }, [busy, onGuardChange])
  useEffect(() => {
    if (!active) return undefined
    let current = true
    const read = ++reads.current
    getEncounterBillingSummary(encounterId).then((data) => {
      if (current && read === reads.current) setState({ data })
    }).catch((failure) => {
      if (!current || read !== reads.current) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else setState({ error: failure.message })
    })
    return () => { current = false }
  }, [active, encounterId, reload, onSessionExpired])

  function refresh() {
    if (busyRef.current) return
    setState({ loading: true })
    setError(null)
    setMessage('')
    setConfirmed({})
    // Preserve command keys across reloads: a failed response may already have committed.
    setReload((previous) => previous + 1)
  }

  async function run(invoice) {
    if (busyRef.current || !writable || (invoice && !confirmed[invoice.id])) return
    busyRef.current = true
    reads.current += 1
    const action = invoice ? `issue-${invoice.id}` : 'create'
    const signature = invoice ? `${invoice.id}:${invoice.version}` : 'create'
    commands.current[signature] ||= crypto.randomUUID()
    setBusy(action)
    setError(null)
    setMessage('')
    try {
      const result = invoice
        ? await issueExaminationInvoice(encounterId, invoice.id, invoice.version, commands.current[signature])
        : await createExaminationInvoice(encounterId, commands.current[signature])
      reads.current += 1
      setState((previous) => ({ stale: true, data: { ...previous.data, invoices: [...(previous.data?.invoices || []).filter((item) => item.invoice.id !== result.id), { invoice: result }].sort((a, b) => a.invoice.id - b.invoice.id) } }))
      delete commands.current[signature]
      setConfirmed({})
      setMessage(result.replayed ? 'Yêu cầu đã được lưu trước đó; đã lấy lại hóa đơn hiện có.' : invoice ? 'Đã phát hành hóa đơn.' : 'Đã tạo hóa đơn nháp. Hãy kiểm tra trước khi phát hành.')
      try {
        const refreshed = await getEncounterBillingSummary(encounterId)
        setState({ data: refreshed })
      } catch (failure) {
        if (failure.status === 401) onSessionExpired(failure.message)
        else setError({ message: 'Hóa đơn đã được lưu. Chưa tải lại được dữ liệu; chọn “Tải lại hóa đơn” để cập nhật.' })
      }
    } catch (failure) {
      if (failure.status === 401) onSessionExpired(failure.message)
      else setError({ message: failure.message, conflict: failure.status === 409 })
    } finally { setBusy(''); busyRef.current = false }
  }

  const invoices = state.data?.invoices || []
  const hasExamination = state.stale || state.data?.examinationInvoiced
  const disabled = Boolean(busy) || Boolean(error?.conflict) || state.stale
  const totalCost = state.stale ? null : state.data?.costs.filter((line) => line.status !== 'CANCELLED').reduce((sum, line) => sum + BigInt(line.amount), 0n)
  return <section className='min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5' aria-labelledby='invoice-heading' aria-busy={Boolean(busy) || state.loading}>
    <div className='flex flex-wrap items-start justify-between gap-3'>
      <div className='min-w-0'><h2 id='invoice-heading' className='text-lg font-bold'>Viện phí và hóa đơn</h2></div>
      <Button disabled={Boolean(busy)} onClick={refresh}>Tải lại hóa đơn</Button>
    </div>
    {state.loading && <p role='status'>Đang tải hóa đơn...</p>}
    {state.error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{state.error}</p><Button onClick={refresh}>Thử lại danh sách hóa đơn</Button></div>}
    {error && <div role='alert' className='space-y-2 rounded-lg border border-clinical-danger/30 p-3 text-sm text-clinical-danger'><p>{error.message}</p>{error.conflict && <p>Chọn “Tải lại hóa đơn” để cập nhật.</p>}</div>}
    {message && <p role='status' className='rounded-lg border border-clinical-success/30 p-3 text-sm font-semibold text-clinical-success'>{message}</p>}
    {state.data && <>
      <EncounterBillingMetrics {...(state.stale ? {} : state.data)} />
      {state.stale && <p role='status' className='text-sm text-clinical-muted'>{busy ? 'Đang cập nhật viện phí…' : 'Cần tải lại để cập nhật viện phí.'}</p>}
      {!state.stale && <div className='space-y-3'><h3 className='font-semibold'>Chi tiết chi phí khám bệnh</h3><EncounterCostTable lines={state.data.costs} /><p className='flex flex-wrap justify-between gap-2 py-2 font-bold'><span>Tổng chi phí hiện tại</span><span className='tabular-nums'>{money(totalCost)}</span></p></div>}
      <div className='flex flex-wrap items-center justify-between gap-3 border-t border-clinical-border pt-4'><span className='rounded-full bg-suncare-100 px-3 py-1 text-xs font-semibold text-clinical-warning'>{hasExamination ? 'Đã lập hóa đơn phí khám' : 'Chưa lập hóa đơn'}</span>{writable && !hasExamination && <Button variant='primary' disabled={disabled} onClick={() => run()}>{busy === 'create' ? 'Đang lập hóa đơn...' : 'Lập hóa đơn phí khám'}</Button>}</div>
      <h3 className='border-t border-clinical-border pt-5 text-lg font-bold'>Danh sách hóa đơn</h3>
      {!writable && <p className='text-sm text-clinical-muted'>Chỉ được lập hoặc phát hành hóa đơn sau khi bắt đầu khám và trước khi hoàn tất lượt khám.</p>}
      {invoices.length === 0 && <p className='text-sm text-clinical-muted'>Lượt khám chưa có hóa đơn.</p>}
      {invoices.map(({ invoice, receivedAmount, paymentStatus }) => <article key={invoice.id} aria-label={`Hóa đơn ${invoice.code}`} className='min-w-0 space-y-4 border-t border-clinical-border pt-4'>
        <div className='flex flex-wrap items-start justify-between gap-2'><h3 className='min-w-0 break-all font-semibold'>{invoice.code}</h3><div className='flex flex-wrap gap-2'><span className='rounded-full bg-clinical-canvas px-3 py-1 text-xs font-semibold'>Hóa đơn: {statuses[invoice.status] || invoice.status}</span><span className={`rounded-full px-3 py-1 text-xs font-semibold ${paymentStatus === 'PAID' ? 'bg-clinical-leaf-soft text-clinical-success' : 'bg-suncare-100 text-clinical-warning'}`}>Thanh toán: {paymentStatuses[paymentStatus] || 'Đang cập nhật'}</span></div></div>
        <dl className='grid min-w-0 gap-3 text-sm sm:grid-cols-2'>
          <div><dt className='text-clinical-muted'>Bệnh nhân</dt><dd className='break-words font-semibold'>{invoice.patientName}</dd><dd className='break-words'>{invoice.patientAddress || 'Chưa ghi nhận địa chỉ'}</dd></div>
          <div><dt className='text-clinical-muted'>Cơ sở</dt><dd className='break-words font-semibold'>{invoice.facilityName}</dd><dd className='break-words'>{invoice.facilityAddress}</dd></div>
          {invoice.issuedAt && <div><dt className='text-clinical-muted'>Phát hành lúc</dt><dd>{issuedTime(invoice.issuedAt)}</dd></div>}
        </dl>
        <EncounterCostTable lines={invoice.lines} label={`Chi tiết dòng thu ${invoice.code}`} />
        <div className='flex flex-wrap justify-between gap-3 text-sm'><p>Thực thu: <strong className='tabular-nums'>{receivedAmount == null ? '—' : money(receivedAmount)}</strong></p><p className='font-bold'>Giá trị hóa đơn: <span className='tabular-nums'>{money(invoice.totalAmount)}</span></p></div>

        {invoice.status === 'DRAFT' && writable && <div className='space-y-3'>

          <label className='flex min-h-11 items-start gap-2 text-sm'><input type='checkbox' checked={Boolean(confirmed[invoice.id])} disabled={disabled} onChange={(event) => setConfirmed((previous) => ({ ...previous, [invoice.id]: event.target.checked }))} className='mt-1 h-4 w-4 shrink-0 accent-suncare-700' />Tôi đã kiểm tra nội dung hóa đơn và xác nhận phát hành.</label>
          <Button variant='primary' disabled={disabled || !confirmed[invoice.id]} onClick={() => run(invoice)}>{busy === `issue-${invoice.id}` ? 'Đang phát hành...' : 'Phát hành hóa đơn'}</Button>
        </div>}
      </article>)}
    </>}
  </section>
}
