import { useEffect, useId, useRef, useState } from 'react'
import {
  ChevronDown,
  ChevronUp,
  CircleCheck,
  ClipboardList,
  CreditCard,
  Eye,
  FlaskConical,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  X
} from 'lucide-react'
import Button from '../../components/ui/Button'
import {
  createClinicalOrder,
  getClinicalOrderOptions,
  getClinicalOrders
} from '../../services/encounterApi'
import {
  clinicalTotals,
  money,
  performingDepartment
} from './clinicalOrderState'
import styles from './EncounterClinicalOrders.module.css'

const input =
  'mt-1 min-h-11 w-full rounded-lg border border-clinical-border-strong bg-white px-3 py-2 text-sm focus:outline-none focus:border-suncare-700 disabled:bg-clinical-canvas'
const statuses = {
  PENDING_PAYMENT: 'Chờ thanh toán',
  READY: 'Chờ đến phòng CLS',
  QUEUED: 'Đang chờ thực hiện',
  ACCEPTED: 'Đã tiếp nhận',
  IN_PROGRESS: 'Đang thực hiện',
  COMPLETED: 'Đã hoàn tất CLS',
  CANCELLED: 'Đã hủy'
}
const payments = {
  NOT_DUE: 'Chưa phát hành',
  UNPAID: 'Chưa thanh toán',
  PARTIAL: 'Thanh toán một phần',
  PAID: 'Đã thanh toán',
  NOT_APPLICABLE: 'Không áp dụng',
  UNKNOWN: 'Chưa xác minh'
}
const dateTime = (value) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Saigon',
        dateStyle: 'short',
        timeStyle: 'short'
      }).format(new Date(value))
    : 'Chưa ghi nhận'
const badgeColors = {
  neutral: 'bg-clinical-canvas text-clinical-muted',
  success: 'bg-clinical-leaf-soft text-clinical-success',
  warning: 'bg-suncare-100 text-clinical-warning',
  info: 'bg-sky-50 text-clinical-info',
  danger: 'bg-rose-50 text-clinical-danger'
}
function Badge({ children, tone = 'neutral' }) {
  return (
    <span
      className={`inline-block rounded-md px-2 py-1 text-[11px] font-semibold ${badgeColors[tone]}`}
    >
      {children}
    </span>
  )
}

