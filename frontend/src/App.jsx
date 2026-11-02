import { useCallback, useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faHeartPulse, faCalendarCheck, faClipboard, faMoneyBill, faGear, faRightFromBracket } from '@fortawesome/free-solid-svg-icons'
import AccountForm from './features/account/AccountForm'
import PersonalProfile from './features/account/PersonalProfile'
import StaffManagement from './features/account/StaffManagement'
import OrganizationManagement from './features/organization/OrganizationManagement'
import RoomManagement from './features/organization/RoomManagement'
import StaffAssignments from './features/organization/StaffAssignments'
import ClinicalServiceManagement from './features/clinical/ClinicalServiceManagement'
import SchedulingCatalogManagement from './features/scheduling/SchedulingCatalogManagement'
import clinicReception from './assets/clinic-reception-illustration.jpg'
import { clearToken, getCurrentAccount, getToken, login, logout, registerPatient, saveToken } from './services/accountApi'

const roleLabels = {
  PATIENT: 'Bệnh nhân', DOCTOR: 'Bác sĩ', LAB_STAFF: 'Nhân viên cận lâm sàng',
  DEPARTMENT_HEAD: 'Trưởng khoa', CASHIER: 'Thu ngân', SYSTEM_ADMIN: 'Quản trị viên',
}

const plannedLinks = [
  { title: 'Lịch khám', icon: faCalendarCheck, roles: ['PATIENT', 'DOCTOR', 'DEPARTMENT_HEAD'] },
  { title: 'Khám và kết quả', icon: faClipboard, roles: ['DOCTOR', 'LAB_STAFF'] },
  { title: 'Thu phí', icon: faMoneyBill, roles: ['CASHIER'] },
  { title: 'Quản trị', icon: faGear, roles: ['SYSTEM_ADMIN'] },
]

