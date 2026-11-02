import { useCallback, useEffect, useMemo, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faCheck,
  faDoorOpen,
  faFlaskVial,
  faMagnifyingGlass,
  faPenToSquare,
  faPlus,
  faRotateRight,
  faStethoscope,
  faTags,
  faXmark,
} from '@fortawesome/free-solid-svg-icons'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import {
  createService,
  createServicePrice,
  endServicePrice,
  getServicePrices,
  getServiceRooms,
  getServices,
  toggleServiceRoom,
  updateService,
} from '../../services/clinicalServiceApi'
import { getOrganization } from '../../services/organizationApi'

const field = 'min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus:border-suncare-700 focus:outline-none'

const emptyServiceForm = {
  code: '',
  name: '',
  serviceType: 'CLS',
  resultType: 'LAB',
  specialtyId: '',
  isActive: true,
}

const emptyPriceForm = {
  amount: '',
  validFrom: '',
  validTo: '',
}

const utcString = (localVal) => {
  if (!localVal) return null
  return new Date(`${localVal}+07:00`).toISOString().slice(0, 23)
}

const localNowInput = () => {
  const d = new Date(Date.now() + 7 * 60 * 60 * 1000)
  return d.toISOString().slice(0, 16)
}

const formatCurrency = (val) => {
  if (val === null || val === undefined) return 'Chưa có giá'
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

export default function ClinicalServiceManagement({ onSessionExpired }) {
  const [services, setServices] = useState([])
  const [specialties, setSpecialties] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')

  // Filters
  const [typeFilter, setTypeFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [searchQuery, setSearchQuery] = useState('')

  // Service modal
  const [serviceModalMode, setServiceModalMode] = useState(null) // 'create' | 'edit' | null
  const [serviceForm, setServiceForm] = useState(emptyServiceForm)
  const [editingService, setEditingService] = useState(null)
  const [formError, setFormError] = useState('')

  // Room mapping modal
  const [roomModalService, setRoomModalService] = useState(null)
  const [roomMappings, setRoomMappings] = useState([])
  const [loadingRooms, setLoadingRooms] = useState(false)
  const [togglingRoomId, setTogglingRoomId] = useState(null)
  const [roomError, setRoomError] = useState('')

  // Price modal
  const [priceModalService, setPriceModalService] = useState(null)
  const [prices, setPrices] = useState([])
  const [loadingPrices, setLoadingPrices] = useState(false)
  const [priceForm, setPriceForm] = useState(emptyPriceForm)
  const [priceError, setPriceError] = useState('')
  const [endingPriceId, setEndingPriceId] = useState(null)
  const [endDateTime, setEndDateTime] = useState('')

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [serviceList, org] = await Promise.all([
        getServices(),
        getOrganization(),
      ])
      setServices(serviceList)
      setSpecialties(org.specialties || [])
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [onSessionExpired])

  useEffect(() => {
    let active = true
    Promise.resolve().then(() => { if (active) void loadData() })
    return () => { active = false }
  }, [loadData])

  const filteredServices = useMemo(() => {
    return services.filter((srv) => {
      if (typeFilter && srv.serviceType !== typeFilter) return false
      if (statusFilter === 'active' && !srv.isActive) return false
      if (statusFilter === 'inactive' && srv.isActive) return false
      if (searchQuery) {
        const query = searchQuery.trim().toLowerCase()
        const matchCode = srv.code?.toLowerCase().includes(query)
        const matchName = srv.name?.toLowerCase().includes(query)
        if (!matchCode && !matchName) return false
      }
      return true
    })
  }, [services, typeFilter, statusFilter, searchQuery])

  // --- Service Create / Edit ---
  const handleOpenCreate = () => {
    setServiceForm({
      code: '',
      name: '',
      serviceType: 'CLS',
      resultType: 'LAB',
      specialtyId: '',
      isActive: true,
    })
    setFormError('')
    setEditingService(null)
    setServiceModalMode('create')
  }

  const handleOpenEdit = (srv) => {
    setServiceForm({
      code: srv.code,
      name: srv.name,
      serviceType: srv.serviceType,
      resultType: srv.resultType || '',
      specialtyId: srv.specialtyId ? String(srv.specialtyId) : '',
      isActive: srv.isActive,
    })
    setFormError('')
    setEditingService(srv)
    setServiceModalMode('edit')
  }

  const handleCloseServiceModal = () => {
    setServiceModalMode(null)
    setEditingService(null)
    setFormError('')
  }

  const handleSaveService = async (e) => {
    e.preventDefault()
    setBusy(true)
    setFormError('')
    try {
      if (serviceModalMode === 'create') {
        const payload = {
          code: serviceForm.code.trim().toUpperCase(),
          name: serviceForm.name.trim(),
          serviceType: serviceForm.serviceType,
          resultType: serviceForm.serviceType === 'CLS' && serviceForm.resultType ? serviceForm.resultType : null,
          specialtyId: serviceForm.specialtyId ? Number(serviceForm.specialtyId) : null,
          isActive: serviceForm.isActive,
        }
        await createService(payload)
        setSuccessMsg(`Đã tạo thành công dịch vụ ${payload.code}`)
      } else if (serviceModalMode === 'edit' && editingService) {
        const payload = {
          name: serviceForm.name.trim(),
          resultType: editingService.serviceType === 'CLS' && serviceForm.resultType ? serviceForm.resultType : null,
          specialtyId: serviceForm.specialtyId ? Number(serviceForm.specialtyId) : null,
          isActive: serviceForm.isActive,
          version: editingService.version,
        }
        await updateService(editingService.id, payload)
        setSuccessMsg(`Đã cập nhật dịch vụ ${editingService.code}`)
      }
      handleCloseServiceModal()
      await loadData()
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setFormError(err.message)
    } finally {
      setBusy(false)
    }
  }

  // --- Room Mapping Modal ---
  const handleOpenRooms = async (srv) => {
    setRoomModalService(srv)
    setRoomError('')
    setLoadingRooms(true)
    try {
      const mappings = await getServiceRooms(srv.id)
      setRoomMappings(mappings)
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setRoomError(err.message)
    } finally {
      setLoadingRooms(false)
    }
  }

  const handleToggleRoom = async (roomId, currentSupported) => {
    if (!roomModalService) return
    setTogglingRoomId(roomId)
    setRoomError('')
    try {
      const updated = await toggleServiceRoom(roomModalService.id, roomId, !currentSupported)
      setRoomMappings(updated)
      setServices((prev) =>
        prev.map((s) => {
          if (s.id === roomModalService.id) {
            const count = updated.filter((r) => r.isSupported).length
            return { ...s, supportedRoomsCount: count }
          }
          return s
        })
      )
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setRoomError(err.message)
    } finally {
      setTogglingRoomId(null)
    }
  }

  // --- Price Modal ---
  const handleOpenPrices = async (srv) => {
    setPriceModalService(srv)
    setPriceError('')
    setEndingPriceId(null)
    setPriceForm({
      amount: '',
      validFrom: localNowInput(),
      validTo: '',
    })
    setLoadingPrices(true)
    try {
      const list = await getServicePrices(srv.id)
      setPrices(list)
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setPriceError(err.message)
    } finally {
      setLoadingPrices(false)
    }
  }

  const handleCreatePrice = async (e) => {
    e.preventDefault()
    if (!priceModalService) return
    setBusy(true)
    setPriceError('')
    try {
      const payload = {
        amount: priceForm.amount.trim(),
        validFrom: utcString(priceForm.validFrom),
        validTo: priceForm.validTo ? utcString(priceForm.validTo) : null,
      }
      const updatedList = await createServicePrice(priceModalService.id, payload)
      setPrices(updatedList)
      setPriceForm({
        amount: '',
        validFrom: localNowInput(),
        validTo: '',
      })
      // update main list current price
      const effective = updatedList.find((p) => p.isEffective)
      setServices((prev) =>
        prev.map((s) => (s.id === priceModalService.id ? { ...s, currentPrice: effective ? effective.amount : s.currentPrice } : s))
      )
      setSuccessMsg('Đã thêm mức giá mới thành công')
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setPriceError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const handleOpenEndPrice = (price) => {
    setEndingPriceId(price.id)
    setEndDateTime(localNowInput())
    setPriceError('')
  }

  const handleConfirmEndPrice = async (price) => {
    if (!priceModalService || !endDateTime) return
    setBusy(true)
    setPriceError('')
    try {
      const payload = {
        validTo: utcString(endDateTime),
        version: price.version,
      }
      const updatedList = await endServicePrice(priceModalService.id, price.id, payload)
      setPrices(updatedList)
      setEndingPriceId(null)
      const effective = updatedList.find((p) => p.isEffective)
      setServices((prev) =>
        prev.map((s) => (s.id === priceModalService.id ? { ...s, currentPrice: effective ? effective.amount : null } : s))
      )
      setSuccessMsg('Đã kết thúc mức giá thành công')
    } catch (err) {
      if (err.status === 401) onSessionExpired?.()
      else setPriceError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Top Banner / Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-clinical-ink sm:text-3xl">
            Dịch vụ & giá cận lâm sàng
          </h1>
          <p className="mt-1 text-sm text-clinical-muted">
            Quản lý danh mục dịch vụ khám và cận lâm sàng (CLS), phòng xét nghiệm thực hiện và bảng giá có hiệu lực.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={loadData} disabled={loading} title="Tải lại danh sách">
            <FontAwesomeIcon icon={faRotateRight} className={loading ? 'animate-spin' : ''} />
            <span className="hidden sm:inline">Làm mới</span>
          </Button>
          <Button variant="primary" onClick={handleOpenCreate}>
            <FontAwesomeIcon icon={faPlus} />
            <span>Thêm dịch vụ</span>
          </Button>
        </div>
      </div>

      {/* Alerts */}
      {successMsg && (
        <div className="flex items-center justify-between rounded-xl border border-clinical-leaf bg-clinical-leaf-soft p-4 text-sm font-medium text-clinical-success">
          <div className="flex items-center gap-2">
            <FontAwesomeIcon icon={faCheck} />
            <span>{successMsg}</span>
          </div>
          <button onClick={() => setSuccessMsg('')} className="text-clinical-leaf hover:opacity-80">
            <FontAwesomeIcon icon={faXmark} />
          </button>
        </div>
      )}

      {error && (
        <div className="flex items-center justify-between rounded-xl border border-clinical-danger bg-rose-50 p-4 text-sm text-clinical-danger">
          <span>{error}</span>
          <button onClick={() => setError('')} className="text-clinical-danger hover:opacity-80">
            <FontAwesomeIcon icon={faXmark} />
          </button>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="flex flex-col gap-3 rounded-2xl border border-clinical-border bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-wrap items-center gap-3">
          <div className="relative min-w-[200px] flex-1">
            <FontAwesomeIcon icon={faMagnifyingGlass} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-clinical-muted" />
            <input
              type="text"
              placeholder="Tìm theo mã hoặc tên dịch vụ..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="min-h-11 w-full rounded-xl border border-clinical-border-strong bg-white pl-10 pr-3 py-2 text-sm text-clinical-ink focus:border-suncare-700 focus:outline-none"
            />
          </div>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="min-h-11 rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus:border-suncare-700 focus:outline-none"
          >
            <option value="">Tất cả loại dịch vụ</option>
            <option value="CLS">Cận lâm sàng (CLS)</option>
            <option value="EXAMINATION">Khám bệnh</option>
          </select>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="min-h-11 rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm text-clinical-ink focus:border-suncare-700 focus:outline-none"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="active">Đang hoạt động</option>
            <option value="inactive">Ngừng hoạt động</option>
          </select>
        </div>
        <div className="text-xs font-medium text-clinical-muted">
          Tổng số: <span className="font-bold text-clinical-ink">{filteredServices.length}</span> dịch vụ
        </div>
      </div>

      {/* Services Table */}
      <div className="overflow-hidden rounded-2xl border border-clinical-border bg-white shadow-sm">
        {loading ? (
          <div className="p-12 text-center text-clinical-muted">
            <FontAwesomeIcon icon={faRotateRight} className="animate-spin text-2xl" />
            <p className="mt-3 text-sm">Đang nạp danh mục dịch vụ...</p>
          </div>
        ) : filteredServices.length === 0 ? (
          <div className="p-12 text-center text-clinical-muted">
            <FontAwesomeIcon icon={faStethoscope} className="text-3xl text-suncare-300" />
            <p className="mt-3 text-sm font-semibold text-clinical-ink">Không tìm thấy dịch vụ nào</p>
            <p className="mt-1 text-xs">Hãy thử thay đổi điều kiện lọc hoặc tạo dịch vụ mới.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-clinical-ink">
              <thead className="border-b border-clinical-border bg-suncare-50/50 text-xs font-semibold uppercase text-clinical-muted">
                <tr>
                  <th scope="col" className="px-5 py-3.5">Mã & Tên dịch vụ</th>
                  <th scope="col" className="px-4 py-3.5">Loại</th>
                  <th scope="col" className="px-4 py-3.5">Chuyên khoa</th>
                  <th scope="col" className="px-4 py-3.5">Phòng hỗ trợ</th>
                  <th scope="col" className="px-4 py-3.5">Giá hiện hành</th>
                  <th scope="col" className="px-4 py-3.5">Trạng thái</th>
                  <th scope="col" className="px-5 py-3.5 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-clinical-border">
                {filteredServices.map((srv) => (
                  <tr key={srv.id} className="hover:bg-suncare-50/30 transition-colors">
                    <td className="px-5 py-4">
                      <div className="font-bold text-clinical-ink">{srv.name}</div>
                      <div className="font-mono text-xs text-clinical-muted">{srv.code}</div>
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      {srv.serviceType === 'EXAMINATION' ? (
                        <Badge tone="warning">
                          <FontAwesomeIcon icon={faStethoscope} className="mr-1" />
                          Khám bệnh
                        </Badge>
                      ) : (
                        <Badge tone="neutral">
                          <FontAwesomeIcon icon={srv.resultType === 'IMAGING' ? faStethoscope : faFlaskVial} className="mr-1 text-suncare-700" />
                          CLS {srv.resultType ? `(${srv.resultType})` : ''}
                        </Badge>
                      )}
                    </td>
                    <td className="px-4 py-4 text-xs text-clinical-muted">
                      {srv.specialtyName || '—'}
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      {srv.serviceType === 'CLS' ? (
                        <button
                          type="button"
                          onClick={() => handleOpenRooms(srv)}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-clinical-border bg-clinical-canvas px-2.5 py-1 text-xs font-medium text-clinical-ink hover:border-suncare-700 hover:bg-suncare-50"
                        >
                          <FontAwesomeIcon icon={faDoorOpen} className="text-suncare-700" />
                          <span>{srv.supportedRoomsCount} phòng</span>
                        </button>
                      ) : (
                        <span className="text-xs text-clinical-muted">Phòng theo ca</span>
                      )}
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap font-medium">
                      {srv.serviceType === 'CLS' ? (
                        <button
                          type="button"
                          onClick={() => handleOpenPrices(srv)}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-clinical-border bg-clinical-canvas px-2.5 py-1 text-xs font-bold text-clinical-ink hover:border-suncare-700 hover:bg-suncare-50"
                        >
                          <FontAwesomeIcon icon={faTags} className="text-clinical-leaf" />
                          <span>{formatCurrency(srv.currentPrice)}</span>
                        </button>
                      ) : (
                        <span className="text-xs text-clinical-muted italic">Theo bác sĩ (P12)</span>
                      )}
                    </td>
                    <td className="px-4 py-4 whitespace-nowrap">
                      <Badge tone={srv.isActive ? 'success' : 'danger'}>
                        {srv.isActive ? 'Đang hoạt động' : 'Ngừng hoạt động'}
                      </Badge>
                    </td>
                    <td className="px-5 py-4 text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => handleOpenEdit(srv)}
                          className="rounded-lg p-2 text-clinical-muted hover:bg-suncare-100 hover:text-clinical-ink"
                          title="Chỉnh sửa thông tin"
                        >
                          <FontAwesomeIcon icon={faPenToSquare} />
                        </button>
                        {srv.serviceType === 'CLS' && (
                          <>
                            <button
                              type="button"
                              onClick={() => handleOpenRooms(srv)}
                              className="rounded-lg p-2 text-clinical-muted hover:bg-suncare-100 hover:text-clinical-ink"
                              title="Cấu hình phòng thực hiện"
                            >
                              <FontAwesomeIcon icon={faDoorOpen} />
                            </button>
                            <button
                              type="button"
                              onClick={() => handleOpenPrices(srv)}
                              className="rounded-lg p-2 text-clinical-muted hover:bg-suncare-100 hover:text-clinical-ink"
                              title="Bảng giá CLS"
                            >
                              <FontAwesomeIcon icon={faTags} />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* --- Modal: Thêm / Sửa Dịch Vụ --- */}
      {serviceModalMode && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-clinical-ink/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl border border-clinical-border bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-clinical-border px-6 py-4">
              <h2 className="text-lg font-bold text-clinical-ink">
                {serviceModalMode === 'create' ? 'Thêm dịch vụ mới' : `Chỉnh sửa: ${editingService?.code}`}
              </h2>
              <button onClick={handleCloseServiceModal} className="rounded-lg p-1 text-clinical-muted hover:bg-suncare-100">
                <FontAwesomeIcon icon={faXmark} />
              </button>
            </div>
            <form onSubmit={handleSaveService} className="p-6 space-y-4">
              {formError && (
                <div className="rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger">
                  {formError}
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-clinical-muted mb-1">Mã dịch vụ *</label>
                <input
                  type="text"
                  required
                  disabled={serviceModalMode === 'edit'}
                  value={serviceForm.code}
                  onChange={(e) => setServiceForm({ ...serviceForm, code: e.target.value.toUpperCase() })}
                  placeholder="Ví dụ: CLS-XQUANG-NGUC, KHAM-NOI"
                  className={`${field} font-mono uppercase ${serviceModalMode === 'edit' ? 'bg-suncare-50 text-clinical-muted cursor-not-allowed' : ''}`}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-clinical-muted mb-1">Tên dịch vụ *</label>
                <input
                  type="text"
                  required
                  value={serviceForm.name}
                  onChange={(e) => setServiceForm({ ...serviceForm, name: e.target.value })}
                  placeholder="Ví dụ: Chụp X-Quang ngực thẳng"
                  className={field}
                />
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label className="block text-xs font-semibold text-clinical-muted mb-1">Loại dịch vụ *</label>
                  <select
                    disabled={serviceModalMode === 'edit'}
                    value={serviceForm.serviceType}
                    onChange={(e) => setServiceForm({ ...serviceForm, serviceType: e.target.value })}
                    className={`${field} ${serviceModalMode === 'edit' ? 'bg-suncare-50 text-clinical-muted cursor-not-allowed' : ''}`}
                  >
                    <option value="CLS">Cận lâm sàng (CLS)</option>
                    <option value="EXAMINATION">Khám bệnh</option>
                  </select>
                </div>

                {serviceForm.serviceType === 'CLS' ? (
                  <div>
                    <label className="block text-xs font-semibold text-clinical-muted mb-1">Loại kết quả</label>
                    <select
                      value={serviceForm.resultType}
                      onChange={(e) => setServiceForm({ ...serviceForm, resultType: e.target.value })}
                      className={field}
                    >
                      <option value="">Không yêu cầu kết quả</option>
                      <option value="LAB">Xét nghiệm (LAB)</option>
                      <option value="IMAGING">CĐ hình ảnh (IMAGING)</option>
                    </select>
                  </div>
                ) : (
                  <div>
                    <label className="block text-xs font-semibold text-clinical-muted mb-1">Loại kết quả</label>
                    <div className="min-h-11 flex items-center px-3 text-xs text-clinical-muted bg-suncare-50 rounded-xl border border-clinical-border">
                      Khám bệnh không có kết quả CLS
                    </div>
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-clinical-muted mb-1">Chuyên khoa</label>
                <select
                  value={serviceForm.specialtyId}
                  onChange={(e) => setServiceForm({ ...serviceForm, specialtyId: e.target.value })}
                  className={field}
                >
                  <option value="">Chung (không gắn riêng chuyên khoa)</option>
                  {specialties.map((sp) => (
                    <option key={sp.id} value={sp.id}>
                      {sp.name} ({sp.code})
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="isActiveToggle"
                  checked={serviceForm.isActive}
                  onChange={(e) => setServiceForm({ ...serviceForm, isActive: e.target.checked })}
                  className="h-4 w-4 rounded border-clinical-border text-suncare-700 focus:ring-suncare-700"
                />
                <label htmlFor="isActiveToggle" className="text-sm font-medium text-clinical-ink">
                  Đang hoạt động (cho phép chỉ định / đặt lịch)
                </label>
              </div>

              <div className="flex items-center justify-end gap-3 border-t border-clinical-border pt-4">
                <Button variant="outline" onClick={handleCloseServiceModal} disabled={busy}>
                  Hủy
                </Button>
                <Button variant="primary" type="submit" disabled={busy}>
                  {busy ? 'Đang lưu...' : serviceModalMode === 'create' ? 'Tạo dịch vụ' : 'Lưu thay đổi'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* --- Modal: Cấu hình Phòng thực hiện (RoomService) --- */}
      {roomModalService && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-clinical-ink/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-xl rounded-2xl border border-clinical-border bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-clinical-border px-6 py-4">
              <div>
                <h2 className="text-lg font-bold text-clinical-ink">Phòng thực hiện dịch vụ</h2>
                <p className="text-xs text-clinical-muted">
                  {roomModalService.name} ({roomModalService.code})
                </p>
              </div>
              <button
                onClick={() => setRoomModalService(null)}
                className="rounded-lg p-1 text-clinical-muted hover:bg-suncare-100"
              >
                <FontAwesomeIcon icon={faXmark} />
              </button>
            </div>

            <div className="p-6 space-y-4">
              <p className="text-xs text-clinical-muted leading-relaxed">
                Chỉ các phòng loại <strong>LAB</strong> (thuộc các khoa xét nghiệm, chẩn đoán hình ảnh) mới có thể thực hiện dịch vụ CLS. Bật các phòng được trang bị máy móc phù hợp.
              </p>

              {roomError && (
                <div className="rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger">
                  {roomError}
                </div>
              )}

              {loadingRooms ? (
                <div className="p-8 text-center text-clinical-muted">
                  <FontAwesomeIcon icon={faRotateRight} className="animate-spin text-xl" />
                  <p className="mt-2 text-xs">Đang tải danh sách phòng LAB...</p>
                </div>
              ) : roomMappings.length === 0 ? (
                <div className="p-8 text-center text-clinical-muted">
                  <p className="text-sm">Chưa có phòng LAB nào trong hệ thống.</p>
                  <p className="mt-1 text-xs">Vui lòng tạo phòng loại LAB tại mục Quản lý phòng.</p>
                </div>
              ) : (
                <div className="max-h-80 overflow-y-auto divide-y divide-clinical-border rounded-xl border border-clinical-border">
                  {roomMappings.map((rm) => (
                    <div key={rm.roomId} className="flex items-center justify-between p-3.5 hover:bg-suncare-50/50">
                      <div>
                        <div className="font-semibold text-sm text-clinical-ink">{rm.roomName}</div>
                        <div className="text-xs text-clinical-muted font-mono">{rm.roomCode} · {rm.departmentName}</div>
                      </div>
                      <Button
                        variant={rm.isSupported ? 'primary' : 'outline'}
                        disabled={togglingRoomId === rm.roomId}
                        onClick={() => handleToggleRoom(rm.roomId, rm.isSupported)}
                        className="text-xs py-1.5 min-h-9"
                      >
                        {togglingRoomId === rm.roomId ? (
                          <FontAwesomeIcon icon={faRotateRight} className="animate-spin" />
                        ) : rm.isSupported ? (
                          <>
                            <FontAwesomeIcon icon={faCheck} />
                            <span>Đang hỗ trợ</span>
                          </>
                        ) : (
                          <span>Bật hỗ trợ</span>
                        )}
                      </Button>
                    </div>
                  ))}
                </div>
              )}

              <div className="flex justify-end border-t border-clinical-border pt-4">
                <Button variant="outline" onClick={() => setRoomModalService(null)}>
                  Đóng
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* --- Modal: Bảng giá CLS (ServicePrice) --- */}
      {priceModalService && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-clinical-ink/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-2xl rounded-2xl border border-clinical-border bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-clinical-border px-6 py-4">
              <div>
                <h2 className="text-lg font-bold text-clinical-ink">Bảng giá cận lâm sàng</h2>
                <p className="text-xs text-clinical-muted">
                  {priceModalService.name} ({priceModalService.code})
                </p>
              </div>
              <button
                onClick={() => setPriceModalService(null)}
                className="rounded-lg p-1 text-clinical-muted hover:bg-suncare-100"
              >
                <FontAwesomeIcon icon={faXmark} />
              </button>
            </div>

            <div className="p-6 space-y-6 max-h-[80vh] overflow-y-auto">
              {priceError && (
                <div className="rounded-xl border border-clinical-danger bg-rose-50 p-3 text-sm text-clinical-danger">
                  {priceError}
                </div>
              )}

              {/* Form thêm mức giá mới */}
              <div className="rounded-xl border border-suncare-200 bg-suncare-50/60 p-4">
                <h3 className="text-sm font-bold text-clinical-ink mb-3">Thêm mức giá có hiệu lực mới</h3>
                <form onSubmit={handleCreatePrice} className="space-y-3">
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <div>
                      <label className="block text-xs font-semibold text-clinical-muted mb-1">Số tiền (VNĐ) *</label>
                      <input
                        type="number"
                        min="1"
                        step="1000"
                        required
                        value={priceForm.amount}
                        onChange={(e) => setPriceForm({ ...priceForm, amount: e.target.value })}
                        placeholder="Ví dụ: 150000"
                        className={field}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-clinical-muted mb-1">Hiệu lực từ *</label>
                      <input
                        type="datetime-local"
                        required
                        value={priceForm.validFrom}
                        onChange={(e) => setPriceForm({ ...priceForm, validFrom: e.target.value })}
                        className={field}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-clinical-muted mb-1">Hiệu lực đến</label>
                      <input
                        type="datetime-local"
                        value={priceForm.validTo}
                        onChange={(e) => setPriceForm({ ...priceForm, validTo: e.target.value })}
                        className={field}
                      />
                    </div>
                  </div>
                  <div className="flex items-center justify-between pt-1">
                    <p className="text-xs text-clinical-muted">
                      Khoảng thời gian không được giao cắt với các mức giá đã có. Để trống nếu áp dụng vô hạn.
                    </p>
                    <Button variant="primary" type="submit" disabled={busy} className="text-xs min-h-9 py-1.5">
                      {busy ? 'Đang thêm...' : 'Áp dụng mức giá'}
                    </Button>
                  </div>
                </form>
              </div>

              {/* Danh sách các mức giá */}
              <div>
                <h3 className="text-sm font-bold text-clinical-ink mb-3">Lịch sử bảng giá</h3>
                {loadingPrices ? (
                  <div className="p-6 text-center text-clinical-muted">
                    <FontAwesomeIcon icon={faRotateRight} className="animate-spin text-xl" />
                    <p className="mt-2 text-xs">Đang tải lịch sử giá...</p>
                  </div>
                ) : prices.length === 0 ? (
                  <div className="p-6 text-center text-xs text-clinical-muted border border-dashed rounded-xl border-clinical-border">
                    Chưa có mức giá nào được thiết lập cho dịch vụ này.
                  </div>
                ) : (
                  <div className="overflow-x-auto rounded-xl border border-clinical-border">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-suncare-50 border-b border-clinical-border font-semibold text-clinical-muted">
                        <tr>
                          <th className="px-3.5 py-2.5">Số tiền</th>
                          <th className="px-3.5 py-2.5">Hiệu lực từ</th>
                          <th className="px-3.5 py-2.5">Hiệu lực đến</th>
                          <th className="px-3.5 py-2.5">Trạng thái</th>
                          <th className="px-3.5 py-2.5 text-right">Thao tác</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-clinical-border">
                        {prices.map((pr) => (
                          <tr key={pr.id} className={pr.isEffective ? 'bg-clinical-leaf-soft/30' : ''}>
                            <td className="px-3.5 py-3 font-bold text-clinical-ink">
                              {formatCurrency(pr.amount)}
                            </td>
                            <td className="px-3.5 py-3 text-clinical-ink">
                              {formatDateTime(pr.validFrom)}
                            </td>
                            <td className="px-3.5 py-3 text-clinical-muted">
                              {formatDateTime(pr.validTo)}
                            </td>
                            <td className="px-3.5 py-3">
                              {pr.isEffective ? (
                                <Badge tone="success">Đang áp dụng</Badge>
                              ) : pr.validTo && new Date(pr.validTo.endsWith('Z') ? pr.validTo : `${pr.validTo}Z`) < new Date() ? (
                                <Badge tone="danger">Đã kết thúc</Badge>
                              ) : (
                                <Badge tone="neutral">Chưa hiệu lực</Badge>
                              )}
                            </td>
                            <td className="px-3.5 py-3 text-right">
                              {(!pr.validTo || new Date(pr.validTo.endsWith('Z') ? pr.validTo : `${pr.validTo}Z`) > new Date()) && (
                                endingPriceId === pr.id ? (
                                  <div className="flex items-center justify-end gap-1.5">
                                    <input
                                      type="datetime-local"
                                      value={endDateTime}
                                      onChange={(e) => setEndDateTime(e.target.value)}
                                      className="min-h-8 rounded-lg border border-clinical-border-strong px-2 py-1 text-xs"
                                    />
                                    <Button
                                      variant="danger"
                                      disabled={busy}
                                      onClick={() => handleConfirmEndPrice(pr)}
                                      className="text-xs min-h-8 py-1 px-2.5"
                                    >
                                      Xác nhận
                                    </Button>
                                    <button
                                      type="button"
                                      onClick={() => setEndingPriceId(null)}
                                      className="text-xs text-clinical-muted hover:underline p-1"
                                    >
                                      Hủy
                                    </button>
                                  </div>
                                ) : (
                                  <button
                                    type="button"
                                    onClick={() => handleOpenEndPrice(pr)}
                                    className="text-xs font-semibold text-clinical-danger hover:underline"
                                  >
                                    Kết thúc sớm
                                  </button>
                                )
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="flex justify-end border-t border-clinical-border pt-4">
                <Button variant="outline" onClick={() => setPriceModalService(null)}>
                  Đóng
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
