import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEye } from '@fortawesome/free-solid-svg-icons'
import InvoiceDetailModal from '../billing/InvoiceDetailModal'
import { EncounterBillingMetrics } from './EncounterCostTable'
import {
  createExaminationInvoice,
  getEncounterBillingSummary,
  issueExaminationInvoice
} from '../../services/encounterApi'

const money = (value) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0
  }).format(BigInt(value))
const issuedTime = (value) =>
  new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Saigon',
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(new Date(value))
const paymentStatuses = {
  NOT_DUE: 'Chưa phát hành',
  NOT_APPLICABLE: 'Không áp dụng',
  UNPAID: 'Chưa thanh toán',
  PARTIAL: 'Chưa thanh toán đủ',
  PAID: 'Đã thanh toán'
}
const statuses = {
  DRAFT: 'Bản nháp',
  ISSUED: 'Đã phát hành',
  VOID: 'Đã hủy nháp'
}

export default function EncounterInvoices({
  encounterId,
  active,
  writable,
  revision = 0,
  onSessionExpired,
  onGuardChange
}) {
  const [state, setState] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [confirmed, setConfirmed] = useState({})
  const [selectedInvoice, setSelectedInvoice] = useState(null)
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
    getEncounterBillingSummary(encounterId)
      .then((data) => {
        if (current && read === reads.current) {
          setState({ data })
          setConfirmed({})
        }
      })
      .catch((failure) => {
        if (!current || read !== reads.current) return
        if (failure.status === 401) onSessionExpired(failure.message)
        else setState({ error: failure.message })
      })
    return () => {
      current = false
    }
  }, [active, encounterId, reload, revision, onSessionExpired])

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
    if (busyRef.current || !writable || (invoice && !confirmed[invoice.id]))
      return
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
        ? await issueExaminationInvoice(
            encounterId,
            invoice.id,
            invoice.version,
            commands.current[signature]
          )
        : await createExaminationInvoice(
            encounterId,
            commands.current[signature]
          )
      if (
        !result.id ||
        !Array.isArray(result.lines) ||
        (invoice && result.status !== 'ISSUED')
      )
        throw new Error(
          'Chưa xác minh được hóa đơn đã phát hành. Hãy tải lại dữ liệu trước khi tiếp tục.'
        )
      reads.current += 1
      setState((previous) => ({
        stale: true,
        data: {
          ...previous.data,
          invoices: [
            ...(previous.data?.invoices || []).filter(
              (item) => item.invoice.id !== result.id
            ),
            { invoice: result }
          ].sort((a, b) => a.invoice.id - b.invoice.id)
        }
      }))
      delete commands.current[signature]
      setConfirmed({})
      if (invoice) setSelectedInvoice(result.id)
      setMessage(
        result.replayed
          ? 'Yêu cầu đã được lưu trước đó; đã lấy lại hóa đơn hiện có.'
          : invoice
            ? 'Đã phát hành hóa đơn.'
            : 'Đã tạo hóa đơn nháp. Hãy kiểm tra trước khi phát hành.'
      )
      try {
        const refreshed = await getEncounterBillingSummary(encounterId)
        setState({ data: refreshed })
      } catch (failure) {
        if (failure.status === 401) onSessionExpired(failure.message)
        else
          setError({
            message:
              'Hóa đơn đã được lưu. Chưa tải lại được dữ liệu; chọn “Tải lại hóa đơn” để cập nhật.'
          })
      }
    } catch (failure) {
      if (failure.status === 401) onSessionExpired(failure.message)
      else
        setError({ message: failure.message, conflict: failure.status === 409 })
    } finally {
      setBusy('')
      busyRef.current = false
    }
  }

  const invoices = [...(state.data?.invoices || [])].sort(
    (a, b) => b.invoice.id - a.invoice.id
  )
  const hasExamination = state.stale || state.data?.examinationInvoiced
  const disabled = Boolean(busy) || Boolean(error?.conflict) || state.stale
  const totalCost = state.stale
    ? null
    : state.data?.costs
        .filter((line) => line.status !== 'CANCELLED')
        .reduce((sum, line) => sum + BigInt(line.amount), 0n)
  return (
    <section
      className='min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'
      aria-labelledby='invoice-heading'
      aria-busy={Boolean(busy) || state.loading}
    >
      <div className='flex flex-wrap items-start justify-between gap-3'>
        <div className='min-w-0'>
          <h2 id='invoice-heading' className='text-lg font-bold'>
            Viện phí và hóa đơn
          </h2>
        </div>
        <Button disabled={Boolean(busy)} onClick={refresh}>
          Tải lại hóa đơn
        </Button>
      </div>
      {state.loading && <p role='status'>Đang tải hóa đơn...</p>}
      {state.error && (
        <div role='alert' className='space-y-2 text-sm text-clinical-danger'>
          <p>{state.error}</p>
          <Button onClick={refresh}>Thử lại danh sách hóa đơn</Button>
        </div>
      )}
      {error && (
        <div
          role='alert'
          className='space-y-2 rounded-lg border border-clinical-danger/30 p-3 text-sm text-clinical-danger'
        >
          <p>{error.message}</p>
          {error.conflict && <p>Chọn “Tải lại hóa đơn” để cập nhật.</p>}
        </div>
      )}
      {message && (
        <p
          role='status'
          className='rounded-lg border border-clinical-success/30 p-3 text-sm font-semibold text-clinical-success'
        >
          {message}
        </p>
      )}
      {state.data && (
        <>
          <EncounterBillingMetrics {...(state.stale ? {} : state.data)} />
          {state.stale && (
            <p role='status' className='text-sm text-clinical-muted'>
              {busy
                ? 'Đang cập nhật viện phí…'
                : 'Cần tải lại để cập nhật viện phí.'}
            </p>
          )}
          {!state.stale && (
            <div className='space-y-3'>
              <p className='flex flex-wrap justify-between gap-2 py-2 font-bold'>
                <span>Tổng chi phí hiện tại</span>
                <span className='tabular-nums'>{money(totalCost)}</span>
              </p>
            </div>
          )}
          <div className='flex flex-wrap items-center justify-between gap-3 border-t border-clinical-border pt-4'>
            <span className='rounded-full bg-suncare-100 px-3 py-1 text-xs font-semibold text-clinical-warning'>
              {hasExamination ? 'Đã lập hóa đơn phí khám' : 'Chưa lập hóa đơn'}
            </span>
            {writable && !hasExamination && (
              <Button
                variant='primary'
                disabled={disabled}
                onClick={() => run()}
              >
                {busy === 'create'
                  ? 'Đang lập hóa đơn...'
                  : 'Lập hóa đơn phí khám'}
              </Button>
            )}
          </div>
          <h3 className='border-t border-clinical-border pt-5 text-lg font-bold'>
            Danh sách hóa đơn
          </h3>
          {!writable && (
            <p className='text-sm text-clinical-muted'>
              Chỉ được lập hoặc phát hành hóa đơn sau khi bắt đầu khám và trước
              khi hoàn tất lượt khám.
            </p>
          )}
          {invoices.length === 0 && (
            <p className='text-sm text-clinical-muted'>
              Lượt khám chưa có hóa đơn.
            </p>
          )}
          {invoices.length > 0 && (
            <div
              className='overflow-x-auto rounded-xl border border-clinical-border'
              role='region'
              aria-label='Danh sách hóa đơn viện phí'
              tabIndex={0}
            >
              <table className='w-full min-w-[62rem] text-left text-xs'>
                <thead className='bg-clinical-canvas'>
                  <tr>
                    {[
                      'STT',
                      'Mã hóa đơn',
                      'Ngày giờ phát hành',
                      'Giá trị hóa đơn',
                      'Đã thanh toán',
                      'Còn phải thanh toán',
                      'Trạng thái hóa đơn',
                      'Trạng thái thanh toán',
                      'Thao tác'
                    ].map((label) => (
                      <th key={label} className='p-3'>
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {invoices.map(
                    (
                      {
                        invoice,
                        receivedAmount,
                        paymentStatus,
                        outstandingAmount
                      },
                      index
                    ) => (
                      <tr
                        key={invoice.id}
                        tabIndex={0}
                        aria-label={`Xem hóa đơn ${invoice.code}`}
                        onClick={(event) => {
                          if (!event.target.closest('button,input,label'))
                            setSelectedInvoice(invoice.id)
                        }}
                        onKeyDown={(event) => {
                          if (
                            event.target === event.currentTarget &&
                            ['Enter', ' '].includes(event.key)
                          ) {
                            event.preventDefault()
                            setSelectedInvoice(invoice.id)
                          }
                        }}
                        className='cursor-pointer border-t border-clinical-border align-top hover:bg-suncare-50 focus-visible:outline-2 focus-visible:outline-suncare-700'
                      >
                        <td className='p-3'>{index + 1}</td>
                        <td className='max-w-52 break-all p-3 font-semibold'>
                          {invoice.code}
                        </td>
                        <td className='p-3'>
                          {invoice.issuedAt
                            ? issuedTime(invoice.issuedAt)
                            : '—'}
                        </td>
                        <td className='whitespace-nowrap p-3 font-semibold tabular-nums'>
                          {money(invoice.totalAmount)}
                        </td>
                        <td className='whitespace-nowrap p-3 tabular-nums text-clinical-success'>
                          {state.stale || receivedAmount == null
                            ? '—'
                            : money(receivedAmount)}
                        </td>
                        <td className='whitespace-nowrap p-3 tabular-nums text-clinical-warning'>
                          {state.stale || outstandingAmount == null
                            ? '—'
                            : money(outstandingAmount)}
                        </td>
                        <td className='p-3'>
                          <span className='inline-block rounded-md bg-clinical-canvas px-2 py-1 font-semibold'>
                            {statuses[invoice.status] || 'Chưa xác minh'}
                          </span>
                        </td>
                        <td className='p-3'>
                          <span
                            className={`inline-block rounded-md px-2 py-1 font-semibold ${paymentStatus === 'PAID' ? 'bg-clinical-leaf-soft text-clinical-success' : 'bg-suncare-100 text-clinical-warning'}`}
                          >
                            {state.stale
                              ? 'Đang cập nhật'
                              : paymentStatuses[paymentStatus] ||
                                'Đang cập nhật'}
                          </span>
                        </td>
                        <td className='p-3'>
                          <div className='space-y-2'>
                            <Button
                              id={`invoice-view-${invoice.id}`}
                              aria-label={`Xem chi tiết hóa đơn ${invoice.code}`}
                              title='Xem chi tiết hóa đơn'
                              onClick={() => setSelectedInvoice(invoice.id)}
                              className='px-3'
                            >
                              <FontAwesomeIcon icon={faEye} />
                            </Button>
                            {invoice.status === 'DRAFT' && writable && (
                              <>
                                <label className='flex min-h-11 items-start gap-2'>
                                  <input
                                    type='checkbox'
                                    checked={Boolean(confirmed[invoice.id])}
                                    disabled={disabled}
                                    onChange={(event) =>
                                      setConfirmed((previous) => ({
                                        ...previous,
                                        [invoice.id]: event.target.checked
                                      }))
                                    }
                                    className='mt-1 h-4 w-4 shrink-0 accent-suncare-700'
                                  />
                                  <span>
                                    Đã kiểm tra nội dung, xác nhận phát hành.
                                  </span>
                                </label>
                                <Button
                                  variant='primary'
                                  disabled={disabled || !confirmed[invoice.id]}
                                  onClick={() => run(invoice)}
                                >
                                  {busy === `issue-${invoice.id}`
                                    ? 'Đang phát hành...'
                                    : 'Phát hành hóa đơn'}
                                </Button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  )}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
      {selectedInvoice != null && (
        <InvoiceDetailModal
          key={selectedInvoice}
          encounterId={encounterId}
          invoiceId={selectedInvoice}
          onSessionExpired={onSessionExpired}
          onClose={() => setSelectedInvoice(null)}
        />
      )}
    </section>
  )
}