function App() {
  const [mode, setMode] = useState('home')
  const [account, setAccount] = useState(null)
  const [authState, setAuthState] = useState(getToken() ? 'loading' : 'guest')
  const [authError, setAuthError] = useState('')
  const [logoutBusy, setLogoutBusy] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [authClosing, setAuthClosing] = useState(false)
  const logoutDialogRef = useRef(null)
  const authExitTimerRef = useRef(null)
  const dialogExitTimerRef = useRef(null)

  useEffect(() => () => {
    window.clearTimeout(authExitTimerRef.current)
    window.clearTimeout(dialogExitTimerRef.current)
  }, [])

  useEffect(() => {
    const token = getToken()
    if (!token) return
    let active = true
    getCurrentAccount(token).then((current) => {
      if (active) { setAccount(current); setAuthState('signedIn') }
    }).catch((error) => {
      if (!active) return
      if (error.status === 401) { clearToken(); setAuthState('guest') }
      else { setAuthState('error'); setAuthError(error.message) }
    })
    return () => { active = false }
  }, [])

  async function handleLogin(credentials) {
    const result = await login(credentials)
    const current = await getCurrentAccount(result.accessToken)
    saveToken(result.accessToken)
    setAuthClosing(true)
    await new Promise((resolve) => window.setTimeout(resolve, 180))
    setAccount(current)
    setAuthState('signedIn')
    setAuthError('')
    setAuthClosing(false)
  }

  function changeMode(nextMode) {
    window.clearTimeout(authExitTimerRef.current)
    if (authState === 'guest' && (mode === 'login' || mode === 'register') && nextMode === 'home') {
      setAuthClosing(true)
      authExitTimerRef.current = window.setTimeout(() => {
        setMode('home')
        setAuthClosing(false)
      }, 180)
      return
    }
    setAuthClosing(false)
    setMode(nextMode)
  }

  async function handleLogout() {
    setLogoutBusy(true)
    setLogoutError('')
    try {
      await logout(getToken())
      clearToken()
      setAccount(null)
      setAuthState('guest')
      setMode('home')
      closeLogoutDialog()
    } catch (error) { setLogoutError(error.message) }
    finally { setLogoutBusy(false) }
  }

  function openLogoutDialog() {
    setLogoutError('')
    logoutDialogRef.current?.classList.remove('logout-dialog--closing')
    logoutDialogRef.current?.showModal()
  }

  function closeLogoutDialog() {
    const dialog = logoutDialogRef.current
    if (!dialog?.open) return
    dialog.classList.add('logout-dialog--closing')
    window.clearTimeout(dialogExitTimerRef.current)
    dialogExitTimerRef.current = window.setTimeout(() => {
      dialog.close()
      dialog.classList.remove('logout-dialog--closing')
    }, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180)
  }

  async function refreshAccount() {
    setAuthError('')
    try {
      setAccount(await getCurrentAccount(getToken()))
    } catch (error) {
      if (error.status === 401) {
        clearToken()
        setAccount(null)
        setAuthState('guest')
        setMode('home')
      }
      setAuthError(error.message)
    }
  }

  const handleSessionExpired = useCallback(() => {
    clearToken()
    setAccount(null)
    setAuthState('guest')
    setMode('login')
  }, [])

  const availableLinks = plannedLinks.filter((link) => account?.roles.some((role) => link.roles.includes(role)))

  return (
    <div className="flex min-h-svh flex-col bg-clinical-canvas text-clinical-ink">
      <header className="shrink-0 border-b border-suncare-200 bg-suncare-50">
        <div className="flex flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-8 lg:px-14">
          <div className="flex items-center gap-3 text-suncare-900">
            <span className="grid h-11 w-11 place-items-center rounded-xl bg-suncare-400 text-clinical-ink"><FontAwesomeIcon icon={faHeartPulse} /></span>
            <span className="text-lg font-extrabold tracking-wide">SUNCARE <span className="text-clinical-leaf">CLINIC</span></span>
          </div>
          <nav className="flex w-full flex-wrap items-center gap-1 text-sm font-semibold sm:w-auto sm:gap-3" aria-label="Điều hướng tài khoản">
            <button onClick={() => changeMode('home')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Giới thiệu</button>
            {authState === 'signedIn' ? <>
              <button onClick={() => setMode('account')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Tài khoản</button>
              {account?.permissions.includes('ACCOUNT_MANAGE') &&
                <button onClick={() => setMode('staff')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Quản lý nhân viên</button>}
              {account?.permissions.includes('CATALOG_MANAGE') &&
                <button onClick={() => setMode('organization')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Khoa và chuyên khoa</button>}
              {account?.permissions.includes('CATALOG_MANAGE') &&
                <button onClick={() => setMode('rooms')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Phòng</button>}
              {account?.permissions.includes('CATALOG_MANAGE') &&
                <button onClick={() => setMode('services')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Dịch vụ & giá CLS</button>}
              {account?.permissions.includes('CATALOG_MANAGE') &&
                <button onClick={() => setMode('scheduling-catalog')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Ca & giá khám</button>}
              {account?.permissions.includes('ACCOUNT_MANAGE') &&
                <button onClick={() => setMode('assignments')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Phân công</button>}
              <button onClick={openLogoutDialog} className="rounded-lg bg-suncare-100 px-3 py-2 hover:bg-suncare-200">Đăng xuất</button>
            </> : <>
              <button onClick={() => changeMode('login')} className="rounded-lg px-3 py-2 hover:bg-suncare-100">Đăng nhập</button>
              <button onClick={() => changeMode('register')} className="rounded-lg bg-suncare-400 px-4 py-2 text-clinical-ink hover:bg-suncare-500">Đăng ký</button>
            </>}
          </nav>
        </div>
      </header>

      <main className={authState === 'guest' ? 'flex w-full flex-1 flex-col' : 'mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 sm:py-10'}>
        {authError && <div role="alert" className="mb-6 rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-700">{authError}</div>}
        {authState === 'loading' && <p role="status" className="rounded-2xl bg-white p-6">Đang kiểm tra phiên đăng nhập...</p>}
        {authState === 'error' && <button onClick={() => window.location.reload()} className="rounded-xl bg-suncare-700 px-5 py-3 text-white">Thử kết nối lại</button>}
        {authState === 'guest' && (mode === 'login' || mode === 'register') &&
          <AccountForm mode={mode} closing={authClosing} onModeChange={changeMode} onLogin={handleLogin} onRegister={registerPatient} />}
        {authState === 'guest' && mode === 'home' && <>
          <section className="bg-suncare-100 md:grid md:min-h-[34rem] md:grid-cols-[0.92fr_1.08fr]">
            <div className="flex flex-col justify-center px-5 py-12 sm:px-10 lg:px-14 lg:py-20">
              <p className="font-semibold text-suncare-700">Suncare Clinic · Chăm sóc ngoại trú</p>
              <h1 className="mt-4 max-w-2xl text-4xl font-bold leading-tight sm:text-5xl lg:text-6xl">Chăm sóc sức khỏe bắt đầu từ sự an tâm.</h1>
              <p className="mt-6 max-w-xl text-base leading-8 text-clinical-muted">Suncare xây dựng hành trình khám ngoại trú tại một cơ sở: từ đăng ký tài khoản, đặt lịch, thăm khám đến theo dõi kết quả. Bạn có thể bắt đầu bằng tài khoản bệnh nhân.</p>
              <div className="mt-8 flex flex-wrap gap-3">
                <button onClick={() => changeMode('register')} className="rounded-xl bg-suncare-400 px-5 py-3 font-semibold text-clinical-ink hover:bg-suncare-500">Đăng ký tài khoản</button>
                <button onClick={() => changeMode('login')} className="rounded-xl border border-suncare-700 px-5 py-3 font-semibold text-suncare-700 hover:bg-suncare-200">Đăng nhập</button>
              </div>
            </div>
            <div className="relative min-h-72 sm:min-h-96 md:min-h-full">
              <img src={clinicReception} alt="Minh họa không gian tiếp đón tại một phòng khám" className="absolute inset-0 h-full w-full object-cover" />
            </div>
          </section>
          <section className="bg-clinical-leaf-soft px-5 py-12 sm:px-10 lg:px-14 lg:py-16" aria-labelledby="care-title">
            <div className="grid gap-8 lg:grid-cols-[0.8fr_1.2fr] lg:gap-16">
              <div>
                <h2 id="care-title" className="max-w-md text-3xl font-bold leading-tight sm:text-4xl">Một hành trình khám được thiết kế rõ từng bước</h2>
                <p className="mt-4 max-w-md leading-7 text-clinical-muted">Suncare tập trung vào khám ngoại trú trực tiếp và trực tuyến. Các bước đặt lịch và thăm khám đang được phát triển.</p>
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <article className="border-l-4 border-clinical-leaf pl-5">
                  <h3 className="text-xl font-bold">Khám trực tiếp</h3>
                  <p className="mt-3 leading-7 text-clinical-muted">Theo quy trình dự kiến, bệnh nhân chọn chuyên khoa, ngày và ca khám. Hệ thống phân bác sĩ phù hợp với lịch đã công bố.</p>
                </article>
                <article className="border-l-4 border-suncare-600 pl-5">
                  <h3 className="text-xl font-bold">Khám trực tuyến</h3>
                  <p className="mt-3 leading-7 text-clinical-muted">Theo quy trình dự kiến, bệnh nhân chọn giờ khám; lịch hẹn chỉ được xác nhận sau khi thanh toán được hệ thống kiểm tra.</p>
                </article>
              </div>
            </div>
          </section>
          <div className="grid md:grid-cols-2">
            <section className="bg-clinical-gold-soft px-5 py-12 sm:px-10 lg:px-14" aria-labelledby="departments-title">
              <FontAwesomeIcon icon={faHeartPulse} className="text-2xl text-clinical-leaf" aria-hidden="true" />
              <h2 id="departments-title" className="mt-5 text-2xl font-bold">Khoa và chuyên khoa</h2>
              <p className="mt-3 max-w-lg leading-7 text-clinical-muted">Danh mục cụ thể đang được cập nhật. Khi được công bố, bạn sẽ có thể xem chuyên khoa trước khi chọn lịch khám.</p>
            </section>
            <section className="bg-suncare-200 px-5 py-12 sm:px-10 lg:px-14" aria-labelledby="doctors-title">
              <FontAwesomeIcon icon={faClipboard} className="text-2xl text-suncare-700" aria-hidden="true" />
              <h2 id="doctors-title" className="mt-5 text-2xl font-bold">Đội ngũ bác sĩ</h2>
              <p className="mt-3 max-w-lg leading-7 text-clinical-muted">Thông tin bác sĩ và lịch làm việc đang được cập nhật. Suncare sẽ hiển thị danh sách sau khi có dữ liệu được xác nhận.</p>
            </section>
          </div>
        </>}
        {authState === 'signedIn' && !['account', 'staff', 'organization', 'rooms', 'assignments', 'services', 'scheduling-catalog'].includes(mode) && <>
          <section className="rounded-2xl border border-suncare-200 bg-suncare-100 p-7 text-clinical-ink sm:p-10">
            <p className="text-sm font-semibold text-suncare-700">Suncare Clinic</p>
            <h1 className="mt-3 text-3xl font-bold sm:text-4xl">{account ? `Xin chào, ${account.loginName}` : 'Chào mừng đến Suncare Clinic'}</h1>
            <p className="mt-4 max-w-2xl text-sm leading-7 text-clinical-muted">Các chức năng dành cho tài khoản của bạn sẽ xuất hiện tại đây khi được triển khai.</p>
          </section>
          {account && <section className="mt-8 grid gap-5 lg:grid-cols-[1fr_1.3fr]">
            <article className="rounded-2xl border border-clinical-border bg-white p-6">
              <h2 className="text-lg font-bold">Tài khoản hiện tại</h2>
              <p className="mt-3 text-sm text-clinical-muted">Mã tài khoản: {account.accountId}</p>
              <p className="mt-2 text-sm text-clinical-muted">Vai trò: {account.roles.map((role) => roleLabels[role] || role).join(', ') || 'Chưa được cấp vai trò'}</p>
              <button onClick={refreshAccount} className="mt-5 text-sm font-semibold text-suncare-700 hover:underline">Làm mới quyền</button>
            </article>
            <article className="rounded-2xl border border-clinical-border bg-white p-6">
              <h2 className="text-lg font-bold">Điều hướng theo vai trò</h2>
              {availableLinks.length === 0 ? <p className="mt-3 text-sm text-clinical-muted">Chưa có mục phù hợp với vai trò hiện tại.</p> :
                <div className="mt-4 grid gap-3 sm:grid-cols-2">{availableLinks.map((link) => <div key={link.title} className="rounded-xl bg-clinical-canvas p-4 text-sm"><FontAwesomeIcon icon={link.icon} className="mr-2 text-suncare-700" />{link.title}<p className="mt-1 text-xs text-clinical-muted">Sẽ triển khai ở bước sau</p></div>)}</div>}
            </article>
          </section>}
        </>}
        {authState === 'signedIn' && mode === 'account' &&
          <PersonalProfile account={account} onSessionExpired={handleSessionExpired} />}
        {authState === 'signedIn' && mode === 'staff' && account?.permissions.includes('ACCOUNT_MANAGE') &&
          <StaffManagement currentAccountId={account.accountId} onSessionExpired={handleSessionExpired} />}
        {authState === 'signedIn' && mode === 'organization' && account?.permissions.includes('CATALOG_MANAGE') &&
          <OrganizationManagement onSessionExpired={handleSessionExpired} />}
        {authState === 'signedIn' && mode === 'rooms' && account?.permissions.includes('CATALOG_MANAGE') &&
          <RoomManagement onSessionExpired={handleSessionExpired} />}
        {authState === 'signedIn' && mode === 'assignments' && account?.permissions.includes('ACCOUNT_MANAGE') &&
          <StaffAssignments onSessionExpired={handleSessionExpired} />}
        {authState === 'signedIn' && mode === 'services' && account?.permissions.includes('CATALOG_MANAGE') &&
          <ClinicalServiceManagement onSessionExpired={handleSessionExpired} />}
        {authState === 'signedIn' && mode === 'scheduling-catalog' && account?.permissions.includes('CATALOG_MANAGE') &&
          <SchedulingCatalogManagement onSessionExpired={handleSessionExpired} />}
      </main>
      <dialog ref={logoutDialogRef} aria-labelledby="logout-title" aria-describedby="logout-description"
        onCancel={(event) => { event.preventDefault(); if (!logoutBusy) closeLogoutDialog() }}
        onClick={(event) => { if (event.target === event.currentTarget && !logoutBusy) closeLogoutDialog() }}
        className="logout-dialog m-auto w-[min(27rem,calc(100%-2rem))] overflow-hidden rounded-2xl border border-clinical-border bg-white p-0 text-clinical-ink shadow-2xl backdrop:bg-clinical-ink/60">
        <div className="p-6 sm:p-7">
          <div className="flex items-start gap-4">
            <span className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-suncare-100 text-lg text-suncare-700" aria-hidden="true"><FontAwesomeIcon icon={faRightFromBracket} /></span>
            <div>
              <h2 id="logout-title" className="text-xl font-bold">Bạn muốn đăng xuất?</h2>
              <p id="logout-description" className="mt-2 text-sm leading-6 text-clinical-muted">Bạn có thể đăng nhập lại bất cứ lúc nào để tiếp tục sử dụng tài khoản.</p>
            </div>
          </div>
          {logoutError && <p role="alert" className="mt-5 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{logoutError}</p>}
        </div>
        <div className="grid grid-cols-2 gap-3 border-t border-clinical-border bg-clinical-canvas px-6 py-4 sm:px-7">
          <button type="button" autoFocus disabled={logoutBusy} onClick={closeLogoutDialog} className="min-h-11 rounded-xl border border-clinical-border-strong bg-white px-3 font-semibold hover:bg-suncare-50 disabled:opacity-60">Ở lại</button>
          <button type="button" disabled={logoutBusy} onClick={handleLogout} className="min-h-11 rounded-xl bg-suncare-400 px-3 font-semibold text-clinical-ink hover:bg-suncare-500 disabled:opacity-60">{logoutBusy ? 'Đang xử lý...' : 'Đăng xuất'}</button>
        </div>
      </dialog>
    </div>
  )
}

export default App

