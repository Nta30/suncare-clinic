import { useEffect, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faArrowLeft, faCalendarCheck, faChevronRight, faRotateRight,
} from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import { getToken } from '../../services/accountApi'
import {
  getDoctorAppointment, getDoctorAppointments, getPatientAppointment, getPatientAppointments,
} from '../../services/appointmentScheduleApi'

const appointmentStatusLabels = {
  BOOKED: 'Đã đặt',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Vắng mặt',
}

const encounterStatusLabels = {
  PLANNED: 'Chưa bắt đầu khám',
  IN_PROGRESS: 'Đang khám',
  WAITING_CLS: 'Đang chờ cận lâm sàng',
  COMPLETED: 'Đã hoàn tất lượt khám',
  CANCELLED: 'Lượt khám đã hủy',
  NO_SHOW: 'Không đến khám',
}

function formatBusinessDate(value) {
  if (!value) return 'Thời gian chưa được cập nhật'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Thời gian chưa được cập nhật'
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Saigon',
  }).format(date)
}

function formatBusinessTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Saigon',
  }).format(date)
}

function formatVnd(amount) {
  if (amount == null) return 'Chưa có thông tin phí khám'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency', currency: 'VND', maximumFractionDigits: 0,
  }).format(BigInt(amount))
}

function appointmentStatusTone(status) {
  if (status === 'COMPLETED') return 'border-clinical-success/30 bg-emerald-50 text-clinical-success'
  if (status === 'CANCELLED' || status === 'NO_SHOW') return 'border-clinical-danger/30 bg-rose-50 text-clinical-danger'
  return 'border-suncare-200 bg-suncare-50 text-suncare-800'
}

function AppointmentListItem({ appointment, isDoctor, onOpen }) {
  return (
    <button
      type='button'
      onClick={() => onOpen(appointment.appointmentId)}
      aria-label={`Xem chi tiết lịch ${appointment.appointmentCode}`}
      className='w-full rounded-2xl border border-clinical-border bg-white p-4 text-left shadow-sm transition hover:border-suncare-500 hover:bg-suncare-50/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 sm:p-5'
    >
      <span className='flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between'>
        <span className='min-w-0'>
          <span className='block text-xs font-semibold uppercase tracking-wide text-clinical-muted'>Mã lịch khám</span>
          <span className='mt-1 block break-all text-base font-bold text-suncare-900'>{appointment.appointmentCode}</span>
        </span>
        <span className='flex flex-wrap items-center gap-2'>
          <span className={`inline-flex min-h-8 items-center rounded-full border px-3 py-1 text-xs font-semibold ${appointmentStatusTone(appointment.appointmentStatus)}`}>
            {appointmentStatusLabels[appointment.appointmentStatus] || appointment.appointmentStatus}
          </span>
          <span className='inline-flex min-h-8 items-center rounded-full border border-clinical-border bg-clinical-canvas px-3 py-1 text-xs font-semibold text-clinical-ink'>
            {appointment.visitMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}
          </span>
        </span>
      </span>

      <span className='mt-4 flex flex-col gap-3 border-t border-clinical-border pt-4 sm:flex-row sm:items-end sm:justify-between'>
        <span className='min-w-0'>
          <span className='block break-words text-lg font-bold text-clinical-ink'>Khám {appointment.specialtyName}</span>
          <span className='mt-2 block text-sm font-semibold capitalize text-clinical-ink'>
            {formatBusinessDate(appointment.startsAt)}
            {formatBusinessTime(appointment.startsAt) && ` · ${formatBusinessTime(appointment.startsAt)}`}
          </span>
          <span className='mt-1 block text-sm text-clinical-muted'>
            {isDoctor ? `Người tới khám: ${appointment.patientName}` : `Bác sĩ: ${appointment.doctorName}`}
          </span>
        </span>
        <span className='inline-flex shrink-0 items-center gap-2 text-sm font-semibold text-suncare-700'>
          Xem chi tiết <FontAwesomeIcon icon={faChevronRight} aria-hidden='true' />
        </span>
      </span>
    </button>
  )
}

function DetailRow({ label, children }) {
  return (
    <div className='grid gap-1 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-4'>
      <dt className='font-semibold text-clinical-ink'>{label}</dt>
      <dd className='min-w-0 break-words text-clinical-ink'>{children}</dd>
    </div>
  )
}

