import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faMoneyBill, faSearch, faRotateRight } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import CashierConfirmDialog from './CashierConfirmDialog'
import { collectCashPayment, getCashierWorkspace, getCashInvoiceDetail } from '../../services/billingApi'

const money = (value) => value == null ? '—' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(BigInt(value))
const time = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
const tabs = [['PENDING', 'Chờ thanh toán'], ['PAID', 'Đã thanh toán'], ['ALL', 'Tất cả']]
const panelClass = 'min-w-0 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'

function restorePending(key) {
  try {
    const value = JSON.parse(sessionStorage.getItem(key))
    if (value && Number.isSafeInteger(value.invoiceId) && value.invoiceId > 0
      && typeof value.amount === 'string' && /^[0-9]{1,19}$/.test(value.amount) && BigInt(value.amount) > 0n
      && typeof value.code === 'string' && /^[A-Za-z0-9_-]{1,40}$/.test(value.code)
      && typeof value.key === 'string' && /^[\w-]{1,100}$/.test(value.key)) return value
  } catch { /* A new payment requires a recoverable command below. */ }
  return null
}

export default function CashierPayments({ accountId, onSessionExpired }) {
  const storageKey = `suncare.cash.pending.${accountId}`
  const [pending, setPending] = useState(() => restorePending(storageKey))
  const initialPending = useRef(pending)
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState({ search: '', status: 'PENDING', page: 1, pageSize: 10 })
  const [list, setList] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const [view, setView] = useState({})
  const [busy, setBusy] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [error, setError] = useState(null)
  const [receipt, setReceipt] = useState(null)
  const commandBusy = useRef(false)
  const detailReads = useRef(0)
  const mounted = useRef(true)
  const detailSection = useRef(null)
  const payButton = useRef(null)
  const locked = busy || Boolean(pending) || confirmOpen

  useEffect(() => {
    mounted.current = true
    const recovery = initialPending.current
    if (recovery) {
      const generation = ++detailReads.current
      getCashInvoiceDetail(recovery.invoiceId).then((data) => {
        if (mounted.current && generation === detailReads.current) setView({ data })
      }).catch((failure) => {
        if (!mounted.current || generation !== detailReads.current) return
        if (failure.status === 401) onSessionExpired(failure.message)
        else setView({ error: failure.message, id: recovery.invoiceId })
      })
    }
    return () => { mounted.current = false; detailReads.current += 1 }
  }, [onSessionExpired])

  useEffect(() => {
    if (locked || query.search === search.trim()) return undefined
    const timer = window.setTimeout(() => {
      setList({ loading: true })
      setQuery((previous) => ({ ...previous, search: search.trim(), page: 1 }))
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search, locked, query.search])

  useEffect(() => {
    let current = true
    getCashierWorkspace(query).then((data) => { if (current) setList({ data }) }).catch((failure) => {
      if (!current) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else setList({ error: failure.message })
    })
    return () => { current = false }
  }, [query, reload, onSessionExpired])

  useEffect(() => {
    if (!view.data) return
    detailSection.current?.querySelector('h2')?.focus({ preventScroll: true })
    detailSection.current?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }, [view.data])

  function changeQuery(changes) {
    if (locked) return
    setList({ loading: true })
    setQuery((previous) => ({ ...previous, ...changes }))
  }
  function refreshList() { setList({ loading: true }); setReload((value) => value + 1) }
  async function selectInvoice(id, refresh = false) {
    if (commandBusy.current || confirmOpen || (!refresh && pending)) return
    const generation = ++detailReads.current
    setView({ loading: true, id })
    setError(null)
    if (!refresh) setReceipt(null)
    try {
      const data = await getCashInvoiceDetail(id)
      if (mounted.current && generation === detailReads.current) setView({ data })
    } catch (failure) {
      if (!mounted.current || generation !== detailReads.current) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else setView({ error: failure.message, id })
    }
  }
  async function collect() {
    if (commandBusy.current) return
    const command = pending || (view.data?.canCollect && confirmOpen ? {
      invoiceId: view.data.invoice.id, code: view.data.invoice.code,
      amount: view.data.paymentAmount, key: crypto.randomUUID(),
    } : null)
    if (!command) return
    try { sessionStorage.setItem(storageKey, JSON.stringify(command)) }
    catch { setError({ message: 'Không lưu được yêu cầu để thử lại. Hãy cho phép lưu trữ phiên trước khi thu tiền.' }); setConfirmOpen(false); return }
    commandBusy.current = true
    detailReads.current += 1
    setBusy(true)
    setPending(command)
    setConfirmOpen(false)
    setError(null)
    setReceipt(null)
    try {
      const result = await collectCashPayment(command.invoiceId, command.amount, command.key)
      if (!mounted.current) return
      sessionStorage.removeItem(storageKey)
      setPending(null)
      setReceipt(result)
      setConfirmOpen(false)
      refreshList()
      setView({ loading: true, id: command.invoiceId })
      try { const data = await getCashInvoiceDetail(command.invoiceId); if (mounted.current) setView({ data }) }
      catch (failure) {
        if (!mounted.current) return
        if (failure.status === 401) onSessionExpired(failure.message)
        else setView({ error: 'Đã ghi nhận thanh toán. Chưa tải được chi tiết mới; hãy tải lại.', id: command.invoiceId })
      }
    } catch (failure) {
      if (!mounted.current) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else {
        const definitive = [400, 409, 422].includes(failure.status)
        if (definitive) { sessionStorage.removeItem(storageKey); setPending(null) }
        setError({ message: failure.message, conflict: definitive })
      }
    } finally {
      commandBusy.current = false
      if (mounted.current) { setBusy(false); setConfirmOpen(false) }
    }
  }

  const data = view.data
  const invoice = data?.invoice
  const statistics = list.data?.statistics
  return <section className='min-w-0 space-y-4 text-clinical-ink' aria-labelledby='cashier-heading'>
    <header className='flex flex-wrap items-center justify-between gap-3'><h1 id='cashier-heading' className='text-xl font-bold sm:text-2xl'>Quản lý thu viện phí</h1><Button disabled={locked || list.loading} onClick={refreshList}><FontAwesomeIcon icon={faRotateRight} />Tải lại</Button></header>
    <dl className='grid gap-3 sm:grid-cols-3'>
      {[['Chờ thanh toán', statistics?.pending, 'text-clinical-warning'], ['Đã thanh toán', statistics?.paid, 'text-clinical-success'], ['Thực thu hôm nay', statistics ? money(statistics.receivedToday) : null, 'text-clinical-ink']].map(([label, value, color]) => <div key={label} className={panelClass}><dt className='text-sm text-clinical-muted'>{label}</dt><dd className={`mt-2 break-words text-2xl font-bold tabular-nums ${color}`}>{value ?? '—'}</dd></div>)}
    </dl>
    <form onSubmit={(event) => { event.preventDefault(); changeQuery({ search: search.trim(), page: 1 }) }} role='search' className='flex min-w-0 gap-2'>
      <label className='relative block min-w-0 flex-1'><span className='sr-only'>Tìm hóa đơn hoặc người bệnh</span><FontAwesomeIcon icon={faSearch} className='pointer-events-none absolute left-3 top-4 text-clinical-muted' /><input type='search' value={search} disabled={locked} maxLength={100} onChange={(event) => setSearch(event.target.value)} autoComplete='off' placeholder='Mã hóa đơn, người bệnh, họ tên, CCCD hoặc số điện thoại' className='min-h-12 w-full min-w-0 rounded-xl border border-clinical-border bg-white py-3 pl-10 pr-3 text-sm' /></label><Button type='submit' disabled={locked}>Tìm</Button>
    </form>
    <div role='tablist' aria-label='Lọc thanh toán' className='flex flex-wrap gap-2'>{tabs.map(([status, label], index) => <button key={status} type='button' role='tab' aria-selected={query.status === status} aria-controls='cashier-invoice-list' id={`cashier-tab-${status}`} tabIndex={query.status === status ? 0 : -1} disabled={locked} onClick={() => changeQuery({ status, page: 1 })} onKeyDown={(event) => {
      let target
      if (event.key === 'ArrowRight') target = (index + 1) % tabs.length
      else if (event.key === 'ArrowLeft') target = (index + tabs.length - 1) % tabs.length
      else if (event.key === 'Home') target = 0
      else if (event.key === 'End') target = tabs.length - 1
      else return
      event.preventDefault(); changeQuery({ status: tabs[target][0], page: 1 }); document.getElementById(`cashier-tab-${tabs[target][0]}`)?.focus()
    }} className={`min-h-11 rounded-xl border px-3 py-2 text-sm font-semibold disabled:opacity-50 ${query.status === status ? 'border-suncare-200 bg-suncare-100 text-suncare-900' : 'border-clinical-border bg-white hover:bg-suncare-50'}`}>{label}</button>)}</div>
    {pending && <div role='status' className='space-y-2 rounded-xl border border-clinical-warning/30 bg-suncare-50 p-4 text-sm'><p className='font-semibold'>Khoản thu cần xác minh: {money(pending.amount)} · <span className='break-all'>{pending.code}</span></p><p>Chưa nhận được kết quả chắc chắn. Thử lại để xác minh khoản thu; không nhận tiền lần nữa.</p><Button disabled={busy} onClick={collect}>{busy ? 'Đang xác minh…' : 'Thử lại khoản thu'}</Button></div>}
    {error && <div role='alert' className='rounded-xl border border-clinical-danger/30 bg-white p-3 text-sm text-clinical-danger'><p>{error.message}</p>{error.conflict && <Button className='mt-2' disabled={busy} onClick={() => { refreshList(); selectInvoice(view.data?.invoice.id || view.id, true) }}>Tải lại hóa đơn</Button>}</div>}
    {receipt && <div role='status' className='rounded-xl border border-clinical-success/30 bg-white p-4 text-sm'><p className='font-semibold text-clinical-success'>{receipt.replayed ? 'Đã lấy lại khoản thu đã ghi nhận' : 'Đã ghi nhận thanh toán'}: {money(receipt.amount)}</p><p className='mt-1 break-all'>{receipt.code} · {time(receipt.paidAt)}</p></div>}
    <div id='cashier-invoice-list' role='tabpanel' aria-labelledby={`cashier-tab-${query.status}`} className={panelClass} aria-busy={Boolean(list.loading)}>
      {list.loading && <p role='status'>Đang tải danh sách hóa đơn…</p>}
      {list.error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{list.error}</p><Button onClick={refreshList}>Thử lại danh sách</Button></div>}
      {list.data && <>
        {list.data.items.length === 0 ? <p role='status' className='py-5 text-sm text-clinical-muted'>Không có hóa đơn phù hợp.</p> : <div className='overflow-x-auto' role='region' aria-label='Danh sách hóa đơn' tabIndex={0}><table className='w-full min-w-[48rem] text-left text-sm'><thead className='text-xs text-clinical-muted'><tr>{['Mã hóa đơn', 'Người bệnh', 'Số điện thoại', 'Phát hành lúc', 'Tổng tiền', 'Thao tác'].map((label) => <th key={label} className='px-2 pb-3 font-medium'>{label}</th>)}</tr></thead><tbody>{list.data.items.map((item) => <tr key={item.id} aria-selected={invoice?.id === item.id} className={`border-t border-clinical-border ${invoice?.id === item.id ? 'bg-suncare-50' : ''}`}><td className='max-w-48 break-all px-2 py-3 font-semibold'>{item.code}</td><td className='px-2 py-3'><p className='font-semibold'>{item.patientName}</p><p className='mt-1 text-xs text-clinical-muted'>{item.patientCode}</p></td><td className='whitespace-nowrap px-2 py-3'>{item.phone || '—'}</td><td className='whitespace-nowrap px-2 py-3 text-xs'>{time(item.issuedAt)}</td><td className='whitespace-nowrap px-2 py-3 font-bold tabular-nums'>{money(item.totalAmount)}</td><td className='px-2 py-3'><Button variant={item.paymentStatus === 'PENDING' ? 'primary' : 'outline'} disabled={locked || list.loading} onClick={() => selectInvoice(item.id)} aria-label={`${item.paymentStatus === 'PENDING' ? 'Thu tiền' : 'Xem chi tiết'} ${item.code}`}>{item.paymentStatus === 'PENDING' ? 'Thu tiền' : 'Xem chi tiết'}</Button></td></tr>)}</tbody></table></div>}
        <div className='mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-clinical-border pt-3 text-sm'><label className='flex items-center gap-2'>Mỗi trang<select value={query.pageSize} disabled={locked} onChange={(event) => changeQuery({ pageSize: Number(event.target.value), page: 1 })} className='min-h-11 rounded-lg border border-clinical-border px-2'>{[10, 20, 50].map((size) => <option key={size} value={size}>{size}</option>)}</select></label><span>{list.data.totalElements} hóa đơn · Trang {list.data.page}/{Math.max(1, list.data.totalPages)}</span><div className='flex gap-2'><Button disabled={locked || list.data.page <= 1} onClick={() => changeQuery({ page: list.data.page - 1 })}>Trước</Button><Button disabled={locked || list.data.page >= list.data.totalPages} onClick={() => changeQuery({ page: list.data.page + 1 })}>Sau</Button></div></div>
      </>}
    </div>
    {view.loading && <p role='status'>Đang tải chi tiết thanh toán…</p>}
    {view.error && <div role='alert' className={`${panelClass} space-y-2 text-sm text-clinical-danger`}><p>{view.error}</p><Button disabled={busy} onClick={() => selectInvoice(view.id, true)}>Thử lại chi tiết</Button></div>}
    {data && <section ref={detailSection} className={`${panelClass} scroll-mt-4 space-y-4`} aria-label='Chi tiết thanh toán'>
      <div className='flex flex-wrap items-center justify-between gap-2'><h2 tabIndex={-1} className='min-w-0 break-all text-lg font-bold'>Chi tiết thanh toán – {invoice.code}</h2><span className={`rounded-full px-3 py-1 text-xs font-semibold ${data.paymentStatus === 'PAID' ? 'bg-clinical-leaf-soft text-clinical-success' : 'bg-suncare-100 text-clinical-warning'}`}>{data.paymentStatus === 'PAID' ? 'Đã thanh toán' : data.paymentStatus === 'PENDING' ? 'Chờ thanh toán' : 'Không có khoản cần thu'}</span></div>
      <dl className='grid gap-3 text-sm sm:grid-cols-2'><div><dt className='text-clinical-muted'>Người bệnh</dt><dd className='break-words font-semibold'>{invoice.patientName}</dd><dd className='mt-1 text-xs text-clinical-muted'>{data.patientCode}</dd></div><div><dt className='text-clinical-muted'>Số điện thoại</dt><dd>{data.phone || 'Chưa ghi nhận'}</dd></div></dl>
      <div className='overflow-x-auto' role='region' aria-label='Dịch vụ thanh toán' tabIndex={0}><table className='w-full min-w-[30rem] text-left text-sm'><thead className='text-xs text-clinical-muted'><tr><th className='py-3 pr-3'>Dịch vụ</th><th className='p-3'>SL</th><th className='p-3 text-right'>Đơn giá</th><th className='py-3 pl-3 text-right'>Thành tiền</th></tr></thead><tbody>{invoice.lines.map((line) => <tr key={line.id} className='border-t border-clinical-border'><td className='py-3 pr-3'><p className='font-semibold'>{line.serviceName}</p>{line.status === 'CANCELLED' && <p className='text-xs text-clinical-muted'>Đã hủy</p>}</td><td className='whitespace-nowrap p-3'>{line.quantity} {line.unitName}</td><td className='whitespace-nowrap p-3 text-right tabular-nums'>{money(line.unitPrice)}</td><td className='whitespace-nowrap py-3 pl-3 text-right font-semibold tabular-nums'>{money(line.amount)}</td></tr>)}</tbody></table></div>
      <div className='flex flex-wrap items-center justify-between gap-2 border-t border-clinical-border pt-4'><p className='font-semibold'>Tổng tiền thanh toán</p><p className='break-words text-2xl font-bold tabular-nums'>{money(data.paymentStatus === 'PENDING' ? data.paymentAmount : invoice.totalAmount)}</p></div>
      <p className='text-sm'><FontAwesomeIcon icon={faMoneyBill} className='mr-2 text-clinical-leaf' />Phương thức: Tiền mặt</p>
      {data.canCollect ? <Button ref={payButton} variant='primary' className='w-full' disabled={locked || Boolean(error?.conflict)} onClick={() => setConfirmOpen(true)}>Xác nhận đã thu tiền</Button> : data.paymentStatus === 'PENDING' && <p className='text-sm text-clinical-warning'>Khoản thu này chưa hỗ trợ thanh toán tiền mặt.</p>}
    </section>}
    <CashierConfirmDialog open={confirmOpen} busy={busy} code={invoice?.code || ''} amount={data ? money(data.paymentAmount) : ''} onCancel={() => { setConfirmOpen(false); payButton.current?.focus() }} onConfirm={collect} />
  </section>
}
