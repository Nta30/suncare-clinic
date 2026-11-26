import { useEffect, useId, useState } from 'react'
import { searchSymptoms } from '../../services/encounterApi'

const normalize = (value) => value.normalize('NFD').replace(/\p{M}/gu, '').toLocaleLowerCase('vi-VN').replaceAll('đ', 'd').trim().replace(/\s+/g, ' ')

export default function SymptomMultiSelect({ value, onChange, disabled, onSessionExpired }) {
  const id = useId()
  const [query, setQuery] = useState('')
  const [state, setState] = useState({})
  const [open, setOpen] = useState(false)
  const [index, setIndex] = useState(-1)
  const [message, setMessage] = useState('')
  const [reload, setReload] = useState(0)
  const term = query.trim().replace(/\s+/g, ' ')
  useEffect(() => {
    if (!term) return undefined
    let current = true
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      searchSymptoms(term, controller.signal).then((data) => {
        if (current) setState({ term, data })
      }).catch((error) => {
        if (!current) return
        if (error.status === 401) onSessionExpired(error.message)
        else setState({ term, error: error.message })
      })
    }, 250)
    return () => { current = false; window.clearTimeout(timer); controller.abort() }
  }, [term, reload, onSessionExpired])
  const loading = Boolean(term) && (state.term !== term || state.loading)
  const items = !loading && !state.error ? state.data || [] : []
  const expanded = open && Boolean(term)
  const selected = (item) => value.some((existing) => normalize(existing.name) === normalize(item.name)
    || (item.symptomId != null && existing.symptomId === item.symptomId))
  function add(item) {
    if (disabled) return
    const name = item.name.trim().replace(/\s+/g, ' ')
    if (!normalize(name)) return
    if (name.length > 200) { setMessage('Tên triệu chứng tối đa 200 ký tự.'); return }
    if (selected({ ...item, name })) { setMessage('Triệu chứng này đã được chọn.'); return }
    if (value.length >= 50) { setMessage('Tối đa 50 triệu chứng cho một lượt khám.'); return }
    onChange([...value, { symptomId: item.symptomId ?? null, name }])
    setQuery('')
    setIndex(-1)
    setOpen(false)
    setMessage('')
  }
  function change(text) { setQuery(text); setOpen(true); setIndex(-1); setMessage('') }
  function keyboard(event) {
    if (event.key === 'Escape') { setOpen(false); return }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      if (items.length) setIndex((previous) => event.key === 'ArrowDown' ? (previous + 1) % items.length : (previous < 0 ? items.length - 1 : (previous + items.length - 1) % items.length))
    }
    if (event.key !== 'Enter') return
    event.preventDefault()
    if (!term || loading || state.error) return
    if (items.length) { if (index >= 0) add(items[index]); else setIndex(0) }
    else add({ name: term })
  }
  return <div className='min-w-0 space-y-2'>
    <label htmlFor={id} className='block text-sm font-semibold'>Triệu chứng cơ năng <span className='font-normal text-clinical-muted'>(không bắt buộc)</span></label>
    {value.length > 0 && <ul className='flex flex-wrap gap-2' aria-label='Triệu chứng đã chọn'>{value.map((item, position) => <li key={`${item.symptomId ?? 'free'}-${normalize(item.name)}`} className='inline-flex min-w-0 max-w-full items-center gap-1 rounded-lg border border-clinical-border bg-clinical-leaf-soft px-2 py-1 text-sm'><span className='min-w-0 break-words'>{item.name}{item.symptomId == null && <span className='ml-1 text-xs text-clinical-muted'>· Tự nhập</span>}</span><button type='button' disabled={disabled} onClick={() => onChange(value.filter((_, index) => index !== position))} aria-label={`Xóa triệu chứng ${item.name}`} className='grid h-9 w-9 shrink-0 place-items-center rounded-lg font-bold hover:bg-white disabled:opacity-50'>×</button></li>)}</ul>}
    <input id={id} type='text' role='combobox' aria-autocomplete='list' aria-expanded={expanded} aria-controls={`${id}-options`} aria-activedescendant={expanded && index >= 0 && items[index] ? `${id}-option-${index}` : undefined} aria-describedby={`${id}-help`} value={query} onChange={(event) => change(event.target.value)} onFocus={() => setOpen(true)} onBlur={() => setOpen(false)} onKeyDown={keyboard} disabled={disabled} maxLength={100} autoComplete='off' placeholder='Nhập tên triệu chứng để tìm kiếm…' className='min-h-11 w-full min-w-0 rounded-lg border border-clinical-border-strong bg-white px-3 py-2 text-sm disabled:bg-clinical-canvas' />
    <p id={`${id}-help`} className='text-xs text-clinical-muted'>Tìm có dấu hoặc không dấu. Khi không có kết quả, nhấn Enter để ghi triệu chứng tự nhập cho lượt khám này; không thêm vào danh mục dùng chung.</p>
    {message && <p role='status' className='text-sm text-clinical-warning'>{message}</p>}
    {expanded && <div className='rounded-lg border border-clinical-border bg-white p-2'>
      {loading && <p role='status' className='p-2 text-sm text-clinical-muted'>Đang tìm triệu chứng…</p>}
      {!loading && state.error && <div role='alert' className='p-2 text-sm text-clinical-danger'><p>{state.error}</p><button type='button' disabled={disabled} onClick={() => { setState({ term, loading: true }); setReload((previous) => previous + 1) }} className='mt-2 min-h-11 underline'>Thử lại tra cứu triệu chứng</button></div>}
      <div id={`${id}-options`} role='listbox' aria-label='Gợi ý triệu chứng' aria-multiselectable='true' className='max-h-64 overflow-y-auto'>{items.map((item, position) => <button id={`${id}-option-${position}`} role='option' aria-selected={selected(item)} tabIndex={-1} type='button' key={item.symptomId} disabled={disabled} onMouseDown={(event) => event.preventDefault()} onClick={() => add(item)} className={`block min-h-11 w-full break-words rounded-lg px-3 py-2 text-left text-sm hover:bg-suncare-50 ${index === position ? 'bg-suncare-100' : ''}`}>{item.name}{selected(item) ? ' · Đã chọn' : ''}</button>)}</div>
      {!loading && !state.error && items.length === 0 && <p role='status' className='p-2 text-sm'>Không tìm thấy triệu chứng. Nhấn Enter để ghi nhận “{term}” cho lượt khám này.</p>}
      {items.length === 20 && <p className='px-2 pt-2 text-xs text-clinical-muted'>Hiển thị tối đa 20 gợi ý. Nhập thêm từ khóa để thu hẹp kết quả.</p>}
    </div>}
  </div>
}
