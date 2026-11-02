import { useCallback, useEffect, useMemo, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faCheck,
  faClock,
  faFilter,
  faHourglassEnd,
  faMagnifyingGlass,
  faPenToSquare,
  faPlus,
  faRotateRight,
  faStethoscope,
  faUserDoctor,
  faVideo,
  faXmark,
} from '@fortawesome/free-solid-svg-icons'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import {
  createConsultationPrice,
  endConsultationPrice,
  getAllConsultationPrices,
  getDoctors,
  getExaminationServices,
  getShifts,
  initDefaultShifts,
  updateShift,
} from '../../services/schedulingCatalogApi'

const field = 'min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus:border-suncare-700 focus:outline-none'

const formatCurrency = (val) => {
  if (val === null || val === undefined || val === '') return '0 đ'
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val)
}

const formatDateTime = (val) => {
  if (!val) return 'Không giới hạn'
  return new Date(val.endsWith('Z') ? val : `${val}Z`).toLocaleString('vi-VN', {
    timeZone: 'Asia/Saigon',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const localNowInput = () => {
  const d = new Date(Date.now() + 7 * 60 * 60 * 1000)
  return d.toISOString().slice(0, 16)
}

const utcString = (localVal) => {
  if (!localVal) return null
  return new Date(`${localVal}+07:00`).toISOString().slice(0, 23)
}

export default function SchedulingCatalogManagement({ onSessionExpired }) {
  const [activeTab, setActiveTab] = useState('shifts') // 'shifts' | 'prices'
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')

  // ── Shift Definition State ──
  const [shifts, setShifts] = useState([])
  const [editingShift, setEditingShift] = useState(null)
  const [shiftForm, setShiftForm] = useState({ name: '', localStart: '', localEnd: '', isActive: true, version: '' })
  const [shiftFormError, setShiftFormError] = useState('')

  // ── Consultation Pricing State ──
  const [prices, setPrices] = useState([])
  const [doctors, setDoctors] = useState([])
  const [examinationServices, setExaminationServices] = useState([])

  // Pricing Filters
  const [serviceFilter, setServiceFilter] = useState('')
  const [doctorFilter, setDoctorFilter] = useState('')
  const [modeFilter, setModeFilter] = useState('')
  const [activeOnlyFilter, setActiveOnlyFilter] = useState(false)
  const [searchDoctor, setSearchDoctor] = useState('')

  // Create Price Modal
  const [showPriceModal, setShowPriceModal] = useState(false)
  const [priceForm, setPriceForm] = useState({
    serviceId: '',
    doctorId: '',
    visitMode: 'ONSITE',
    amount: '',
    validFrom: localNowInput(),
    validTo: '',
  })
  const [priceFormError, setPriceFormError] = useState('')

  // End Price Modal
  const [endingPrice, setEndingPrice] = useState(null)
  const [endForm, setEndForm] = useState({ validTo: localNowInput() })
  const [endFormError, setEndFormError] = useState('')

  const handleApiError = useCallback((err) => {
    if (err?.status === 401) {
      onSessionExpired?.()
      return
    }
    setError(err?.message || 'Có lỗi xảy ra khi gọi máy chủ.')
  }, [onSessionExpired])

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [shiftData, doctorData, examData, priceData] = await Promise.all([
        getShifts(),
        getDoctors(),
        getExaminationServices(),
        getAllConsultationPrices({
          serviceId: serviceFilter || undefined,
          doctorId: doctorFilter || undefined,
          visitMode: modeFilter || undefined,
          activeOnly: activeOnlyFilter || undefined,
        }),
      ])
      setShifts(shiftData || [])
      setDoctors(doctorData || [])
      setExaminationServices(examData || [])
      setPrices(priceData || [])
    } catch (err) {
      handleApiError(err)
    } finally {
      setLoading(false)
    }
  }, [serviceFilter, doctorFilter, modeFilter, activeOnlyFilter, handleApiError])

  useEffect(() => {
    let active = true
    Promise.resolve().then(() => { if (active) void loadData() })
    return () => { active = false }
  }, [loadData])

  // Filtered prices by search
  const filteredPrices = useMemo(() => {
    if (!searchDoctor.trim()) return prices
    const q = searchDoctor.trim().toLowerCase()
    return prices.filter(
      (p) =>
        p.doctorName?.toLowerCase().includes(q) ||
        p.doctorCode?.toLowerCase().includes(q) ||
        p.serviceName?.toLowerCase().includes(q)
    )
  }, [prices, searchDoctor])

  // ── Shift Handlers ──
  const handleOpenEditShift = (shift) => {
    setEditingShift(shift)
    setShiftForm({
      name: shift.name,
      localStart: shift.localStart?.slice(0, 5) || '07:30',
      localEnd: shift.localEnd?.slice(0, 5) || '11:30',
      isActive: shift.isActive,
      version: shift.version,
    })
    setShiftFormError('')
  }

  const handleSaveShift = async (e) => {
    e.preventDefault()
    setShiftFormError('')
    if (!shiftForm.localStart || !shiftForm.localEnd) {
      setShiftFormError('Vui lòng chọn giờ bắt đầu và giờ kết thúc.')
      return
    }
    if (shiftForm.localEnd <= shiftForm.localStart) {
      setShiftFormError('Giờ kết thúc ca phải sau giờ bắt đầu.')
      return
    }

    setBusy(true)
    try {
      const updated = await updateShift(editingShift.id, {
        name: shiftForm.name.trim(),
        localStart: `${shiftForm.localStart}:00`,
        localEnd: `${shiftForm.localEnd}:00`,
        isActive: shiftForm.isActive,
        version: shiftForm.version,
      })
      setShifts((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
      setEditingShift(null)
      setSuccessMsg(`Đã cập nhật ca ${updated.name} thành công.`)
      setTimeout(() => setSuccessMsg(''), 4000)
    } catch (err) {
      if (err?.code === 'CONCURRENT_UPDATE') {
        setShiftFormError('Dữ liệu ca đã thay đổi bởi người khác. Vui lòng tải lại trang.')
      } else {
        setShiftFormError(err?.message || 'Không lưu được ca khám.')
      }
    } finally {
      setBusy(false)
    }
  }

  const handleInitDefaultShifts = async () => {
    setBusy(true)
    setError('')
    try {
      const data = await initDefaultShifts()
      setShifts(data || [])
      setSuccessMsg('Đã khởi tạo các ca chuẩn (Ca sáng và Ca chiều).')
      setTimeout(() => setSuccessMsg(''), 4000)
    } catch (err) {
      handleApiError(err)
    } finally {
      setBusy(false)
    }
  }

  // ── Price Handlers ──
  const handleOpenCreatePrice = () => {
    setPriceForm({
      serviceId: examinationServices[0]?.id || '',
      doctorId: doctors[0]?.id || '',
      visitMode: 'ONSITE',
      amount: '',
      validFrom: localNowInput(),
      validTo: '',
    })
    setPriceFormError('')
    setShowPriceModal(true)
  }

  const handleSavePrice = async (e) => {
    e.preventDefault()
    setPriceFormError('')

    if (!priceForm.serviceId) {
      setPriceFormError('Vui lòng chọn dịch vụ khám bệnh.')
      return
    }
    if (!priceForm.doctorId) {
      setPriceFormError('Vui lòng chọn bác sĩ phụ trách.')
      return
    }
    const amt = Number(priceForm.amount)
    if (!amt || amt <= 0) {
      setPriceFormError('Giá khám phải là số nguyên dương.')
      return
    }
    if (!priceForm.validFrom) {
      setPriceFormError('Vui lòng chọn thời điểm bắt đầu hiệu lực.')
      return
    }
    if (priceForm.validTo && priceForm.validTo <= priceForm.validFrom) {
      setPriceFormError('Thời điểm kết thúc phải sau thời điểm bắt đầu.')
      return
    }

    setBusy(true)
    try {
      await createConsultationPrice(priceForm.serviceId, {
        doctorId: Number(priceForm.doctorId),
        visitMode: priceForm.visitMode,
        amount: amt,
        validFrom: utcString(priceForm.validFrom),
        validTo: utcString(priceForm.validTo),
      })
      setShowPriceModal(false)
      setSuccessMsg('Thiết lập giá khám theo bác sĩ thành công.')
      setTimeout(() => setSuccessMsg(''), 4000)
      loadData()
    } catch (err) {
      if (err?.code === 'PRICE_OVERLAP') {
        setPriceFormError('Khoảng thời gian hiệu lực bị chồng lấn với mức giá đã có của bác sĩ này.')
      } else {
        setPriceFormError(err?.message || 'Không thể tạo giá khám.')
      }
    } finally {
      setBusy(false)
    }
  }

  const handleOpenEndPrice = (price) => {
    setEndingPrice(price)
    setEndForm({ validTo: localNowInput() })
    setEndFormError('')
  }

  const handleConfirmEndPrice = async (e) => {
    e.preventDefault()
    setEndFormError('')
    if (!endForm.validTo) {
      setEndFormError('Vui lòng chọn thời điểm kết thúc.')
      return
    }

    setBusy(true)
    try {
      await endConsultationPrice(endingPrice.serviceId, endingPrice.id, {
        validTo: utcString(endForm.validTo),
        version: endingPrice.version,
      })
      setEndingPrice(null)
      setSuccessMsg('Đã kết thúc mức giá khám thành công.')
      setTimeout(() => setSuccessMsg(''), 4000)
      loadData()
    } catch (err) {
      if (err?.code === 'CONCURRENT_UPDATE') {
        setEndFormError('Mức giá đã được cập nhật bởi thao tác khác. Vui lòng tải lại.')
      } else {
        setEndFormError(err?.message || 'Không thể kết thúc mức giá.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-clinical-border pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-clinical-ink">
            Ca khám & Bảng giá khám bệnh
          </h1>
          <p className="mt-1 text-sm text-clinical-ink-muted">
            Quản lý khung giờ ca sáng/chiều và chính sách giá khám theo từng bác sĩ & hình thức khám (P12).
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={loadData} disabled={loading || busy}>
            <FontAwesomeIcon icon={faRotateRight} className={loading ? 'animate-spin' : ''} />
            <span className="hidden sm:inline">Tải lại</span>
          </Button>
        </div>
      </div>

      {/* Global Alert / Toast */}
      {error && (
        <div className="mb-4 flex items-center justify-between rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <div>{error}</div>
          <button onClick={() => setError('')} className="text-red-500 hover:text-red-700">
            <FontAwesomeIcon icon={faXmark} />
          </button>
        </div>
      )}
      {successMsg && (
        <div className="mb-4 flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <div className="flex items-center gap-2">
            <FontAwesomeIcon icon={faCheck} />
            <span>{successMsg}</span>
          </div>
          <button onClick={() => setSuccessMsg('')} className="text-emerald-600 hover:text-emerald-800">
            <FontAwesomeIcon icon={faXmark} />
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="mb-6 flex border-b border-clinical-border">
        <button
          onClick={() => setActiveTab('shifts')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold transition-colors ${
            activeTab === 'shifts'
              ? 'border-suncare-700 text-suncare-800'
              : 'border-transparent text-clinical-ink-muted hover:text-clinical-ink'
          }`}
        >
          <FontAwesomeIcon icon={faClock} />
          <span>Khung ca làm việc (ShiftDefinition)</span>
          <span className="rounded-full bg-suncare-100 px-2 py-0.5 text-xs text-suncare-800">
            {shifts.length}
          </span>
        </button>
        <button
          onClick={() => setActiveTab('prices')}
          className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold transition-colors ${
            activeTab === 'prices'
              ? 'border-suncare-700 text-suncare-800'
              : 'border-transparent text-clinical-ink-muted hover:text-clinical-ink'
          }`}
        >
          <FontAwesomeIcon icon={faStethoscope} />
          <span>Bảng giá khám theo bác sĩ</span>
          <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800">
            {prices.length}
          </span>
        </button>
      </div>

      {/* Tab 1: Khung ca làm việc */}
      {activeTab === 'shifts' && (
        <div className="space-y-6">
          <div className="rounded-2xl border border-clinical-border bg-clinical-surface-soft p-4 sm:p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-lg font-semibold text-clinical-ink">Định nghĩa ca khám</h2>
              {shifts.length < 2 && (
                <Button variant="primary" onClick={handleInitDefaultShifts} disabled={busy}>
                  <FontAwesomeIcon icon={faPlus} />
                  <span>Khởi tạo 2 ca chuẩn (Sáng / Chiều)</span>
                </Button>
              )}
            </div>
          </div>

          {loading ? (
            <div className="flex h-40 items-center justify-center rounded-2xl border border-clinical-border bg-white text-clinical-ink-muted">
              Đang tải danh sách ca làm việc...
            </div>
          ) : shifts.length === 0 ? (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-clinical-border bg-white p-8 text-center">
              <FontAwesomeIcon icon={faClock} className="mb-3 text-3xl text-clinical-ink-muted" />
              <p className="font-semibold text-clinical-ink">Chưa có ca làm việc nào được cấu hình</p>
              <div className="mt-4">
                <Button variant="primary" onClick={handleInitDefaultShifts} disabled={busy}>
                  <FontAwesomeIcon icon={faPlus} />
                  <span>Khởi tạo 2 ca chuẩn</span>
                </Button>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {shifts.map((shift) => (
                <div
                  key={shift.id}
                  className="relative flex flex-col justify-between rounded-2xl border border-clinical-border bg-white p-5 shadow-xs transition-shadow hover:shadow-sm"
                >
                  <div>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-bold text-suncare-700">
                          {shift.code}
                        </span>
                        <h3 className="text-base font-bold text-clinical-ink">{shift.name}</h3>
                      </div>
                      <Badge variant={shift.isActive ? 'success' : 'neutral'}>
                        {shift.isActive ? 'Đang hoạt động' : 'Tạm dừng'}
                      </Badge>
                    </div>

                    <div className="mt-4 rounded-xl border border-clinical-border bg-clinical-surface-soft p-3">
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-clinical-ink-muted">Khung giờ làm việc:</span>
                        <span className="font-mono font-semibold text-clinical-ink">
                          {shift.localStart?.slice(0, 5)} - {shift.localEnd?.slice(0, 5)}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="mt-5 flex justify-end border-t border-clinical-border pt-4">
                    <Button variant="outline" size="sm" onClick={() => handleOpenEditShift(shift)}>
                      <FontAwesomeIcon icon={faPenToSquare} />
                      <span>Chỉnh sửa ca</span>
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Bảng giá khám theo bác sĩ */}
      {activeTab === 'prices' && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-clinical-border bg-clinical-surface-soft p-4 sm:p-5">
            <h2 className="text-lg font-semibold text-clinical-ink">Bảng giá khám theo bác sĩ & hình thức</h2>
            <Button variant="primary" onClick={handleOpenCreatePrice}>
              <FontAwesomeIcon icon={faPlus} />
              <span>Thiết lập giá khám mới</span>
            </Button>
          </div>

          {/* Filters Bar */}
          <div className="rounded-2xl border border-clinical-border bg-white p-4">
            <div className="mb-3 flex items-center gap-2 text-xs font-semibold text-clinical-ink-muted">
              <FontAwesomeIcon icon={faFilter} />
              <span>Bộ lọc tìm kiếm</span>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <label className="mb-1 block text-xs font-medium text-clinical-ink">Dịch vụ khám</label>
                <select
                  value={serviceFilter}
                  onChange={(e) => setServiceFilter(e.target.value)}
                  className={field}
                >
                  <option value="">Tất cả dịch vụ khám</option>
                  {examinationServices.map((s) => (
                    <option key={s.id} value={s.id}>
                      [{s.code}] {s.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-clinical-ink">Bác sĩ</label>
                <select
                  value={doctorFilter}
                  onChange={(e) => setDoctorFilter(e.target.value)}
                  className={field}
                >
                  <option value="">Tất cả bác sĩ</option>
                  {doctors.map((d) => (
                    <option key={d.id} value={d.id}>
                      [{d.code}] {d.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-clinical-ink">Hình thức khám</label>
                <select
                  value={modeFilter}
                  onChange={(e) => setModeFilter(e.target.value)}
                  className={field}
                >
                  <option value="">Tất cả hình thức</option>
                  <option value="ONSITE">Trực tiếp (ONSITE)</option>
                  <option value="ONLINE">Trực tuyến (ONLINE)</option>
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-clinical-ink">Tìm kiếm nhanh</label>
                <div className="relative">
                  <input
                    type="text"
                    value={searchDoctor}
                    onChange={(e) => setSearchDoctor(e.target.value)}
                    placeholder="Tên bác sĩ hoặc mã..."
                    className={field}
                  />
                  <FontAwesomeIcon
                    icon={faMagnifyingGlass}
                    className="pointer-events-none absolute right-3 top-3 text-clinical-ink-muted"
                  />
                </div>
              </div>
            </div>

            <div className="mt-3 flex items-center justify-between border-t border-clinical-border pt-3">
              <label className="flex items-center gap-2 text-xs font-medium text-clinical-ink cursor-pointer">
                <input
                  type="checkbox"
                  checked={activeOnlyFilter}
                  onChange={(e) => setActiveOnlyFilter(e.target.checked)}
                  className="rounded border-clinical-border text-suncare-700 focus:ring-suncare-700"
                />
                <span>Chỉ hiển thị các mức giá đang áp dụng</span>
              </label>
              <div className="text-xs text-clinical-ink-muted">
                Tìm thấy <span className="font-semibold text-clinical-ink">{filteredPrices.length}</span> mức giá
              </div>
            </div>
          </div>

          {/* Pricing Table */}
          {loading ? (
            <div className="flex h-40 items-center justify-center rounded-2xl border border-clinical-border bg-white text-clinical-ink-muted">
              Đang tải danh sách giá khám...
            </div>
          ) : filteredPrices.length === 0 ? (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-clinical-border bg-white p-8 text-center">
              <FontAwesomeIcon icon={faStethoscope} className="mb-3 text-3xl text-clinical-ink-muted" />
              <p className="font-semibold text-clinical-ink">Chưa có mức giá khám nào phù hợp</p>
              <div className="mt-4">
                <Button variant="primary" onClick={handleOpenCreatePrice}>
                  <FontAwesomeIcon icon={faPlus} />
                  <span>Thiết lập giá khám mới</span>
                </Button>
              </div>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-2xl border border-clinical-border bg-white shadow-xs">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-clinical-border bg-clinical-surface-soft text-xs font-semibold uppercase text-clinical-ink-muted">
                  <tr>
                    <th className="px-4 py-3">Dịch vụ khám</th>
                    <th className="px-4 py-3">Bác sĩ phụ trách</th>
                    <th className="px-4 py-3">Hình thức</th>
                    <th className="px-4 py-3">Mức giá (VND)</th>
                    <th className="px-4 py-3">Hiệu lực</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-clinical-border">
                  {filteredPrices.map((p) => {
                    const isEnded = p.validTo && new Date(p.validTo) <= new Date()
                    return (
                      <tr key={p.id} className="hover:bg-clinical-surface-soft/60">
                        <td className="px-4 py-3">
                          <div className="font-semibold text-clinical-ink">{p.serviceName}</div>
                          <div className="font-mono text-xs text-clinical-ink-muted">{p.serviceCode}</div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <FontAwesomeIcon icon={faUserDoctor} className="text-suncare-700" />
                            <span className="font-semibold text-clinical-ink">{p.doctorName}</span>
                          </div>
                          <div className="font-mono text-xs text-clinical-ink-muted">{p.doctorCode}</div>
                        </td>
                        <td className="px-4 py-3">
                          {p.visitMode === 'ONLINE' ? (
                            <Badge variant="info">
                              <FontAwesomeIcon icon={faVideo} className="mr-1" />
                              Trực tuyến
                            </Badge>
                          ) : (
                            <Badge variant="neutral">
                              <FontAwesomeIcon icon={faStethoscope} className="mr-1" />
                              Trực tiếp
                            </Badge>
                          )}
                        </td>
                        <td className="px-4 py-3 font-semibold text-clinical-ink">
                          {formatCurrency(p.amount)}
                        </td>
                        <td className="px-4 py-3 text-xs">
                          <div>Từ: {formatDateTime(p.validFrom)}</div>
                          <div className="text-clinical-ink-muted">Đến: {formatDateTime(p.validTo)}</div>
                        </td>
                        <td className="px-4 py-3">
                          {p.isEffective ? (
                            <Badge variant="success">Đang áp dụng</Badge>
                          ) : isEnded ? (
                            <Badge variant="neutral">Đã kết thúc</Badge>
                          ) : (
                            <Badge variant="warning">Sắp áp dụng</Badge>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {p.isEffective && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenEndPrice(p)}
                              title="Kết thúc hiệu lực mức giá này sớm hơn"
                            >
                              <FontAwesomeIcon icon={faHourglassEnd} />
                              <span className="hidden sm:inline">Kết thúc sớm</span>
                            </Button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── Modal Chỉnh sửa ca (ShiftDefinition) ── */}
      {editingShift && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs"
          onClick={() => setEditingShift(null)}
          onKeyDown={(e) => e.key === 'Escape' && setEditingShift(null)}
        >
          <div
            className="w-full max-w-lg rounded-2xl border border-clinical-border bg-white p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between border-b border-clinical-border pb-3">
              <h3 className="text-lg font-bold text-clinical-ink">
                Chỉnh sửa {editingShift.name} ({editingShift.code})
              </h3>
              <button
                onClick={() => setEditingShift(null)}
                className="text-clinical-ink-muted hover:text-clinical-ink"
              >
                <FontAwesomeIcon icon={faXmark} />
              </button>
            </div>

            {shiftFormError && (
              <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                {shiftFormError}
              </div>
            )}

            <form onSubmit={handleSaveShift} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-semibold text-clinical-ink">Tên ca làm việc</label>
                <input
                  type="text"
                  required
                  value={shiftForm.name}
                  onChange={(e) => setShiftForm({ ...shiftForm, name: e.target.value })}
                  className={field}
                  placeholder="Ví dụ: Ca sáng, Ca chiều..."
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-semibold text-clinical-ink">Giờ bắt đầu</label>
                  <input
                    type="time"
                    required
                    value={shiftForm.localStart}
                    onChange={(e) => setShiftForm({ ...shiftForm, localStart: e.target.value })}
                    className={field}
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-semibold text-clinical-ink">Giờ kết thúc</label>
                  <input
                    type="time"
                    required
                    value={shiftForm.localEnd}
                    onChange={(e) => setShiftForm({ ...shiftForm, localEnd: e.target.value })}
                    className={field}
                  />
                </div>
              </div>

              <div>
                <label className="flex items-center gap-2 text-sm font-medium text-clinical-ink cursor-pointer">
                  <input
                    type="checkbox"
                    checked={shiftForm.isActive}
                    onChange={(e) => setShiftForm({ ...shiftForm, isActive: e.target.checked })}
                    className="rounded border-clinical-border text-suncare-700 focus:ring-suncare-700"
                  />
                  <span>Kích hoạt ca làm việc này</span>
                </label>
              </div>

              <div className="mt-6 flex justify-end gap-3 border-t border-clinical-border pt-4">
                <Button variant="outline" type="button" onClick={() => setEditingShift(null)}>
                  Hủy
                </Button>
                <Button variant="primary" type="submit" disabled={busy}>
                  {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal Thiết lập giá khám mới ── */}
      {showPriceModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs"
          onClick={() => setShowPriceModal(false)}
          onKeyDown={(e) => e.key === 'Escape' && setShowPriceModal(false)}
        >
          <div
            className="w-full max-w-xl rounded-2xl border border-clinical-border bg-white p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between border-b border-clinical-border pb-3">
              <h3 className="text-lg font-bold text-clinical-ink">Thiết lập giá khám theo bác sĩ</h3>
              <button
                onClick={() => setShowPriceModal(false)}
                className="text-clinical-ink-muted hover:text-clinical-ink"
              >
                <FontAwesomeIcon icon={faXmark} />
              </button>
            </div>

            {priceFormError && (
              <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                {priceFormError}
              </div>
            )}

            <form onSubmit={handleSavePrice} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-semibold text-clinical-ink">
                  Dịch vụ khám bệnh (EXAMINATION)
                </label>
                <select
                  required
                  value={priceForm.serviceId}
                  onChange={(e) => setPriceForm({ ...priceForm, serviceId: e.target.value })}
                  className={field}
                >
                  <option value="">-- Chọn dịch vụ khám --</option>
                  {examinationServices.map((s) => (
                    <option key={s.id} value={s.id}>
                      [{s.code}] {s.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-semibold text-clinical-ink">
                  Bác sĩ phụ trách
                </label>
                <select
                  required
                  value={priceForm.doctorId}
                  onChange={(e) => setPriceForm({ ...priceForm, doctorId: e.target.value })}
                  className={field}
                >
                  <option value="">-- Chọn bác sĩ --</option>
                  {doctors.map((d) => (
                    <option key={d.id} value={d.id}>
                      [{d.code}] {d.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-semibold text-clinical-ink">Hình thức khám</label>
                  <select
                    value={priceForm.visitMode}
                    onChange={(e) => setPriceForm({ ...priceForm, visitMode: e.target.value })}
                    className={field}
                  >
                    <option value="ONSITE">Trực tiếp (ONSITE)</option>
                    <option value="ONLINE">Trực tuyến (ONLINE)</option>
                  </select>
                </div>

                <div>
                  <label className="mb-1 block text-xs font-semibold text-clinical-ink">Mức giá (VND)</label>
                  <input
                    type="number"
                    required
                    min="1000"
                    step="1000"
                    value={priceForm.amount}
                    onChange={(e) => setPriceForm({ ...priceForm, amount: e.target.value })}
                    placeholder="Ví dụ: 200000"
                    className={field}
                  />
                  {priceForm.amount && (
                    <div className="mt-1 text-right text-xs font-semibold text-suncare-700">
                      {formatCurrency(priceForm.amount)}
                    </div>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs font-semibold text-clinical-ink">Hiệu lực từ</label>
                  <input
                    type="datetime-local"
                    required
                    value={priceForm.validFrom}
                    onChange={(e) => setPriceForm({ ...priceForm, validFrom: e.target.value })}
                    className={field}
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-semibold text-clinical-ink">
                    Hiệu lực đến (Tùy chọn)
                  </label>
                  <input
                    type="datetime-local"
                    value={priceForm.validTo}
                    onChange={(e) => setPriceForm({ ...priceForm, validTo: e.target.value })}
                    className={field}
                  />
                </div>
              </div>

              <div className="mt-6 flex justify-end gap-3 border-t border-clinical-border pt-4">
                <Button variant="outline" type="button" onClick={() => setShowPriceModal(false)}>
                  Hủy
                </Button>
                <Button variant="primary" type="submit" disabled={busy}>
                  {busy ? 'Đang lưu...' : 'Xác nhận tạo giá'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal Kết thúc giá sớm ── */}
      {endingPrice && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 backdrop-blur-xs"
          onClick={() => setEndingPrice(null)}
          onKeyDown={(e) => e.key === 'Escape' && setEndingPrice(null)}
        >
          <div
            className="w-full max-w-md rounded-2xl border border-clinical-border bg-white p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between border-b border-clinical-border pb-3">
              <h3 className="text-lg font-bold text-clinical-ink">Kết thúc mức giá sớm</h3>
              <button
                onClick={() => setEndingPrice(null)}
                className="text-clinical-ink-muted hover:text-clinical-ink"
              >
                <FontAwesomeIcon icon={faXmark} />
              </button>
            </div>

            {endFormError && (
              <div className="mb-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                {endFormError}
              </div>
            )}

            <div className="mb-4 rounded-xl border border-clinical-border bg-clinical-surface-soft p-3 text-sm">
              <div>
                <span className="text-clinical-ink-muted">Bác sĩ: </span>
                <span className="font-semibold text-clinical-ink">{endingPrice.doctorName}</span>
              </div>
              <div className="mt-1">
                <span className="text-clinical-ink-muted">Dịch vụ: </span>
                <span className="font-semibold text-clinical-ink">{endingPrice.serviceName}</span>
              </div>
              <div className="mt-1">
                <span className="text-clinical-ink-muted">Mức giá: </span>
                <span className="font-bold text-suncare-700">{formatCurrency(endingPrice.amount)}</span>
              </div>
              <div className="mt-1">
                <span className="text-clinical-ink-muted">Bắt đầu từ: </span>
                <span>{formatDateTime(endingPrice.validFrom)}</span>
              </div>
            </div>

            <form onSubmit={handleConfirmEndPrice} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-semibold text-clinical-ink">
                  Thời điểm kết thúc mới (giờ Việt Nam)
                </label>
                <input
                  type="datetime-local"
                  required
                  value={endForm.validTo}
                  onChange={(e) => setEndForm({ validTo: e.target.value })}
                  className={field}
                />
                <p className="mt-1 text-xs text-clinical-ink-muted">
                  Thời điểm kết thúc phải lớn hơn thời điểm hiện tại và không hồi tố lịch sử.
                </p>
              </div>

              <div className="mt-6 flex justify-end gap-3 border-t border-clinical-border pt-4">
                <Button variant="outline" type="button" onClick={() => setEndingPrice(null)}>
                  Hủy
                </Button>
                <Button variant="danger" type="submit" disabled={busy}>
                  {busy ? 'Đang xử lý...' : 'Xác nhận kết thúc'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
