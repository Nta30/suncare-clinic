import { useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCalendarDay } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'

const dateInputClass = 'mt-1.5 min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:bg-suncare-50 disabled:text-clinical-muted'

function addDays(date, days) {
  const nextDate = new Date(`${date}T00:00:00Z`)
  nextDate.setUTCDate(nextDate.getUTCDate() + days)
  return nextDate.toISOString().slice(0, 10)
}

function formatDate(date) {
  if (!date) return ''
  return new Date(`${date}T12:00:00+07:00`).toLocaleDateString('vi-VN', {
    timeZone: 'Asia/Saigon', day: '2-digit', month: '2-digit', year: 'numeric',
  })
}

export default function OnsiteDateRangePicker({ dateFrom, dateTo, minDate, disabled, onApply }) {
  const [isOpen, setIsOpen] = useState(false)
  const [draftFrom, setDraftFrom] = useState('')
  const [draftTo, setDraftTo] = useState('')
  const [error, setError] = useState('')
  const [panelPosition, setPanelPosition] = useState({ top: 8, left: 8 })
  const triggerRef = useRef(null)
  const panelRef = useRef(null)

  useLayoutEffect(() => {
    if (!isOpen) return undefined

    function updatePosition() {
      const bounds = triggerRef.current?.getBoundingClientRect()
      if (!bounds) return
      const panelWidth = Math.min(768, window.innerWidth * 0.92)
      const panelHeight = Math.min(panelRef.current?.getBoundingClientRect().height || 360, window.innerHeight - 16)
      const left = Math.max(8, Math.min(bounds.left, window.innerWidth - panelWidth - 8))
      let top = bounds.bottom + 8
      if (top + panelHeight > window.innerHeight - 8) top = bounds.top - panelHeight - 8
      top = Math.max(8, Math.min(top, window.innerHeight - panelHeight - 8))
      setPanelPosition((current) => current.top === top && current.left === left ? current : { top, left })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [isOpen])

  function openPicker() {
    if (disabled) return
    setDraftFrom(dateFrom)
    setDraftTo(dateTo)
    setError('')
    setIsOpen(true)
  }

  function closePicker() {
    setIsOpen(false)
    setError('')
    triggerRef.current?.focus()
  }

  function clearRange() {
    setDraftFrom('')
    setDraftTo('')
    setError('')
    onApply('', '')
    setIsOpen(false)
    triggerRef.current?.focus()
  }

  function applyRange(from = draftFrom, to = draftTo) {
    if (from && to && from > to) {
      setError('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.')
      return
    }
    setError('')
    onApply(from, to)
    setIsOpen(false)
    triggerRef.current?.focus()
  }

  function applyPreset(days) {
    const from = addDays(minDate, 1)
    const to = addDays(from, days - 1)
    setDraftFrom(from)
    setDraftTo(to)
    applyRange(from, to)
  }

  function changeStartDate(event) {
    const nextFrom = event.target.value
    setDraftFrom(nextFrom)
    if (draftTo && draftTo < nextFrom) setDraftTo('')
    setError('')
  }

  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      closePicker()
    }
  }

  const rangeLabel = dateFrom || dateTo
    ? `${dateFrom ? formatDate(dateFrom) : 'Từ ngày'} → ${dateTo ? formatDate(dateTo) : 'Đến ngày'}`
    : 'Ngày bắt đầu  →  Ngày kết thúc'

  return (
    <div className='relative'>
      <div className='flex min-h-5 items-center justify-between gap-2'>
        <span className='text-sm font-semibold'><span className='text-suncare-800'>3.</span> Ngày khám</span>
        {(dateFrom || dateTo) && <button type='button' disabled={disabled} onClick={clearRange} className='min-h-11 text-xs font-semibold text-suncare-800 underline underline-offset-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:opacity-50'>Bỏ khoảng</button>}
      </div>
      <button ref={triggerRef} type='button' aria-haspopup='dialog' aria-expanded={isOpen} aria-controls='onsite-date-range-panel' disabled={disabled} onClick={() => isOpen ? closePicker() : openPicker()} className='mt-1.5 flex min-h-11 w-full items-center justify-between gap-2 rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-left text-sm text-clinical-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:bg-suncare-50 disabled:text-clinical-muted'>
        <span className='min-w-0 truncate'>{rangeLabel}</span>
        <FontAwesomeIcon icon={faCalendarDay} className='shrink-0 text-suncare-800' aria-hidden='true' />
      </button>

      {isOpen && createPortal(<div ref={panelRef} id='onsite-date-range-panel' role='dialog' aria-label='Chọn khoảng ngày khám' onKeyDown={handleKeyDown} style={{ top: `${panelPosition.top}px`, left: `${panelPosition.left}px` }} className='fixed z-50 grid max-h-[calc(100svh-1rem)] w-[min(48rem,92vw)] gap-4 overflow-y-auto rounded-2xl border border-clinical-border bg-white p-4 shadow-xl md:grid-cols-[9rem_minmax(0,1fr)]'>
        <section className='border-b border-clinical-border pb-4 md:border-b-0 md:border-r md:pb-0 md:pr-4'>
          <h3 className='text-sm font-bold'>Chọn nhanh</h3>
          <div className='mt-2 grid grid-cols-3 gap-2 md:grid-cols-1'>
            {[['Ngày mai', 1], ['7 ngày tới', 7], ['30 ngày tới', 30]].map(([label, days]) => <Button key={label} variant='outline' disabled={disabled} className='w-full px-2 text-xs md:justify-start md:px-3 md:text-sm' onClick={() => applyPreset(days)}>{label}</Button>)}
          </div>
        </section>

        <section className='space-y-4'>
          <h3 className='text-sm font-bold'>Khoảng ngày khám</h3>
          <div className='grid gap-3 sm:grid-cols-2'>
            <label htmlFor='onsite-date-from' className='block text-sm font-semibold'>Ngày bắt đầu
              <input id='onsite-date-from' type='date' min={minDate} value={draftFrom} onChange={changeStartDate} disabled={disabled} className={dateInputClass} />
            </label>
            <label htmlFor='onsite-date-to' className='block text-sm font-semibold'>Ngày kết thúc
              <input id='onsite-date-to' type='date' min={draftFrom || minDate} value={draftTo} onChange={(event) => {
                setDraftTo(event.target.value)
                setError('')
              }} disabled={disabled} className={dateInputClass} />
            </label>
          </div>
          {error && <p role='alert' className='text-sm text-clinical-danger'>{error}</p>}
          <div className='flex flex-wrap items-center justify-between gap-2 border-t border-clinical-border pt-3'>
            <button type='button' disabled={disabled} onClick={() => {
              clearRange()
            }} className='min-h-11 text-sm font-semibold text-suncare-800 underline underline-offset-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:opacity-50'>Xóa ngày</button>
            <div className='flex gap-2'>
              <Button variant='outline' onClick={closePicker}>Đóng</Button>
              <Button variant='primary' disabled={disabled} onClick={() => applyRange()}>Áp dụng</Button>
            </div>
          </div>
        </section>
      </div>, document.body)}
    </div>
  )
}
