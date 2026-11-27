import { useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faChevronDown } from '@fortawesome/free-solid-svg-icons'

const routes = ['Uống', 'Bôi ngoài da', 'Hít', 'Xịt mũi', 'Nhỏ tai', 'Nhỏ mắt', 'Đặt trực tràng']
const fold = (value) => value.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').toLowerCase()

export default function RouteLookup({ id, value, onChange, onBlur, ...inputProps }) {
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)
  const options = routes.filter((route) => fold(route).includes(fold(value.trim())))
  function choose(value) { onChange(value); setOpen(false) }
  return <div className='relative' onBlur={(event) => {
    if (!event.currentTarget.contains(event.relatedTarget)) { setOpen(false); onBlur() }
  }}>
    <input {...inputProps} id={id} role='combobox' aria-label='Đường dùng *' aria-autocomplete='list'
      aria-expanded={open} aria-controls={id + '-routes'} aria-activedescendant={open && options[highlighted] ? id + '-route-' + highlighted : undefined}
      autoComplete='off' value={value} onFocus={() => setOpen(true)}
      onChange={(event) => { onChange(event.target.value); setOpen(true); setHighlighted(0) }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') { setOpen(false); event.stopPropagation() }
        if (event.key === 'Enter') {
          event.preventDefault()
          if (open && options[highlighted]) choose(options[highlighted])
          else setOpen(false)
        }
        if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
          event.preventDefault()
          setOpen(true)
          setHighlighted((index) => !open ? 0 : options.length ? (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length : 0)
        }
      }} />
    <FontAwesomeIcon icon={faChevronDown} className='pointer-events-none absolute right-3 top-4 text-xs text-clinical-muted' />
    <div hidden={!open} className='prescription-lookup absolute left-0 right-0 z-20 mt-1 rounded-lg border border-clinical-border bg-white p-1 shadow-lg'>
      <ul id={id + '-routes'} role='listbox' aria-label='Đường dùng gợi ý'>
        {options.map((route, index) => <li key={route} id={id + '-route-' + index} role='option' aria-selected={highlighted === index}
          onMouseDown={(event) => event.preventDefault()} onClick={() => choose(route)}
          className={'cursor-pointer rounded-md px-3 py-2 text-sm ' + (highlighted === index ? 'bg-suncare-100' : 'hover:bg-clinical-canvas')}>{route}</li>)}
      </ul>
      <p className='px-3 py-2 text-xs text-clinical-muted'>Có thể nhập đường dùng khác theo chỉ định.</p>
    </div>
  </div>
}
