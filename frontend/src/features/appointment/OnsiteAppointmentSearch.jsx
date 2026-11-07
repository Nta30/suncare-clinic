import { useEffect, useMemo, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCalendarDay, faMagnifyingGlass, faRotateRight, faUser, faUsers } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import OnsiteDateRangePicker from './OnsiteDateRangePicker'
import { getToken } from '../../services/accountApi'
import { bookOnsiteAppointment, getOnsiteAppointmentOptions, searchOnsiteAppointmentAvailability } from '../../services/onsiteAppointmentApi'

const inputClass = 'mt-1.5 min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:bg-suncare-50 disabled:text-clinical-muted'

function businessToday() {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Saigon', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
  return `${values.year}-${values.month}-${values.day}`
}

function formatVnd(amount) {
  if (amount == null) return ''
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
    .format(BigInt(amount))
}

function formatWorkDate(date) {
  return new Date(`${date}T12:00:00+07:00`).toLocaleDateString('vi-VN', {
    timeZone: 'Asia/Saigon', weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric',
  })
}

function formatShortWorkDate(date) {
  return new Date(`${date}T12:00:00+07:00`).toLocaleDateString('vi-VN', {
    timeZone: 'Asia/Saigon', weekday: 'short', day: '2-digit', month: '2-digit',
  })
}

function emptyFilters() {
  return { specialtyId: '', dateFrom: '', dateTo: '', shiftId: '', doctorName: '' }
}

function hasSearchCriteria(filters) {
  return Boolean(filters.specialtyId || filters.dateFrom || filters.dateTo || filters.shiftId || filters.doctorName.trim())
}

