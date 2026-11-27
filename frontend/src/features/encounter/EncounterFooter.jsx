import { useLayoutEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faChevronLeft, faChevronRight, faFloppyDisk } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'

export default function EncounterFooter({ sections, section, onMove, locked, saving, saveDisabled, saveReason, completion }) {
  const slot = useRef(null)
  const footer = useRef(null)
  const [bounds, setBounds] = useState(null)
  const [height, setHeight] = useState(88)
  const index = sections.findIndex(([code]) => code === section)
  const last = index === sections.length - 1
  useLayoutEffect(() => {
    function measure() {
      const rect = slot.current.getBoundingClientRect()
      const measuredHeight = Math.ceil(footer.current.getBoundingClientRect().height)
      setBounds((previous) => previous?.left === rect.left && previous?.width === rect.width ? previous : { left: rect.left, width: rect.width })
      setHeight(measuredHeight)
    }
    const observer = new ResizeObserver(measure)
    observer.observe(slot.current)
    observer.observe(footer.current)
    window.addEventListener('resize', measure)
    measure()
    return () => { observer.disconnect(); window.removeEventListener('resize', measure) }
  }, [])
  return <div ref={slot} style={{ height }} className='w-full shrink-0'>
    <footer ref={footer} role='region' aria-label='Điều hướng hồ sơ khám' style={bounds || undefined}
      className={`${bounds ? 'fixed bottom-0' : ''} z-30 grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)] items-center gap-2 border-t border-clinical-border bg-white px-2 py-3 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:px-4`}>
      <div className='min-w-0 justify-self-start'><Button disabled={index === 0 || locked} onClick={() => onMove(-1)} aria-label='Quay lại phần trước' className='px-2! sm:px-4!'>
        <FontAwesomeIcon icon={faChevronLeft} aria-hidden='true' /><span className='hidden sm:inline'>Quay lại</span>
      </Button></div>
      <p role='status' className='min-w-0 text-center text-[11px] leading-4 text-clinical-muted sm:text-sm'><span className='font-semibold tabular-nums text-clinical-ink'>{index + 1}/{sections.length}</span><span className='block sm:ml-2 sm:inline'>– {sections[index][1]}</span></p>
      <div className='flex min-w-0 items-center justify-self-end gap-1 sm:gap-2'>
        {last ? <><Button key='save' type='submit' form='encounter-notes-form' variant='outline' disabled={saveDisabled || locked} aria-label='Lưu hồ sơ khám' aria-description={saveReason} title={saveReason} aria-busy={saving} className='px-2! sm:px-3! xl:px-4!'>
          <FontAwesomeIcon icon={faFloppyDisk} aria-hidden='true' /><span className='hidden xl:inline'>{saving ? 'Đang lưu…' : 'Lưu hồ sơ khám'}</span>
        </Button>{completion}</> : <Button key='next' variant='primary' disabled={locked} onClick={(event) => { event.preventDefault(); onMove(1) }} aria-label='Tiếp theo phần sau' className='px-2! sm:px-4!'><span className='hidden sm:inline'>Tiếp theo</span><FontAwesomeIcon icon={faChevronRight} aria-hidden='true' /></Button>}
      </div>
    </footer>
  </div>
}
