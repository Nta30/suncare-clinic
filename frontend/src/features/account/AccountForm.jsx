import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowRight, faHeartPulse } from '@fortawesome/free-solid-svg-icons'
import consultation from '../../assets/consultation-illustration.jpg'
import sunflowerDetail from '../../assets/sunflower-detail.jpg'

const emptyForm = {
  loginName: '', password: '', fullName: '', birthDate: '', sex: '',
  phone: '', email: '', address: '', identityNumber: '',
  emergencyName: '', emergencyPhone: '',
}

const labels = {
  loginName: 'Tên đăng nhập', password: 'Mật khẩu', fullName: 'Họ và tên',
  birthDate: 'Ngày sinh', sex: 'Giới tính', phone: 'Số điện thoại',
  email: 'Email', address: 'Địa chỉ', identityNumber: 'Số giấy tờ tùy thân',
  emergencyName: 'Tên người liên hệ khẩn cấp', emergencyPhone: 'Số điện thoại khẩn cấp',
}

function validate(form, registering) {
  const errors = {}
  if (registering && !/^[A-Za-z0-9._@-]{3,100}$/.test(form.loginName.trim())) {
    errors.loginName = 'Dùng 3–100 ký tự không dấu, không có khoảng trắng.'
  }
  if (!registering && !form.loginName.trim()) errors.loginName = 'Vui lòng nhập tên đăng nhập.'
  if (registering && (form.password.length < 8 || form.password.length > 72)) {
    errors.password = 'Mật khẩu cần từ 8 đến 72 ký tự.'
  }
  if (!registering && !form.password) errors.password = 'Vui lòng nhập mật khẩu.'
  if (registering) {
    const today = new Date()
    const localDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    if (!form.fullName.trim() || form.fullName.trim().length > 150) errors.fullName = 'Họ và tên cần có và tối đa 150 ký tự.'
    if (form.birthDate && form.birthDate > localDate) errors.birthDate = 'Ngày sinh không được ở tương lai.'
    if (form.phone.length > 20) errors.phone = 'Tối đa 20 ký tự.'
    if (form.emergencyPhone.length > 20) errors.emergencyPhone = 'Tối đa 20 ký tự.'
    if (form.identityNumber.length > 30) errors.identityNumber = 'Tối đa 30 ký tự.'
    if (form.emergencyName.length > 150) errors.emergencyName = 'Tối đa 150 ký tự.'
    if (form.email.length > 254 || (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))) {
      errors.email = 'Email không đúng định dạng hoặc quá dài.'
    }
    if (form.address.length > 500) errors.address = 'Tối đa 500 ký tự.'
  }
  return errors
}

