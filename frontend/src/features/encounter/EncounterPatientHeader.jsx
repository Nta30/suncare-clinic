import { useEffect, useRef, useState } from 'react'

export default function EncounterPatientHeader({ children, patientName, status, allergies }) {
  const fullHeader = useRef(null)
  const [compact, setCompact] = useState(false)
  useEffect(() => {
    const observer = new IntersectionObserver(([entry]) => {
      setCompact(!entry.isIntersecting && entry.boundingClientRect.bottom <= 0)
    }, { threshold: 0 })
    observer.observe(fullHeader.current)
    return () => observer.disconnect()
  }, [])
  const allergyText = allergies.length
    ? `Dị ứng: ${allergies.map((item) => item.substance || item.otherNote || 'Chất chưa đối chiếu').join(', ')}`
    : 'Dị ứng: chưa ghi nhận'
  return <>
    <header ref={fullHeader} className='encounter-context space-y-3 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'>{children}</header>
    {/* Reserve the compact bar's space so revealing it never moves the form. */}
    <div className='encounter-compact-slot sticky top-0 z-20 h-14' data-compact={compact}>
      <div className='encounter-compact-header grid h-14 min-w-0 grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 border-b border-clinical-border bg-white px-3 shadow-sm sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:gap-x-5 sm:px-4' aria-hidden={!compact} aria-label='Thông tin người bệnh thu gọn' role='region'>
        <p className='truncate text-sm font-bold' title={patientName}>{patientName}</p>
        <span className='row-span-2 rounded-full bg-clinical-leaf-soft px-2 py-1 text-xs font-bold text-clinical-success sm:row-span-1'>{status}</span>
        <p className={`col-start-1 truncate text-xs sm:col-start-auto ${allergies.length ? 'font-semibold text-clinical-warning' : 'text-clinical-muted'}`} title={allergyText}>{allergyText}</p>
      </div>
    </div>
  </>
}
