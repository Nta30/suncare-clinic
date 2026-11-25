import { useEffect, useState } from 'react'
import Button from '../../components/ui/Button'
import { getAllergens, saveAllergen } from '../../services/allergenApi'

const empty = { code: '', name: '', isActive: true }
const inputClass = 'mt-1 min-h-11 w-full rounded-lg border border-clinical-border-strong bg-white px-3 py-2 focus-visible:outline-2 focus-visible:outline-suncare-700'
export default function AllergenManagement({ onSessionExpired }) {
  const [state, setState] = useState({ loading: true, items: [] })
  const [reload, setReload] = useState(0)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(empty)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [search, setSearch] = useState('')
  useEffect(() => {
    let active = true
    getAllergens().then((items) => { if (active) setState({ items }) }).catch((failure) => {
      if (!active) return
      if (failure.status === 401) onSessionExpired(failure.message)
      else setState({ items: [], error: failure.message })
    })
    return () => { active = false }
  }, [reload, onSessionExpired])
  function reset() {
    setEditing(null)
    setForm(empty)
    setError('')
    setMessage('')
  }
  function refresh() {
    reset()
    setState({ loading: true, items: [] })
    setReload((value) => value + 1)
  }
  function edit(item) {
    setEditing(item)
    setForm({ code: item.code, name: item.name, isActive: item.isActive })
    setError('')
    setMessage('')
  }
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const items = await saveAllergen(editing?.id, { ...form, code: form.code.trim().toUpperCase(), name: form.name.trim(), ...(editing ? { version: editing.version } : {}) })
      setState({ items })
      setEditing(null)
      setForm(empty)
      setMessage('Đã lưu chất gây dị ứng.')
    } catch (failure) {
      if (failure.status === 401) onSessionExpired(failure.message)
      else setError([failure.message, ...Object.values(failure.fieldErrors || {})].join(' '))
    } finally { setBusy(false) }
  }
  const normalize = (value) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase()
  const visible = state.items.filter((item) => normalize(`${item.code} ${item.name}`).includes(normalize(search.trim())))
  return <section className='w-full space-y-4 text-clinical-ink' aria-labelledby='allergen-title'>
    <header className='flex flex-wrap items-center justify-between gap-3'><div><h1 id='allergen-title' className='text-2xl font-bold'>Danh mục chất gây dị ứng</h1><p className='mt-1 text-sm text-clinical-muted'>Quản lý mã nội bộ và tên thống nhất để bác sĩ chọn khi ghi nhận dị ứng.</p></div><Button disabled={busy} onClick={refresh}>Tải lại</Button></header>
    {message && <p role='status' className='text-sm text-clinical-success'>{message}</p>}{error && <p role='alert' className='text-sm text-clinical-danger'>{error}</p>}
    <div className='grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_22rem]'>
      <div className='min-w-0 space-y-3'><label className='block text-sm font-semibold'>Tìm mã hoặc tên<input type='search' value={search} onChange={(event) => setSearch(event.target.value)} className={inputClass} /></label>
        {state.loading && <p role='status'>Đang tải danh mục...</p>}{state.error && <p role='alert' className='text-clinical-danger'>{state.error} Hãy chọn Tải lại.</p>}
        {!state.loading && !state.error && visible.length === 0 && <p className='rounded-xl border border-dashed border-clinical-border-strong p-5'>{state.items.length === 0 ? 'Chưa có chất gây dị ứng. Hãy tạo mã và tên trước khi bác sĩ sử dụng.' : 'Không có chất phù hợp với từ khóa.'}</p>}
        <ul className='divide-y divide-clinical-border rounded-xl border border-clinical-border bg-white'>{visible.map((item) => <li key={item.id} className='flex flex-wrap items-center justify-between gap-3 p-4'><div className='min-w-0'><p className='break-words font-bold'>{item.name}</p><p className='text-sm text-clinical-muted'>{item.code} · {item.isActive ? 'Đang áp dụng' : 'Đã ngừng áp dụng'}</p></div><Button disabled={busy} onClick={() => edit(item)}>Sửa</Button></li>)}</ul>
      </div>
      <form onSubmit={submit} className='space-y-4 rounded-xl border border-clinical-border bg-white p-5'><h2 className='text-lg font-bold'>{editing ? 'Sửa chất gây dị ứng' : 'Thêm chất gây dị ứng'}</h2><fieldset disabled={busy || state.loading || Boolean(state.error)} className='space-y-4'>
        <label className='block text-sm font-semibold'>Mã chất *<input required maxLength={40} pattern='[A-Za-z0-9_-]{1,40}' readOnly={Boolean(editing)} value={form.code} onChange={(event) => setForm((previous) => ({ ...previous, code: event.target.value }))} className={inputClass} /><span className='mt-1 block text-xs font-normal text-clinical-muted'>Chữ, số, dấu _ hoặc -. Mã giữ cố định sau khi tạo.</span></label>
        <label className='block text-sm font-semibold'>Tên chất rõ ràng *<input required maxLength={200} value={form.name} onChange={(event) => setForm((previous) => ({ ...previous, name: event.target.value }))} className={inputClass} /></label>
        <label className='flex min-h-11 items-center gap-2 text-sm'><input type='checkbox' checked={form.isActive} onChange={(event) => setForm((previous) => ({ ...previous, isActive: event.target.checked }))} />Đang áp dụng</label>
        <p className='text-xs text-clinical-muted'>Ngừng áp dụng sẽ ẩn chất khỏi lựa chọn mới; không xóa các hồ sơ và bản chốt đã có.</p>
        <div className='flex flex-wrap gap-2'><Button type='submit' variant='primary'>{busy ? 'Đang lưu...' : 'Lưu danh mục'}</Button>{editing && <Button onClick={reset}>Bỏ chỉnh sửa</Button>}</div>
      </fieldset></form>
    </div>
  </section>
}