export default function AccountForm({ mode, closing, onLogin, onRegister, onModeChange }) {
  const [form, setForm] = useState(emptyForm)
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)
  const [switching, setSwitching] = useState(false)
  const [showStory, setShowStory] = useState(mode !== 'register')
  const switchTimerRef = useRef(null)
  const registering = mode === 'register'

  useEffect(() => () => window.clearTimeout(switchTimerRef.current), [])

  useEffect(() => {
    if (!registering) {
      const timer = window.setTimeout(() => setShowStory(true), 0)
      return () => window.clearTimeout(timer)
    }
    const timer = window.setTimeout(() => setShowStory(false), window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 420)
    return () => window.clearTimeout(timer)
  }, [registering])

  function update(event) {
    setForm((previous) => ({ ...previous, [event.target.name]: event.target.value }))
    setErrors((previous) => ({ ...previous, [event.target.name]: undefined }))
  }

  function field(name, type = 'text', hint = '') {
    return (
      <label key={name} className="block text-sm font-medium text-clinical-ink">
        <span>{labels[name]}</span>
        <input name={name} type={type} value={form[name]} onChange={update}
          aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `${name}-error` : undefined}
          autoComplete={name === 'loginName' ? 'username' : name === 'password' ? (registering ? 'new-password' : 'current-password') : 'on'}
          className={`mt-2 w-full rounded-xl border bg-white px-4 py-3 text-base text-clinical-ink outline-none transition focus:ring-2 ${errors[name] ? 'border-rose-400 focus:ring-rose-100' : 'border-clinical-border-strong focus:border-suncare-700 focus:ring-suncare-100'}`} />
        {hint && !errors[name] && <span className="mt-1 block text-xs font-normal text-clinical-muted">{hint}</span>}
        {errors[name] && <span id={`${name}-error`} className="mt-1 block text-xs font-semibold text-rose-700">{errors[name]}</span>}
      </label>
    )
  }

  async function submit(event) {
    event.preventDefault()
    setMessage(null)
    const nextErrors = validate(form, registering)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) {
      setMessage({ type: 'error', text: 'Vui lòng kiểm tra các trường được đánh dấu.' })
      return
    }
    setBusy(true)
    try {
      if (registering) {
        const data = Object.fromEntries(Object.entries(form)
          .filter(([name, value]) => ['loginName', 'password', 'fullName'].includes(name) || value !== ''))
        data.loginName = data.loginName.trim()
        data.fullName = data.fullName.trim()
        await onRegister(data)
        setForm({ ...emptyForm, loginName: data.loginName, password: form.password })
        setMessage({ type: 'success', text: 'Tạo tài khoản thành công. Tên đăng nhập và mật khẩu đã được điền sẵn; hãy chọn Đăng nhập.' })
        onModeChange('login')
      } else {
        await onLogin({ loginName: form.loginName.trim(), password: form.password })
        setForm(emptyForm)
      }
    } catch (failure) {
      const serverErrors = failure.fieldErrors || {}
      setErrors(Object.fromEntries(Object.keys(serverErrors)
        .map((name) => [name, `${labels[name] || name} không hợp lệ.`])))
      setMessage({ type: 'error', text: Object.keys(serverErrors).length
        ? `Dữ liệu chưa hợp lệ: ${Object.keys(serverErrors).map((name) => labels[name] || name).join(', ')}.`
        : failure.message })
    } finally {
      setBusy(false)
    }
  }

  function switchMode(nextMode) {
    if (nextMode === mode || switching || busy) return
    setErrors({})
    setMessage(null)
    setSwitching(true)
    switchTimerRef.current = window.setTimeout(() => {
      onModeChange(nextMode)
      setSwitching(false)
    }, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 140)
  }

  return (
    <section className={`auth-shell ${registering ? 'auth-shell--register' : ''} ${closing ? 'auth-shell--closing' : ''}`} aria-label={registering ? 'Đăng ký tài khoản' : 'Đăng nhập tài khoản'}>
      <div className="auth-form-pane bg-clinical-canvas px-5 py-8 sm:px-10 sm:py-12 lg:px-12">
        <div className="auth-form-inner mx-auto">
        <div className="flex rounded-xl bg-suncare-100 p-1 text-sm font-semibold" role="tablist" aria-label="Tài khoản">
          <button type="button" role="tab" aria-selected={!registering} disabled={switching || busy} onClick={() => switchMode('login')}
            className={`flex-1 rounded-lg px-3 py-2.5 transition ${!registering ? 'bg-white text-suncare-900 shadow-sm' : 'text-clinical-muted hover:text-clinical-ink'}`}>Đăng nhập</button>
          <button type="button" role="tab" aria-selected={registering} disabled={switching || busy} onClick={() => switchMode('register')}
            className={`flex-1 rounded-lg px-3 py-2.5 transition ${registering ? 'bg-white text-suncare-900 shadow-sm' : 'text-clinical-muted hover:text-clinical-ink'}`}>Đăng ký</button>
        </div>
        <div key={mode} className={`auth-form-content ${switching ? 'auth-form-content--leaving' : ''}`}>
        <h2 className="mt-8 text-3xl font-bold text-clinical-ink sm:text-4xl">{registering ? 'Tạo tài khoản bệnh nhân' : 'Chào mừng trở lại'}</h2>
        <p className="mt-3 text-sm leading-6 text-clinical-muted">{registering ? 'Điền thông tin của bạn để tạo tài khoản. Các mục có dấu * là bắt buộc.' : 'Nhập tên đăng nhập và mật khẩu để tiếp tục.'}</p>

        <form onSubmit={submit} noValidate className={registering ? 'auth-register-form mt-7' : 'mt-7 space-y-7'}>
          {registering && <div className="auth-patient-section space-y-4">
            <h3 className="border-b border-clinical-border pb-2 text-sm font-bold text-suncare-700">Thông tin bệnh nhân</h3>
            {field('fullName', 'text', 'Bắt buộc *')}
            <div className="grid gap-4 sm:grid-cols-2">{field('birthDate', 'date')}
              <label className="block text-sm font-medium text-clinical-ink">Giới tính
                <select name="sex" value={form.sex} onChange={update} className="mt-2 w-full rounded-xl border border-clinical-border-strong bg-white px-4 py-3 text-base">
                  <option value="">Chưa chọn</option><option value="MALE">Nam</option><option value="FEMALE">Nữ</option><option value="OTHER">Khác</option><option value="UNKNOWN">Không rõ</option>
                </select>
              </label>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">{field('phone', 'tel')}{field('email', 'email')}</div>
            {field('address')}
            {field('identityNumber', 'text', 'Không bắt buộc; tối đa 30 ký tự.')}
          </div>}
          {registering && <div className="auth-emergency-section space-y-4">
            <h3 className="border-b border-clinical-border pb-2 text-sm font-bold text-suncare-700">Liên hệ khi khẩn cấp</h3>
            <div className="grid gap-4 sm:grid-cols-2">{field('emergencyName')}{field('emergencyPhone', 'tel')}</div>
          </div>}
          <div className="auth-credentials-section space-y-4">
            {registering && <h3 className="border-b border-clinical-border pb-2 text-sm font-bold text-suncare-700">Thông tin đăng nhập</h3>}
            {field('loginName', 'text', registering ? 'Bắt buộc * · 3–100 ký tự không dấu, không có khoảng trắng.' : '')}
            {field('password', 'password', registering ? 'Bắt buộc * · Từ 8 đến 72 ký tự.' : '')}
          </div>
          {message && <div role={message.type === 'error' ? 'alert' : 'status'} aria-live="polite"
            className={`auth-form-message rounded-xl border px-4 py-3 text-sm font-medium ${message.type === 'error' ? 'border-rose-200 bg-rose-50 text-rose-800' : 'border-emerald-200 bg-emerald-50 text-emerald-800'}`}>{message.text}</div>}
          <button disabled={busy} className="auth-submit flex w-full items-center justify-center gap-3 rounded-xl bg-suncare-400 px-5 py-3.5 font-bold text-clinical-ink transition hover:bg-suncare-500 disabled:cursor-wait disabled:opacity-60">
            {busy ? 'Đang xử lý...' : registering ? 'Tạo tài khoản' : 'Đăng nhập'}
            {!busy && <FontAwesomeIcon icon={faArrowRight} />}
          </button>
        </form>
        </div>
        </div>
      </div>
      {showStory && <aside aria-hidden={registering} className="auth-story-pane bg-clinical-leaf-soft px-4 py-5 sm:px-6 sm:py-8 lg:px-8">
        <div className="auth-photo-composition">
          <img src={consultation} alt="Minh họa cuộc trao đổi giữa nhân viên y tế và người đến khám" className="auth-photo-primary" />
          <img src={sunflowerDetail} alt="Hoa hướng dương trong không gian đón tiếp" className="auth-photo-accent" loading="lazy" />
        </div>
        <div className="auth-story-copy mt-5 border-l-4 border-clinical-leaf pl-5 lg:mt-7">
          <FontAwesomeIcon icon={faHeartPulse} className="text-xl text-clinical-leaf" aria-hidden="true" />
          <p className="mt-2 text-xl font-semibold leading-snug text-clinical-ink">Chăm sóc bắt đầu từ sự lắng nghe.</p>
          <p className="mt-2 max-w-lg text-sm leading-6 text-clinical-muted">Suncare Clinic hướng đến một trải nghiệm khám ngoại trú rõ ràng, gần gũi và thuận tiện cho người bệnh.</p>
        </div>
      </aside>}
    </section>
  )
}

