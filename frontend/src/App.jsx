
import { useEffect, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faHeartPulse,
  faCalendarCheck,
  faStethoscope,
  faFileMedical,
  faFlaskVial,
  faWallet,
  faVideo,
  faUsers,
  faServer,
  faRotate,
  faCircleCheck,
  faCircleExclamation,
  faArrowRight,
} from '@fortawesome/free-solid-svg-icons'

import { getHealth } from './services/healthApi'

const navigation = [
  { label: 'Tổng quan', href: '#tong-quan' },
  { label: 'Kết nối API', href: '#ket-noi' },
  { label: 'Các phân hệ', href: '#phan-he' },
]

const modules = [
  {
    title: 'Đặt lịch khám',
    description: 'Quản lý lịch trực tiếp, lịch online và phân công bác sĩ.',
    icon: faCalendarCheck,
    color: 'bg-sky-50 text-sky-700',
  },
  {
    title: 'Khám và bệnh án',
    description: 'Theo dõi lần khám, chẩn đoán và hồ sơ bệnh án điện tử.',
    icon: faStethoscope,
    color: 'bg-teal-50 text-teal-700',
  },
  {
    title: 'Cận lâm sàng',
    description: 'Quản lý chỉ định, hàng đợi và kết quả xét nghiệm.',
    icon: faFlaskVial,
    color: 'bg-violet-50 text-violet-700',
  },
  {
    title: 'Thu phí và hoàn tiền',
    description: 'Quản lý hóa đơn, thanh toán MoMo và hoàn tiền.',
    icon: faWallet,
    color: 'bg-amber-50 text-amber-700',
  },
  {
    title: 'Khám trực tuyến',
    description: 'Hỗ trợ quy trình khám online và phiên video.',
    icon: faVideo,
    color: 'bg-indigo-50 text-indigo-700',
  },
  {
    title: 'Tài khoản và nhân sự',
    description: 'Quản lý bệnh nhân, nhân viên và phân quyền.',
    icon: faUsers,
    color: 'bg-rose-50 text-rose-700',
  },
]

function FlowStep({ number, icon, title, description }) {
  return (
    <div className="flex items-center gap-4 rounded-2xl border border-white/15 bg-white/10 p-4">
      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-white/15">
        <FontAwesomeIcon icon={icon} className="text-lg" />
      </div>

      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        <p className="mt-1 text-xs leading-5 text-blue-100">
          {description}
        </p>
      </div>

      <span className="text-sm font-bold text-white/50">
        {number}
      </span>
    </div>
  )
}

