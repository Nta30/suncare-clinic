import { useCallback, useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faFlask, faCalendarDays, faRotateRight, faClock, faUserCheck, faArrowLeft, faPlay, faCircleCheck } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import { actOnLabOrder, getLabAssignments, getLabCounts, getLabOrder, getLabQueue } from '../../services/labApi'
import styles from './LabWorkspace.module.css'

const labels = { READY: 'Chờ tiếp nhận', ACCEPTED: 'Đã tiếp nhận', IN_PROGRESS: 'Đang thực hiện' }
const formatTime = (value) => value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Saigon', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' }) : '—'
function demographics(order) {
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Saigon' })
  const age = order.birthDate ? Number(today.slice(0, 4)) - Number(order.birthDate.slice(0, 4)) - (today.slice(5) < order.birthDate.slice(5) ? 1 : 0) : null
  return [age === null ? 'Chưa có ngày sinh' : `${age} tuổi`, { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác', UNKNOWN: 'Chưa rõ giới tính' }[order.sex] || 'Chưa rõ giới tính'].join(' · ')
}

function OrderFacts({ order, roomName }) {
  const facts = [
    ['Dịch vụ được chỉ định', order.serviceName], ['Mã chỉ định', order.code],
    ['Bác sĩ chỉ định', order.doctorName], ['Thời gian chỉ định', formatTime(order.orderedAt)],
    ['Thanh toán', order.paid ? 'Đã thanh toán đầy đủ' : 'Chưa đủ điều kiện thanh toán'],
    ['Thời gian thanh toán', formatTime(order.fullyPaidAt)], ['Hạn thực hiện', formatTime(order.expiresAt)],
    ['Phòng thực hiện', order.roomName || roomName], ['Nhân viên phụ trách', order.staffName || 'Chưa tiếp nhận'],
    ['Thời gian tiếp nhận', formatTime(order.acceptedAt)]
  ]
  return <dl className={styles.facts}>{facts.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || '—'}</dd></div>)}</dl>
}

function ReceptionDialog({ order, room, busy, error, onClose, onConfirm }) {
  const dialog = useRef(null)
  const timer = useRef(null)
  const [present, setPresent] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer) }, [])
  const valid = order.paid && new Date(order.expiresAt).getTime() > now
  useEffect(() => {
    const opener = document.activeElement
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current.showModal()
    return () => { clearTimeout(timer.current); document.body.style.overflow = overflow; opener?.focus?.() }
  }, [])
  function close() {
    if (busy) return
    dialog.current.classList.add('logout-dialog--closing')
    timer.current = setTimeout(onClose, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180)
  }
  return <dialog ref={dialog} className={`${styles.dialog} logout-dialog`} aria-labelledby='lab-reception-title'
    onCancel={(event) => { event.preventDefault(); close() }}
    onClick={(event) => { if (event.target === event.currentTarget) close() }}>
    <h2 id='lab-reception-title' className='text-xl font-bold'><FontAwesomeIcon icon={faUserCheck} className='mr-2 text-clinical-leaf' />Xác nhận tiếp nhận</h2>
    <p className='my-3 text-sm text-clinical-muted'>Đối chiếu thông tin bệnh nhân trước khi thực hiện.</p>
    <div className='mb-5 rounded-lg bg-clinical-canvas p-4'><strong>{order.patientName}</strong><p>{order.patientCode} · {demographics(order)}</p></div>
    <OrderFacts order={order} roomName={room.roomName} />
    <p className='mt-4'><strong>Nội dung chỉ định: </strong>{order.indication || 'Bác sĩ không ghi yêu cầu bổ sung.'}</p>
    <ul className={styles.checks}>
      {[[order.paid, 'Đã thanh toán đầy đủ'], [new Date(order.expiresAt).getTime() > now, 'Chỉ định còn hạn'], [true, 'Đúng phòng và ca trực']].map(([ok, label]) =>
        <li key={label} className={ok ? '' : 'text-clinical-danger'}><FontAwesomeIcon icon={faCircleCheck} className='mr-2' />{label}{ok ? '' : ' — chưa đáp ứng'}</li>)}
    </ul>
    <label className={styles.check}><input type='checkbox' checked={present} onChange={(event) => setPresent(event.target.checked)} disabled={busy} />Xác nhận đúng bệnh nhân và bệnh nhân đã có mặt.</label>
    {error && <p role='alert' className={`${styles.error} mt-4`}>{error}</p>}
    <div className={styles.footer}><Button onClick={close} disabled={busy}>Hủy</Button><Button variant='primary' disabled={!present || !valid || busy} onClick={onConfirm}>{busy ? 'Đang tiếp nhận...' : 'Xác nhận tiếp nhận'}</Button></div>
  </dialog>
}