export default function OnsiteAppointmentSearch({ onSessionExpired }) {
  const [visitMode, setVisitMode] = useState('')
  const [options, setOptions] = useState(null)
  const [optionsState, setOptionsState] = useState('loading')
  const [optionsError, setOptionsError] = useState('')
  const [filters, setFilters] = useState(emptyFilters)
  const [results, setResults] = useState([])
  const [searchState, setSearchState] = useState('idle')
  const [searchError, setSearchError] = useState('')
  const [validationError, setValidationError] = useState('')
  const [expandedDoctorId, setExpandedDoctorId] = useState(null)
  const [expandedDate, setExpandedDate] = useState('')
  const [selectedDoctorId, setSelectedDoctorId] = useState(null)
  const [selectedSchedule, setSelectedSchedule] = useState(null)
  const [patientSymptoms, setPatientSymptoms] = useState('')
  const [bookingState, setBookingState] = useState('idle')
  const [bookingError, setBookingError] = useState('')
  const [bookingErrorStatus, setBookingErrorStatus] = useState(null)
  const [bookingResult, setBookingResult] = useState(null)
  const latestRequestId = useRef(0)
  const searchTimer = useRef(null)
  const bookingRequestRef = useRef(null)
  const bookingInFlightRef = useRef(false)

  useEffect(() => {
    let active = true
    getOnsiteAppointmentOptions(getToken()).then((data) => {
      if (!active) return
      setOptions(data)
      setOptionsState('ready')
    }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired()
      setOptionsError(error.message)
      setOptionsState('error')
    })
    return () => {
      active = false
      window.clearTimeout(searchTimer.current)
      latestRequestId.current += 1
    }
  }, [onSessionExpired])

  function updateFilters(nextFilters) {
    if (bookingInFlightRef.current || bookingOutcomeUnknown) return
    setFilters(nextFilters)
    setResults([])
    setExpandedDoctorId(null)
    setExpandedDate('')
    setSelectedDoctorId(null)
    setSelectedSchedule(null)
    setBookingState('idle')
    setBookingError('')
    setBookingErrorStatus(null)
    setBookingResult(null)
    bookingRequestRef.current = null
    setSearchError('')
    setValidationError('')

    const requestId = ++latestRequestId.current
    window.clearTimeout(searchTimer.current)
    if (!hasSearchCriteria(nextFilters)) {
      setSearchState('idle')
      return
    }

    const today = businessToday()
    if ((nextFilters.dateFrom && nextFilters.dateFrom < today)
      || (nextFilters.dateTo && nextFilters.dateTo < today)) {
      setValidationError('Khoảng ngày khám phải từ hôm nay trở đi.')
      setSearchState('idle')
      return
    }
    if (nextFilters.dateFrom && nextFilters.dateTo && nextFilters.dateFrom > nextFilters.dateTo) {
      setValidationError('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.')
      setSearchState('idle')
      return
    }

    setSearchState('loading')
    searchTimer.current = window.setTimeout(() => {
      void searchOnsiteAppointmentAvailability(getToken(), nextFilters).then((data) => {
        if (requestId !== latestRequestId.current) return
        setResults(data)
        setSearchState('success')
      }).catch((error) => {
        if (requestId !== latestRequestId.current) return
        if (error.status === 401) onSessionExpired()
        setSearchError(error.message)
        setSearchState('error')
      })
    }, nextFilters.doctorName.trim() ? 250 : 0)
  }

  function changeFilter(event) {
    updateFilters({ ...filters, [event.target.name]: event.target.value })
  }

  function clearFilters() {
    updateFilters(emptyFilters())
  }

  function resetBooking() {
    setBookingState('idle')
    setBookingError('')
    setBookingErrorStatus(null)
    setBookingResult(null)
    bookingRequestRef.current = null
  }

  function chooseSchedule(doctor, schedule) {
    const isAlreadySelected = selectedSchedule
      && String(selectedSchedule.specialtyId) === String(schedule.specialtyId)
      && selectedSchedule.date === schedule.date
      && String(selectedSchedule.shiftId) === String(schedule.shiftId)
      && selectedSchedule.doctorId === doctor.doctorId
    if (isAlreadySelected) {
      setSelectedSchedule(null)
      setSelectedDoctorId(null)
    } else {
      setSelectedSchedule(schedule)
      setSelectedDoctorId(doctor.doctorId)
    }
    resetBooking()
  }

  function toggleDoctorSchedules(doctor) {
    if (expandedDoctorId === doctor.doctorId) {
      setExpandedDoctorId(null)
      setExpandedDate('')
      return
    }
    const doctorDates = [...new Set(doctor.schedules.map((schedule) => schedule.date))].sort()
    setExpandedDoctorId(doctor.doctorId)
    setExpandedDate(selectedSchedule?.doctorId === doctor.doctorId && doctorDates.includes(selectedSchedule.date)
      ? selectedSchedule.date
      : doctorDates[0] || '')
  }

  function selectDoctorDate(date) {
    if (bookingState === 'submitting' || bookingState === 'success' || bookingOutcomeUnknown) return
    setExpandedDate(date)
    if (selectedSchedule && selectedSchedule.doctorId === expandedDoctorId && selectedSchedule.date !== date) {
      setSelectedSchedule(null)
      setSelectedDoctorId(null)
      resetBooking()
    }
  }

  async function submitBooking() {
    if (!selectedSchedule || bookingInFlightRef.current || bookingState === 'success') return

    const booking = {
      specialtyId: Number(selectedSchedule.specialtyId),
      date: selectedSchedule.date,
      shiftId: Number(selectedSchedule.shiftId),
      ...(selectedDoctorId ? { doctorId: Number(selectedDoctorId) } : {}),
      ...(patientSymptoms.trim() ? { symptoms: patientSymptoms.trim() } : {}),
    }
    const requestBody = JSON.stringify(booking)
    if (bookingRequestRef.current?.requestBody !== requestBody) {
      bookingRequestRef.current = { requestBody, key: window.crypto.randomUUID() }
    }

    bookingInFlightRef.current = true
    setBookingState('submitting')
    setBookingError('')
    setBookingErrorStatus(null)
    try {
      const result = await bookOnsiteAppointment(getToken(), booking, bookingRequestRef.current.key)
      setBookingResult(result)
      setBookingState('success')
    } catch (error) {
      if (error.status === 401) onSessionExpired()
      setBookingError(error.message)
      setBookingErrorStatus(error.status || null)
      setBookingState('error')
    } finally {
      bookingInFlightRef.current = false
    }
  }

  async function reloadOptions() {
    setOptionsState('loading')
    setOptionsError('')
    try {
      setOptions(await getOnsiteAppointmentOptions(getToken()))
      setOptionsState('ready')
    } catch (error) {
      if (error.status === 401) onSessionExpired()
      setOptionsError(error.message)
      setOptionsState('error')
    }
  }

  const optionsEmpty = optionsState === 'ready'
    && (options.specialties.length === 0 || options.shifts.length === 0)
  const bookingOutcomeUnknown = bookingState === 'error' && (bookingErrorStatus == null || bookingErrorStatus >= 500)
  const filtersDisabled = optionsState !== 'ready' || optionsEmpty || bookingState === 'submitting' || bookingOutcomeUnknown
  const hasFilters = hasSearchCriteria(filters)
  const doctors = useMemo(() => {
    const grouped = new Map()
    results.forEach((schedule) => {
      const doctor = grouped.get(schedule.doctorId) || {
        doctorId: schedule.doctorId,
        doctorName: schedule.doctorName,
        specialties: new Set(),
        schedules: [],
      }
      doctor.specialties.add(schedule.specialtyName)
      doctor.schedules.push(schedule)
      grouped.set(schedule.doctorId, doctor)
    })
    return [...grouped.values()]
      .map((doctor) => ({
        ...doctor,
        specialties: [...doctor.specialties],
        schedules: [...doctor.schedules].sort((left, right) => left.date.localeCompare(right.date) || left.startTime.localeCompare(right.startTime)),
      }))
      .sort((left, right) => left.doctorName.localeCompare(right.doctorName, 'vi'))
  }, [results])
  const selectedDoctor = doctors.find((doctor) => doctor.doctorId === selectedDoctorId)
  const matchingSchedules = selectedSchedule
    ? results.filter((schedule) => (
      String(schedule.specialtyId) === String(selectedSchedule.specialtyId)
      && schedule.date === selectedSchedule.date
      && String(schedule.shiftId) === String(selectedSchedule.shiftId)
    ))
    : []
  const eligibleSchedules = selectedDoctorId
    ? matchingSchedules.filter((schedule) => schedule.doctorId === selectedDoctorId)
    : matchingSchedules
  const estimatedFees = eligibleSchedules.map((schedule) => schedule.minimumFee)
  const minimumEstimatedFee = estimatedFees.length ? estimatedFees.reduce((lowest, fee) => BigInt(fee) < BigInt(lowest) ? fee : lowest) : null
  const maximumEstimatedFee = eligibleSchedules.length
    ? eligibleSchedules.map((schedule) => schedule.maximumFee).reduce((highest, fee) => BigInt(fee) > BigInt(highest) ? fee : highest)
    : null

  return (
    <div className='w-full xl:flex xl:h-full xl:min-h-0 xl:flex-col'>
      <div className='grid grid-cols-1 items-start gap-5 xl:min-h-0 xl:flex-1 xl:grid-cols-[minmax(16rem,18rem)_minmax(0,1fr)_minmax(20rem,22rem)] xl:grid-rows-[minmax(0,1fr)] xl:items-stretch'>
        <aside className='min-w-0 xl:min-h-0 xl:overflow-y-auto xl:overscroll-contain xl:pr-1' aria-label='Thông tin và bộ lọc đặt khám'>
          <section className='rounded-2xl border border-clinical-border bg-white p-5 shadow-sm'>
            <div className='flex items-center gap-3 border-b border-clinical-border pb-4'>
              <FontAwesomeIcon icon={faCalendarDay} className='text-lg text-suncare-800' aria-hidden='true' />
              <h2 className='text-lg font-bold text-suncare-900'>Thông tin đặt khám</h2>
            </div>

            <div className='mt-4 space-y-5'>
              <div>
                <p className='text-sm font-semibold'>1. Phương thức khám</p>
                <div className='mt-2 grid grid-cols-2 gap-2'>
                  <Button variant={visitMode === 'ONSITE' ? 'primary' : 'outline'} aria-pressed={visitMode === 'ONSITE'} className='w-full px-2' onClick={() => setVisitMode('ONSITE')}>
                    Trực tiếp
                  </Button>
                  <Button disabled aria-describedby='online-mode-status' className='w-full px-2'>Trực tuyến</Button>
                </div>
                <p id='online-mode-status' className='mt-2 text-xs leading-5 text-clinical-muted'>Trực tuyến chưa khả dụng.</p>
              </div>

              {visitMode === 'ONSITE' && optionsState === 'ready' && !optionsEmpty && <div className='space-y-4 border-t border-clinical-border pt-4'>
                <label htmlFor='onsite-specialty' className='block text-sm font-semibold'><span className='text-suncare-800'>2.</span> Chuyên khoa
                  <select id='onsite-specialty' name='specialtyId' value={filters.specialtyId} onChange={changeFilter} disabled={filtersDisabled} className={inputClass}>
                    <option value=''>Tất cả chuyên khoa</option>
                    {options.specialties.map((specialty) => <option key={specialty.id} value={specialty.id}>{specialty.name}</option>)}
                  </select>
                </label>
              <OnsiteDateRangePicker
                dateFrom={filters.dateFrom}
                dateTo={filters.dateTo}
                minDate={businessToday()}
                disabled={filtersDisabled}
                onApply={(dateFrom, dateTo) => updateFilters({ ...filters, dateFrom, dateTo })}
              />
                <label htmlFor='onsite-shift' className='block text-sm font-semibold'><span className='text-suncare-800'>4.</span> Ca khám
                  <select id='onsite-shift' name='shiftId' value={filters.shiftId} onChange={changeFilter} disabled={filtersDisabled} className={inputClass}>
                    <option value=''>Tất cả ca</option>
                    {options.shifts.map((shift) => <option key={shift.id} value={shift.id}>{shift.name} · {shift.startTime}–{shift.endTime}</option>)}
                  </select>
                </label>
                {validationError && <p role='alert' className='text-sm text-clinical-danger'>{validationError}</p>}
                <div className='border-t border-clinical-border pt-3'>
                  <p className='text-xs leading-5 text-clinical-muted'>Lịch tự cập nhật khi bạn thay đổi bộ lọc. Trường để trống không giới hạn kết quả.</p>
                  {hasFilters && <Button className='mt-3 w-full' disabled={bookingState === 'submitting'} onClick={clearFilters}>Xóa bộ lọc</Button>}
                </div>
              </div>}

              {optionsState === 'loading' && <p role='status' className='border-t border-clinical-border pt-4 text-sm text-clinical-muted'>Đang tải chuyên khoa và ca khám...</p>}
              {optionsState === 'error' && <div className='border-t border-clinical-border pt-4'>
                <p role='alert' className='text-sm text-clinical-danger'>{optionsError}</p>
                <Button className='mt-3 w-full' onClick={() => void reloadOptions()}><FontAwesomeIcon icon={faRotateRight} aria-hidden='true' /> Tải lại</Button>
              </div>}
              {visitMode === 'ONSITE' && optionsEmpty && <p role='status' className='border-t border-clinical-border pt-4 text-sm text-clinical-muted'>Hiện chưa có chuyên khoa hoặc ca khám trực tiếp đang hoạt động.</p>}
            </div>
          </section>
        </aside>

        <section className='min-w-0 space-y-4 xl:min-h-0 xl:overflow-y-auto xl:overscroll-contain xl:pr-1' aria-label='Danh sách lịch khám'>
          <div className='flex flex-wrap items-baseline justify-between gap-2'>
            <h2 className='text-lg font-bold'>Lịch khám còn chỗ</h2>
            {searchState === 'success' && <p className='text-sm text-clinical-muted'>{doctors.length} bác sĩ · {results.length} ca</p>}
          </div>

          {!visitMode && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm text-clinical-muted'>Chọn phương thức khám để tiếp tục.</p>}
          {visitMode === 'ONSITE' && optionsState === 'loading' && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm'>Đang tải tiêu chí tìm lịch...</p>}
          {visitMode === 'ONSITE' && optionsState === 'error' && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm text-clinical-muted'>Chưa thể tìm lịch vì bộ lọc chưa tải được.</p>}
          {visitMode === 'ONSITE' && optionsEmpty && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm text-clinical-muted'>Chưa có lịch phù hợp.</p>}
          {visitMode === 'ONSITE' && optionsState === 'ready' && !optionsEmpty && <label htmlFor='onsite-doctor-search' className='block text-sm font-semibold'>Tìm theo bác sĩ
            <span className='relative mt-1.5 block'>
              <FontAwesomeIcon icon={faMagnifyingGlass} className='pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-clinical-muted' aria-hidden='true' />
              <input id='onsite-doctor-search' name='doctorName' type='search' value={filters.doctorName} onChange={changeFilter} placeholder='Nhập tên bác sĩ' disabled={filtersDisabled} className={`${inputClass} pl-10`} />
            </span>
          </label>}
          {visitMode === 'ONSITE' && optionsState === 'ready' && !optionsEmpty && searchState === 'idle' && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm text-clinical-muted'>Chọn ít nhất một tiêu chí hoặc tìm theo tên bác sĩ. Các trường chưa chọn sẽ để mở.</p>}
          {visitMode === 'ONSITE' && searchState === 'error' && <p role='alert' className='rounded-xl border border-clinical-danger/40 bg-white p-4 text-sm text-clinical-danger'>{searchError}</p>}
          {visitMode === 'ONSITE' && searchState === 'loading' && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm'>Đang cập nhật lịch theo bộ lọc...</p>}
          {visitMode === 'ONSITE' && searchState === 'success' && results.length === 0 && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm'>Không tìm thấy lịch còn chỗ phù hợp với các tiêu chí đã chọn.</p>}
          {visitMode === 'ONSITE' && searchState === 'success' && doctors.length > 0 && <div className='space-y-4'>
            {doctors.map((doctor) => {
              const isExpanded = expandedDoctorId === doctor.doctorId
              const isSelected = selectedDoctorId === doctor.doctorId
              const doctorDates = [...new Set(doctor.schedules.map((schedule) => schedule.date))]
              const daySchedules = doctor.schedules.filter((schedule) => schedule.date === expandedDate)
              const scheduleListId = `doctor-schedules-${doctor.doctorId}`
              return <article key={doctor.doctorId} className={`min-w-0 overflow-hidden rounded-2xl border bg-white shadow-sm ${isSelected ? 'border-suncare-500' : 'border-clinical-border'}`}>
                <div className='flex min-w-0 flex-wrap items-center gap-4 p-4 sm:flex-nowrap sm:p-5'>
                  <span className='grid h-12 w-12 shrink-0 place-items-center rounded-full border-2 border-suncare-200 text-clinical-muted'>
                    <FontAwesomeIcon icon={faUser} className='text-xl' aria-hidden='true' />
                  </span>
                  <span className='min-w-0 flex-1'>
                    <span className='block truncate font-bold'>{doctor.doctorName}</span>
                    <span className='mt-1 block text-sm text-suncare-800'>Chuyên khoa: {doctor.specialties.join(' · ')}</span>
                    <span className='mt-1 block text-xs text-clinical-muted'>{doctor.schedules.length} ca còn chỗ</span>
                  </span>
                  <Button variant={isExpanded ? 'primary' : 'outline'} aria-expanded={isExpanded} aria-controls={scheduleListId} disabled={bookingState === 'submitting' || bookingOutcomeUnknown} className='w-full sm:w-auto' onClick={() => toggleDoctorSchedules(doctor)}>
                    {isExpanded ? 'Ẩn lịch' : 'Xem lịch'}
                  </Button>
                </div>
                {isExpanded && <div id={scheduleListId} className='space-y-4 border-t border-clinical-border bg-clinical-canvas/40 p-4 sm:p-5'>
                  <div>
                    <h4 className='text-sm font-bold'>Chọn ngày khám</h4>
                    <div className='mt-3 flex gap-2 overflow-x-auto pb-2 xl:flex-wrap xl:overflow-visible' role='group' aria-label={`Ngày khám của ${doctor.doctorName}`}>
                      {doctorDates.map((date) => {
                        const isActiveDate = expandedDate === date
                        const dayCount = doctor.schedules.filter((schedule) => schedule.date === date).length
                        return <button key={date} type='button' aria-pressed={isActiveDate} disabled={bookingState === 'submitting' || bookingState === 'success' || bookingOutcomeUnknown} onClick={() => selectDoctorDate(date)} className={`min-h-16 min-w-24 shrink-0 rounded-xl border px-3 py-2 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-60 ${isActiveDate ? 'border-suncare-700 bg-suncare-700 text-white' : 'border-clinical-border bg-white text-clinical-ink hover:border-suncare-500 hover:bg-suncare-50'}`}>
                          <span className='block font-semibold'>{formatShortWorkDate(date)}</span>
                          <span className={`mt-1 block text-xs ${isActiveDate ? 'text-white/90' : 'text-clinical-muted'}`}>{dayCount} buổi</span>
                        </button>
                      })}
                    </div>
                  </div>
                  <div>
                    <div className='mb-3 flex flex-wrap items-baseline justify-between gap-2'>
                      <h4 className='text-sm font-bold'>{formatWorkDate(expandedDate)}</h4>
                      <p className='text-xs text-clinical-muted'>Chọn ca sáng hoặc chiều</p>
                    </div>
                    <div className='grid gap-3 sm:grid-cols-2'>
                    {daySchedules.map((schedule) => {
                      const isScheduleSelected = selectedSchedule
                        && String(selectedSchedule.specialtyId) === String(schedule.specialtyId)
                        && selectedSchedule.date === schedule.date
                        && String(selectedSchedule.shiftId) === String(schedule.shiftId)
                        && selectedSchedule.doctorId === doctor.doctorId
                      return <button key={`${schedule.specialtyId}-${schedule.date}-${schedule.shiftId}`} type='button' aria-pressed={Boolean(isScheduleSelected)} disabled={bookingState === 'submitting' || bookingState === 'success' || bookingOutcomeUnknown} onClick={() => chooseSchedule(doctor, schedule)} className={`min-h-32 rounded-xl border p-4 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-60 ${isScheduleSelected ? 'border-suncare-700 bg-suncare-50 ring-1 ring-suncare-700' : 'border-clinical-border bg-white hover:border-suncare-500 hover:bg-suncare-50'}`}>
                        <span className='flex items-center justify-between gap-2'>
                          <span className='font-bold'>{schedule.shiftName}</span>
                          <span className={`text-xs font-semibold ${isScheduleSelected ? 'text-suncare-800' : 'text-clinical-muted'}`}>{isScheduleSelected ? 'Đã chọn · bấm để bỏ' : 'Chọn buổi'}</span>
                        </span>
                        <span className='mt-1 block text-sm text-clinical-muted'>{schedule.startTime}–{schedule.endTime}</span>
                        <span className='mt-3 flex flex-wrap items-center justify-between gap-2'>
                          <span className='text-xs font-semibold text-clinical-success'><FontAwesomeIcon icon={faUsers} className='mr-1.5' aria-hidden='true' />Còn {schedule.availablePlaces} lượt</span>
                          <span className='font-bold text-suncare-800'>{schedule.minimumFee === schedule.maximumFee ? formatVnd(schedule.minimumFee) : `${formatVnd(schedule.minimumFee)} – ${formatVnd(schedule.maximumFee)}`}</span>
                        </span>
                      </button>
                    })}
                    </div>
                  </div>
                </div>}
              </article>
            })}
          </div>}
        </section>

        <aside className='min-w-0 xl:min-h-0 xl:overflow-y-auto xl:overscroll-contain xl:pr-1' aria-label='Tóm tắt lịch khám'>
          <section className='rounded-2xl border border-clinical-border bg-white shadow-sm'>
            <h2 className='border-b border-clinical-border px-5 py-4 text-lg font-bold text-suncare-900'>Tóm tắt lịch khám</h2>
            <div className='space-y-4 p-5 text-sm'>
              {!selectedSchedule && <div role='status' className='rounded-xl border border-dashed border-clinical-border-strong bg-clinical-canvas p-4'>
                <p className='font-semibold'>Chưa chọn buổi khám</p>
                <p className='mt-1 text-xs leading-5 text-clinical-muted'>Mở lịch của bác sĩ, chọn ngày rồi chọn ca sáng hoặc chiều để xem nội dung đặt khám tại đây.</p>
              </div>}

              {selectedSchedule && <>
                <div className='space-y-3 rounded-2xl border border-clinical-border bg-clinical-canvas/50 p-4'>
                  <div>
                    <strong className='block text-xs font-semibold text-clinical-muted'>Phòng khám</strong>
                    <span className='mt-1 block font-semibold'>{bookingResult ? `${bookingResult.roomCode} · ${bookingResult.roomName}` : selectedDoctorId ? `${selectedSchedule.roomCode} · ${selectedSchedule.roomName}` : 'Sẽ xác định theo bác sĩ được phân'}</span>
                  </div>
                  <div>
                    <strong className='block text-xs font-semibold text-clinical-muted'>Khoa khám</strong>
                    <span className='mt-1 block'>{bookingResult?.departmentName || (selectedDoctorId ? selectedSchedule.departmentName : 'Sẽ xác định theo bác sĩ được phân')}</span>
                  </div>
                  <div>
                    <strong className='block text-xs font-semibold text-clinical-muted'>Chuyên khoa</strong>
                    <span className='mt-1 block font-semibold'>{bookingResult?.specialtyName || selectedSchedule.specialtyName}</span>
                  </div>
                  <div>
                    <strong className='block text-xs font-semibold text-clinical-muted'>Bác sĩ</strong>
                    <span className='mt-1 block'>{bookingResult?.doctorName || selectedDoctor?.doctorName || 'Hệ thống sẽ tự phân bác sĩ còn chỗ'}</span>
                  </div>
                  <div>
                    <strong className='block text-xs font-semibold text-clinical-muted'>Ngày và ca khám</strong>
                    <span className='mt-1 block'>{formatWorkDate(selectedSchedule.date)}</span>
                    <span className='block'>{selectedSchedule.shiftName} · {selectedSchedule.startTime}–{selectedSchedule.endTime}</span>
                  </div>
                  <div className='border-t border-clinical-border pt-3'>
                    <strong className='block text-xs font-semibold text-clinical-muted'>Phí khám</strong>
                    <span className='mt-1 block font-bold text-suncare-800'>{bookingResult ? formatVnd(bookingResult.examinationAmount) : minimumEstimatedFee == null ? 'Được xác nhận khi đặt' : minimumEstimatedFee === maximumEstimatedFee ? formatVnd(minimumEstimatedFee) : `${formatVnd(minimumEstimatedFee)} – ${formatVnd(maximumEstimatedFee)}`}</span>
                  </div>
                </div>

                {!bookingResult && <fieldset className='rounded-2xl border border-clinical-border bg-white p-4' aria-label='Cách phân bác sĩ'>
                  <legend className='px-1 text-sm font-bold text-suncare-900'>Ai sẽ chọn bác sĩ?</legend>
                  <p className='mb-3 text-xs leading-5 text-clinical-muted'>Chọn bác sĩ bạn muốn khám hoặc để hệ thống phân người còn chỗ trong cùng ca.</p>
                  <div className='space-y-2'>
                    <button type='button' aria-pressed={Boolean(selectedDoctorId)} disabled={bookingState === 'submitting' || bookingState === 'success' || bookingOutcomeUnknown} onClick={() => {
                      setSelectedDoctorId(selectedSchedule.doctorId)
                      resetBooking()
                    }} className={`flex min-h-20 w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-60 ${selectedDoctorId ? 'border-suncare-700 bg-suncare-50 ring-1 ring-suncare-700' : 'border-clinical-border bg-white hover:border-suncare-500 hover:bg-suncare-50'}`}>
                      <span className='grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white text-suncare-800 shadow-sm'><FontAwesomeIcon icon={faUser} aria-hidden='true' /></span>
                      <span className='min-w-0 flex-1'>
                        <span className='block font-bold'>Ưu tiên bác sĩ này</span>
                        <span className='mt-0.5 block break-words text-sm text-clinical-ink'>{selectedSchedule.doctorName}</span>
                      </span>
                      <span className={`grid h-5 w-5 shrink-0 place-items-center rounded-full border ${selectedDoctorId ? 'border-suncare-700 bg-suncare-700 text-white' : 'border-clinical-border-strong bg-white'}`} aria-hidden='true'>{selectedDoctorId && <span className='h-2 w-2 rounded-full bg-white' />}</span>
                    </button>
                    <button type='button' aria-pressed={!selectedDoctorId} disabled={bookingState === 'submitting' || bookingState === 'success' || bookingOutcomeUnknown || Boolean(filters.doctorName.trim())} onClick={() => {
                      setSelectedDoctorId(null)
                      resetBooking()
                    }} className={`flex min-h-20 w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-60 ${!selectedDoctorId ? 'border-suncare-700 bg-suncare-50 ring-1 ring-suncare-700' : 'border-clinical-border bg-white hover:border-suncare-500 hover:bg-suncare-50'}`}>
                      <span className='grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white text-suncare-800 shadow-sm'><FontAwesomeIcon icon={faUsers} aria-hidden='true' /></span>
                      <span className='min-w-0 flex-1'>
                        <span className='block font-bold'>Hệ thống tự phân</span>
                        <span className='mt-0.5 block text-sm leading-5 text-clinical-muted'>Cân bằng theo sức chứa trong ca này</span>
                      </span>
                      <span className={`grid h-5 w-5 shrink-0 place-items-center rounded-full border ${!selectedDoctorId ? 'border-suncare-700 bg-suncare-700 text-white' : 'border-clinical-border-strong bg-white'}`} aria-hidden='true'>{!selectedDoctorId && <span className='h-2 w-2 rounded-full bg-white' />}</span>
                    </button>
                  </div>
                  {filters.doctorName.trim() && <p className='mt-2 text-xs leading-5 text-clinical-muted'>Đang lọc theo tên bác sĩ nên hệ thống chỉ có thể đặt với bác sĩ đang hiển thị. Xóa tên tìm kiếm để bật tự phân.</p>}
                </fieldset>}

                <div>
                  <label htmlFor='onsite-patient-symptoms' className='block text-sm font-semibold'>Triệu chứng / lý do khám <span className='font-normal text-clinical-muted'>(không bắt buộc)</span>
                    <textarea id='onsite-patient-symptoms' value={patientSymptoms} maxLength={2000} rows={4} disabled={bookingState === 'submitting' || bookingState === 'success' || bookingOutcomeUnknown} onChange={(event) => setPatientSymptoms(event.target.value)} placeholder='Mô tả triệu chứng hoặc lý do bạn muốn đi khám...' className='mt-1.5 w-full resize-y rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink placeholder:text-clinical-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:bg-suncare-50 disabled:text-clinical-muted' />
                  </label>
                  <p className='mt-1 flex justify-between gap-2 text-xs text-clinical-muted'><span>Thông tin này được lưu để nhân viên y tế tham khảo trước lượt khám.</span><span className='shrink-0'>{patientSymptoms.length}/2000</span></p>
                </div>

                {bookingState === 'idle' && <Button className='w-full' onClick={() => setBookingState('review')}>Tiếp tục đặt lịch</Button>}
                {bookingState === 'review' && <div className='space-y-3 rounded-xl border border-suncare-200 bg-suncare-50 p-4'>
                  <p className='font-bold text-suncare-900'>Xác nhận đặt khám</p>
                  <p className='text-xs leading-5 text-clinical-ink'>Bạn đang đặt {selectedSchedule.specialtyName}, {formatWorkDate(selectedSchedule.date)}, {selectedSchedule.shiftName}. {selectedDoctor ? `Bác sĩ: ${selectedDoctor.doctorName}.` : 'Hệ thống sẽ tự phân bác sĩ còn sức chứa.'}</p>
                  {patientSymptoms.trim() && <p className='text-xs leading-5 text-clinical-ink'><strong>Triệu chứng:</strong> {patientSymptoms.trim()}</p>}
                  <Button className='w-full' disabled={bookingState === 'submitting'} onClick={() => void submitBooking()}>Xác nhận đặt lịch</Button>
                  <Button className='w-full' disabled={bookingState === 'submitting'} onClick={() => {
                    setBookingState('idle')
                    bookingRequestRef.current = null
                  }}>Quay lại</Button>
                </div>}
                {bookingState === 'submitting' && <p role='status' className='rounded-xl bg-suncare-50 p-3 text-sm text-clinical-ink'>Đang xác nhận chỗ và tạo lịch khám...</p>}
                {bookingState === 'error' && <div className='space-y-3 rounded-xl border border-clinical-danger/40 bg-white p-4'>
                  <p role='alert' className='text-sm text-clinical-danger'>{bookingError}</p>
                  {bookingErrorStatus === 409
                    ? <p className='text-xs leading-5 text-clinical-muted'>Ca vừa hết chỗ hoặc không còn phù hợp. Hãy tải lại kết quả rồi chọn ca khác.</p>
                    : <p className='text-xs leading-5 text-clinical-muted'>Kết quả từ máy chủ chưa rõ. Hãy thử lại cùng mã yêu cầu trước khi đổi ca.</p>}
                  <Button className='w-full' onClick={() => void submitBooking()}>Thử lại</Button>
                  {bookingErrorStatus === 409 && <Button className='w-full' onClick={() => updateFilters(filters)}>Tải lại lịch</Button>}
                </div>}
                {bookingState === 'success' && bookingResult && <div role='status' className='space-y-2 rounded-xl border border-clinical-success/40 bg-emerald-50 p-4 text-sm'>
                  <p className='font-bold text-clinical-success'>Đặt lịch thành công</p>
                  <p>Mã lịch khám: <strong>{bookingResult.appointmentCode}</strong></p>
                  <p>Mã lượt khám: <strong>{bookingResult.encounterCode}</strong></p>
                  <p className='text-xs leading-5 text-clinical-muted'>Lịch khám và lượt khám đã được tạo. Hãy lưu lại các mã này.</p>
                  <Button className='w-full' onClick={() => updateFilters(filters)}>Tìm lịch khác</Button>
                </div>}
              </>}
            </div>
          </section>
        </aside>
      </div>
    </div>
  )
}
