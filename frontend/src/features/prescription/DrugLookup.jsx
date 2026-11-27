import { useId, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faChevronDown, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons'

const normalize = (value) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase()

export default function DrugLookup({ drugs, disabled, onSelect }) {
  const id = useId()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)
  const matches = drugs.filter((drug) => drug.isActive && normalize(`${drug.name} ${drug.ingredient || ''} ${drug.code}`).includes(normalize(query.trim())))
  const options = matches.slice(0, 30)
  const expanded = open && !disabled
  function choose(drug) {
    if (onSelect(drug) === false) return
    setQuery('')
    setOpen(false)
  }
  function keydown(event) {
    if (event.key === 'Escape') { setOpen(false); event.stopPropagation() }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      setHighlighted((index) => !expanded ? 0 : options.length ? (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length : 0)
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      if (expanded && options[highlighted]) choose(options[highlighted])
    }
  }
  return <div className='relative min-w-0' onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false) }}>
    <label htmlFor={id} className='mb-2 block text-sm font-semibold'>Tìm kiếm thuốc</label>
    <div className='relative'>
      <FontAwesomeIcon icon={faMagnifyingGlass} className='pointer-events-none absolute left-3 top-3.5 text-sm text-clinical-muted' />
      <input id={id} role='combobox' aria-autocomplete='list' aria-expanded={expanded} aria-controls={`${id}-options`}
        aria-activedescendant={expanded && options[highlighted] ? `${id}-option-${highlighted}` : undefined}
        aria-describedby={`${id}-help`} autoComplete='off' value={query} disabled={disabled} maxLength={100}
        placeholder='Nhập tên thuốc, hoạt chất hoặc mã thuốc…' onFocus={() => setOpen(true)} onKeyDown={keydown}
        onChange={(event) => { setQuery(event.target.value); setHighlighted(0); setOpen(true) }}
        className='min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white py-2 pl-9 pr-9 text-sm disabled:bg-clinical-canvas' />
      <button type='button' disabled={disabled} aria-label={expanded ? 'Đóng danh mục thuốc' : 'Mở danh mục thuốc'}
        onClick={() => setOpen((previous) => !previous)} className='absolute right-0 top-0 grid h-11 w-9 place-items-center rounded-r-xl text-clinical-muted'>
        <FontAwesomeIcon icon={faChevronDown} className='text-xs' /></button>
    </div>
    <p id={`${id}-help`} className='sr-only'>Chọn thuốc đang sử dụng từ danh mục. Dùng mũi tên và Enter để chọn, Escape để đóng.</p>
    <div hidden={!expanded} className='prescription-lookup absolute left-0 right-0 z-30 mt-1 overflow-hidden rounded-xl border border-clinical-border-strong bg-white shadow-lg'>
      <ul id={`${id}-options`} role='listbox' aria-label='Kết quả tìm thuốc' className='max-h-64 overflow-y-auto p-1'>
        {options.map((drug, index) => <li key={drug.id} id={`${id}-option-${index}`} role='option' aria-selected={index === highlighted}
          onMouseDown={(event) => event.preventDefault()} onMouseMove={() => setHighlighted(index)}
          onClick={() => choose(drug)} className={`cursor-pointer rounded-lg px-3 py-2.5 text-sm ${index === highlighted ? 'bg-suncare-100' : 'hover:bg-clinical-canvas'}`}>
          <p className='break-words font-semibold'>{drug.name} {drug.strength || ''}</p>
          <p className='mt-1 break-words text-xs text-clinical-muted'>{drug.ingredient || 'Chưa có hoạt chất'} · {drug.dosageForm || 'Chưa có dạng bào chế'}</p>
          <p className='mt-1 text-xs text-clinical-muted'>Liều: {drug.doseUnit || 'chưa xác nhận'} · Cấp phát: {drug.dispensingUnit || 'cần rà soát'}</p>
          <p className='mt-1 break-all text-xs text-clinical-muted'>{drug.code}</p>
        </li>)}
      </ul>
      {options.length === 0 && <p role='status' className='p-3 text-sm text-clinical-muted'>{drugs.length ? 'Không tìm thấy thuốc phù hợp.' : 'Chưa có thuốc đang sử dụng. Quản trị viên cần bổ sung danh mục.'}</p>}
      {matches.length > 30 && <p role='status' className='border-t border-clinical-border px-3 py-2 text-xs text-clinical-muted'>30/{matches.length} kết quả. Nhập thêm từ khóa để thu hẹp.</p>}
    </div>
  </div>
}