function AppointmentDetail({ appointment, isDoctor, onBack }) {
  const startTime = formatBusinessTime(appointment.startsAt)
  const endTime = formatBusinessTime(appointment.endsAt)
  const location = [appointment.roomCode, appointment.roomName].filter(Boolean).join(' · ')

  return (
    <section className='w-full space-y-4' aria-labelledby='appointment-detail-title'>
      <div className='flex flex-col gap-3 rounded-2xl border border-clinical-border bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5'>
        <Button onClick={onBack}>
          <FontAwesomeIcon icon={faArrowLeft} aria-hidden='true' />
          Quay lại danh sách
        </Button>
        <div className='flex flex-wrap items-center gap-x-3 gap-y-2 sm:justify-end'>
          <span className='text-sm text-clinical-ink'>Mã lịch khám: <strong>{appointment.appointmentCode}</strong></span>
          <span className={`inline-flex min-h-8 items-center rounded-full border px-3 py-1 text-xs font-semibold ${appointmentStatusTone(appointment.appointmentStatus)}`}>
            {appointmentStatusLabels[appointment.appointmentStatus] || appointment.appointmentStatus}
          </span>
        </div>
      </div>

      <div className='overflow-hidden rounded-2xl border border-clinical-border bg-white'>
        <div className='px-4 py-5 sm:px-6 sm:py-6'>
          <h1 id='appointment-detail-title' className='text-2xl font-bold text-suncare-800 sm:text-3xl'>
            Khám {appointment.specialtyName} [{appointment.departmentName}]
          </h1>
          <dl className='mt-5 space-y-4 text-sm sm:text-base'>
            <DetailRow label='Hình thức khám'>{appointment.visitMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</DetailRow>
            <DetailRow label='Người tới khám'>
              {appointment.patientName}{appointment.patientCode ? ` · ${appointment.patientCode}` : ''}
            </DetailRow>
            <DetailRow label='Bác sĩ'>{appointment.doctorName}{appointment.doctorCode ? ` · ${appointment.doctorCode}` : ''}</DetailRow>
            <DetailRow label='Giờ hẹn'>
              {formatBusinessDate(appointment.startsAt)}{startTime ? ` · ${startTime}` : ''}{endTime && endTime !== startTime ? ` – ${endTime}` : ''}
            </DetailRow>
            <DetailRow label='Địa điểm'>
              {location || 'Chưa có thông tin phòng'}{appointment.departmentName ? ` · ${appointment.departmentName}` : ''}
            </DetailRow>
            <DetailRow label='Triệu chứng'>
              {appointment.symptoms?.trim() || 'Chưa khai báo triệu chứng'}
            </DetailRow>
            {appointment.encounterStatus && <DetailRow label='Trạng thái lượt khám'>
              {encounterStatusLabels[appointment.encounterStatus] || appointment.encounterStatus}
            </DetailRow>}
          </dl>
          {isDoctor && <p className='mt-5 border-t border-clinical-border pt-4 text-sm text-clinical-muted'>Thông tin này chỉ dành cho bác sĩ được phân công.</p>}
        </div>

        <div className='border-t border-clinical-border bg-clinical-canvas/50 px-4 py-4 sm:px-6'>
          <div className='flex flex-wrap items-start justify-between gap-3 border-b border-clinical-border pb-3 text-sm'>
            <span>Khám {appointment.specialtyName}</span>
            <strong>{formatVnd(appointment.examinationAmount)}</strong>
          </div>
          <div className='ml-auto flex max-w-xl flex-wrap items-center justify-between gap-3 pt-4'>
            <span className='font-semibold text-clinical-muted'>Phí khám đã chốt</span>
            <strong className='text-xl font-bold text-suncare-800'>{formatVnd(appointment.examinationAmount)}</strong>
          </div>
        </div>
      </div>
    </section>
  )
}

export default function AppointmentSchedule({ account, onSessionExpired }) {
  const isDoctor = account.roles.includes('DOCTOR')
  const [appointments, setAppointments] = useState([])
  const [viewState, setViewState] = useState('loading')
  const [errorMessage, setErrorMessage] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedAppointmentId, setSelectedAppointmentId] = useState(null)
  const [appointmentDetail, setAppointmentDetail] = useState(null)
  const [detailState, setDetailState] = useState('idle')
  const [detailError, setDetailError] = useState('')
  const [detailReloadKey, setDetailReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    const loadAppointments = async () => {
      try {
        const token = getToken()
        if (!token) {
          onSessionExpired()
          return
        }
        const result = isDoctor
          ? await getDoctorAppointments(token)
          : await getPatientAppointments(token)
        if (!active) return
        setAppointments(result)
        setViewState('success')
      } catch (error) {
        if (!active) return
        if (error.status === 401) {
          onSessionExpired(error.message)
          return
        }
        setErrorMessage(error.message)
        setViewState('error')
      }
    }

    void loadAppointments()
    return () => { active = false }
  }, [account.accountId, isDoctor, onSessionExpired, reloadKey])

  useEffect(() => {
    if (selectedAppointmentId == null) return undefined
    let active = true
    const loadAppointmentDetail = async () => {
      try {
        const token = getToken()
        if (!token) {
          onSessionExpired()
          return
        }
        const result = isDoctor
          ? await getDoctorAppointment(token, selectedAppointmentId)
          : await getPatientAppointment(token, selectedAppointmentId)
        if (!active) return
        setAppointmentDetail(result)
        setDetailState('success')
      } catch (error) {
        if (!active) return
        if (error.status === 401) {
          onSessionExpired(error.message)
          return
        }
        setDetailError(error.message)
        setDetailState('error')
      }
    }

    void loadAppointmentDetail()
    return () => { active = false }
  }, [account.accountId, detailReloadKey, isDoctor, onSessionExpired, selectedAppointmentId])

  function reloadAppointments() {
    setErrorMessage('')
    setViewState('loading')
    setReloadKey((current) => current + 1)
  }

  function openAppointment(appointmentId) {
    setAppointmentDetail(null)
    setDetailError('')
    setDetailState('loading')
    setSelectedAppointmentId(appointmentId)
  }

  function closeAppointment() {
    setSelectedAppointmentId(null)
    setAppointmentDetail(null)
    setDetailError('')
    setDetailState('idle')
  }

  function reloadAppointmentDetail() {
    setAppointmentDetail(null)
    setDetailError('')
    setDetailState('loading')
    setDetailReloadKey((current) => current + 1)
  }

  if (selectedAppointmentId != null) {
    return (
      <div className='w-full'>
        {detailState === 'loading' && <div className='space-y-4'>
          <Button onClick={closeAppointment}><FontAwesomeIcon icon={faArrowLeft} aria-hidden='true' />Quay lại danh sách</Button>
          <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm text-clinical-muted'>Đang tải chi tiết lịch khám...</p>
        </div>}
        {detailState === 'error' && <div className='space-y-4 rounded-xl border border-clinical-danger/40 bg-white p-5'>
          <p role='alert' className='text-sm font-semibold text-clinical-danger'>Không tải được chi tiết lịch khám</p>
          <p className='text-sm text-clinical-muted'>{detailError}</p>
          <div className='flex flex-wrap gap-3'>
            <Button onClick={closeAppointment}><FontAwesomeIcon icon={faArrowLeft} aria-hidden='true' />Quay lại danh sách</Button>
            <Button onClick={reloadAppointmentDetail}><FontAwesomeIcon icon={faRotateRight} aria-hidden='true' />Thử lại</Button>
          </div>
        </div>}
        {detailState === 'success' && appointmentDetail && <AppointmentDetail
          appointment={appointmentDetail}
          isDoctor={isDoctor}
          onBack={closeAppointment}
        />}
      </div>
    )
  }

  return (
    <section className='w-full space-y-5' aria-labelledby='appointment-schedule-title'>
      <header className='flex flex-col gap-4 border-b border-clinical-border pb-5 sm:flex-row sm:items-end sm:justify-between'>
        <div>
          <p className='text-sm font-semibold text-suncare-700'>Lịch khám · thông tin chỉ xem</p>
          <h1 id='appointment-schedule-title' className='mt-1 text-2xl font-bold text-suncare-900 sm:text-3xl'>
            {isDoctor ? 'Lịch khám được giao' : 'Lịch hẹn của tôi'}
          </h1>
          <p className='mt-2 max-w-3xl text-sm leading-6 text-clinical-muted'>
            {isDoctor
              ? 'Chọn một lịch để xem thông tin chi tiết của bệnh nhân được phân công.'
              : 'Chọn một lịch để xem đầy đủ thông tin buổi khám của bạn.'}
          </p>
        </div>
        <Button onClick={reloadAppointments} disabled={viewState === 'loading'}>
          <FontAwesomeIcon icon={faRotateRight} className={viewState === 'loading' ? 'animate-spin' : ''} aria-hidden='true' />
          Tải lại lịch
        </Button>
      </header>

      {viewState === 'success' && <p className='text-sm text-clinical-muted' aria-live='polite'>
        {appointments.length} lịch hẹn
      </p>}

      {viewState === 'loading' && <p role='status' className='rounded-xl border border-clinical-border bg-white p-5 text-sm text-clinical-muted'>Đang tải lịch hẹn...</p>}
      {viewState === 'error' && <div className='rounded-xl border border-clinical-danger/40 bg-white p-5'>
        <p role='alert' className='text-sm font-semibold text-clinical-danger'>Không tải được lịch hẹn</p>
        <p className='mt-1 text-sm text-clinical-muted'>{errorMessage}</p>
        <Button className='mt-4' onClick={reloadAppointments}>Thử lại</Button>
      </div>}
      {viewState === 'success' && appointments.length === 0 && <div className='rounded-xl border border-dashed border-clinical-border-strong bg-white p-7 text-center'>
        <FontAwesomeIcon icon={faCalendarCheck} className='text-2xl text-suncare-700' aria-hidden='true' />
        <p className='mt-3 font-semibold'>Chưa có lịch hẹn</p>
        <p className='mt-1 text-sm text-clinical-muted'>{isDoctor ? 'Hiện chưa có lịch nào được giao cho bạn.' : 'Các lịch hẹn của bạn sẽ hiển thị tại đây sau khi được tạo.'}</p>
      </div>}
      {viewState === 'success' && appointments.length > 0 && <div className='grid gap-3'>
        {appointments.map((appointment) => <AppointmentListItem
          key={appointment.appointmentId}
          appointment={appointment}
          isDoctor={isDoctor}
          onOpen={openAppointment}
        />)}
      </div>}
    </section>
  )
}
