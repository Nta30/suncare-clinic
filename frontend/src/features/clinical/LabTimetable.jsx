import { useEffect, useState } from 'react'
import Button from '../../components/ui/Button'
import StaffTimetableCalendar from '../../components/scheduling/StaffTimetableCalendar'
import { getLabAssignments } from '../../services/labApi'
import { addDays, currentWeek, dateKey, dateLabel, time } from '../../utils/timetable'

export default function LabTimetable({ onSessionExpired }) {
  const [week, setWeek] = useState(currentWeek)
  const [revision, setRevision] = useState(0)
  const [state, setState] = useState({ loading: true, data: [] })
  const [selected, setSelected] = useState(null)
  const [today] = useState(() => dateKey(new Date()))
  const firstDate = addDays(today, -1)
  const lastDate = addDays(today, 29)
  const monday = (date) => addDays(date, -((new Date(`${date}T12:00:00+07:00`).getUTCDay() + 6) % 7))

  useEffect(() => {
    const controller = new AbortController()
    getLabAssignments(controller.signal, false).then((data) => {
      if (controller.signal.aborted) return
      setState({ loading: false, data: data.map((item) => ({ ...item,
        assignmentId: item.id, workDate: dateKey(new Date(item.startsAt)), visitMode: 'LAB',
        // The LAB API exposes actual hours, including legacy assignments without a shift.
        shiftCode: `${time(item.startsAt)}-${time(item.endsAt)}`,
        shiftName: `Ca ${time(item.startsAt)} – ${time(item.endsAt)}`,
      })) })
    }).catch((error) => {
      if (controller.signal.aborted) return
      if (error.status === 401) onSessionExpired(error.message)
      else setState({ loading: false, data: [], error: error.message })
    })
    return () => controller.abort()
  }, [revision, onSessionExpired])

  function refresh() {
    setSelected(null)
    setState({ loading: true, data: [] })
    setRevision((value) => value + 1)
  }
  function changeWeek(next) {
    if (next === week) { refresh(); return }
    setWeek(next)
    setSelected(null)
  }
  const calendar = { ...state, data: state.data.filter((item) => item.workDate >= week && item.workDate <= addDays(week, 6)) }
  return <section className='w-full min-w-0 space-y-5 text-clinical-ink' aria-label='Lịch được giao'>
    <StaffTimetableCalendar week={week} onWeekChange={changeWeek} onRefresh={refresh}
      state={calendar} selectedId={selected?.assignmentId} onSelect={setSelected}
      minWeek={monday(firstDate)} maxWeek={monday(lastDate)}
      description='Chọn ca để xem địa điểm làm việc và phòng thực hiện cận lâm sàng.' />
    <p className='text-sm text-clinical-muted'>Lịch CLS được cung cấp từ {dateLabel(firstDate)} đến {dateLabel(lastDate)}. Các ca hiển thị theo giờ Việt Nam.</p>
    {selected && <section className='space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-6' aria-labelledby='lab-shift-detail-title'>
      <div className='flex flex-wrap justify-between gap-3'><h2 id='lab-shift-detail-title' className='text-lg font-bold'>{selected.shiftName} · {dateLabel(selected.workDate)} · Cận lâm sàng</h2><Button onClick={() => setSelected(null)}>Đóng chi tiết</Button></div>
      <dl className='grid gap-3 text-sm sm:grid-cols-2'>
        <div><dt className='text-clinical-muted'>Thời gian</dt><dd>{time(selected.startsAt)} – {time(selected.endsAt)}</dd></div>
        <div><dt className='text-clinical-muted'>Phòng</dt><dd>{selected.roomName}</dd></div>
        <div><dt className='text-clinical-muted'>Khoa</dt><dd>{selected.departmentName}</dd></div>
        <div><dt className='text-clinical-muted'>Nhân viên</dt><dd>{selected.staffName}</dd></div>
      </dl>
    </section>}
  </section>
}
