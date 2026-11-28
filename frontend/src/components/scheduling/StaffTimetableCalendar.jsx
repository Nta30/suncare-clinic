import Button from '../ui/Button'
import { time, dateLabel, addDays, currentWeek } from '../../utils/timetable'

export default function StaffTimetableCalendar({ week, onWeekChange, onRefresh, state, selectedId, onSelect, description, minWeek, maxWeek }) {
  const days = Array.from({ length: 7 }, (_, index) => addDays(week, index))
  const shifts = [...new Map([...state.data].sort((a, b) => time(a.startsAt).localeCompare(time(b.startsAt)))
    .map((item) => [item.shiftCode || item.shiftName, item.shiftName || 'Ca làm việc'])).entries()]
  return <>
    <header className='flex flex-wrap items-center justify-between gap-3'>
      <div><h1 className='text-2xl font-bold'>Thời khóa biểu của tôi</h1><p className='mt-1 text-sm text-clinical-muted'>{description}</p></div>
      <Button onClick={onRefresh} disabled={state.loading}>Tải lại</Button>
    </header>
    <div className='flex flex-wrap items-center gap-2'>
      <Button disabled={!!minWeek && addDays(week, -7) < minWeek} onClick={() => onWeekChange(addDays(week, -7))}>Tuần trước</Button>
      <Button onClick={() => onWeekChange(currentWeek())}>Tuần này</Button>
      <Button disabled={!!maxWeek && addDays(week, 7) > maxWeek} onClick={() => onWeekChange(addDays(week, 7))}>Tuần sau</Button>
      <span className='text-sm font-semibold'>{dateLabel(week)} – {dateLabel(days[6])}, {week.slice(0, 4)}</span>
    </div>
    {state.loading ? <p role='status' className='py-4 text-clinical-muted'>Đang tải...</p> : state.error ?
      <div role='alert' className='space-y-3 py-4'><p className='text-clinical-danger'>{state.error}</p><Button onClick={onRefresh}>Thử lại</Button></div> :
      state.data.length === 0 ? <p className='rounded-xl border border-dashed border-clinical-border-strong bg-white p-6'>Chưa có ca làm việc được công bố trong tuần này. Hãy chọn tuần khác hoặc tải lại.</p> :
        <div className='max-w-full overflow-x-auto rounded-xl border border-clinical-border bg-white' tabIndex={0} role='region' aria-label='Thời khóa biểu theo ngày và ca'>
          <table className='w-full min-w-[960px] table-fixed text-sm'>
            <thead><tr className='bg-clinical-canvas'><th scope='col' className='w-28 border-b border-clinical-border p-3 text-left'>Ca làm việc</th>{days.map((day) => <th scope='col' key={day} className='border-b border-l border-clinical-border p-3 text-left'>{dateLabel(day)}</th>)}</tr></thead>
            <tbody>{shifts.map(([code, name]) => <tr key={code}><th scope='row' className='p-3 text-left align-top'>{name}</th>{days.map((day) => <td key={day} className='border-l border-t border-clinical-border p-2 align-top'>
              <div className='min-h-28 space-y-2'>{state.data.filter((item) => item.workDate === day && (item.shiftCode || item.shiftName) === code).map((item) => <button type='button' key={item.assignmentId} onClick={() => onSelect(item)} aria-pressed={selectedId === item.assignmentId}
                className={`w-full rounded-lg border p-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 ${selectedId === item.assignmentId ? 'border-suncare-700 bg-suncare-100' : 'border-clinical-border-strong bg-clinical-canvas hover:bg-suncare-50'}`}>
                <span className={`block font-bold ${item.visitMode === 'ONLINE' ? 'text-clinical-info' : 'text-clinical-leaf'}`}>{item.visitMode === 'LAB' ? 'Cận lâm sàng' : item.visitMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</span>
                <span className='mt-1 block'>{time(item.startsAt)} – {time(item.endsAt)}</span>
                <span className='mt-1 block break-words'>{item.roomCode || item.roomName || 'Chưa có phòng'}</span><span className='mt-1 block break-words text-xs'>{item.departmentName}</span>
              </button>)}</div>
            </td>)}</tr>)}</tbody>
          </table>
        </div>}
  </>
}
