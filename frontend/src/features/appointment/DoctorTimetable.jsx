import { useEffect, useState } from 'react'
import Button from '../../components/ui/Button'
import StaffTimetableCalendar from '../../components/scheduling/StaffTimetableCalendar'
import { zone, time, dateLabel, addDays, currentWeek } from '../../utils/timetable'
import { getToken } from '../../services/accountApi'
import { getDoctorTimetable, getDoctorShiftSlots, getDoctorSlotPatient } from '../../services/appointmentScheduleApi'


function LoadMessage({ state, onRetry }) {
  if (state.loading) return <p role='status' className='py-4 text-clinical-muted'>Đang tải...</p>
  if (state.error) return <div role='alert' className='space-y-3 py-4'><p className='text-clinical-danger'>{state.error}</p><Button onClick={onRetry}>Thử lại</Button></div>
  return null
}

export default function DoctorTimetable({ account, onSessionExpired }) {
  const [week, setWeek] = useState(currentWeek)
  const [reload, setReload] = useState(0)
  const [calendar, setCalendar] = useState({ loading: true, data: [] })
  const [selected, setSelected] = useState(null)
  const [slots, setSlots] = useState({ loading: false, data: [] })
  const [slotReload, setSlotReload] = useState(0)
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [patient, setPatient] = useState({ loading: false })
  const [patientReload, setPatientReload] = useState(0)

  useEffect(() => {
    let active = true
    const token = getToken()
    if (!token) { onSessionExpired(); return undefined }
    getDoctorTimetable(token, week, addDays(week, 6)).then((data) => {
      if (active) setCalendar({ data, loading: false })
    }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired(error.message)
      else setCalendar({ data: [], error: error.message })
    })
    return () => { active = false }
  }, [account.accountId, week, reload, onSessionExpired])

  useEffect(() => {
    if (selected?.visitMode !== 'ONLINE') return undefined
    let active = true
    const token = getToken()
    if (!token) { onSessionExpired(); return undefined }
    getDoctorShiftSlots(token, selected.assignmentId).then((data) => {
      if (active) setSlots({ data })
    }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired(error.message)
      else setSlots({ data: [], error: error.message })
    })
    return () => { active = false }
  }, [selected, slotReload, onSessionExpired])

  useEffect(() => {
    if (!selectedSlot) return undefined
    let active = true
    const token = getToken()
    if (!token) { onSessionExpired(); return undefined }
    getDoctorSlotPatient(token, selectedSlot.slotId).then((data) => {
      if (active) setPatient({ data })
    }).catch((error) => {
      if (!active) return
      if (error.status === 401) onSessionExpired(error.message)
      else setPatient({ error: error.message })
    })
    return () => { active = false }
  }, [selectedSlot, patientReload, onSessionExpired])

  function changeWeek(next) {
    if (next === week) { refresh(); return }
    setWeek(next)
    setSelected(null)
    setSelectedSlot(null)
    setCalendar({ loading: true, data: [] })
  }
  function refresh() {
    setSelected(null)
    setSelectedSlot(null)
    setCalendar({ loading: true, data: [] })
    setReload((value) => value + 1)
  }
  function openShift(shift) {
    setSelected(shift)
    setSelectedSlot(null)
    setPatient({ loading: false })
    setSlots({ loading: shift.visitMode === 'ONLINE', data: [] })
    setSlotReload((value) => value + 1)
  }

  return <section className='w-full min-w-0 space-y-5 text-clinical-ink' aria-label='Lịch được giao'>
    <StaffTimetableCalendar week={week} onWeekChange={changeWeek} onRefresh={refresh}
      state={calendar} selectedId={selected?.assignmentId} onSelect={openShift}
      description='Chọn ca để xem địa điểm làm việc hoặc lịch hẹn trực tuyến.' />
    {selected && <section className='space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-6' aria-labelledby='shift-detail-title'>
      <div className='flex flex-wrap justify-between gap-3'><h2 id='shift-detail-title' className='text-lg font-bold'>{selected.shiftName} · {dateLabel(selected.workDate)} · {selected.visitMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</h2><Button onClick={() => { setSelected(null); setSelectedSlot(null) }}>Đóng chi tiết</Button></div>
      <dl className='grid gap-3 text-sm sm:grid-cols-2'>
        <div><dt className='text-clinical-muted'>Thời gian</dt><dd>{time(selected.startsAt)} – {time(selected.endsAt)}</dd></div>
        <div><dt className='text-clinical-muted'>Phòng</dt><dd>{[selected.roomCode, selected.roomName].filter(Boolean).join(' · ') || 'Chưa có phòng'}</dd></div>
        <div><dt className='text-clinical-muted'>Khoa</dt><dd>{selected.departmentName}</dd></div>
        <div><dt className='text-clinical-muted'>Chuyên khoa</dt><dd>{selected.specialtyName || 'Chưa cập nhật'}</dd></div>
      </dl>
      {selected.visitMode === 'ONLINE' && <>
        <h3 className='border-t border-clinical-border pt-4 font-semibold'>Khung giờ khám trực tuyến</h3>
        <LoadMessage state={slots} onRetry={() => { setSlots({ loading: true, data: [] }); setSlotReload((value) => value + 1) }} />
        {!slots.loading && !slots.error && (slots.data.length === 0 ? <p>Chưa có khung giờ trong ca này.</p> : <div className='grid gap-2 sm:grid-cols-2 xl:grid-cols-3'>{slots.data.map((slot) => <button type='button' key={slot.slotId} disabled={!slot.appointmentId} onClick={() => { setSelectedSlot(slot); setPatient({ loading: true }); setPatientReload((value) => value + 1) }} aria-pressed={selectedSlot?.slotId === slot.slotId} className='rounded-lg border border-clinical-border-strong p-3 text-left enabled:hover:bg-suncare-50 disabled:bg-clinical-canvas focus-visible:outline-2 focus-visible:outline-suncare-700'>
          <span className='block font-semibold'>{time(slot.startsAt)} – {time(slot.endsAt)}</span><span className='mt-1 block'>{slot.patientName || (slot.open ? 'Chưa có bệnh nhân đặt' : 'Khung giờ đã đóng')}</span>
          {slot.appointmentId && <span className='mt-1 block text-xs text-suncare-700'>{{ BOOKED: 'Đã đặt', COMPLETED: 'Đã hoàn tất', NO_SHOW: 'Vắng mặt' }[slot.appointmentStatus] || slot.appointmentStatus} · Xem bệnh nhân</span>}
        </button>)}</div>)}
        {selectedSlot && <div className='border-t border-clinical-border pt-4'>
          <h3 className='font-semibold'>Bệnh nhân · {time(selectedSlot.startsAt)} – {time(selectedSlot.endsAt)}</h3>
          <LoadMessage state={patient} onRetry={() => { setPatient({ loading: true }); setPatientReload((value) => value + 1) }} />
          {patient.data && <dl className='mt-3 grid gap-3 text-sm sm:grid-cols-2'>{[
            ['Họ tên', patient.data.fullName], ['Mã bệnh nhân', patient.data.patientCode], ['Ngày sinh', patient.data.birthDate ? new Intl.DateTimeFormat('vi-VN', { timeZone: zone }).format(new Date(`${patient.data.birthDate}T12:00:00+07:00`)) : null],
            ['Giới tính', { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác', UNKNOWN: 'Chưa xác định' }[patient.data.sex] || patient.data.sex], ['Điện thoại', patient.data.phone],
          ].map(([label, value]) => <div key={label}><dt className='text-clinical-muted'>{label}</dt><dd className='break-words'>{value || 'Chưa cập nhật'}</dd></div>)}</dl>}
        </div>}
      </>}
    </section>}
  </section>
}
