import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import { createExaminationInvoice, getEncounterInvoices, issueExaminationInvoice } from '../../services/encounterApi'

const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value)
const issuedTime = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
const statuses = { DRAFT: 'Bản nháp', ISSUED: 'Đã phát hành', VOID: 'Đã hủy nháp' }

export default function EncounterInvoices({ encounterId, active, writable, onSessionExpired }) {
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
    if (!active) return undefined
    let current = true
    const read = ++reads.current
    getEncounterInvoices(encounterId).then((data) => {
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
      setState((previous) => ({ data: [...(previous.data || []).filter((item) => item.id !== result.id), result].sort((a, b) => a.id - b.id) }))
      delete commands.current[signature]
      setConfirmed({})
      setMessage(result.replayed ? 'Yêu cầu đã được lưu trước đó; đã lấy lại hóa đơn hiện có.' : invoice ? 'Đã phát hành hóa đơn.' : 'Đã tạo hóa đơn nháp. Hãy kiểm tra trước khi phát hành.')
    } catch (failure) {
      if (failure.status === 401) onSessionExpired(failure.message)
      else setError({ message: failure.message, conflict: failure.status === 409 })
    } finally { setBusy(''); busyRef.current = false }
  }

  const invoices = state.data || []
  const hasExamination = invoices.some((invoice) => invoice.status !== 'VOID' && invoice.lines.some((line) => line.lineType === 'EXAMINATION'))
  const disabled = Boolean(busy) || Boolean(error?.conflict)
  return <section className='min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5' aria-labelledby='invoice-heading' aria-busy={Boolean(busy) || state.loading}>
    <div className='flex flex-wrap items-start justify-between gap-3'>
      <div className='min-w-0'><h2 id='invoice-heading' className='text-lg font-bold'>Hóa đơn phí khám</h2><p className='mt-1 text-sm text-clinical-muted'>Chứng từ thu dịch vụ nội bộ. Phí khám dùng giá đã chốt khi đặt lịch.</p></div>
      <Button disabled={Boolean(busy)} onClick={refresh}>Tải lại hóa đơn</Button>
    </div>
    {state.loading && <p role='status'>Đang tải hóa đơn...</p>}
    {state.error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{state.error}</p><Button onClick={refresh}>Thử lại danh sách hóa đơn</Button></div>}
    {error && <div role='alert' className='space-y-2 rounded-lg border border-clinical-danger/30 p-3 text-sm text-clinical-danger'><p>{error.message}</p><p>{error.conflict ? 'Chọn “Tải lại hóa đơn” để kiểm tra phiên bản và trạng thái mới.' : 'Có thể thử lại thao tác với cùng yêu cầu. Nếu cơ sở chưa được cấu hình, cần quản trị viên bổ sung trước.'}</p></div>}
    {message && <p role='status' className='rounded-lg border border-clinical-success/30 p-3 text-sm font-semibold text-clinical-success'>{message}</p>}
    {state.data && <>
      {!writable && <p className='text-sm text-clinical-muted'>Chỉ được lập hoặc phát hành hóa đơn sau khi bắt đầu khám và trước khi hoàn tất lượt khám.</p>}
      {invoices.length === 0 && <p className='text-sm text-clinical-muted'>Lượt khám chưa có hóa đơn.</p>}
      {writable && !hasExamination && <Button variant='primary' disabled={disabled} onClick={() => run()}>{busy === 'create' ? 'Đang tạo nháp...' : 'Tạo hóa đơn phí khám'}</Button>}
      {invoices.map((invoice) => <article key={invoice.id} aria-label={`Hóa đơn ${invoice.code}`} className='min-w-0 space-y-4 border-t border-clinical-border pt-4'>
        <div className='flex flex-wrap items-start justify-between gap-2'><h3 className='min-w-0 break-all font-semibold'>{invoice.code}</h3><span className='rounded bg-suncare-100 px-2 py-1 text-sm font-semibold'>{statuses[invoice.status] || invoice.status}</span></div>
        <dl className='grid min-w-0 gap-3 text-sm sm:grid-cols-2'>
          <div><dt className='text-clinical-muted'>Bệnh nhân</dt><dd className='break-words font-semibold'>{invoice.patientName}</dd><dd className='break-words'>{invoice.patientAddress || 'Chưa ghi nhận địa chỉ'}</dd></div>
          <div><dt className='text-clinical-muted'>Cơ sở</dt><dd className='break-words font-semibold'>{invoice.facilityName}</dd><dd className='break-words'>{invoice.facilityAddress}</dd></div>
          {invoice.issuedAt && <div><dt className='text-clinical-muted'>Phát hành lúc</dt><dd>{issuedTime(invoice.issuedAt)}</dd></div>}
        </dl>
        <div className='overflow-x-auto rounded-lg border border-clinical-border' role='region' aria-label={`Chi tiết dòng thu ${invoice.code}`} tabIndex={0}>
          <table className='w-full min-w-[32rem] text-left text-sm'><thead className='bg-clinical-canvas'><tr><th className='p-3'>Dịch vụ</th><th className='p-3'>Số lượng</th><th className='p-3 text-right'>Đơn giá</th><th className='p-3 text-right'>Thành tiền</th></tr></thead><tbody>{invoice.lines.map((line) => <tr key={line.id} className='border-t border-clinical-border'><td className='p-3'><span className='block font-semibold'>{line.serviceName}</span>{line.status === 'CANCELLED' && <span className='text-clinical-warning'>Đã hủy dòng thu</span>}</td><td className='p-3'>{line.quantity} {line.unitName}</td><td className='whitespace-nowrap p-3 text-right'>{money(line.unitPrice)}</td><td className='whitespace-nowrap p-3 text-right'>{money(line.amount)}</td></tr>)}</tbody></table>
        </div>
        <p className='text-right text-lg font-bold'>Tổng giá trị hóa đơn: {money(invoice.totalAmount)}</p>
        {invoice.status === 'ISSUED' && <p className='text-sm text-clinical-muted'>Nội dung đã chốt khi phát hành. Trạng thái này chưa xác nhận bệnh nhân đã thanh toán.</p>}
        {invoice.status === 'DRAFT' && writable && <div className='space-y-3'>
          <p className='text-sm text-clinical-muted'>Thông tin bệnh nhân, cơ sở và tên dịch vụ sẽ được chốt tại thời điểm phát hành; giá phí khám giữ nguyên.</p>
          <label className='flex min-h-11 items-start gap-2 text-sm'><input type='checkbox' checked={Boolean(confirmed[invoice.id])} disabled={disabled} onChange={(event) => setConfirmed((previous) => ({ ...previous, [invoice.id]: event.target.checked }))} className='mt-1 h-4 w-4 shrink-0 accent-suncare-700' />Tôi đã kiểm tra nội dung hóa đơn và xác nhận phát hành.</label>
          <Button variant='primary' disabled={disabled || !confirmed[invoice.id]} onClick={() => run(invoice)}>{busy === `issue-${invoice.id}` ? 'Đang phát hành...' : 'Phát hành hóa đơn'}</Button>
        </div>}
      </article>)}
    </>}
  </section>
}