function App() {
  const [refreshIndex, setRefreshIndex] = useState(0)

  const [health, setHealth] = useState({
    state: 'loading',
    data: null,
    error: '',
    checkedAt: null,
  })

  useEffect(() => {
    const controller = new AbortController()

    async function checkBackend() {
      try {
        const data = await getHealth(controller.signal)

        if (!controller.signal.aborted) {
          setHealth({
            state: 'online',
            data,
            error: '',
            checkedAt: new Date().toLocaleTimeString('vi-VN'),
          })
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setHealth({
            state: 'offline',
            data: null,
            error:
              error instanceof Error
                ? error.message
                : 'Không thể kiểm tra backend.',
            checkedAt: new Date().toLocaleTimeString('vi-VN'),
          })
        }
      }
    }

    checkBackend()

    return () => {
      controller.abort()
    }
  }, [refreshIndex])

  function handleRefresh() {
    setHealth({
      state: 'loading',
      data: null,
      error: '',
      checkedAt: null,
    })

    setRefreshIndex((value) => value + 1)
  }

  const isLoading = health.state === 'loading'
  const isOnline = health.state === 'online'
  const isOffline = health.state === 'offline'

  const statusTitle = isLoading
    ? 'Đang kiểm tra kết nối'
    : isOnline
      ? 'Đã kết nối Spring Boot'
      : 'Không kết nối tới backend'

  const statusDescription = isLoading
    ? 'Đang gửi yêu cầu tới Health API...'
    : isOnline
      ? 'API trả về HTTP 200 và JSON hợp lệ.'
      : 'Chưa nhận được phản hồi Health API hợp lệ.'

  const statusIcon = isLoading
    ? faRotate
    : isOnline
      ? faCircleCheck
      : faCircleExclamation

  const statusColor = isLoading
    ? 'border-sky-200 bg-sky-50 text-sky-700'
    : isOnline
      ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
      : 'border-rose-200 bg-rose-50 text-rose-700'

  return (
    <div className="min-h-screen bg-[#f4f8fc] text-slate-800">
      <div className="mx-auto flex min-h-screen max-w-[1600px]">

        {/* Desktop sidebar */}
        <aside className="hidden w-64 shrink-0 flex-col bg-suncare-900 px-6 py-8 text-white lg:flex">

          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/15">
              <FontAwesomeIcon
                icon={faHeartPulse}
                className="text-2xl text-white"
              />
            </div>

            <div>
              <p className="text-xl font-extrabold tracking-wide">
                SUNCARE
              </p>
              <p className="text-xs tracking-[0.25em] text-blue-200">
                CLINIC
              </p>
            </div>
          </div>

          <div className="mt-12">
            <p className="mb-4 px-4 text-xs font-semibold uppercase tracking-widest text-blue-200">
              Điều hướng
            </p>

            <nav className="space-y-2" aria-label="Điều hướng chính">
              {navigation.map((item) => (
                <a
                  key={item.href}
                  href={item.href}
                  className="flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium text-blue-100 transition hover:bg-white/10 hover:text-white"
                >
                  <span className="h-2 w-2 rounded-full bg-teal-300" />
                  {item.label}
                </a>
              ))}
            </nav>
          </div>

          <div className="mt-auto rounded-2xl border border-white/15 bg-white/10 p-5">
            <FontAwesomeIcon
              icon={faHeartPulse}
              className="text-2xl text-teal-200"
            />

            <p className="mt-4 text-sm font-bold">
              Suncare Clinic
            </p>

            <p className="mt-2 text-xs leading-6 text-blue-100">
              Nền tảng quản lý phòng khám đang trong giai đoạn khởi tạo.
            </p>

            <p className="mt-4 text-xs font-semibold text-teal-200">
              Giai đoạn: Thiết lập hệ thống
            </p>
          </div>
        </aside>

        {/* Main application */}
        <div className="min-w-0 flex-1">

          {/* Header */}
          <header className="border-b border-slate-200 bg-white px-5 py-5 sm:px-8 lg:px-10">

            <div className="flex items-center justify-between gap-4">

              <div className="flex items-center gap-3 lg:hidden">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-suncare-600 text-white">
                  <FontAwesomeIcon icon={faHeartPulse} />
                </div>

                <div>
                  <p className="font-extrabold tracking-wide text-suncare-900">
                    SUNCARE
                  </p>
                  <p className="text-xs tracking-widest text-slate-500">
                    CLINIC
                  </p>
                </div>
              </div>

              <div className="hidden lg:block">
                <p className="text-xs font-semibold uppercase tracking-widest text-slate-400">
                  Suncare Clinic
                </p>
                <h2 className="mt-1 text-lg font-bold text-suncare-900">
                  Không gian quản lý phòng khám
                </h2>
              </div>

              <span className="rounded-full border border-sky-100 bg-sky-50 px-4 py-2 text-xs font-semibold text-suncare-700">
                Bản giao diện khởi tạo
              </span>
            </div>

            {/* Mobile navigation */}
            <nav
              className="mt-5 flex gap-5 overflow-x-auto text-sm font-semibold text-slate-600 lg:hidden"
              aria-label="Điều hướng di động"
            >
              {navigation.map((item) => (
                <a
                  key={item.href}
                  href={item.href}
                  className="shrink-0 pb-1 transition hover:text-suncare-600"
                >
                  {item.label}
                </a>
              ))}
            </nav>
          </header>

          <main className="space-y-9 px-5 py-7 sm:px-8 lg:px-10 lg:py-10">

            {/* Hero */}
            <section
              id="tong-quan"
              className="relative scroll-mt-6 overflow-hidden rounded-3xl bg-gradient-to-br from-suncare-900 via-suncare-700 to-suncare-600 px-6 py-10 text-white shadow-xl shadow-blue-900/10 sm:px-10 lg:px-12"
            >
              <div className="pointer-events-none absolute -right-24 -top-32 h-96 w-96 rounded-full border border-white/20" />
              <div className="pointer-events-none absolute -bottom-40 right-20 h-80 w-80 rounded-full bg-cyan-300/10 blur-3xl" />

              <div className="relative grid items-center gap-10 xl:grid-cols-2">

                <div>
                  <span className="inline-flex items-center rounded-full border border-white/25 bg-white/10 px-4 py-2 text-xs font-semibold tracking-wide text-blue-50">
                    SUNCARE — CLINIC MANAGEMENT
                  </span>

                  <h1 className="mt-6 text-3xl font-extrabold leading-tight tracking-tight sm:text-4xl lg:text-5xl">
                    Chăm sóc tốt hơn.
                    <span className="mt-2 block text-cyan-200">
                      Vận hành rõ ràng hơn.
                    </span>
                  </h1>

                  <p className="mt-6 max-w-xl text-sm leading-7 text-blue-100 sm:text-base">
                    Suncare Clinic hướng tới việc kết nối quy trình
                    đặt khám, khám bệnh, cận lâm sàng và thanh toán
                    trong một nền tảng thống nhất.
                  </p>

                  <a
                    href="#ket-noi"
                    className="mt-8 inline-flex items-center gap-3 rounded-xl bg-white px-6 py-3 font-bold text-suncare-900 shadow-lg transition hover:bg-cyan-50"
                  >
                    Xem trạng thái kết nối

                    <FontAwesomeIcon icon={faArrowRight} />
                  </a>
                </div>

                {/* Workflow illustration */}
                <div className="rounded-3xl border border-white/20 bg-white/10 p-5 backdrop-blur-md sm:p-6">

                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-widest text-blue-200">
                        Tổng quan
                      </p>
                      <h2 className="mt-1 text-lg font-bold">
                        Quy trình khám dự kiến
                      </h2>
                    </div>

                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15">
                      <FontAwesomeIcon icon={faHeartPulse} />
                    </div>
                  </div>

                  <div className="mt-6 space-y-3">
                    <FlowStep
                      number="01"
                      icon={faCalendarCheck}
                      title="Đặt lịch khám"
                      description="Lựa chọn hình thức và lịch khám."
                    />

                    <FlowStep
                      number="02"
                      icon={faStethoscope}
                      title="Thăm khám"
                      description="Bác sĩ tiếp nhận và đánh giá."
                    />

                    <FlowStep
                      number="03"
                      icon={faFileMedical}
                      title="Hồ sơ và kết quả"
                      description="Theo dõi kết quả, đơn thuốc khi có."
                    />
                  </div>

                  <p className="mt-5 text-xs text-blue-100">
                    Minh họa giao diện — chưa phát sinh dữ liệu khám.
                  </p>
                </div>
              </div>
            </section>

            {/* Health API status */}
            <section id="ket-noi" className="scroll-mt-6">

              <div className="mb-5">
                <p className="text-xs font-bold uppercase tracking-widest text-suncare-teal">
                  Kiểm tra kỹ thuật
                </p>

                <h2 className="mt-2 text-2xl font-extrabold text-suncare-900">
                  Trạng thái kết nối hệ thống
                </h2>

                <p className="mt-2 text-sm leading-6 text-slate-500">
                  Kiểm tra kết nối thực tế giữa React và Spring Boot.
                </p>
              </div>

              <div className="grid gap-5 xl:grid-cols-[1.4fr_1fr]">

                <article className="rounded-3xl border border-slate-100 bg-white p-6 shadow-sm sm:p-8">

                  <div className="flex items-center gap-4">
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-sky-50 text-suncare-700">
                      <FontAwesomeIcon
                        icon={faServer}
                        className="text-xl"
                      />
                    </div>

                    <div>
                      <h3 className="text-lg font-bold text-suncare-900">
                        Suncare Backend
                      </h3>
                      <p className="text-sm text-slate-500">
                        Spring Boot REST API
                      </p>
                    </div>
                  </div>

                  <div
                    role="status"
                    aria-live="polite"
                    className={`mt-7 rounded-2xl border p-5 ${statusColor}`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-5">

                      <div className="flex items-center gap-3">
                        <FontAwesomeIcon
                          icon={statusIcon}
                          className={`text-2xl ${isLoading ? 'animate-spin' : ''}`}
                        />

                        <div>
                          <p className="font-bold">
                            {statusTitle}
                          </p>

                          <p className="mt-1 text-sm">
                            {statusDescription}
                          </p>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={handleRefresh}
                        disabled={isLoading}
                        className="inline-flex items-center gap-2 rounded-xl border border-current/20 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <FontAwesomeIcon icon={faRotate} />
                        Kiểm tra lại
                      </button>
                    </div>
                  </div>

                  {isOffline && (
                    <div className="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
                      <p className="font-semibold">
                        Không thể xác nhận kết nối.
                      </p>
                      <p className="mt-1 break-words">
                        {health.error}
                      </p>
                      <p className="mt-2">
                        Hãy kiểm tra Spring Boot đang chạy trên cổng 8080.
                      </p>
                    </div>
                  )}

                  <p className="mt-5 text-xs leading-5 text-slate-500">
                    Trạng thái được kiểm tra khi mở trang hoặc nhấn
                    nút Kiểm tra lại. Đây không phải hệ thống giám
                    sát liên tục.
                  </p>
                </article>

                {/* API details */}
                <article className="rounded-3xl border border-slate-100 bg-white p-6 shadow-sm sm:p-8">

                  <h3 className="text-lg font-bold text-suncare-900">
                    Thông tin API
                  </h3>

                  <p className="mt-1 text-sm text-slate-500">
                    Dữ liệu từ yêu cầu kiểm tra gần nhất.
                  </p>

                  <dl className="mt-6 space-y-4">
                    <div className="border-b border-slate-100 pb-3">
                      <dt className="text-xs font-semibold text-slate-400">
                        Endpoint
                      </dt>
                      <dd className="mt-1 break-all font-mono text-sm font-semibold text-slate-700">
                        GET /api/health
                      </dd>
                    </div>

                    <div className="border-b border-slate-100 pb-3">
                      <dt className="text-xs font-semibold text-slate-400">
                        Backend service
                      </dt>
                      <dd className="mt-1 font-semibold text-slate-800">
                        {health.data?.service ?? 'Chưa xác nhận'}
                      </dd>
                    </div>

                    <div className="border-b border-slate-100 pb-3">
                      <dt className="text-xs font-semibold text-slate-400">
                        Trạng thái phản hồi
                      </dt>
                      <dd className="mt-1 font-bold text-slate-800">
                        {health.data?.status ?? '—'}
                      </dd>
                    </div>

                    <div>
                      <dt className="text-xs font-semibold text-slate-400">
                        Kiểm tra lúc
                      </dt>
                      <dd className="mt-1 font-semibold text-slate-800">
                        {health.checkedAt ?? '—'}
                      </dd>
                    </div>
                  </dl>
                </article>
              </div>
            </section>

            {/* Planned modules */}
            <section id="phan-he" className="scroll-mt-6">

              <div className="mb-5">
                <p className="text-xs font-bold uppercase tracking-widest text-suncare-teal">
                  Nền tảng Suncare
                </p>

                <h2 className="mt-2 text-2xl font-extrabold text-suncare-900">
                  Các phân hệ dự kiến
                </h2>

                <p className="mt-2 text-sm leading-6 text-slate-500">
                  Các nhóm chức năng sẽ được triển khai trong những
                  giai đoạn tiếp theo.
                </p>
              </div>

              <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">

                {modules.map((module) => (
                  <article
                    key={module.title}
                    className="rounded-3xl border border-slate-100 bg-white p-6 shadow-sm transition hover:border-sky-100 hover:shadow-md"
                  >
                    <div className={`flex h-14 w-14 items-center justify-center rounded-2xl ${module.color}`}>
                      <FontAwesomeIcon
                        icon={module.icon}
                        className="text-2xl"
                      />
                    </div>

                    <h3 className="mt-5 text-lg font-bold text-suncare-900">
                      {module.title}
                    </h3>

                    <p className="mt-2 min-h-14 text-sm leading-6 text-slate-500">
                      {module.description}
                    </p>

                    <div className="mt-5 border-t border-slate-100 pt-4">
                      <span className="text-xs font-semibold text-slate-400">
                        Chưa triển khai
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          </main>

          <footer className="mx-5 border-t border-slate-200 py-6 text-center text-xs leading-6 text-slate-500 sm:mx-8 lg:mx-10">
            © Suncare Clinic — Giao diện khởi tạo kỹ thuật.
            Chưa sử dụng dữ liệu bệnh nhân thực.
          </footer>
        </div>
      </div>
    </div>
  )
}

export default App
