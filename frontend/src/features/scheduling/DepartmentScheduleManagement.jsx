import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowLeft, faArrowRight, faPlus, faRotateRight, faTrashCan, faXmark } from '@fortawesome/free-solid-svg-icons'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import {
  addHeadScheduleAssignment,
  createHeadSchedule,
  getHeadScheduleAssignments,
  getHeadScheduleOptions,
  listHeadSchedules,
  publishHeadSchedule,
  removeHeadScheduleAssignment,
} from '../../services/headScheduleApi'

const field = 'min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-suncare-700'
const HISTORY_PAGE_SIZE = 5
const emptyOptions = { departments: [], staff: [], rooms: [], specialties: [], shifts: [] }
const dayLabels = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy']

const dateAtNoon = (value) => new Date(`${value}T12:00:00Z`)
const toDateInput = (value) => dateAtNoon(value).toISOString().slice(0, 10)
const addDays = (value, amount) => {
  const date = dateAtNoon(value)
  date.setUTCDate(date.getUTCDate() + amount)
  return toDateInput(date.toISOString().slice(0, 10))
}
const saigonDateTime = (value) => {
  if (!value) return null
  const utcValue = value.endsWith('Z') ? value : `${value}Z`
  const instant = new Date(utcValue)
  if (Number.isNaN(instant.getTime())) return null
  return new Date(instant.getTime() + 7 * 60 * 60 * 1000)
}
const isSaigonMidnight = (date) => date && date.getUTCHours() === 0 && date.getUTCMinutes() === 0
  && date.getUTCSeconds() === 0 && date.getUTCMilliseconds() === 0
const getScheduleDateBounds = (department) => {
  const validFrom = saigonDateTime(department.validFrom)
  const validTo = saigonDateTime(department.validTo)
  const validFromDate = validFrom?.toISOString().slice(0, 10)
  const validToDate = validTo?.toISOString().slice(0, 10)
  return {
    earliestStart: validFromDate
      ? isSaigonMidnight(validFrom) ? validFromDate : addDays(validFromDate, 1)
      : '',
    latestEnd: validToDate
      ? isSaigonMidnight(validTo) ? addDays(validToDate, -1) : validToDate
      : '',
  }
}
const mondayOf = (value) => {
  const date = dateAtNoon(value)
  date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7))
  return toDateInput(date.toISOString().slice(0, 10))
}
const todayInSaigon = () => new Date(Date.now() + 7 * 60 * 60 * 1000).toISOString().slice(0, 10)
const displayDate = (value, options = { day: '2-digit', month: '2-digit' }) =>
  dateAtNoon(value).toLocaleDateString('vi-VN', { ...options, timeZone: 'UTC' })
const displayDateTime = (value) => {
  if (!value) return 'Chưa xác định'
  const date = new Date(value.endsWith('Z') ? value : `${value}Z`)
  return date.toLocaleString('vi-VN', { timeZone: 'Asia/Saigon' })
}
const assignmentOverlapsSlot = (assignment, slot) => {
  const slotStartsAt = Date.parse(`${slot.workDate}T${slot.localStart}+07:00`)
  const slotEndsAt = Date.parse(`${slot.workDate}T${slot.localEnd}+07:00`)
  const assignmentStartsAt = Date.parse(assignment.startsAt)
  const assignmentEndsAt = Date.parse(assignment.endsAt)
  return assignmentStartsAt < slotEndsAt && slotStartsAt < assignmentEndsAt
}
const errorMessage = (failure) => {
  if (failure.status === 401) return 'Phiên đăng nhập không còn hợp lệ. Hãy đăng nhập lại rồi mở Lịch khoa.'
  if (failure.code === 'SCHEDULE_PERMISSION_REQUIRED' || failure.code === 'SCHEDULE_AUTHORITY_MISMATCH' || failure.code === 'DEPARTMENT_SCOPE') return failure.message
  if (failure.code === 'ACCESS_DENIED') return failure.message || `API ${failure.endpoint || 'phân lịch'} trả HTTP 403.`
  return failure.message || 'Không tải được dữ liệu. Hãy thử tải lại.'
}

