import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import { getDrugs, saveDrug } from '../../services/drugApi'

const empty = { code: '', name: '', ingredient: '', strength: '', dosageForm: '', doseUnit: '', dispensingUnit: '', isActive: true }
const fields = [
  ['code', 'Mã thuốc', 40, true], ['name', 'Tên thuốc', 200, true],
  ['ingredient', 'Hoạt chất', 300], ['strength', 'Hàm lượng', 100],
  ['dosageForm', 'Dạng bào chế', 100], ['doseUnit', 'Đơn vị liều dùng', 50, true], ['dispensingUnit', 'Đơn vị cấp phát', 50, true],
]
const inputClass = 'mt-1 min-h-11 w-full rounded-lg border border-clinical-border-strong bg-white px-3 py-2 focus-visible:outline-2 focus-visible:outline-suncare-700'
const normalize = (value) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase()

export default function DrugManagement({ onSessionExpired }) {
  const [state, setState] = useState({ loading: true, items: [] })
  const [reload, setReload] = useState(0)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  const [message, setMessage] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(1)
  const commandRef = useRef(false)
  const aliveRef = useRef(false)
  const nameRef = useRef(null)

  useEffect(() => {
    aliveRef.current = true
    return () => { aliveRef.current = false }
  }, [])

  useEffect(() => {
    let active = true
    getDrugs().then((items) => { if (active) setState({ items }) }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired(error.message)
      else setState({ items: [], error: error.message })
    })
    return () => { active = false }
  }, [reload, onSessionExpired])

  function reset() {
    setEditing(null)
    setForm(empty)
    setFailure(null)
  }

  function refresh() {
    if (commandRef.current) return
    if (editing && !window.confirm('Tải lại sẽ bỏ nội dung đang sửa. Bạn muốn tiếp tục?')) return
    reset()
    setMessage('')
    setState({ loading: true, items: [] })
    setReload((value) => value + 1)
  }

  function edit(item) {
    if (editing && !window.confirm('Bỏ nội dung đang sửa để chọn thuốc khác?')) return
    setEditing(item)
    setForm(Object.fromEntries(Object.keys(empty).map((key) => [key, item[key] ?? ''])))
    setFailure(null)
    setMessage('')
    nameRef.current?.focus()
  }

  async function submit(event) {
    event.preventDefault()
    if (commandRef.current) return
    commandRef.current = true
    setBusy(true)
    setFailure(null)
    setMessage('')
    const payload = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]))
    payload.code = payload.code.toUpperCase()
    if (editing) payload.version = editing.version
    try {
      const items = await saveDrug(editing?.id, payload)
      if (!aliveRef.current) return
      setState({ items })
      reset()
      setMessage('Đã lưu danh mục thuốc.')
    } catch (error) {
      if (!aliveRef.current) return
      if (error.status === 401) onSessionExpired(error.message)
      else setFailure(error)
    } finally {
      commandRef.current = false
      if (aliveRef.current) setBusy(false)
    }
  }

  const query = normalize(search.trim())
  const visible = state.items.filter((item) => (status === 'ALL' || item.isActive === (status === 'ACTIVE'))
    && normalize(`${item.code} ${item.name} ${item.ingredient || ''}`).includes(query))
  const pages = Math.max(1, Math.ceil(visible.length / 10))
  const currentPage = Math.min(page, pages)
  const rows = visible.slice((currentPage - 1) * 10, currentPage * 10)
  const locked = busy || state.loading || Boolean(state.error)

  return <section className='w-full space-y-5 text-clinical-ink' aria-labelledby='drug-title'>
    <header className='flex flex-wrap items-center justify-between gap-3'>
      <div><h1 id='drug-title' className='text-2xl font-bold'>Danh mục thuốc</h1>
        <p className='mt-1 text-sm text-clinical-muted'>Quản lý thông tin thuốc dùng khi kê đơn ngoại trú.</p></div>
      <Button onClick={refresh} disabled={busy || state.loading}>Tải lại</Button>
    </header>
    {message && <p role='status' className='text-clinical-success'>{message}</p>}
    <div className='grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_24rem]'>
      <div className='min-w-0 space-y-4'>
        <div className='grid gap-3 sm:grid-cols-[minmax(0,1fr)_12rem]'>
          <label className='text-sm font-semibold'>Tìm mã, tên hoặc hoạt chất
            <input type='search' maxLength={300} value={search} onChange={(event) => { setSearch(event.target.value); setPage(1) }} className={inputClass} /></label>
          <label className='text-sm font-semibold'>Trạng thái
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1) }} className={inputClass}>
              <option value='ALL'>Tất cả</option><option value='ACTIVE'>Đang sử dụng</option><option value='INACTIVE'>Ngừng sử dụng</option>
            </select></label>
        </div>
        {state.loading && <p role='status'>Đang tải danh mục thuốc...</p>}
        {state.error && <div role='alert' className='space-y-3 text-clinical-danger'><p>{state.error}</p><Button onClick={refresh}>Thử lại danh mục</Button></div>}
        {!state.loading && !state.error && <>
          <p className='text-sm text-clinical-muted' role='status'>{visible.length} thuốc phù hợp</p>
          {rows.length === 0 ? <p className='rounded-xl border border-dashed border-clinical-border-strong p-5'>
            {state.items.length === 0 ? 'Chưa có thuốc. Hãy thêm thuốc đầu tiên vào danh mục.' : 'Không có thuốc phù hợp. Hãy đổi từ khóa hoặc trạng thái.'}</p>
            : <div role='region' aria-label='Danh sách thuốc' tabIndex={0} className='overflow-x-auto rounded-xl border border-clinical-border bg-white focus-visible:outline-2 focus-visible:outline-suncare-700'>
              <table className='w-full min-w-[44rem] text-left text-sm'>
                <caption className='sr-only'>Thông tin thuốc và trạng thái sử dụng</caption>
                <thead className='bg-suncare-50'><tr>{['Mã / tên thuốc', 'Hoạt chất', 'Hàm lượng / dạng', 'Đơn vị', 'Trạng thái', 'Thao tác'].map((text) => <th key={text} scope='col' className='px-4 py-3 font-semibold'>{text}</th>)}</tr></thead>
                <tbody className='divide-y divide-clinical-border'>{rows.map((item) => <tr key={item.id}>
                  <td className='max-w-64 break-words px-4 py-3'><p className='font-semibold'>{item.name}</p><p className='mt-1 text-clinical-muted'>{item.code}</p></td>
                  <td className='max-w-48 break-words px-4 py-3'>{item.ingredient || '—'}</td>
                  <td className='max-w-40 break-words px-4 py-3'>{item.strength || '—'}<span className='block text-clinical-muted'>{item.dosageForm || '—'}</span></td>
                  <td className='max-w-40 break-words px-4 py-3'><p>Liều: {item.doseUnit || 'Chưa xác nhận'}</p><p>Cấp phát: {item.dispensingUnit || 'Cần rà soát'}</p></td>
                  <td className='px-4 py-3'>{item.isActive ? 'Đang sử dụng' : 'Ngừng sử dụng'}</td>
                  <td className='px-4 py-3'><Button disabled={busy} onClick={() => edit(item)} aria-label={`Sửa thuốc ${item.code}`}>Sửa</Button></td>
                </tr>)}</tbody>
              </table></div>}
          {pages > 1 && <nav aria-label='Phân trang thuốc' className='flex flex-wrap items-center gap-3'>
            <Button disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>Trang trước</Button>
            <span className='text-sm'>Trang {currentPage}/{pages}</span>
            <Button disabled={currentPage === pages} onClick={() => setPage(currentPage + 1)}>Trang sau</Button>
          </nav>}
        </>}
      </div>
      <form onSubmit={submit} className='space-y-4 rounded-xl border border-clinical-border bg-white p-5' aria-labelledby='drug-form-title'>
        <h2 id='drug-form-title' className='text-lg font-bold'>{editing ? 'Sửa thuốc' : 'Thêm thuốc'}</h2>
        {failure && <div role='alert' className='text-sm text-clinical-danger'>{failure.message}</div>}
        <fieldset disabled={locked} className='space-y-4'>
          {fields.map(([key, label, maxLength, required]) => <div key={key}>
            <label htmlFor={`drug-${key}`} className='block text-sm font-semibold'>{label}{required ? ' *' : ''}</label>
            <input id={`drug-${key}`} ref={key === 'name' ? nameRef : undefined} required={required} maxLength={maxLength}
              pattern={key === 'code' ? '[A-Za-z0-9_-]{1,40}' : undefined} readOnly={key === 'code' && Boolean(editing)}
              value={form[key]} onChange={(event) => setForm((previous) => ({ ...previous, [key]: event.target.value }))}
              aria-invalid={Boolean(failure?.fieldErrors?.[key])}
              aria-describedby={failure?.fieldErrors?.[key] ? `drug-${key}-error` : key === 'code' ? 'drug-code-help' : undefined}
              className={inputClass} />
            {key === 'code' && <p id='drug-code-help' className='mt-1 text-xs text-clinical-muted'>Chữ, số, dấu _ hoặc -. Mã cố định sau khi tạo.</p>}
            {failure?.fieldErrors?.[key] && <p id={`drug-${key}-error`} className='mt-1 text-sm text-clinical-danger'>{failure.fieldErrors[key]}</p>}
          </div>)}
          <label className='flex min-h-11 items-center gap-2 text-sm'><input type='checkbox' checked={form.isActive}
            onChange={(event) => setForm((previous) => ({ ...previous, isActive: event.target.checked }))} />Đang sử dụng</label>
          <p className='text-xs leading-5 text-clinical-muted'>Ngừng sử dụng giữ lại thuốc và lịch sử đơn đã kê. Thay đổi danh mục không sửa nội dung đơn thuốc đã lưu.</p>
          <div className='flex flex-wrap gap-2'>
            <Button type='submit' variant='primary'>{busy ? 'Đang lưu...' : 'Lưu thuốc'}</Button>
            {editing && <Button onClick={reset}>Bỏ chỉnh sửa</Button>}
          </div>
        </fieldset>
      </form>
    </div>
  </section>
}
