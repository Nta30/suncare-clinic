import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faHeartPulse,
  faStethoscope,
  faCircleCheck,
} from '@fortawesome/free-solid-svg-icons'

function App() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-suncare-50 via-white to-cyan-50 text-slate-800">
      <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-5 py-6 sm:px-8 lg:px-12">

        {/* Header */}
        <header className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-suncare-600 text-white shadow-md">
            <FontAwesomeIcon
              icon={faHeartPulse}
              className="text-2xl"
            />
          </div>

          <div>
            <p className="text-xl font-extrabold tracking-tight text-suncare-900">
              SUNCARE
            </p>
            <p className="text-xs font-semibold tracking-[0.2em] text-slate-500">
              CLINIC
            </p>
          </div>
        </header>

        {/* Main content */}
        <main className="grid flex-1 items-center gap-10 py-14 md:grid-cols-2">

          {/* Introduction */}
          <section>
            <span className="inline-flex items-center gap-2 rounded-full bg-teal-50 px-4 py-2 text-sm font-semibold text-teal-700">
              <FontAwesomeIcon icon={faCircleCheck} />
              Frontend đã sẵn sàng
            </span>

            <h1 className="mt-6 text-4xl font-extrabold leading-tight tracking-tight text-suncare-900 sm:text-5xl">
              Chào mừng đến với{' '}
              <span className="text-suncare-600">
                Suncare Clinic
              </span>
            </h1>

            <p className="mt-5 max-w-lg text-base leading-8 text-slate-600">
              Nền tảng quản lý phòng khám với giao diện hiện đại,
              thân thiện và tối ưu cho nhiều kích thước màn hình.
            </p>

            <p className="mt-5 text-sm font-medium text-suncare-teal">
              React + Vite + Tailwind CSS v4 + Font Awesome
            </p>
          </section>

          {/* Technology verification card */}
          <section className="rounded-3xl border border-slate-100 bg-white p-6 shadow-xl shadow-blue-100/60 sm:p-8">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-suncare-100 text-suncare-700">
                <FontAwesomeIcon
                  icon={faStethoscope}
                  className="text-xl"
                />
              </div>

              <div>
                <h2 className="text-xl font-bold text-suncare-900">
                  Kiểm tra thư viện
                </h2>
                <p className="text-sm text-slate-500">
                  Suncare Frontend
                </p>
              </div>
            </div>

            <div className="mt-7 space-y-4">
              <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-4">
                <FontAwesomeIcon
                  icon={faCircleCheck}
                  className="text-xl text-teal-600"
                />
                <div>
                  <p className="font-semibold">React + Vite</p>
                  <p className="text-sm text-slate-500">
                    Component React hoạt động
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-4">
                <FontAwesomeIcon
                  icon={faCircleCheck}
                  className="text-xl text-teal-600"
                />
                <div>
                  <p className="font-semibold">Tailwind CSS v4</p>
                  <p className="text-sm text-slate-500">
                    Màu sắc và responsive hoạt động
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 rounded-2xl bg-slate-50 p-4">
                <FontAwesomeIcon
                  icon={faCircleCheck}
                  className="text-xl text-teal-600"
                />
                <div>
                  <p className="font-semibold">Font Awesome</p>
                  <p className="text-sm text-slate-500">
                    Icon SVG được hiển thị
                  </p>
                </div>
              </div>
            </div>
          </section>
        </main>

        {/* Footer */}
        <footer className="border-t border-slate-200 py-5 text-center text-sm text-slate-500">
          SUNCARE CLINIC — Giao diện kiểm tra kỹ thuật
        </footer>
      </div>
    </div>
  )
}

export default App