export default function EncounterClinicalOrders({
  encounterId,
  active,
  writable,
  onSessionExpired,
  onGuardChange,
  onCreated,
  onPresenceChange,
  onRemoveStep,
  onOpenInvoices
}) {
  const id = useId()
  const [list, setList] = useState({ loading: true, items: [] })
  const [options, setOptions] = useState({ loading: true, items: [] })
  const [reload, setReload] = useState(0)
  const [expanded, setExpanded] = useState(false)
  const [search, setSearch] = useState('')
  const [pickerOpen, setPickerOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const [selected, setSelected] = useState(null)
  const [room, setRoom] = useState('')
  const [indication, setIndication] = useState('')
  const [drafts, setDrafts] = useState([])
  const [editing, setEditing] = useState(null)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [detail, setDetail] = useState(null)
  const sending = useRef(false)
  const form = useRef(null)
  const uncertain = drafts.some((item) => item.uncertain)
  const locked = busy || uncertain
  const dirty = Boolean(
    selected || search.trim() || indication.trim() || drafts.length || busy
  )
  const rooms = selected?.rooms || []
  const totals = clinicalTotals(list.items)
  useEffect(() => {
    onGuardChange?.('clinical-order', dirty)
    return () => onGuardChange?.('clinical-order', false)
  }, [dirty, onGuardChange])
  useEffect(() => {
    if (!dirty) return undefined
    const warn = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
  useEffect(() => {
    // Read even while hidden, to restore the step when reopening an existing Encounter.
    const controller = new AbortController()
    getClinicalOrders(encounterId, controller.signal)
      .then((items) => {
        if (controller.signal.aborted) return
        if (!Array.isArray(items))
          throw new Error('Danh sách chỉ định không hợp lệ.')
        setList({ items })
        onPresenceChange?.(items.length > 0)
      })
      .catch((failure) => {
        if (controller.signal.aborted) return
        if (failure.status === 401) onSessionExpired(failure.message)
        else
          setList((previous) => ({
            items: previous.items,
            error: failure.message
          }))
      })
    return () => controller.abort()
  }, [active, encounterId, reload, onPresenceChange, onSessionExpired])
  useEffect(() => {
    if (!active || !writable || !expanded) return undefined
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      getClinicalOrderOptions(encounterId, search.trim(), controller.signal)
        .then((items) => {
          if (controller.signal.aborted) return
          if (!Array.isArray(items))
            throw new Error('Danh mục chỉ định không hợp lệ.')
          setOptions({ items })
        })
        .catch((failure) => {
          if (controller.signal.aborted) return
          if (failure.status === 401) onSessionExpired(failure.message)
          else setOptions({ items: [], error: failure.message })
        })
    }, 250)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [
    active,
    writable,
    expanded,
    encounterId,
    search,
    reload,
    onSessionExpired
  ])
  function refresh() {
    setList((previous) => ({ ...previous, loading: true }))
    setReload((value) => value + 1)
  }
  function clearFields() {
    setSelected(null)
    setSearch('')
    setRoom('')
    setIndication('')
    setEditing(null)
    setErrors({})
    setPickerOpen(false)
  }
  function choose(service) {
    setSelected(service)
    setSearch('')
    setRoom(service.rooms.length === 1 ? String(service.rooms[0].roomId) : '')
    setErrors({})
    setPickerOpen(false)
  }
  function discard(removeStep = false) {
    if (locked || sending.current) return
    if (
      dirty &&
      !window.confirm(
        'Bỏ dữ liệu CLS đang soạn? Các chỉ định đã lập vẫn được giữ nguyên.'
      )
    )
      return
    clearFields()
    setDrafts([])
    setExpanded(false)
    setError('')
    if (removeStep) onRemoveStep?.()
  }
  function addDraft(event) {
    event.preventDefault()
    if (locked) return
    const departmentId = performingDepartment(selected, room)
    const invalid = {}
    if (!selected) invalid.serviceId = 'Chọn dịch vụ cần chỉ định.'
    else if (!departmentId)
      invalid.requestedRoomId =
        'Dịch vụ có nhiều khoa hỗ trợ. Chọn phòng ưu tiên để xác định nơi thực hiện.'
    setErrors(invalid)
    if (Object.keys(invalid).length) {
      form.current?.querySelector('[aria-invalid="true"]')?.focus()
      return
    }
    const draft = {
      id: editing || crypto.randomUUID(),
      key: crypto.randomUUID(),
      service: selected,
      roomName: rooms.find((item) => String(item.roomId) === room)?.roomName,
      data: {
        serviceId: selected.serviceId,
        performingDepartmentId: departmentId,
        requestedRoomId: room ? Number(room) : null,
        indication: indication.trim()
      }
    }
    setDrafts((previous) =>
      editing
        ? previous.map((item) => (item.id === editing ? draft : item))
        : [...previous, draft]
    )
    clearFields()
    setError('')
    setMessage('')
  }
  function editDraft(item) {
    setEditing(item.id)
    setSelected(item.service)
    setRoom(item.data.requestedRoomId ? String(item.data.requestedRoomId) : '')
    setIndication(item.data.indication)
    setErrors({})
    setSearch('')
    setPickerOpen(false)
    form.current?.scrollIntoView({ block: 'nearest' })
  }
  async function submitDrafts() {
    if (sending.current || !writable || !drafts.length) return
    if (selected || indication.trim() || search.trim()) {
      setError(
        'Thêm dịch vụ đang nhập vào danh sách hoặc xóa nội dung trước khi lập chỉ định.'
      )
      return
    }
    sending.current = true
    setBusy(true)
    setError('')
    setMessage('')
    let created = 0
    let failed = false
    try {
      for (const item of drafts) {
        try {
          if (item.uncertain) {
            // Read backend before replaying the SAME key; names are not proof of success.
            const current = await getClinicalOrders(encounterId)
            if (!Array.isArray(current))
              throw new Error('Chưa đọc được dữ liệu để xác minh yêu cầu cũ.')
            setList({ items: current })
            onPresenceChange?.(current.length > 0)
          }
          const result = await createClinicalOrder(
            encounterId,
            item.data,
            item.key
          )
          if (!result.id || !result.invoiceId)
            throw new Error('Phản hồi chưa xác minh được chỉ định đã lưu.')
          created++
          setDrafts((previous) => previous.filter((row) => row.id !== item.id))
          onPresenceChange?.(true)
          onCreated?.()
        } catch (failure) {
          failed = true
          setExpanded(true)
          const unknown = !(failure.status >= 400 && failure.status < 500)
          setDrafts((previous) =>
            previous.map((row) =>
              row.id === item.id
                ? {
                    ...row,
                    uncertain: unknown || item.uncertain,
                    error: failure.message
                  }
                : row
            )
          )
          setError(
            `${created ? `Đã lưu ${created} chỉ định. ` : ''}${failure.message} Các mục chưa lưu được giữ lại.`
          )
          if (failure.status === 401) onSessionExpired(failure.message)
          break
        }
      }
      if (!failed) {
        clearFields()
        setExpanded(false)
        setMessage(
          `Đã lập ${created} chỉ định. Phát hành hóa đơn tại mục Viện phí.`
        )
      }
    } finally {
      sending.current = false
      setBusy(false)
      refresh()
    }
  }
  const attributes = (name) => ({
    id: `${id}-${name}`,
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': errors[name] ? `${id}-${name}-error` : undefined,
    className: input
  })
  const field = (label, name, control) => (
    <div className='min-w-0'>
      <label htmlFor={`${id}-${name}`} className='text-xs font-semibold'>
        {label}
      </label>
      {control}
      {errors[name] && (
        <p
          id={`${id}-${name}-error`}
          className='mt-1 text-sm text-clinical-danger'
        >
          {errors[name]}
        </p>
      )}
    </div>
  )
  const inProgress = list.items.filter((item) =>
    ['ACCEPTED', 'IN_PROGRESS'].includes(item.status)
  ).length
  const waitingPayment = list.items.filter(
    (item) =>
      ['NOT_DUE', 'UNPAID', 'PARTIAL'].includes(item.paymentStatus) &&
      item.status !== 'CANCELLED'
  ).length
  return (
    <section
      className='min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-3 sm:p-4'
      aria-labelledby={`${id}-title`}
    >
      <header className='flex flex-wrap items-start justify-between gap-3'>
        <div className='flex min-w-0 items-start gap-3'>
          <span className='rounded-lg bg-clinical-leaf-soft p-2 text-clinical-success'>
            <FlaskConical size={22} />
          </span>
          <div>
            <h2 id={`${id}-title`} className='text-lg font-bold'>
              Cận lâm sàng
            </h2>
            <p className='mt-1 text-xs text-clinical-muted'>
              Quản lý chỉ định, theo dõi thực hiện và kết quả cận lâm sàng.
            </p>
          </div>
        </div>
        <div className='flex flex-wrap gap-2'>
          <Button
            title='Tải lại chỉ định'
            aria-label='Tải lại chỉ định'
            disabled={busy}
            onClick={refresh}
          >
            <RefreshCw size={16} />
          </Button>
          {writable && (
            <Button
              variant='primary'
              disabled={busy}
              onClick={() => setExpanded(true)}
            >
              <Plus size={16} />
              Thêm chỉ định
            </Button>
          )}
        </div>
      </header>
      <dl className='grid grid-cols-2 gap-2 xl:grid-cols-4'>
        {[
          [
            ClipboardList,
            'Tổng chỉ định',
            list.items.length,
            'text-clinical-ink'
          ],
          [
            CreditCard,
            'Chờ thanh toán',
            waitingPayment,
            'text-clinical-warning'
          ],
          [FlaskConical, 'Đang thực hiện', inProgress, 'text-clinical-info'],
          [CircleCheck, 'Có kết quả', null, 'text-clinical-success']
        ].map(([Icon, label, value, color]) => (
          <div
            key={label}
            className='flex min-w-0 items-center gap-2 rounded-lg border border-clinical-border bg-clinical-canvas p-3'
          >
            <Icon size={18} className={`shrink-0 ${color}`} />
            <div className='min-w-0'>
              <dt className='text-xs text-clinical-muted'>{label}</dt>
              <dd className={`mt-1 text-xl font-bold tabular-nums ${color}`}>
                {list.loading || list.error ? '—' : (value ?? '—')}
              </dd>
            </div>
          </div>
        ))}
      </dl>
      <p className='text-xs text-clinical-muted'>
        Kết quả và đánh giá chưa khả dụng. Phòng ưu tiên chưa phải phòng thực
        hiện chính thức.
      </p>
      {message && (
        <p
          role='status'
          className='rounded-lg bg-clinical-leaf-soft p-3 text-sm font-semibold text-clinical-success'
        >
          {message}
        </p>
      )}
      {error && (
        <p role='alert' className='text-sm text-clinical-danger'>
          {error}
        </p>
      )}
      {uncertain && (
        <p
          role='alert'
          className='rounded-lg bg-suncare-100 p-3 text-sm text-clinical-warning'
        >
          Có yêu cầu chưa xác minh. Nội dung được khóa; lần thử lại sẽ đọc
          backend rồi gửi lại đúng khóa cũ để tránh tạo trùng.
        </p>
      )}
      {writable && (
        <div
          className={`${styles.collapse} ${expanded ? styles.expanded : ''}`}
          inert={!expanded}
          aria-hidden={!expanded}
        >
          <div className={styles.inner}>
            <section
              className='space-y-3 rounded-xl border border-clinical-border bg-clinical-canvas p-3'
              aria-label='Thêm chỉ định CLS'
            >
              <button
                type='button'
                className='flex min-h-10 w-full items-center justify-between gap-2 text-sm font-bold'
                aria-expanded={expanded}
                onClick={() => setExpanded(false)}
              >
                <span className='inline-flex items-center gap-2'>
                  <Plus size={18} />
                  Thêm chỉ định CLS
                </span>
                <ChevronUp size={18} />
              </button>
              <form
                ref={form}
                onSubmit={addDraft}
                noValidate
                className='space-y-3'
              >
                <fieldset disabled={locked} className='min-w-0 space-y-3'>
                  <div className='grid min-w-0 gap-3 xl:grid-cols-[minmax(0,9fr)_minmax(0,6fr)_minmax(0,5fr)]'>
                    <div
                      className='relative min-w-0'
                      onBlur={(event) => {
                        if (!event.currentTarget.contains(event.relatedTarget))
                          setPickerOpen(false)
                      }}
                    >
                      {field(
                        'Dịch vụ cận lâm sàng *',
                        'serviceId',
                        <div className='relative'>
                          <Search
                            size={15}
                            className='pointer-events-none absolute left-3 top-4 text-clinical-muted'
                          />
                          <input
                            {...attributes('serviceId')}
                            className={`${input} pl-9 pr-7`}
                            role='combobox'
                            autoComplete='off'
                            aria-autocomplete='list'
                            aria-expanded={pickerOpen}
                            aria-controls={`${id}-options`}
                            aria-activedescendant={
                              pickerOpen && options.items[highlight]
                                ? `${id}-option-${highlight}`
                                : undefined
                            }
                            value={
                              selected
                                ? `${selected.code} · ${selected.name}`
                                : search
                            }
                            maxLength={100}
                            placeholder='Tìm theo mã hoặc tên dịch vụ...'
                            onFocus={() => setPickerOpen(true)}
                            onChange={(event) => {
                              setSelected(null)
                              setRoom('')
                              setSearch(event.target.value)
                              setOptions({ loading: true, items: [] })
                              setPickerOpen(true)
                              setHighlight(0)
                            }}
                            onKeyDown={(event) => {
                              if (event.key === 'Escape') {
                                setPickerOpen(false)
                                return
                              }
                              if (
                                event.key === 'ArrowDown' ||
                                event.key === 'ArrowUp'
                              ) {
                                event.preventDefault()
                                setPickerOpen(true)
                                setHighlight((value) =>
                                  Math.max(
                                    0,
                                    Math.min(
                                      options.items.length - 1,
                                      value +
                                        (event.key === 'ArrowDown' ? 1 : -1)
                                    )
                                  )
                                )
                              }
                              if (event.key === 'Enter') {
                                event.preventDefault()
                                if (pickerOpen && options.items[highlight])
                                  choose(options.items[highlight])
                              }
                            }}
                          />
                          <ChevronDown
                            size={14}
                            className='pointer-events-none absolute right-2 top-4 text-clinical-muted'
                          />
                        </div>
                      )}
                      {pickerOpen && (
                        <div
                          className='absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-clinical-border-strong bg-white p-1 shadow-lg'
                          id={`${id}-options`}
                          role='listbox'
                          aria-label='Dịch vụ CLS phù hợp'
                        >
                          {options.loading ? (
                            <p role='status' className='p-3 text-sm'>
                              Đang tra dịch vụ...
                            </p>
                          ) : options.error ? (
                            <p
                              role='alert'
                              className='p-3 text-sm text-clinical-danger'
                            >
                              {options.error} Dùng nút tải lại để thử lại.
                            </p>
                          ) : options.items.length === 0 ? (
                            <p className='p-3 text-sm'>
                              Không có dịch vụ phù hợp có giá hiệu lực và phòng
                              hỗ trợ.
                            </p>
                          ) : (
                            options.items.map((item, index) => (
                              <button
                                type='button'
                                role='option'
                                tabIndex={-1}
                                id={`${id}-option-${index}`}
                              aria-selected={highlight === index}
                                key={item.serviceId}
                                onMouseDown={(event) => event.preventDefault()}
                                onClick={() => choose(item)}
                                className={`w-full rounded-md p-2 text-left text-sm hover:bg-suncare-100 ${highlight === index ? 'bg-suncare-100' : ''}`}
                              >
                                <span className='block font-semibold'>
                                  {item.name}
                                </span>
                                <span className='block text-xs text-clinical-muted'>
                                  {item.code} · {money(item.amount)}
                                </span>
                              </button>
                            ))
                          )}
                        </div>
                      )}
                    </div>
                    {field(
                      'Phòng ưu tiên',
                      'requestedRoomId',
                      <select
                        {...attributes('requestedRoomId')}
                        disabled={!selected || locked}
                        value={room}
                        onChange={(event) => {
                          setRoom(event.target.value)
                          setErrors({})
                        }}
                      >
                        <option value=''>Không chỉ định phòng</option>
                        {rooms.map((item) => (
                          <option key={item.roomId} value={item.roomId}>
                            {item.roomName}
                          </option>
                        ))}
                      </select>
                    )}
                    {field(
                      'Đơn giá dự kiến',
                      'price',
                      <input
                        {...attributes('price')}
                        readOnly
                        value={money(selected?.amount)}
                        className={`${input} bg-clinical-canvas`}
                      />
                    )}
                  </div>
                  {field(
                    'Lý do chỉ định (không bắt buộc)',
                    'indication',
                    <textarea
                      {...attributes('indication')}
                      value={indication}
                      maxLength={2000}
                      rows={2}
                      placeholder='Nhập lý do chỉ định hoặc ghi chú nếu cần...'
                      onChange={(event) => setIndication(event.target.value)}
                    />
                  )}
                  <div className='flex flex-wrap justify-end gap-2'>
                    <Button onClick={clearFields}>
                      <Trash2 size={15} />
                      Xóa nội dung
                    </Button>
                    <Button type='submit' variant='primary'>
                      <Plus size={15} />
                      {editing ? 'Lưu dịch vụ đang sửa' : 'Thêm dịch vụ'}
                    </Button>
                  </div>
                </fieldset>
              </form>
              <p className='text-xs text-clinical-muted'>
                Tối đa 30 kết quả mỗi tìm kiếm. Giá dự kiến được kiểm tra và
                chốt lại khi lập chỉ định.
              </p>
              <h3 className='text-sm font-bold'>
                Danh sách dịch vụ sẽ lập chỉ định ({drafts.length})
              </h3>
              <div
                className='overflow-x-auto rounded-lg border border-clinical-border bg-white'
                tabIndex={0}
                role='region'
                aria-label='Dịch vụ CLS đang chọn'
              >
                <table className='w-full min-w-[32rem] text-left text-xs'>
                  <thead className='bg-clinical-canvas'>
                    <tr>
                      {[
                        'STT',
                        'Dịch vụ',
                        'Phòng ưu tiên',
                        'Đơn giá',
                        'Thao tác'
                      ].map((label) => (
                        <th key={label} className='p-2'>
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {drafts.length === 0 && (
                      <tr>
                        <td colSpan={5} className='p-3 text-clinical-muted'>
                          Chưa có dịch vụ đang chọn.
                        </td>
                      </tr>
                    )}
                    {drafts.map((item, index) => (
                      <tr
                        key={item.id}
                        className='border-t border-clinical-border'
                      >
                        <td className='p-2'>{index + 1}</td>
                        <td className='max-w-64 p-2'>
                          <p className='font-semibold'>{item.service.name}</p>
                          <p>{item.service.code}</p>
                          {item.data.indication && (
                            <p className='mt-1 whitespace-pre-wrap break-words text-clinical-muted'>
                              {item.data.indication}
                            </p>
                          )}
                          {item.error && (
                            <p
                              role='alert'
                              className='mt-1 text-clinical-danger'
                            >
                              {item.error}
                            </p>
                          )}
                        </td>
                        <td className='p-2'>
                          {item.roomName || 'Không chỉ định phòng'}
                        </td>
                        <td className='whitespace-nowrap p-2 font-semibold'>
                          {money(item.service.amount)}
                        </td>
                        <td className='p-2'>
                          <div className='flex gap-1'>
                            <Button
                              disabled={locked}
                              title={`Sửa ${item.service.name}`}
                              aria-label={`Sửa ${item.service.name}`}
                              onClick={() => editDraft(item)}
                              className='px-2!'
                            >
                              <Pencil size={14} />
                            </Button>
                            <Button
                              disabled={locked}
                              title={`Xóa ${item.service.name}`}
                              aria-label={`Xóa ${item.service.name}`}
                              onClick={() => {
                                setDrafts((previous) =>
                                  previous.filter((row) => row.id !== item.id)
                                )
                                if (editing === item.id) clearFields()
                              }}
                              className='px-2!'
                            >
                              <Trash2 size={14} />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className='border-t border-clinical-border'>
                      <th colSpan={3} className='p-2'>
                        Tổng chi phí dự kiến
                      </th>
                      <td
                        colSpan={2}
                        className='p-2 text-right text-base font-bold text-clinical-success'
                      >
                        {money(
                          drafts.reduce(
                            (sum, item) => sum + BigInt(item.service.amount),
                            0n
                          )
                        )}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
              <div className='flex flex-wrap justify-end gap-2'>
                <Button disabled={locked} onClick={() => discard()}>
                  Hủy
                </Button>
                <Button
                  variant='primary'
                  disabled={!drafts.length || busy || !writable}
                  onClick={submitDrafts}
                >
                  <CircleCheck size={16} />
                  {busy
                    ? 'Đang lập chỉ định...'
                    : uncertain
                      ? 'Xác minh và thử lại'
                      : `Lập chỉ định (${drafts.length})`}
                </Button>
              </div>
            </section>
          </div>
        </div>
      )}
      {writable && !expanded && drafts.length > 0 && (
        <Button onClick={() => setExpanded(true)}>
          <ChevronDown size={16} />
          Mở lại {drafts.length} dịch vụ đang soạn
        </Button>
      )}
      <div className='flex flex-wrap items-center justify-between gap-2'>
        <h3 className='font-bold'>Danh sách chỉ định CLS</h3>
        <Button disabled={busy} onClick={onOpenInvoices}>
          Xem viện phí
        </Button>
      </div>
      {list.loading && (
        <p role='status' className='text-sm'>
          Đang tải chỉ định...
        </p>
      )}
      {list.error && (
        <p role='alert' className='text-sm text-clinical-danger'>
          {list.error} Dữ liệu cũ chưa được xác minh lại. Dùng nút tải lại để
          thử lại.
        </p>
      )}
      {!list.loading && !list.error && list.items.length === 0 && (
        <p className='rounded-lg bg-clinical-canvas p-3 text-sm text-clinical-muted'>
          Chưa có chỉ định CLS trong lượt khám này.
        </p>
      )}
      {list.items.length > 0 && (
        <div
          className='overflow-x-auto rounded-lg border border-clinical-border'
          role='region'
          aria-label='Bảng chỉ định CLS'
          tabIndex={0}
        >
          <table className='w-full min-w-[48rem] text-left text-xs'>
            <thead className='bg-clinical-canvas'>
              <tr>
                {[
                  'STT',
                  'Dịch vụ',
                  'Phòng thực hiện',
                  'Đơn giá',
                  'Thanh toán',
                  'Thực hiện',
                  'Đánh giá',
                  'Thao tác'
                ].map((label) => (
                  <th key={label} className='p-2'>
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {list.items.map((item, index) => (
                <tr
                  key={item.id}
                  className='border-t border-clinical-border align-top'
                >
                  <td className='p-2'>{index + 1}</td>
                  <td className='max-w-56 p-2'>
                    <p className='font-semibold'>{item.serviceName}</p>
                    <p className='mt-1 text-clinical-muted'>
                      {item.serviceCode || 'Chưa có mã dịch vụ'}
                    </p>
                  </td>
                  <td className='max-w-40 p-2'>
                    {item.roomName ? (
                      <>
                        <span className='block text-clinical-muted'>
                          Ưu tiên:
                        </span>
                        {item.roomName}
                      </>
                    ) : (
                      'Chưa phân phòng'
                    )}
                  </td>
                  <td className='whitespace-nowrap p-2 font-semibold'>
                    {money(item.amount)}
                  </td>
                  <td className='p-2'>
                    <Badge
                      tone={
                        item.paymentStatus === 'PAID'
                          ? 'success'
                          : ['UNPAID', 'PARTIAL', 'NOT_DUE'].includes(
                                item.paymentStatus
                              )
                            ? 'warning'
                            : item.paymentStatus === 'UNKNOWN'
                              ? 'danger'
                              : 'neutral'
                      }
                    >
                      {payments[item.paymentStatus] || 'Chưa xác minh'}
                    </Badge>
                  </td>
                  <td className='p-2'>
                    <Badge
                      tone={
                        item.status === 'CANCELLED'
                          ? 'danger'
                          : ['ACCEPTED', 'IN_PROGRESS'].includes(item.status)
                            ? 'info'
                            : item.status === 'COMPLETED'
                              ? 'success'
                              : 'neutral'
                      }
                    >
                      {statuses[item.status] || 'Chưa xác minh'}
                    </Badge>
                  </td>
                  <td className='p-2 text-clinical-muted'>
                    Chưa có dữ liệu đánh giá
                  </td>
                  <td className='p-2'>
                    <Button
                      title='Xem chi tiết chỉ định'
                      aria-label={`Xem chi tiết ${item.code}`}
                      className='px-2!'
                      onClick={() =>
                        setDetail(detail === item.id ? null : item.id)
                      }
                    >
                      <Eye size={15} />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.items
        .filter((item) => item.id === detail)
        .map((item) => (
          <section
            key={item.id}
            aria-label='Chi tiết chỉ định'
            className='space-y-2 rounded-lg border border-clinical-border p-3 text-sm'
          >
            <div className='flex justify-between gap-2'>
              <h4 className='break-all font-bold'>
                {item.serviceName} · {item.code}
              </h4>
              <Button
                aria-label='Đóng chi tiết'
                onClick={() => setDetail(null)}
              >
                <X size={16} />
              </Button>
            </div>
            {item.indication && (
              <p className='whitespace-pre-wrap break-words'>
                {item.indication}
              </p>
            )}
            <p>Chỉ định lúc: {dateTime(item.createdAt)}</p>
            <p>
              Trả đủ lúc: {dateTime(item.fullyPaidAt)} · Hạn:{' '}
              {dateTime(item.expiresAt)}
            </p>
            <p className='break-all'>
              Hóa đơn: {item.invoiceCode || 'Chưa xác minh'}
            </p>
            <p>
              Kết quả và đánh giá chưa khả dụng; chỉ định đã lập không sửa tại
              đây.
            </p>
          </section>
        ))}
      <dl className='grid gap-3 rounded-lg border border-clinical-border bg-clinical-canvas p-3 sm:grid-cols-3'>
        {[
          ['Tổng chi phí CLS', totals?.total, 'text-clinical-ink'],
          ['Đã thanh toán', totals?.paid, 'text-clinical-success'],
          ['Còn phải thanh toán', totals?.outstanding, 'text-clinical-warning']
        ].map(([label, value, color]) => (
          <div key={label} className='min-w-0'>
            <dt className='text-xs text-clinical-muted'>{label}</dt>
            <dd
              className={`mt-1 break-words text-lg font-bold tabular-nums ${color}`}
            >
              {list.loading || list.error ? '—' : money(value)}
            </dd>
          </div>
        ))}
      </dl>
      {!totals && (
        <p role='alert' className='text-sm text-clinical-warning'>
          Chưa xác minh đủ dòng thu để tổng hợp chi phí CLS.
        </p>
      )}
      {list.items.length === 0 && !list.loading && !list.error && (
        <Button disabled={locked} onClick={() => discard(true)}>
          Bỏ bước CLS
        </Button>
      )}
    </section>
  )
}