export default function DepartmentScheduleManagement({ account, onSessionExpired }) {
  const [schedules, setSchedules] = useState([])
  const [selectedScheduleId, setSelectedScheduleId] = useState('')
  const [baseOptions, setBaseOptions] = useState(emptyOptions)
  const [scheduleOptions, setScheduleOptions] = useState(emptyOptions)
  const [assignments, setAssignments] = useState([])
  const [loadingWorkspace, setLoadingWorkspace] = useState(true)
  const [loadingSchedule, setLoadingSchedule] = useState(false)
  const [busy, setBusy] = useState(false)
  const [scheduleError, setScheduleError] = useState('')
  const [optionsError, setOptionsError] = useState('')
  const [assignmentError, setAssignmentError] = useState('')
  const [actionError, setActionError] = useState('')
  const [accessFailure, setAccessFailure] = useState(null)
  const [success, setSuccess] = useState('')
  const [historySearch, setHistorySearch] = useState('')
  const [historyStatus, setHistoryStatus] = useState('all')
  const [historyPage, setHistoryPage] = useState(1)
  const [staffSearch, setStaffSearch] = useState('')
  const [specialtyFilter, setSpecialtyFilter] = useState('')
  const [selectedStaffId, setSelectedStaffId] = useState('')
  const [activeSlot, setActiveSlot] = useState(null)
  const [isAddingAssignment, setIsAddingAssignment] = useState(false)
  const [selectedWorkSlots, setSelectedWorkSlots] = useState([])
  const [isConfirmingPublish, setIsConfirmingPublish] = useState(false)
  const [weekStart, setWeekStart] = useState('')
  const [draftForm, setDraftForm] = useState({ departmentId: '', periodStart: '', periodEnd: '' })
  const [assignmentForm, setAssignmentForm] = useState({ roomId: '', specialtyId: '', capacity: '1' })
  const selectedScheduleIdRef = useRef('')
  const calendarDragRef = useRef(null)
  const suppressCalendarClickRef = useRef(false)

  const selectedSchedule = schedules.find((schedule) => String(schedule.id) === String(selectedScheduleId))
  const selectedDepartment = baseOptions.departments.find((item) => String(item.id) === String(draftForm.departmentId))
  const draftDateBounds = selectedDepartment ? getScheduleDateBounds(selectedDepartment) : null
  const noFullDayInTerm = Boolean(draftDateBounds?.earliestStart && draftDateBounds.latestEnd
    && draftDateBounds.earliestStart > draftDateBounds.latestEnd)
  const editable = selectedSchedule?.status === 'DRAFT'
  const selectedStaff = scheduleOptions.staff.find((staff) => String(staff.id) === String(selectedStaffId))
  const accessFailureMessage = accessFailure ? errorMessage(accessFailure) : ''
  const selectedWorkDates = [...new Set(selectedWorkSlots.map((slot) => slot.workDate))].sort()
  const selectedMode = selectedWorkSlots[0]?.mode || ''
  const activeSlotAssignments = activeSlot
    ? assignments.filter((item) => item.workDate === activeSlot.workDate
      && item.mode === activeSlot.mode && String(item.shiftId) === String(activeSlot.shiftId))
    : []
  const assignmentsOverlappingSelection = useMemo(() => assignments.filter((assignment) =>
    selectedWorkSlots.some((slot) => assignmentOverlapsSlot(assignment, slot))), [assignments, selectedWorkSlots])
  const busyStaffIds = useMemo(() => new Set(assignmentsOverlappingSelection.map((item) => String(item.staffId))), [assignmentsOverlappingSelection])
  const busyRoomIds = useMemo(() => new Set(assignmentsOverlappingSelection.map((item) => String(item.roomId))), [assignmentsOverlappingSelection])
  const roomsForSelectedMode = activeSlot
    ? scheduleOptions.rooms.filter((room) => room.roomType === (activeSlot.mode === 'ONLINE' ? 'ONLINE' : 'CONSULTATION'))
    : []
  const eligibleRooms = activeSlot
    ? roomsForSelectedMode.filter((room) => !busyRoomIds.has(String(room.id)))
    : []
  const eligibleSpecialties = selectedStaff
    ? scheduleOptions.specialties.filter((specialty) => selectedStaff.specialties.some((item) => item.id === specialty.id))
    : []

  useEffect(() => {
    const finishCalendarDrag = () => {
      const drag = calendarDragRef.current
      if (drag?.moved || drag?.blocked) suppressCalendarClickRef.current = true
      calendarDragRef.current = null
    }
    window.addEventListener('pointerup', finishCalendarDrag)
    window.addEventListener('pointercancel', finishCalendarDrag)
    return () => {
      window.removeEventListener('pointerup', finishCalendarDrag)
      window.removeEventListener('pointercancel', finishCalendarDrag)
    }
  }, [])

  const filteredSchedules = useMemo(() => {
    const query = historySearch.trim().toLocaleLowerCase('vi')
    return schedules.filter((schedule) => {
      const matchesText = !query || `${schedule.departmentName} ${schedule.periodStart} ${schedule.periodEnd}`
        .toLocaleLowerCase('vi').includes(query)
      return matchesText && (historyStatus === 'all' || schedule.status === historyStatus)
    })
  }, [historySearch, historyStatus, schedules])

  const historyPageCount = Math.max(1, Math.ceil(filteredSchedules.length / HISTORY_PAGE_SIZE))
  const currentHistoryPage = Math.min(Math.max(historyPage, 1), historyPageCount)
  const firstVisibleSchedule = filteredSchedules.length === 0
    ? 0
    : (currentHistoryPage - 1) * HISTORY_PAGE_SIZE + 1
  const lastVisibleSchedule = Math.min(currentHistoryPage * HISTORY_PAGE_SIZE, filteredSchedules.length)
  const visibleSchedules = useMemo(() => filteredSchedules.slice(
    (currentHistoryPage - 1) * HISTORY_PAGE_SIZE,
    currentHistoryPage * HISTORY_PAGE_SIZE,
  ), [currentHistoryPage, filteredSchedules])
  const historyPageNumbers = useMemo(() => {
    const pages = new Set([1, historyPageCount])
    for (let page = Math.max(1, currentHistoryPage - 1); page <= Math.min(historyPageCount, currentHistoryPage + 1); page += 1) {
      pages.add(page)
    }
    return [...pages].sort((first, second) => first - second)
  }, [currentHistoryPage, historyPageCount])

  const filteredStaff = useMemo(() => {
    const query = staffSearch.trim().toLocaleLowerCase('vi')
    return scheduleOptions.staff.filter((staff) => {
      const matchesText = !query || `${staff.code} ${staff.name}`.toLocaleLowerCase('vi').includes(query)
      const isDoctor = staff.roles.includes('DOCTOR')
      const matchesSpecialty = !specialtyFilter || staff.specialties.some((item) => String(item.id) === specialtyFilter)
      return isDoctor && !busyStaffIds.has(String(staff.id)) && matchesText && matchesSpecialty
    })
  }, [busyStaffIds, scheduleOptions.staff, specialtyFilter, staffSearch])
  const hasAvailableDoctors = scheduleOptions.staff.some((staff) =>
    staff.roles.includes('DOCTOR') && !busyStaffIds.has(String(staff.id)))

  const handleApiFailure = useCallback((failure) => {
    if (failure.status === 401) {
      onSessionExpired?.(errorMessage(failure))
    } else if (failure.status === 403) {
      setAccessFailure(failure)
    }
  }, [onSessionExpired])

  const loadWorkspace = useCallback(async (preferredId = '') => {
    setLoadingWorkspace(true)
    setAccessFailure(null)
    setScheduleError('')
    setOptionsError('')
    const [scheduleResult, optionsResult] = await Promise.allSettled([
      listHeadSchedules(),
      getHeadScheduleOptions(),
    ])

    if (scheduleResult.status === 'fulfilled') {
      const list = scheduleResult.value
      setSchedules(list)
      const previousId = selectedScheduleIdRef.current
      const nextId = preferredId || (list.some((item) => String(item.id) === String(previousId))
        ? previousId
        : String(list[0]?.id || ''))
      selectedScheduleIdRef.current = String(nextId)
      setSelectedScheduleId(String(nextId))
      if (!nextId) {
        setAssignments([])
        setScheduleOptions(emptyOptions)
        setLoadingSchedule(false)
      }
    } else {
      handleApiFailure(scheduleResult.reason)
      setScheduleError(errorMessage(scheduleResult.reason))
    }

    if (optionsResult.status === 'fulfilled') {
      const options = optionsResult.value
      setBaseOptions(options)
      setDraftForm((current) => ({
        ...current,
        departmentId: current.departmentId || String(options.departments[0]?.id || ''),
      }))
    } else {
      handleApiFailure(optionsResult.reason)
      setOptionsError(errorMessage(optionsResult.reason))
    }
    setLoadingWorkspace(false)
  }, [handleApiFailure])

  useEffect(() => {
    Promise.resolve().then(() => { void loadWorkspace() })
  }, [loadWorkspace])

  useEffect(() => {
    if (!selectedSchedule) return undefined

    let active = true
    Promise.resolve().then(async () => {
      if (!active) return
      setLoadingSchedule(true)
      setAssignmentError('')
      setSelectedStaffId('')
      setActiveSlot(null)
      setIsAddingAssignment(false)
      setSelectedWorkSlots([])
      calendarDragRef.current = null
      setWeekStart(mondayOf(selectedSchedule.periodStart))
      const [assignmentResult, optionsResult] = await Promise.allSettled([
        getHeadScheduleAssignments(selectedSchedule.id),
        getHeadScheduleOptions(selectedSchedule.departmentId),
      ])
      if (!active) return
      if (assignmentResult.status === 'fulfilled') setAssignments(assignmentResult.value)
      else {
        handleApiFailure(assignmentResult.reason)
        setAssignmentError(errorMessage(assignmentResult.reason))
      }
      if (optionsResult.status === 'fulfilled') setScheduleOptions(optionsResult.value)
      else {
        handleApiFailure(optionsResult.reason)
        setAssignmentError(errorMessage(optionsResult.reason))
      }
      setLoadingSchedule(false)
    })

    return () => { active = false }
  }, [handleApiFailure, selectedSchedule])

  function selectSchedule(id) {
    selectedScheduleIdRef.current = String(id)
    setSelectedScheduleId(String(id))
    setAccessFailure(null)
    setAssignmentError('')
    setActiveSlot(null)
    setIsAddingAssignment(false)
    setSelectedWorkSlots([])
    calendarDragRef.current = null
    setIsConfirmingPublish(false)
    setSelectedStaffId('')
  }

  async function refresh() {
    setSuccess('')
    setActionError('')
    await loadWorkspace(selectedScheduleIdRef.current)
  }

  async function submitDraft(event) {
    event.preventDefault()
    setActionError('')
    setSuccess('')
    if (!draftForm.departmentId || !draftForm.periodStart || !draftForm.periodEnd) {
      setActionError('Hãy chọn khoa và nhập đủ ngày bắt đầu, ngày kết thúc.')
      return
    }
    if (draftForm.periodEnd < draftForm.periodStart) {
      setActionError('Ngày kết thúc phải từ ngày bắt đầu trở đi.')
      return
    }
    if (noFullDayInTerm) {
      setActionError('Nhiệm kỳ hiện tại không bao trọn ngày lịch nào. Hãy nhờ quản trị viên kiểm tra thời hạn nhiệm kỳ.')
      return
    }
    if (draftDateBounds?.earliestStart && draftForm.periodStart < draftDateBounds.earliestStart) {
      setActionError(`Ngày bắt đầu đợt phải từ ${displayDate(draftDateBounds.earliestStart, { day: '2-digit', month: '2-digit', year: 'numeric' })} trở đi để nằm trọn trong nhiệm kỳ.`)
      return
    }
    if (draftDateBounds?.latestEnd && draftForm.periodEnd > draftDateBounds.latestEnd) {
      setActionError(`Ngày kết thúc đợt không được sau ${displayDate(draftDateBounds.latestEnd, { day: '2-digit', month: '2-digit', year: 'numeric' })} để nằm trọn trong nhiệm kỳ.`)
      return
    }

    setBusy(true)
    try {
      const created = await createHeadSchedule({
        departmentId: Number(draftForm.departmentId),
        periodStart: draftForm.periodStart,
        periodEnd: draftForm.periodEnd,
      })
      setDraftForm((current) => ({ ...current, periodStart: '', periodEnd: '' }))
      setHistorySearch('')
      setHistoryStatus('all')
      setHistoryPage(1)
      setSuccess('Đã tạo đợt lịch nháp.')
      await loadWorkspace(String(created.id))
    } catch (failure) {
      handleApiFailure(failure)
      setActionError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  function makeCalendarSlot(date, session) {
    return {
      workDate: date,
      mode: session.mode,
      shiftId: Number(session.shift.id),
      shiftCode: session.shiftCode,
      shiftName: `${session.modeLabel} · ${session.label}`,
      localStart: session.shift.localStart,
      localEnd: session.shift.localEnd,
    }
  }

  function calendarSlotKey(slot) {
    return `${slot.mode}:${slot.workDate}:${slot.shiftId}`
  }

  function selectCalendarCell(date, session, event) {
    if (suppressCalendarClickRef.current && event.detail > 0) {
      suppressCalendarClickRef.current = false
      return
    }
    suppressCalendarClickRef.current = false
    if (!session.shift) return
    const slot = makeCalendarSlot(date, session)
    const addOrRemove = event.ctrlKey || event.metaKey
    let nextSlots = [slot]

    if (addOrRemove) {
      if (selectedWorkSlots.length > 0 && selectedMode !== slot.mode) {
        setActionError('Không thể chọn chung ô Trực tiếp và Trực tuyến. Hãy bỏ chọn trước khi đổi hình thức.')
        return
      }
      const existing = selectedWorkSlots.some((item) => calendarSlotKey(item) === calendarSlotKey(slot))
      nextSlots = existing
        ? selectedWorkSlots.filter((item) => calendarSlotKey(item) !== calendarSlotKey(slot))
        : [...selectedWorkSlots, slot]
    }
    if (nextSlots.length > 124) {
      setActionError('Có thể chọn tối đa 124 ô ngày/ca trong một lần phân công.')
      return
    }

    setSelectedWorkSlots(nextSlots)
    setActiveSlot(nextSlots[0] || null)
    setIsAddingAssignment(false)
    setSelectedStaffId('')
    setAssignmentForm({ roomId: '', specialtyId: '', capacity: '1' })
    setActionError('')
  }

  function handleCalendarPointerDown(date, session, event) {
    suppressCalendarClickRef.current = false
    if (!editable || !session.shift || event.button !== 0) return
    const anchor = makeCalendarSlot(date, session)
    const additive = event.ctrlKey || event.metaKey
    const blocked = additive && selectedWorkSlots.length > 0 && selectedMode !== session.mode
    calendarDragRef.current = {
      anchor,
      additive,
      baseSlots: additive ? selectedWorkSlots : [],
      moved: false,
      blocked,
    }
    if (blocked) setActionError('Không thể kéo chọn chung ô Trực tiếp và Trực tuyến.')
  }

  function handleCalendarPointerEnter(date, session) {
    const drag = calendarDragRef.current
    if (!drag || !session.shift || drag.blocked) return
    if (session.mode !== drag.anchor.mode) {
      drag.blocked = true
      drag.moved = true
      setActionError('Vùng kéo chỉ được nằm trong cùng hình thức Trực tiếp hoặc Trực tuyến.')
      return
    }

    const startDay = visibleDays.indexOf(drag.anchor.workDate)
    const endDay = visibleDays.indexOf(date)
    const shiftOrder = ['MORNING', 'AFTERNOON']
    const startShift = shiftOrder.indexOf(drag.anchor.shiftCode)
    const endShift = shiftOrder.indexOf(session.shiftCode)
    if (startDay < 0 || endDay < 0 || startShift < 0 || endShift < 0) return
    if (date === drag.anchor.workDate && session.shiftCode === drag.anchor.shiftCode) return

    drag.moved = true
    const dateRange = visibleDays.slice(Math.min(startDay, endDay), Math.max(startDay, endDay) + 1)
      .filter((workDate) => workDate >= selectedSchedule.periodStart && workDate <= selectedSchedule.periodEnd)
    const shiftRange = shiftOrder.slice(Math.min(startShift, endShift), Math.max(startShift, endShift) + 1)
    const rangeSlots = dateRange.flatMap((workDate) => sessionRows
      .filter((row) => row.mode === drag.anchor.mode && shiftRange.includes(row.shiftCode) && row.shift)
      .map((row) => makeCalendarSlot(workDate, row)))
    const combinedSlots = drag.additive ? [...drag.baseSlots, ...rangeSlots] : rangeSlots
    const uniqueSlots = [...new Map(combinedSlots.map((slot) => [calendarSlotKey(slot), slot])).values()]
    if (uniqueSlots.length > 124) {
      setActionError('Có thể chọn tối đa 124 ô ngày/ca trong một lần phân công.')
      return
    }
    setSelectedWorkSlots(uniqueSlots)
    setActiveSlot(drag.anchor)
    setIsAddingAssignment(false)
    setSelectedStaffId('')
    setActionError('')
  }

  function openAssignmentForm() {
    if ((!activeSlot && selectedWorkSlots.length === 0) || !editable) return
    if (!activeSlot && selectedWorkSlots.length > 0) setActiveSlot(selectedWorkSlots[0])
    setAssignmentForm({ roomId: '', specialtyId: '', capacity: '1' })
    setSelectedStaffId('')
    setIsAddingAssignment(true)
    setActionError('')
  }

  function cancelAssignmentForm() {
    setIsAddingAssignment(false)
    setSelectedStaffId('')
    setAssignmentForm({ roomId: '', specialtyId: '', capacity: '1' })
    setActionError('')
  }

  function clearCalendarSelection() {
    setSelectedWorkSlots([])
    setActiveSlot(null)
    setIsAddingAssignment(false)
    setSelectedStaffId('')
    setAssignmentForm({ roomId: '', specialtyId: '', capacity: '1' })
    setActionError('')
  }

  function selectStaff(staff) {
    setSelectedStaffId(String(staff.id))
    const firstEligible = scheduleOptions.specialties.find((item) =>
      staff.specialties.some((specialty) => specialty.id === item.id))
    setAssignmentForm((current) => ({
      ...current,
      specialtyId: firstEligible ? String(firstEligible.id) : '',
    }))
    setActionError('')
  }

  async function submitAssignment(event) {
    event.preventDefault()
    setActionError('')
    setSuccess('')
    if (!selectedSchedule || !selectedStaff || !activeSlot) return
    if (!assignmentForm.roomId || !assignmentForm.specialtyId) {
      setActionError('Hãy chọn chuyên khoa và phòng trước khi lưu.')
      return
    }
    if (!eligibleSpecialties.some((item) => String(item.id) === assignmentForm.specialtyId)) {
      setActionError('Chuyên khoa đã chọn không thuộc bác sĩ này.')
      return
    }
    if (selectedWorkSlots.length === 0) {
      setActionError('Hãy chọn ít nhất một ô ngày/ca trên thời khóa biểu.')
      return
    }
    const assignmentMode = selectedWorkSlots[0].mode
    if (selectedWorkSlots.some((slot) => slot.mode !== assignmentMode)) {
      setActionError('Một lần lưu chỉ nhận các ô cùng hình thức Trực tiếp hoặc Trực tuyến.')
      return
    }
    const isOnline = assignmentMode === 'ONLINE'
    const capacity = Number(assignmentForm.capacity)
    if (!isOnline && (!Number.isInteger(capacity) || capacity < 1)) {
      setActionError('Sức chứa phải là số nguyên lớn hơn 0.')
      return
    }

    setBusy(true)
    try {
      const assignment = {
        staffId: selectedStaff.id,
        roomId: Number(assignmentForm.roomId),
        specialtyId: Number(assignmentForm.specialtyId),
        workSlots: selectedWorkSlots.map(({ workDate, shiftId }) => ({ workDate, shiftId })),
        mode: assignmentMode,
        ...(isOnline ? {} : { capacity }),
      }
      const assignmentCount = selectedWorkSlots.length
      const updated = await addHeadScheduleAssignment(selectedSchedule.id, assignment)
      setAssignments(updated)
      setIsAddingAssignment(false)
      setSelectedWorkSlots([])
      setActiveSlot(null)
      calendarDragRef.current = null
      setSelectedStaffId('')
      setAssignmentForm({ roomId: '', specialtyId: '', capacity: '1' })
      setSuccess(`Đã thêm ${assignmentCount} phân công vào đợt nháp.`)
    } catch (failure) {
      handleApiFailure(failure)
      setActionError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function deleteAssignment(assignmentId) {
    setBusy(true)
    setActionError('')
    setSuccess('')
    try {
      const updated = await removeHeadScheduleAssignment(selectedSchedule.id, assignmentId)
      setAssignments(updated)
      setSuccess('Đã gỡ phân công khỏi đợt nháp.')
    } catch (failure) {
      handleApiFailure(failure)
      setActionError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function confirmPublish() {
    if (!selectedSchedule || !editable) return
    setBusy(true)
    setActionError('')
    setSuccess('')
    try {
      const published = await publishHeadSchedule(selectedSchedule.id)
      setIsConfirmingPublish(false)
      setSuccess('Đợt lịch đã được công bố. Các slot trực tuyến đã sẵn sàng theo giờ đã phân công.')
      await loadWorkspace(String(published.id))
    } catch (failure) {
      handleApiFailure(failure)
      setActionError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  const weekStartDate = weekStart || (selectedSchedule ? mondayOf(selectedSchedule.periodStart) : todayInSaigon())
  const visibleDays = Array.from({ length: 6 }, (_, index) => addDays(weekStartDate, index))
  const sessionRows = ['ONSITE', 'ONLINE'].flatMap((mode) => [
    { shiftCode: 'MORNING', label: 'Buổi sáng' },
    { shiftCode: 'AFTERNOON', label: 'Buổi chiều' },
  ].map((session) => {
    const shift = scheduleOptions.shifts.find((item) => item.code === session.shiftCode)
    const modeLabel = mode === 'ONSITE' ? 'Trực tiếp' : 'Trực tuyến'
    return { ...session, code: `${mode}_${session.shiftCode}`, mode, modeLabel, shift }
  }))
  const firstWeek = selectedSchedule ? mondayOf(selectedSchedule.periodStart) : weekStartDate
  const lastWeek = selectedSchedule ? mondayOf(selectedSchedule.periodEnd) : weekStartDate
  const changeWeek = (amount) => {
    const next = addDays(weekStartDate, amount * 7)
    if (next >= firstWeek && next <= lastWeek) {
      setWeekStart(next)
      setIsAddingAssignment(false)
      calendarDragRef.current = null
      setSelectedStaffId('')
    }
  }
  const activeDayIndex = activeSlot
    ? (dateAtNoon(activeSlot.workDate).getUTCDay() + 6) % 7
    : -1

  return (
    <section className='w-full space-y-5' aria-labelledby='schedule-title'>
      <header className='flex flex-wrap items-start justify-between gap-3'>
        <div>
          <p className='text-xs font-bold uppercase tracking-widest text-suncare-700'>Điều phối khoa</p>
          <h1 id='schedule-title' className='mt-1 text-2xl font-bold text-clinical-ink sm:text-3xl'>Phân lịch nhân viên</h1>
          <p className='mt-1 text-sm text-clinical-muted'>Đang đăng nhập: <span className='font-semibold text-clinical-ink'>{account?.loginName || 'không xác định'}</span>. Mỗi đợt có lịch riêng; lịch đã công bố chỉ xem được.</p>
        </div>
        <Button onClick={() => void refresh()} disabled={loadingWorkspace || busy}>
          <FontAwesomeIcon icon={faRotateRight} className={loadingWorkspace ? 'animate-spin' : ''} /> Tải lại
        </Button>
      </header>

      {accessFailure && <div role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-4 text-sm text-clinical-danger'>
        <p className='font-semibold'>{accessFailureMessage}</p>
        <p className='mt-1 text-xs'>Yêu cầu bị từ chối: {accessFailure.endpoint || 'API phân lịch'} · HTTP {accessFailure.status}</p>
        <button type='button' onClick={() => onSessionExpired?.('Đã kết thúc phiên hiện tại. Hãy đăng nhập lại bằng tài khoản cần điều phối lịch.')}
          className='mt-3 min-h-10 rounded-lg border border-clinical-danger px-3 font-semibold hover:bg-rose-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-clinical-danger'>
          Đăng xuất và đăng nhập lại
        </button>
      </div>}
      {!accessFailure && scheduleError && <div role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{scheduleError}</div>}
      {!accessFailure && optionsError && <div role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{optionsError}</div>}
      {!accessFailure && actionError && <div role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{actionError}</div>}
      {success && <div role='status' className='rounded-xl border border-clinical-leaf bg-clinical-leaf-soft p-3 text-sm text-clinical-success'>{success}</div>}

      <div className='grid items-start gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(19rem,0.8fr)]'>
        <section className='overflow-hidden rounded-2xl border border-clinical-border bg-white' aria-labelledby='schedule-history-title'>
          <div className='flex flex-wrap items-end justify-between gap-3 border-b border-clinical-border p-4'>
            <div>
              <h2 id='schedule-history-title' className='font-bold text-clinical-ink'>Các đợt phân lịch</h2>
              <p className='mt-1 text-xs text-clinical-muted'>Chọn một đợt để mở đúng calendar của đợt đó.</p>
            </div>
            <span className='text-xs text-clinical-muted'>{filteredSchedules.length} / {schedules.length} đợt</span>
          </div>
          <div className='grid gap-2 border-b border-clinical-border bg-clinical-canvas/60 p-3 sm:grid-cols-2'>
            <label className='text-xs font-semibold text-clinical-ink'>Tìm khoa hoặc ngày
              <input value={historySearch} onChange={(event) => { setHistorySearch(event.target.value); setHistoryPage(1) }} placeholder='Tên khoa, YYYY-MM-DD' className={`${field} mt-1`} />
            </label>
            <label className='text-xs font-semibold text-clinical-ink'>Trạng thái
              <select value={historyStatus} onChange={(event) => { setHistoryStatus(event.target.value); setHistoryPage(1) }} className={`${field} mt-1`}>
                <option value='all'>Tất cả</option><option value='DRAFT'>Bản nháp</option><option value='PUBLISHED'>Đã công bố</option>
              </select>
            </label>
          </div>
          {loadingWorkspace ? <p role='status' className='p-5 text-sm text-clinical-muted'>Đang tải danh sách lịch khoa…</p> : scheduleError || accessFailure ?
            <p className='p-5 text-sm text-clinical-muted'>Chưa thể xác nhận danh sách đợt lịch. Hãy xử lý thông báo lỗi phía trên rồi tải lại.</p> : schedules.length === 0 ?
              <p className='p-5 text-sm text-clinical-muted'>Chưa có đợt lịch trong khoa được giao.</p> : filteredSchedules.length === 0 ?
              <p className='p-5 text-sm text-clinical-muted'>Không có đợt nào khớp bộ lọc.</p> :
                <div className='overflow-x-auto'>
                  <table className='w-full min-w-[35rem] text-left text-sm'>
                    <thead className='sticky top-0 bg-suncare-50 text-xs text-clinical-muted'><tr><th className='px-3 py-2.5'>Khoa · thời hạn</th><th className='px-3 py-2.5'>Phân công</th><th className='px-3 py-2.5'>Trạng thái</th><th className='px-3 py-2.5 text-right'>Lịch</th></tr></thead>
                    <tbody className='divide-y divide-clinical-border'>
                      {visibleSchedules.map((schedule) => <tr key={schedule.id} className={String(selectedScheduleId) === String(schedule.id) ? 'bg-suncare-50' : 'hover:bg-clinical-canvas'}>
                        <td className='px-3 py-2.5'><div className='font-semibold text-clinical-ink'>{schedule.departmentName}</div><div className='mt-0.5 text-xs text-clinical-muted'>{displayDate(schedule.periodStart, { day: 'numeric', month: 'short', year: 'numeric' })} – {displayDate(schedule.periodEnd, { day: 'numeric', month: 'short', year: 'numeric' })}</div></td>
                        <td className='px-3 py-2.5'>{schedule.assignmentCount}</td>
                        <td className='px-3 py-2.5'><Badge tone={schedule.status === 'DRAFT' ? 'warning' : 'success'}>{schedule.status === 'DRAFT' ? 'Bản nháp' : 'Đã công bố'}</Badge></td>
                        <td className='px-3 py-2.5 text-right'><button type='button' onClick={() => selectSchedule(schedule.id)} className='min-h-9 rounded-lg px-3 font-semibold text-suncare-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-suncare-700'>{String(selectedScheduleId) === String(schedule.id) ? 'Đang xem' : 'Mở lịch'}</button></td>
                      </tr>)}
                    </tbody>
                  </table>
                </div>}
          {!loadingWorkspace && !scheduleError && !accessFailure && filteredSchedules.length > HISTORY_PAGE_SIZE &&
            <nav aria-label='Phân trang đợt lịch' className='flex flex-wrap items-center justify-between gap-3 border-t border-clinical-border px-3 py-3'>
              <p className='text-xs text-clinical-muted' aria-live='polite'>Hiển thị {firstVisibleSchedule}–{lastVisibleSchedule} / {filteredSchedules.length} đợt</p>
              <div className='flex flex-wrap items-center gap-1.5'>
                <button type='button' disabled={currentHistoryPage === 1} onClick={() => setHistoryPage(currentHistoryPage - 1)} aria-label='Trang trước'
                  className='inline-flex min-h-10 items-center gap-2 rounded-lg border border-clinical-border-strong bg-white px-3 text-sm font-semibold text-clinical-ink hover:bg-clinical-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-50'>
                  <FontAwesomeIcon icon={faArrowLeft} /> <span className='hidden sm:inline'>Trước</span>
                </button>
                {historyPageNumbers.map((page, index) => <span key={page} className='contents'>
                  {index > 0 && page - historyPageNumbers[index - 1] > 1 && <span aria-hidden='true' className='px-1 text-clinical-muted'>…</span>}
                  <button type='button' onClick={() => setHistoryPage(page)} aria-label={`Trang ${page}`} aria-current={currentHistoryPage === page ? 'page' : undefined}
                    className={'min-h-10 min-w-10 rounded-lg border px-3 text-sm font-semibold focus-visible:outline focus-visible:outline-2 focus-visible:outline-suncare-700 ' + (currentHistoryPage === page ? 'border-suncare-600 bg-suncare-100 text-clinical-ink' : 'border-clinical-border bg-white text-clinical-ink hover:bg-clinical-canvas')}>
                    {page}
                  </button>
                </span>)}
                <button type='button' disabled={currentHistoryPage === historyPageCount} onClick={() => setHistoryPage(currentHistoryPage + 1)} aria-label='Trang sau'
                  className='inline-flex min-h-10 items-center gap-2 rounded-lg border border-clinical-border-strong bg-white px-3 text-sm font-semibold text-clinical-ink hover:bg-clinical-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-50'>
                  <span className='hidden sm:inline'>Sau</span> <FontAwesomeIcon icon={faArrowRight} />
                </button>
              </div>
            </nav>}
        </section>

        <form onSubmit={submitDraft} className='grid gap-3 rounded-2xl border border-clinical-border bg-white p-4 sm:grid-cols-2'>
          <h2 className='font-bold text-clinical-ink sm:col-span-2'>Tạo đợt phân lịch mới</h2>
          <label className='text-sm font-semibold sm:col-span-2'>Khoa
            <select required value={draftForm.departmentId} onChange={(event) => setDraftForm({ departmentId: event.target.value, periodStart: '', periodEnd: '' })} className={`${field} mt-1`}>
              <option value=''>Chọn khoa</option>{baseOptions.departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
            </select>
          </label>
          {selectedDepartment && <div className='space-y-1 text-xs text-clinical-muted sm:col-span-2'>
            <p>Nhiệm kỳ hiện tại: {displayDateTime(selectedDepartment.validFrom)} – {displayDateTime(selectedDepartment.validTo)}</p>
            {noFullDayInTerm ? <p className='font-semibold text-clinical-danger'>Nhiệm kỳ hiện tại không bao trọn ngày lịch nào.</p> :
              <p>Đợt lịch tính theo ngày trọn vẹn: ngày bắt đầu sớm nhất {displayDate(draftDateBounds.earliestStart, { day: '2-digit', month: '2-digit', year: 'numeric' })}; ngày kết thúc muộn nhất {draftDateBounds.latestEnd ? displayDate(draftDateBounds.latestEnd, { day: '2-digit', month: '2-digit', year: 'numeric' }) : 'không giới hạn'}.</p>}
          </div>}
          {!loadingWorkspace && !optionsError && baseOptions.departments.length === 0 &&
            <p role='status' className='text-sm text-clinical-muted sm:col-span-2'>Tài khoản chưa có nhiệm kỳ trưởng khoa hiệu lực tại khoa khám nào. Quản trị viên cần kiểm tra phân công và thời hạn.</p>}
          <label className='text-xs font-semibold text-clinical-muted'>Từ ngày
            <input required type='date' min={draftDateBounds?.earliestStart || undefined} max={draftDateBounds?.latestEnd || undefined} value={draftForm.periodStart} onChange={(event) => setDraftForm({ ...draftForm, periodStart: event.target.value, periodEnd: '' })} className={`${field} mt-1`} />
          </label>
          <label className='text-xs font-semibold text-clinical-muted'>Đến ngày
            <input required type='date' min={draftForm.periodStart || draftDateBounds?.earliestStart || undefined} max={draftDateBounds?.latestEnd || undefined} value={draftForm.periodEnd} onChange={(event) => setDraftForm({ ...draftForm, periodEnd: event.target.value })} className={`${field} mt-1`} />
          </label>
          <div className='sm:col-span-2 sm:text-right'>
            <Button type='submit' variant='primary' disabled={busy || loadingWorkspace || Boolean(scheduleError) || !baseOptions.departments.length || !draftForm.departmentId || noFullDayInTerm}>
              <FontAwesomeIcon icon={faPlus} /> {busy ? 'Đang lưu…' : 'Tạo bản nháp'}
            </Button>
          </div>
        </form>
      </div>

      {!loadingWorkspace && !scheduleError && !accessFailure && schedules.length === 0 &&
        <div className='rounded-xl border border-dashed border-clinical-border bg-white p-5 text-center text-sm text-clinical-muted'>Tạo một đợt nháp để bắt đầu thêm phân công theo ngày.</div>}

      {selectedSchedule && <section className='space-y-4' aria-label='Thời khóa biểu phân công'>
        <div className='flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-clinical-border bg-white p-4'>
          <div>
            <h2 className='font-bold text-clinical-ink'>{selectedSchedule.departmentName}</h2>
            <p className='mt-1 text-sm text-clinical-muted'>Đợt lịch: {displayDate(selectedSchedule.periodStart, { day: 'numeric', month: 'long', year: 'numeric' })} – {displayDate(selectedSchedule.periodEnd, { day: 'numeric', month: 'long', year: 'numeric' })}</p>
          </div>
          <div className='flex flex-wrap items-center gap-2'>
            {editable && <Button variant='primary' disabled={busy || loadingSchedule || assignments.length === 0 || Boolean(assignmentError) || Boolean(accessFailure)}
              onClick={() => { setIsConfirmingPublish(true); setActionError('') }}>
              Công bố lịch
            </Button>}
            <Badge tone={editable ? 'warning' : 'success'}>{editable ? 'Bản nháp · có thể phân công' : 'Đã công bố · chỉ xem'}</Badge>
          </div>
        </div>

        {isConfirmingPublish && editable && <section className='rounded-xl border border-suncare-200 bg-suncare-50 p-4' aria-label='Xác nhận công bố lịch'>
          <h3 className='font-bold text-clinical-ink'>Công bố đợt lịch này?</h3>
          <p className='mt-1 text-sm text-clinical-muted'>Lịch sẽ chuyển sang chỉ xem. Hệ thống kiểm tra lại bác sĩ, phòng, chuyên khoa, giá và xung đột; các khung ONLINE sẽ sinh slot 20 phút.</p>
          <div className='mt-3 flex flex-wrap gap-2'>
            <Button disabled={busy} onClick={() => setIsConfirmingPublish(false)}>Giữ bản nháp</Button>
            <Button variant='primary' disabled={busy || Boolean(assignmentError) || Boolean(accessFailure)} onClick={() => void confirmPublish()}>{busy ? 'Đang công bố…' : 'Xác nhận công bố'}</Button>
          </div>
        </section>}

        <div className='flex flex-wrap items-center justify-between gap-3'>
          <Button onClick={() => changeWeek(-1)} disabled={weekStartDate <= firstWeek} aria-label='Tuần trước'><FontAwesomeIcon icon={faArrowLeft} /> Tuần trước</Button>
          <p className='text-center text-sm font-semibold text-clinical-ink'>{displayDate(weekStartDate)} – {displayDate(addDays(weekStartDate, 5))}</p>
          <Button onClick={() => changeWeek(1)} disabled={weekStartDate >= lastWeek} aria-label='Tuần sau'>Tuần sau <FontAwesomeIcon icon={faArrowRight} /></Button>
        </div>

        {loadingSchedule && <p role='status' className='text-sm text-clinical-muted'>Đang tải phân công và dữ liệu khoa…</p>}
        {!accessFailure && assignmentError && <div role='alert' className='rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger'>{assignmentError}</div>}

        {sessionRows.some((session) => !session.shift) && <p role='status' className='rounded-xl border border-clinical-border bg-white p-3 text-sm text-clinical-muted'>Chưa cấu hình đủ ca sáng và ca chiều. Hãy kiểm tra danh mục ca làm việc.</p>}
        {selectedWorkSlots.length > 1 && <div className='flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-suncare-200 bg-suncare-50 p-3 sm:p-4'>
          <p role='status' className='text-sm font-semibold text-clinical-ink'>Đã chọn {selectedWorkSlots.length} ô · {selectedWorkDates.length} ngày · {selectedMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} · {displayDate(selectedWorkDates[0])}–{displayDate(selectedWorkDates[selectedWorkDates.length - 1])}<span className='mt-1 block text-xs font-normal text-clinical-muted'>Giữ lựa chọn khi chuyển tuần; dùng Ctrl/⌘-bấm hoặc Ctrl/⌘-kéo để cộng thêm ô.</span></p>
          <div className='flex flex-wrap gap-2'>
            <Button onClick={clearCalendarSelection}>Bỏ chọn</Button>
            {editable && <Button onClick={openAssignmentForm} variant='primary' disabled={busy || loadingSchedule}><FontAwesomeIcon icon={faPlus} /> Phân công bác sĩ</Button>}
          </div>
        </div>}
        <div
          className='overflow-x-auto rounded-2xl border border-clinical-border bg-white'
        >
          <table className='w-full min-w-[72rem] table-fixed border-collapse text-left'>
            <caption className='sr-only'>Thời khóa biểu từ thứ Hai đến thứ Bảy, nhóm riêng ca trực tiếp và trực tuyến</caption>
            <thead className='bg-suncare-50'>
              <tr>
                <th scope='col' className='w-[12rem] min-w-[12rem] max-w-[12rem] border-b border-clinical-border px-4 py-3 text-xs font-bold uppercase tracking-wide text-clinical-muted'>Hình thức · ca</th>
                {visibleDays.map((date, index) => <th key={date} scope='col' className='min-w-40 border-b border-l border-clinical-border px-3 py-3'>
                  <span className='block text-sm font-bold text-clinical-ink'>{dayLabels[index]}</span>
                  <span className='mt-0.5 block text-xs font-normal text-clinical-muted'>{displayDate(date, { day: '2-digit', month: '2-digit' })}</span>
                </th>)}
              </tr>
            </thead>
            <tbody className='divide-y divide-clinical-border'>
              {sessionRows.map((session) => <tr key={session.code} className={session.mode === 'ONLINE' && session.shiftCode === 'MORNING' ? 'border-t-2 border-t-clinical-border-strong' : ''}>
                <th scope='row' className='w-[12rem] min-w-[12rem] max-w-[12rem] border-r border-clinical-border bg-clinical-canvas/60 px-4 py-3 align-top'>
                  <span className='block text-xs font-semibold text-clinical-muted'>{session.modeLabel}</span>
                  <span className='mt-1 block text-sm font-bold text-clinical-ink'>{session.label}</span>
                  {session.shift && <span className='mt-1 block text-xs font-normal text-clinical-muted'>{session.shift.localStart.slice(0, 5)}–{session.shift.localEnd.slice(0, 5)}</span>}
                </th>
                {visibleDays.map((date) => {
                  const inRange = date >= selectedSchedule.periodStart && date <= selectedSchedule.periodEnd
                  const isOnlineSession = session.mode === 'ONLINE'
                  const slotAssignments = session.shift
                    ? assignments.filter((item) => item.workDate === date && item.mode === session.mode
                      && String(item.shiftId) === String(session.shift.id))
                    : []
                  const assignmentSummary = slotAssignments
                    .map((item) => `${item.staffName}, phòng ${item.roomCode} ${item.roomName}${isOnlineSession ? `, ${item.localStart.slice(0, 5)}–${item.localEnd.slice(0, 5)}` : ''}`)
                    .join('; ')
                  const isActive = activeSlot?.workDate === date && activeSlot.shiftCode === session.code
                  const isSelectionCell = selectedWorkSlots.some((slot) => slot.workDate === date
                    && slot.mode === session.mode && slot.shiftId === Number(session.shift?.id))
                  const canOpen = Boolean(session.shift && inRange && !loadingSchedule)
                  return <td key={date} className='h-32 min-w-40 border-l border-clinical-border p-1.5 align-top'>
                    <button
                      type='button'
                      disabled={!canOpen}
                      aria-pressed={Boolean(isActive || isSelectionCell)}
                      aria-label={dayLabels[(dateAtNoon(date).getUTCDay() + 6) % 7] + ', ' + session.modeLabel + ', ' + session.label + ', ' + (slotAssignments.length ? `${slotAssignments.length} phân công: ${assignmentSummary}` : 'chưa có phân công')}
                      onPointerDown={(event) => handleCalendarPointerDown(date, session, event)}
                      onPointerEnter={() => handleCalendarPointerEnter(date, session)}
                      onClick={(event) => selectCalendarCell(date, session, event)}
                      className={'h-full min-h-28 w-full rounded-xl border p-2.5 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 ' +
                        (isSelectionCell ? 'border-suncare-600 bg-suncare-100 ring-2 ring-suncare-300 ' : isActive ? 'border-suncare-600 bg-suncare-50 ring-1 ring-suncare-200 ' :
                          inRange ? 'border-transparent bg-white hover:border-suncare-300 hover:bg-suncare-50 ' : 'border-dashed border-clinical-border bg-clinical-canvas/70 ') +
                        (canOpen ? (editable ? 'cursor-crosshair' : 'cursor-pointer') : 'cursor-default opacity-70')}>
                      {!inRange ? <span className='text-xs text-clinical-muted'>Ngoài đợt lịch</span> : !session.shift ? <span className='text-xs text-clinical-muted'>Chưa cấu hình ca</span> :
                        <span className='flex h-full flex-col'>
                          <span className='flex items-center justify-between gap-2'>
                            <span className='text-xs font-semibold text-clinical-ink'>{slotAssignments.length ? slotAssignments.length + (isOnlineSession ? ' khung online' : ' bác sĩ') : editable ? 'Chưa phân công' : 'Trống'}</span>
                            {isSelectionCell ? <span className='text-xs font-bold text-suncare-800'>Đã chọn</span> : isActive && <span className='text-xs font-bold text-suncare-800'>Đang xem</span>}
                          </span>
                          {slotAssignments.length > 0 ? <span className='mt-2 space-y-1'>
                            {slotAssignments.slice(0, 2).map((assignment) => <span key={assignment.id} className='block rounded-md bg-suncare-50 px-2 py-1'>
                              <span className='block truncate text-xs font-medium text-clinical-ink'>{assignment.staffName}</span>
                              <span className='block truncate text-[11px] text-clinical-muted'>Phòng {assignment.roomCode} · {assignment.roomName}{isOnlineSession ? ` · ${assignment.localStart.slice(0, 5)}–${assignment.localEnd.slice(0, 5)}` : ''}</span>
                            </span>)}
                            {slotAssignments.length > 2 && <span className='block px-1 text-xs text-clinical-muted'>và {slotAssignments.length - 2} người khác</span>}
                          </span> : <span className='mt-2 text-xs text-clinical-muted'>{editable ? 'Bấm để phân công bác sĩ' : 'Chưa có phân công'}</span>}
                        </span>}
                    </button>
                  </td>
                })}
              </tr>)}
            </tbody>
          </table>
        </div>
        <p className='text-xs text-clinical-muted'>Bấm ô để chọn một ca; Ctrl/⌘-bấm để thêm hoặc bỏ ô. Giữ và kéo để chọn vùng ca; Ctrl/⌘-kéo để cộng vùng. Chuyển tuần vẫn giữ các ô đã chọn, nhưng một lần phân công chỉ gồm Trực tiếp hoặc Trực tuyến. Trên màn hình hẹp, vuốt ngang bảng để xem đủ các ngày.</p>

        {activeSlot && (selectedWorkSlots.length <= 1 || isAddingAssignment) && <section className='space-y-4 rounded-2xl border border-clinical-border bg-white p-4 sm:p-5' aria-labelledby='active-slot-title'>
          <header className='flex flex-wrap items-start justify-between gap-3 border-b border-clinical-border pb-4'>
            <div>
              <p className='text-xs font-bold uppercase tracking-wide text-suncare-700'>Chi tiết phân công được chọn</p>
              <h3 id='active-slot-title' className='mt-1 text-lg font-bold text-clinical-ink'>{dayLabels[activeDayIndex]} · {activeSlot.shiftName}</h3>
              <p className='mt-1 text-sm text-clinical-muted'>{displayDate(activeSlot.workDate, { day: 'numeric', month: 'long', year: 'numeric' })}{activeSlot.mode === 'ONLINE' ? ' · Giờ được chọn khi thêm phân công' : ` · ${activeSlot.localStart.slice(0, 5)}–${activeSlot.localEnd.slice(0, 5)}`}</p>
            </div>
            <div className='flex flex-wrap gap-2'>
              {editable && !isAddingAssignment && <Button onClick={openAssignmentForm} variant='primary' disabled={busy || loadingSchedule}><FontAwesomeIcon icon={faPlus} /> {activeSlot.mode === 'ONLINE' ? 'Thêm bác sĩ trực tuyến' : 'Thêm bác sĩ'}</Button>}
              <Button onClick={clearCalendarSelection} aria-label='Đóng chi tiết ca'><FontAwesomeIcon icon={faXmark} /> Bỏ chọn ca</Button>
            </div>
          </header>

          <div>
            <h4 className='mb-2 font-semibold text-clinical-ink'>Phân công hiện có · {displayDate(activeSlot.workDate)} · {activeSlot.shiftName} <span className='font-normal text-clinical-muted'>({activeSlotAssignments.length})</span></h4>
            {activeSlotAssignments.length === 0 ? <p className='rounded-xl border border-dashed border-clinical-border bg-clinical-canvas/50 p-4 text-sm text-clinical-muted'>{editable ? 'Ca này chưa có bác sĩ. Chọn “Thêm bác sĩ” để bắt đầu phân công.' : 'Ca này chưa có phân công.'}</p> :
              <div className='overflow-x-auto rounded-xl border border-clinical-border'>
                <table className='w-full min-w-[48rem] text-left text-sm'>
                  <thead className='bg-suncare-50 text-xs text-clinical-muted'><tr>
                    <th scope='col' className='px-3 py-2.5'>Bác sĩ</th><th scope='col' className='px-3 py-2.5'>Chuyên khoa</th><th scope='col' className='px-3 py-2.5'>Phòng · giờ</th><th scope='col' className='px-3 py-2.5'>Sức chứa</th><th scope='col' className='px-3 py-2.5 text-right'>Thao tác</th>
                  </tr></thead>
                  <tbody className='divide-y divide-clinical-border'>{activeSlotAssignments.map((assignment) => <tr key={assignment.id}>
                    <td className='px-3 py-3'><span className='block font-semibold text-clinical-ink'>{assignment.staffName}</span><span className='mt-0.5 block font-mono text-xs text-clinical-muted'>{assignment.staffCode}</span></td>
                    <td className='px-3 py-3'>{assignment.specialtyName}</td>
                    <td className='px-3 py-3'><span className='block'>{assignment.roomCode} · {assignment.roomName}</span><span className='mt-0.5 block text-xs text-clinical-muted'>{assignment.localStart.slice(0, 5)}–{assignment.localEnd.slice(0, 5)}</span></td>
                    <td className='px-3 py-3'>{assignment.capacity ?? '—'}</td>
                    <td className='px-3 py-3 text-right'>{editable && <button type='button' disabled={busy} onClick={() => void deleteAssignment(assignment.id)} className='inline-flex min-h-9 items-center gap-2 rounded-lg px-2 font-semibold text-clinical-danger underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-clinical-danger disabled:opacity-50'><FontAwesomeIcon icon={faTrashCan} /> Gỡ khỏi bản nháp</button>}</td>
                  </tr>)}</tbody>
                </table>
              </div>}
          </div>

          {isAddingAssignment && editable && <div className='space-y-4 border-t border-clinical-border pt-4'>
            <div className='flex flex-wrap items-center justify-between gap-3'>
              <div><h4 className='font-bold text-clinical-ink'>{activeSlot.mode === 'ONLINE' ? 'Thêm bác sĩ trực tuyến' : 'Thêm bác sĩ trực tiếp'}</h4><p className='mt-1 text-xs text-clinical-muted'>Đang chọn {selectedWorkSlots.length} ca trên {selectedWorkDates.length} ngày. Giờ làm lấy từ ca quản trị viên đã cấu hình.</p></div>
              <Button onClick={cancelAssignmentForm}><FontAwesomeIcon icon={faXmark} /> Hủy thao tác</Button>
            </div>
            <div className='rounded-xl border border-clinical-border bg-clinical-canvas/60 p-3'>
              <p className='text-sm font-semibold text-clinical-ink'>{selectedMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} · {selectedWorkDates.length} ngày · {selectedWorkSlots.length} ca được chọn</p>
              <ul className='mt-2 grid max-h-40 gap-1 overflow-y-auto text-xs text-clinical-muted sm:grid-cols-2'>
                {[...selectedWorkSlots].sort((first, second) => first.workDate.localeCompare(second.workDate)
                  || ['MORNING', 'AFTERNOON'].indexOf(first.shiftCode) - ['MORNING', 'AFTERNOON'].indexOf(second.shiftCode))
                  .map((slot) => <li key={calendarSlotKey(slot)} className='rounded-md bg-white px-2.5 py-2'>
                    {displayDate(slot.workDate, { day: '2-digit', month: 'short', year: 'numeric' })} · {slot.shiftName.split(' · ')[1]} · {slot.localStart.slice(0, 5)}–{slot.localEnd.slice(0, 5)}
                  </li>)}
              </ul>
              <p className='mt-2 text-xs text-clinical-muted'>Muốn sửa vùng chọn, hủy thao tác rồi chọn lại trên lịch. Các ô đã chọn được giữ khi chuyển tuần.</p>
            </div>
            <div className='grid gap-3 sm:grid-cols-2'>
              <label className='text-xs font-semibold text-clinical-muted'>Tìm bác sĩ theo mã hoặc tên
                <input value={staffSearch} onChange={(event) => setStaffSearch(event.target.value)} placeholder='Nhập mã hoặc tên' className={field + ' mt-1'} />
              </label>
              <label className='text-xs font-semibold text-clinical-muted'>Lọc chuyên khoa
                <select value={specialtyFilter} onChange={(event) => setSpecialtyFilter(event.target.value)} className={field + ' mt-1'}>
                  <option value=''>Tất cả chuyên khoa</option>{scheduleOptions.specialties.map((specialty) => <option key={specialty.id} value={specialty.id}>{specialty.name}</option>)}
                </select>
              </label>
            </div>
            {loadingSchedule ? <p role='status' className='rounded-lg bg-clinical-canvas p-3 text-sm text-clinical-muted'>Đang tải bác sĩ…</p> : scheduleOptions.staff.length === 0 ?
              <p className='rounded-lg bg-clinical-canvas p-3 text-sm text-clinical-muted'>Khoa chưa có bác sĩ đang hoạt động để phân lịch.</p> : filteredStaff.length === 0 ?
                <p className='rounded-lg bg-clinical-canvas p-3 text-sm text-clinical-muted'>{hasAvailableDoctors ? 'Không có bác sĩ khớp bộ lọc.' : 'Các bác sĩ đã có phân công chồng giờ trong ô đã chọn.'}</p> :
                  <div className='max-h-64 overflow-auto rounded-xl border border-clinical-border'>
                    <table className='w-full min-w-[34rem] text-left text-sm'>
                      <thead className='sticky top-0 bg-suncare-50 text-xs text-clinical-muted'><tr><th scope='col' className='px-3 py-2'>Bác sĩ</th><th scope='col' className='px-3 py-2'>Chuyên khoa</th><th scope='col' className='px-3 py-2 text-right'>Chọn</th></tr></thead>
                      <tbody className='divide-y divide-clinical-border'>{filteredStaff.map((staff) => <tr key={staff.id} className={String(selectedStaffId) === String(staff.id) ? 'bg-suncare-50' : ''}>
                        <td className='px-3 py-2.5'><span className='block font-semibold text-clinical-ink'>{staff.name}</span><span className='mt-0.5 block font-mono text-xs text-clinical-muted'>{staff.code}</span></td>
                        <td className='px-3 py-2.5 text-clinical-muted'>{staff.specialties.map((item) => item.name).join(', ') || 'Chưa gán chuyên khoa'}</td>
                        <td className='px-3 py-2.5 text-right'><button type='button' disabled={busy || loadingSchedule} aria-pressed={String(selectedStaffId) === String(staff.id)} onClick={() => selectStaff(staff)} className='min-h-9 rounded-lg px-3 font-semibold text-suncare-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-suncare-700 disabled:opacity-50'>{String(selectedStaffId) === String(staff.id) ? 'Đã chọn' : 'Chọn'}</button></td>
                      </tr>)}</tbody>
                    </table>
                  </div>}

            {selectedStaff && <form onSubmit={submitAssignment} className='grid gap-3 border-t border-clinical-border pt-4 sm:grid-cols-2 xl:grid-cols-4'>
              <div className='sm:col-span-2 xl:col-span-4'>
                <h5 className='font-semibold text-clinical-ink'>Thông tin phân công</h5>
                <p className='mt-1 text-xs text-clinical-muted'>Bác sĩ: {selectedStaff.name} · {selectedStaff.code}</p>
              </div>
              <label className='text-xs font-semibold text-clinical-muted'>Chuyên khoa
                <select required value={assignmentForm.specialtyId} onChange={(event) => setAssignmentForm({ ...assignmentForm, specialtyId: event.target.value })} className={field + ' mt-1'}>
                  <option value=''>Chọn chuyên khoa</option>{eligibleSpecialties.map((specialty) => <option key={specialty.id} value={specialty.id}>{specialty.name}</option>)}
                </select>
              </label>
              <label className='text-xs font-semibold text-clinical-muted'>Phòng
                <select required value={assignmentForm.roomId} onChange={(event) => setAssignmentForm({ ...assignmentForm, roomId: event.target.value })} className={field + ' mt-1'}>
                  {eligibleRooms.length === 0
                    ? <option value=''>{roomsForSelectedMode.length === 0 ? 'Khoa chưa có phòng phù hợp' : 'Các phòng đã có bác sĩ trong khung giờ này'}</option>
                    : <><option value=''>Chọn phòng</option>{eligibleRooms.map((room) => <option key={room.id} value={room.id}>{room.code} · {room.name}</option>)}</>}
                </select>
              </label>
              {activeSlot.mode === 'ONSITE' && <label className='text-xs font-semibold text-clinical-muted'>Sức chứa
                <input required type='number' min='1' step='1' value={assignmentForm.capacity} onChange={(event) => setAssignmentForm({ ...assignmentForm, capacity: event.target.value })} className={field + ' mt-1'} />
              </label>}
              <div className='flex flex-wrap items-end gap-2'>
                <Button type='submit' variant='primary' disabled={busy || selectedWorkSlots.length === 0 || !assignmentForm.roomId || !assignmentForm.specialtyId || !eligibleSpecialties.length}>
                  <FontAwesomeIcon icon={faPlus} /> {busy ? 'Đang lưu…' : 'Lưu phân công'}
                </Button>
                <Button onClick={cancelAssignmentForm}>Hủy</Button>
              </div>
              {!eligibleSpecialties.length && <p role='alert' className='text-xs text-clinical-danger sm:col-span-2 xl:col-span-4'>Bác sĩ chưa có chuyên khoa đang hoạt động liên kết với khoa này.</p>}
            </form>}
          </div>}
        </section>}
      </section>}
    </section>
  )
}