// P27 will supply editors using serviceId/resultType and configured ServiceAnalyte data.
function ResultStage({ order }) {
  return <section className={styles.card} aria-labelledby='lab-execution-title'>
    <h2 id='lab-execution-title' className='text-xl font-bold'>Thực hiện dịch vụ</h2>
    <p className='mt-3 font-semibold'>{order.serviceName}</p>
    <p className='mt-2 text-clinical-success'><FontAwesomeIcon icon={faCircleCheck} className='mr-2' />Đã bắt đầu lúc {formatTime(order.startedAt)}.</p>
    <p className={`${styles.message} mt-5`}>Chức năng nhập và phát hành kết quả chưa được mở. Chỉ định vẫn được giữ để bạn tiếp tục công việc.</p>
  </section>
}

export default function LabWorkspace({ account, onSessionExpired }) {
  const [state, setState] = useState({ loading: true, assignments: [], orders: [] })
  const [roomId, setRoomId] = useState(0)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(null)
  const [modal, setModal] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const lock = useRef(false)
  const actionKey = useRef(null)
  const selectionSequence = useRef(0)

  const fail = useCallback((failure) => {
    if (failure.status === 401) onSessionExpired(failure.message)
    else setError(failure.message)
  }, [onSessionExpired])

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      try {
        const assignments = await getLabAssignments(controller.signal)
        const currentRoom = assignments.find((a) => a.roomId === roomId)?.roomId || assignments[0]?.roomId || 0
        const [orders, counts] = await Promise.all([getLabQueue(currentRoom, controller.signal), getLabCounts(currentRoom, controller.signal)])
        if (controller.signal.aborted) return
        setRoomId(currentRoom)
        setState({ assignments, orders, counts, loading: false })
        setSelected((old) => old ? orders.find((o) => o.id === old.id) || null : null)
      } catch (failure) {
        if (!controller.signal.aborted) { setState({ assignments: [], orders: [], loading: false, failed: true }); fail(failure) }
      }
    }
    load()
    return () => controller.abort()
  }, [roomId, revision, fail])

  const room = state.assignments.find((a) => a.roomId === roomId)
  const rows = state.orders.filter((o) => o.acceptedBy === account.staffId || `${o.patientName} ${o.patientCode} ${o.code}`.toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi')))
  function reload() { setError(''); setState((old) => ({ ...old, loading: true })); setRevision((old) => old + 1) }
  async function open(order) {
    if (lock.current || state.loading) return
    lock.current = true
    setBusy(true)
    setError('')
    const sequence = ++selectionSequence.current
    try {
      const detail = await getLabOrder(order.id, order.roomId || roomId)
      if (sequence !== selectionSequence.current) return
      if (detail.status === 'READY') { actionKey.current = null; setModal(detail) }
      else setSelected(detail)
    } catch (failure) { fail(failure); reloadAfterConflict() }
    finally { lock.current = false; setBusy(false) }
  }
  function reloadAfterConflict() { setRevision((old) => old + 1) }
  async function act(action) {
    if (lock.current) return
    const order = action === 'accept' ? modal : selected
    if (!order) return
    lock.current = true
    setBusy(true)
    setError('')
    const data = { roomId: order.roomId || roomId, version: order.version, patientPresent: action === 'accept' }
    const signature = JSON.stringify([order.id, action, data])
    if (actionKey.current?.signature !== signature) actionKey.current = { signature, key: crypto.randomUUID() }
    try {
      const result = await actOnLabOrder(order.id, action, data, actionKey.current.key)
      setSelected(result)
      setModal(null)
      setNotice(action === 'accept' ? 'Đã tiếp nhận bệnh nhân.' : 'Đã bắt đầu thực hiện dịch vụ.')
      actionKey.current = null
    } catch (failure) {
      fail(failure)
      if (failure.status === 409) setModal(null)
    } finally { lock.current = false; setBusy(false); reloadAfterConflict() }
  }

  return <div className={styles.main}>
      {error && <div role='alert' className={styles.error}>{error} <button className='ml-2 underline' onClick={reload} disabled={busy}>Tải lại dữ liệu</button></div>}
      {notice && <p role='status' className='text-sm text-clinical-success'>{notice}</p>}
      {selected ? <>
        <Button className='justify-self-start' disabled={busy} onClick={() => { setSelected(null); reload() }}><FontAwesomeIcon icon={faArrowLeft} />Hàng chờ tiếp nhận</Button>
        <header className={`${styles.card} sticky top-0 z-10`}><div className={styles.toolbar}><div><h1 className='text-xl font-bold'>{selected.patientName}</h1><p className='mt-2 text-sm text-clinical-muted'>{selected.patientCode} · {demographics(selected)} · {selected.code}</p></div><span className={styles.badge}>{labels[selected.status]}</span></div><p className='mt-4 border-t border-clinical-border pt-4 font-semibold'>{selected.serviceName}</p></header>
        <div className={styles.workspace}>
          <aside className={`${styles.card} ${styles.aside}`}><h2 className='mb-5 font-bold'>Thông tin thực hiện</h2><OrderFacts order={selected} /></aside>
          <div className='min-w-0'><div className={styles.steps}>{['Thông tin chỉ định', 'Thực hiện và nhập kết quả', 'Kiểm tra và phát hành'].map((label, i) => <div key={label} aria-current={(selected.status === 'IN_PROGRESS' ? 1 : 0) === i ? 'step' : undefined}><span className='mb-2 block font-bold'>{i + 1}</span>{label}</div>)}</div>
            {selected.status === 'IN_PROGRESS' ? <ResultStage order={selected} /> : <section className={styles.card}><h2 className='text-xl font-bold'>Thông tin chỉ định</h2><p className='mt-5 font-semibold'>Nội dung yêu cầu của bác sĩ</p><p className='mt-3 whitespace-pre-wrap rounded-lg bg-clinical-canvas p-4'>{selected.indication || 'Bác sĩ không ghi yêu cầu bổ sung.'}</p><p className='mt-5 text-sm text-clinical-success'><FontAwesomeIcon icon={faCircleCheck} className='mr-2' />Đã xác nhận bệnh nhân có mặt và tiếp nhận.</p></section>}
            <footer className={`${styles.card} ${styles.footer}`}><Button disabled={busy} onClick={() => { setSelected(null); reload() }}>Quay lại hàng chờ</Button><span className='text-sm text-clinical-muted'>{selected.status === 'IN_PROGRESS' ? '2' : '1'} / 3</span>{selected.status === 'ACCEPTED' && <Button variant='primary' disabled={busy || state.loading || !room || selected.roomId !== roomId || new Date(selected.expiresAt) <= new Date()} onClick={() => act('start')}><FontAwesomeIcon icon={faPlay} />{busy ? 'Đang xử lý...' : 'Bắt đầu thực hiện'}</Button>}</footer>
            {!room && <p className={`${styles.message} mt-3`}>Không có ca trực hợp lệ. Bạn vẫn có thể xem công việc đang phụ trách.</p>}
          </div>
        </div>
      </> : <>
        <header className={styles.toolbar}><div><h1 className='text-2xl font-bold'>Tiếp nhận cận lâm sàng</h1><p className='mt-2 text-sm text-clinical-muted'>Quản lý và thực hiện các chỉ định đã thanh toán.</p></div><Button onClick={reload} disabled={state.loading || busy}><FontAwesomeIcon icon={faRotateRight} />Tải lại</Button></header>
        <section className={styles.card}><label htmlFor='lab-room' className='text-sm font-semibold'>Phòng thực hiện</label><select id='lab-room' className={styles.control} value={roomId} disabled={busy || state.loading || !state.assignments.length} onChange={(event) => { setRoomId(Number(event.target.value)); setState((old) => ({ ...old, orders: [], loading: true })); setError('') }}>
          {!state.assignments.length && <option value='0'>Không có ca trực hợp lệ</option>}{state.assignments.filter((a, i, all) => all.findIndex((b) => b.roomId === a.roomId) === i).map((a) => <option key={a.roomId} value={a.roomId}>{a.roomName} · {a.departmentName}</option>)}</select>
          {room ? <div className={`${styles.toolbar} mt-4 text-sm`}><span><FontAwesomeIcon icon={faCalendarDays} className='mr-2 text-clinical-leaf' />{room.staffName} · {formatTime(room.startsAt)} – {formatTime(room.endsAt)}</span><span className='text-clinical-success'>Đang trực</span></div> : !state.loading && <p className={`${styles.message} mt-4`}>Không có ca trực hợp lệ. Thao tác tiếp nhận và bắt đầu đang bị khóa.</p>}
        </section>
        <div className={styles.stats}>{[['READY', 'Chờ tiếp nhận', faClock], ['ACCEPTED', 'Đã tiếp nhận', faUserCheck], ['IN_PROGRESS', 'Đang thực hiện', faFlask]].map(([status, label, icon]) => <article className={styles.card} key={status}><FontAwesomeIcon icon={icon} className='mb-2 text-clinical-leaf' /><p>{label}</p><strong>{state.loading || state.failed ? '—' : (state.counts?.[status] || 0).toString().padStart(2, '0')}</strong></article>)}</div>
        <section className={styles.card}><div className={styles.toolbar}><h2 className='text-lg font-bold'>Hàng chờ tiếp nhận</h2><span className='text-sm text-clinical-muted'>{!state.loading && !state.failed ? `${rows.length} chỉ định` : ''}</span></div>
          <label className='mt-4 block text-sm' htmlFor='lab-search'>Tìm bệnh nhân hoặc chỉ định</label><input id='lab-search' type='search' className={styles.control} value={search} maxLength={100} onChange={(event) => setSearch(event.target.value)} placeholder='Tên, mã bệnh nhân hoặc mã chỉ định...' />
          {state.loading ? <p role='status' className='py-10'>Đang tải hàng chờ...</p> : state.failed ? <p className='py-10'>Chưa tải được hàng chờ. Hãy thử tải lại dữ liệu.</p> : rows.length ? <div className={styles.scroll}><table className={styles.table}><thead><tr>{['STT', 'Bệnh nhân', 'Mã chỉ định', 'Dịch vụ CLS', 'Thanh toán lúc', 'Hạn thực hiện', 'Trạng thái', 'Thao tác'].map((title) => <th key={title} scope='col'>{title}</th>)}</tr></thead><tbody>{rows.map((order, index) => <tr key={order.id} className={order.acceptedBy === account.staffId ? styles.owned : ''}><td>{index + 1}</td><td><strong>{order.patientName}</strong><small>{order.patientCode}</small></td><td>{order.code}</td><td>{order.serviceName}<small className='text-clinical-success'>{order.paid ? 'Đã thanh toán' : 'Cần kiểm tra thanh toán'}</small></td><td>{formatTime(order.fullyPaidAt)}</td><td>{formatTime(order.expiresAt)}</td><td><span className={styles.badge}>{labels[order.status]}</span>{order.acceptedBy === account.staffId && <small>Bạn đang phụ trách{order.roomName ? ` · ${order.roomName}` : ''}</small>}</td><td><Button variant={order.status === 'READY' ? 'primary' : 'outline'} onClick={() => open(order)} disabled={busy || (order.status === 'READY' && !room)}>{order.status === 'READY' ? 'Tiếp nhận' : 'Tiếp tục thực hiện'}</Button></td></tr>)}</tbody></table></div> : <p className='py-10 text-clinical-muted'>{search ? 'Không tìm thấy chỉ định phù hợp.' : 'Chưa có chỉ định đủ điều kiện tiếp nhận tại phòng này.'}</p>}
        </section>
      </>}
    {modal && room && <ReceptionDialog key={modal.id} order={modal} room={room} busy={busy} error={error} onClose={() => setModal(null)} onConfirm={() => act('accept')} />}
  </div>
}
