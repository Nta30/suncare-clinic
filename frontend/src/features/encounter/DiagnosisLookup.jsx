import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import { searchDiagnosisConcepts } from '../../services/encounterApi'

export default function DiagnosisLookup({ value, onSelect, disabled, onSessionExpired }) {
  const [search, setSearch] = useState('')
  const [groups, setGroups] = useState(true)
  const [page, setPage] = useState(1)
  const [open, setOpen] = useState(!value)
  const [state, setState] = useState({ loading: true })
  const [reload, setReload] = useState(0)
  const searchRef = useRef(null)
  const resultsRef = useRef(null)
  const selectedRef = useRef(null)
  const canSearch = groups || search.trim().length >= 2
  const expanded = open || !value
  useEffect(() => {
    if (!expanded || !canSearch) return undefined
    let active = true
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      searchDiagnosisConcepts(search.trim(), page, groups, controller.signal).then((data) => {
        if (active) setState({ data })
      }).catch((error) => {
        if (!active) return
        if (error.status === 401) onSessionExpired(error.message)
        else setState({ error: error.message })
      })
    }, 300)
    return () => { active = false; window.clearTimeout(timer); controller.abort() }
  }, [expanded, canSearch, search, page, groups, reload, onSessionExpired])
  function change(text, browse = false) {
    setSearch(text)
    setGroups(browse)
    setPage(1)
    setState({ loading: true })
  }
  function retry() { setState({ loading: true }); setReload((previous) => previous + 1) }
  function choose(item) {
    onSelect(item)
    setOpen(false)
    window.requestAnimationFrame(() => selectedRef.current?.focus())
  }
  function reopen() {
    setOpen(true)
    setState({ loading: true })
    window.requestAnimationFrame(() => searchRef.current?.focus())
  }
  function moveResult(event) {
    const buttons = [...(resultsRef.current?.querySelectorAll('button') || [])]
    const index = buttons.indexOf(event.target)
    let next
    if (event.key === 'ArrowDown') next = Math.min(index + 1, buttons.length - 1)
    else if (event.key === 'ArrowUp') next = Math.max(index - 1, 0)
    else if (event.key === 'Home') next = 0
    else if (event.key === 'End') next = buttons.length - 1
    else return
    event.preventDefault()
    buttons[next]?.focus()
  }
  const data = state.data
  const smallerTerms = [...new Set(search.trim().split(/\s+/).filter((term) => term.length >= 2))]
  return <section className='min-w-0 space-y-3 rounded-lg border border-clinical-border p-3' aria-label='Chọn chẩn đoán từ danh mục ICD'>
    {value && <div ref={selectedRef} tabIndex={-1} className='space-y-2 rounded-lg border border-clinical-leaf/40 bg-clinical-leaf-soft p-3 focus-visible:outline-2 focus-visible:outline-suncare-700'>
      <p className='text-xs font-bold uppercase tracking-wide text-clinical-success'>Đã chọn chẩn đoán</p>
      <p className='break-words font-semibold'><span className='mr-2 inline-block rounded bg-white px-2 py-1'>{value.code}</span>{value.name}</p>
      {value.englishName && <p className='break-words text-sm text-clinical-muted' lang='en'>{value.englishName}</p>}
      <p className='text-xs text-clinical-muted'>{value.codeSystem} · Phiên bản {value.codeVersion || 'chưa xác định'}</p>
      {value.categoryCode !== value.code && value.categoryName && <p className='break-words text-sm'>Nhóm {value.categoryCode} · {value.categoryName}</p>}
      {!value.primaryAllowed && <p className='text-sm font-semibold text-clinical-warning'>Mã này không được làm chẩn đoán chính.</p>}
      <div className='flex flex-wrap gap-2'><Button disabled={disabled} onClick={reopen}>Đổi mã bệnh</Button><Button disabled={disabled} onClick={() => { onSelect(null); reopen() }}>Bỏ chọn mã</Button></div>
    </div>}
    {expanded && <>
      <label className='block text-sm font-semibold'>Tìm mã hoặc tên bệnh
        <input ref={searchRef} type='search' disabled={disabled} maxLength={100} value={search} onChange={(event) => change(event.target.value)} onKeyDown={(event) => {
          if (event.key === 'Enter') { event.preventDefault(); if (canSearch) retry() }
          if (event.key === 'ArrowDown') { event.preventDefault(); resultsRef.current?.querySelector('button')?.focus() }
          if (event.key === 'Escape' && value) { setOpen(false); selectedRef.current?.focus() }
        }} placeholder='Tên bệnh tiếng Việt/Anh hoặc mã ICD' aria-describedby='diagnosis-search-help' className='mt-1 min-h-11 w-full rounded-lg border border-clinical-border-strong px-3 py-2 font-normal focus-visible:outline-2 focus-visible:outline-suncare-700' />
      </label>
      <p id='diagnosis-search-help' className='text-xs text-clinical-muted'>Tìm không dấu; mã có hoặc không có dấu chấm. Ưu tiên mã và tên khớp nhất. Bạn cũng có thể duyệt nhóm rồi chọn mã cụ thể.</p>
      <div className='flex flex-wrap items-center gap-2'>
        <Button disabled={disabled} aria-pressed={groups} onClick={() => change('', true)}>Duyệt nhóm ICD</Button>
        {!groups && search.trim().length >= 2 && <Button disabled={disabled} onClick={() => change(search, true)}>Xem nhóm khớp từ khóa</Button>}
        {!groups && <Button disabled={disabled || !canSearch} onClick={retry}>Tìm ngay</Button>}
        {groups && <label className='text-sm'>Lọc theo chữ đầu mã<select aria-label='Chữ đầu mã ICD' disabled={disabled} value={/^[A-Z]$/.test(search) ? search : ''} onChange={(event) => change(event.target.value, true)} className='ml-2 min-h-11 rounded-lg border border-clinical-border-strong bg-white px-2'><option value=''>Tất cả</option>{'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('').map((letter) => <option key={letter}>{letter}</option>)}</select></label>}
        {value && <Button disabled={disabled} onClick={() => setOpen(false)}>Giữ mã đang chọn</Button>}
      </div>
      {!canSearch && <p className='text-sm text-clinical-muted'>Nhập ít nhất 2 ký tự hoặc chọn “Duyệt nhóm ICD” để xem danh mục.</p>}
      {canSearch && state.loading && <p role='status' className='text-sm'>Đang tra cứu danh mục...</p>}
      {canSearch && state.error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{state.error}</p><Button disabled={disabled} onClick={retry}>Thử lại danh mục</Button></div>}
      {canSearch && data && <>
        <p role='status' className='text-sm text-clinical-muted'>{data.totalElements ? `${data.totalElements} ${groups ? 'nhóm bệnh' : 'mã phù hợp'} · ${Math.min((page - 1) * data.pageSize + 1, data.totalElements)}–${Math.min(page * data.pageSize, data.totalElements)}` : 'Không tìm thấy kết quả phù hợp.'}</p>
        {data.items.length === 0 && <div className='space-y-2 text-sm'><p>Danh mục chưa có tên chứa đầy đủ các từ bạn nhập. Hãy thử một từ chính hoặc duyệt nhóm; tên triệu chứng có thể khác tên trong danh mục.</p>{!groups && smallerTerms.length > 1 && <div className='flex flex-wrap gap-2'>{smallerTerms.map((term) => <Button key={term} disabled={disabled} onClick={() => change(term)}>Tìm “{term}”</Button>)}</div>}</div>}
        {data.items.length > 0 && <ul ref={resultsRef} aria-label={groups ? 'Nhóm bệnh ICD' : 'Kết quả tra cứu ICD'} className='max-h-[28rem] divide-y divide-clinical-border overflow-y-auto rounded-lg border border-clinical-border' onKeyDown={moveResult}>{data.items.map((item) => <li key={item.id}>
          <button type='button' disabled={disabled} onClick={() => groups ? change(item.code) : choose(item)} className='min-h-11 w-full space-y-1 p-3 text-left text-sm hover:bg-suncare-50 focus-visible:bg-suncare-100 focus-visible:outline-2 focus-visible:outline-suncare-700'>
            <span className='block break-words font-semibold'><span className='mr-2 inline-block rounded bg-suncare-100 px-2 py-1 font-bold'>{item.code}</span>{item.name}</span>
            {item.englishName && <span className='block break-words text-clinical-muted' lang='en'>{item.englishName}</span>}
            {!groups && item.categoryCode !== item.code && item.categoryName && <span className='block break-words text-xs text-clinical-muted'>Nhóm {item.categoryCode} · {item.categoryName}</span>}
            <span className='block text-xs text-clinical-muted'>{item.codeSystem} · {item.codeVersion}</span>
            {!groups && !item.primaryAllowed && <span className='block font-semibold text-clinical-warning'>Không dùng làm chẩn đoán chính</span>}
            <span className='block font-semibold text-suncare-700'>{groups ? 'Xem mã trong nhóm →' : value?.id === item.id ? 'Đang chọn · Chọn lại' : 'Chọn chẩn đoán này →'}</span>
          </button>
        </li>)}</ul>}
        {data.totalPages > 1 && <nav aria-label='Phân trang danh mục ICD' className='flex flex-wrap items-center gap-2'><Button disabled={disabled || page <= 1} onClick={() => { setPage(page - 1); setState({ loading: true }) }}>Trước</Button><span className='text-sm'>Trang {page}/{data.totalPages}</span><Button disabled={disabled || page >= data.totalPages} onClick={() => { setPage(page + 1); setState({ loading: true }) }}>Sau</Button></nav>}
      </>}
    </>}
  </section>
}
