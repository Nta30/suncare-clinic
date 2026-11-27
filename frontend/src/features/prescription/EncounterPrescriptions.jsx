import { useEffect, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCapsules, faTriangleExclamation, faRotateRight } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import { getPrescriptions, getPrescribingDrugs, sendPrescriptionCommand } from '../../services/prescriptionApi'
import PrescriptionConfirmDialog from './PrescriptionConfirmDialog'
import PrescriptionLines from './PrescriptionLines'
import DrugLookup from './DrugLookup'
import PrescriptionItemEditor from './PrescriptionItemEditor'
import PrescriptionDraftList from './PrescriptionDraftList'
import { validatePrescriptionItem } from './prescriptionUtils'

const toForm = (document) => ({ instruction: document?.instruction || '', items: (document?.items || []).map((item) => ({ ...item, quantity: String(item.quantity), uiId: crypto.randomUUID() })) })
const bodyFor = (form, version) => ({
  version, instruction: form.instruction.trim(),
  items: form.items.map((item) => ({
    drugId: item.drugId, dose: item.dose.trim(), route: item.route.trim(), frequency: item.frequency.trim(),
    durationDays: Number(item.durationDays), quantity: String(item.quantity).replace(',', '.'),
    instruction: (item.instruction || '').trim(),
  })),
})
const severityLabels = { UNKNOWN: 'Chưa xác định mức độ', MILD: 'Mức độ nhẹ', MODERATE: 'Mức độ vừa', SEVERE: 'Mức độ nặng' }
const formSignature = (form, version) => JSON.stringify({
  body: bodyFor(form, version),
  snapshots: form.items.map((item) => ['drugName', 'ingredient', 'strength', 'dosageForm', 'unit', 'doseUnit', 'dispensingUnit'].map((key) => item[key] || null)),
})


export default function EncounterPrescriptions({ encounterId, active, writable, onSessionExpired, allergies = [], onGuardChange }) {
  const [state, setState] = useState({ loading: true, drugs: [], document: null })
  const [form, setForm] = useState(() => toForm(null))
  const [reload, setReload] = useState(0)
  const [editor, setEditor] = useState(null)
  const [composing, setComposing] = useState(false)
  const sectionRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(null)
  const [error, setError] = useState(null)
  const [message, setMessage] = useState('')
  const [confirm, setConfirm] = useState(null)
  const busyRef = useRef(false)
  const pendingRef = useRef(null)
  const alive = useRef(false)
  const loaded = useRef(-1)

  const document = state.document
  const draft = document?.status === 'DRAFT' || composing
  const listDirty = draft && (!document || formSignature(form, document.version) !== formSignature(toFormWithoutIds(document), document.version))

  const dirty = Boolean(editor) || listDirty

  useEffect(() => {
    onGuardChange?.('prescription', Boolean(dirty || busy || pending))
    return () => onGuardChange?.('prescription', false)
  }, [dirty, busy, pending, onGuardChange])

  useEffect(() => {
    alive.current = true
    return () => { alive.current = false }
  }, [])

  useEffect(() => {
    if (!active || loaded.current === reload) return undefined
    let current = true
    Promise.allSettled([getPrescriptions(encounterId), getPrescribingDrugs()]).then(([documents, catalog]) => {
      if (!current) return
      const failures = [documents, catalog].filter((result) => result.status === 'rejected').map((result) => result.reason)
      const expired = failures.find((failure) => failure.status === 401)
      if (expired) { onSessionExpired(expired.message); return }
      if (documents.status === 'rejected') { setState({ loading: false, drugs: [], document: null, error: documents.reason.message }); return }
      if (!Array.isArray(documents.value) || (catalog.status === 'fulfilled' && !Array.isArray(catalog.value))) {
        setState({ drugs: [], document: null, error: 'Dữ liệu đơn thuốc không hợp lệ. Hãy tải lại.' }); return
      }
      const saved = documents.value.find((item) => item.status === 'ISSUED') || documents.value[0] || null
      setState({ document: saved, drugs: catalog.status === 'fulfilled' ? catalog.value : [], drugError: catalog.status === 'rejected' ? catalog.reason.message : '' })
      setForm(toForm(saved))
      setEditor(null)
      setComposing(false)
      loaded.current = reload
    })
    return () => { current = false }
  }, [active, encounterId, reload, onSessionExpired])

  useEffect(() => {
    if (!dirty && !pending) return undefined
    const warn = (event) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, pending])

  function refresh() {
    if (busyRef.current || pendingRef.current) return
    if (dirty && !window.confirm('Tải lại đơn sẽ bỏ thay đổi thuốc chưa lưu. Tiếp tục?')) return
    setState((previous) => ({ ...previous, loading: true, error: '' }))
    setError(null)
    setMessage('')
    setReload((previous) => previous + 1)
  }

  async function run(action) {
    if (busyRef.current || (!pendingRef.current && !writable)) return
    if (!pendingRef.current && action === 'issue' && (!document || !draft || dirty || !document.items.length || state.drugError || error?.blocked)) return
    let command = pendingRef.current
    if (!command) {
      command = { action, id: action === 'create' ? null : document.id, key: crypto.randomUUID(),
        body: action === 'save' ? bodyFor(form, document.version) : action === 'create' ? { instruction: form.instruction.trim(), items: bodyFor(form).items } : { version: document.version } }
      pendingRef.current = command
    }
    busyRef.current = true
    setBusy(true)
    setPending(command.action)
    setError(null)
    setMessage('')
    setConfirm(null)
    try {
      const result = await sendPrescriptionCommand(encounterId, command)
      if (command.action !== 'discard' && (!result.id || !Array.isArray(result.items))) throw new Error('Chưa đọc được kết quả. Hãy thử lại yêu cầu đang chờ.')
      if (command.action === 'discard' && !result.discarded) throw new Error('Chưa xác minh được việc hủy kê đơn.')
      if (!alive.current) return
      const saved = command.action === 'discard' ? null : result
      setState((previous) => ({ ...previous, document: saved }))
      setForm(toForm(saved))
      setEditor(null)
      setComposing(false)

      pendingRef.current = null
      setPending(null)
      setMessage(result.replayed ? 'Đã lấy lại kết quả đơn thuốc đã được lưu.' : {
        create: 'Đã lưu đơn thuốc. Kiểm tra nội dung trước khi phát hành.',
        save: 'Đã lưu đơn thuốc. Kiểm tra nội dung trước khi phát hành.',
        issue: 'Đã phát hành đơn thuốc.',
        discard: 'Đã hủy kê đơn.',
      }[command.action])
    } catch (failure) {
      if (!alive.current) return
      if ([400, 401, 403, 404, 409, 422].includes(failure.status)) {
        pendingRef.current = null
        setPending(null)
      }
      if (failure.status === 401) onSessionExpired(failure.message)
      else {
        const fields = failure.fieldErrors || {}
        setError({ message: failure.message, fields, blocked: [403, 404, 409].includes(failure.status) })
        openFirstInvalidItem(fields)
        focusInvalid()
      }
    } finally {
      busyRef.current = false
      if (alive.current) setBusy(false)
    }
  }

  function canReplaceEditor() {
    return !editor || window.confirm('Bỏ nội dung thuốc đang soạn để chọn hoặc sửa dòng khác?')
  }

  function chooseDrug(drug) {
    if (disabled || !drug.isActive || !canReplaceEditor()) return false
    if (!drug.doseUnit || !drug.dispensingUnit) {
      setError({ message: 'Thuốc chưa xác nhận đủ đơn vị liều dùng/cấp phát. Cần quản trị viên rà soát danh mục.' })
      return false
    }
    setEditor({ index: null, uiId: crypto.randomUUID(), drugId: drug.id, drugName: drug.name,
      ingredient: drug.ingredient, strength: drug.strength, dosageForm: drug.dosageForm, unit: drug.dispensingUnit,
      doseUnit: drug.doseUnit, dispensingUnit: drug.dispensingUnit,
      dose: '', route: '', frequency: '', durationDays: '', quantity: '', instruction: '' })
    return true
  }

  function editItem(index) {
    if (disabled || !canReplaceEditor()) return
    const item = form.items[index]
    const drug = state.drugs.find((drug) => drug.id === item.drugId && drug.isActive)
    if (!drug) { setError({ message: 'Thuốc đã ngừng sử dụng. Chọn thuốc đang hoạt động để thay thế, hoặc xóa dòng này.' }); return }
    setEditor({ ...item, index, drugName: drug.name, ingredient: drug.ingredient, strength: drug.strength,
      dosageForm: drug.dosageForm, unit: drug.dispensingUnit, doseUnit: drug.doseUnit, dispensingUnit: drug.dispensingUnit })
  }

  function applyItem() {
    if (disabled || !editor) return
    if (editor.index == null && form.items.length >= 100) { setError({ message: 'Mỗi đơn tối đa 100 dòng thuốc.' }); return }
    const index = editor.index
    const item = { ...editor }
    delete item.index
    delete item.fieldErrors
    setForm((previous) => ({ ...previous, items: index == null ? [...previous.items, item] : previous.items.map((row, i) => i === index ? item : row) }))
    setEditor(null)
    setError(null)
    setMessage('')
  }

  function removeItem(index) {
    if (disabled) return
    if (editor && !canReplaceEditor()) return
    setEditor(null)
    setForm((previous) => ({ ...previous, items: previous.items.filter((_, i) => i !== index) }))
    setMessage('')
  }

  function saveDraft() {
    if (editor) {
      setError({ message: 'Thêm hoặc áp dụng thuốc đang soạn trước khi lưu. Bạn cũng có thể bỏ chỉnh sửa dòng này.' })
      setEditor((previous) => ({ ...previous, uiId: crypto.randomUUID(), fieldErrors: validatePrescriptionItem(previous) }))
      focusInvalid('form input')
      return
    }
    const fields = {}
    if (!form.items.length) fields.items = 'Cần thêm ít nhất một thuốc trước khi lưu đơn thuốc.'
    if (form.instruction.length > 2000) fields.instruction = 'Lời dặn chung tối đa 2000 ký tự.'
    form.items.forEach((item, index) => {
      for (const [key, message] of Object.entries(validatePrescriptionItem(item))) fields['items[' + index + '].' + key] = message
    })
    if (Object.keys(fields).length) {
      setError({ message: 'Kiểm tra các trường trước khi lưu đơn thuốc.', fields })
      openFirstInvalidItem(fields)
      focusInvalid()
      return
    }
    run(document ? 'save' : 'create')
  }

  function focusInvalid(fallback = '[role=combobox]') {
    requestAnimationFrame(() => {
      const root = sectionRef.current
      const target = root?.querySelector('[aria-invalid=true]') || root?.querySelector(fallback)
      target?.focus()
    })
  }

  function openFirstInvalidItem(fields) {
    const first = Object.keys(fields).find((key) => /^items\[\d+\]\./.test(key))
    if (!first) return
    const index = Number(first.match(/\d+/)[0])
    if (form.items[index]) setEditor({ ...form.items[index], index, fieldErrors: Object.fromEntries(
      Object.entries(fields).filter(([key]) => key.startsWith('items[' + index + '].')).map(([key, value]) => [key.split('.').at(-1), value])) })
  }

  function cancelPrescribing() {
    if (document) { setConfirm('discard'); return }
    if (editor || form.items.length || form.instruction.trim()) { setConfirm('discard'); return }
    clearLocal()
  }
  function clearLocal() {
    setForm(toForm(null))
    setEditor(null)
    setComposing(false)
    setError(null)
    setMessage('Đã hủy kê đơn.')
  }

  const disabled = busy || Boolean(pending) || state.loading || Boolean(error?.blocked) || !writable
  const issued = document?.status === 'ISSUED'
  const currentAllergies = allergies.filter((item) => item.isActive !== false)
  return <section ref={sectionRef} aria-labelledby='prescription-title' className='prescription-section min-w-0 space-y-4 rounded-xl border border-clinical-border bg-white p-4 sm:p-5'>
    <header className='flex items-start justify-between gap-3'>
      <div className='min-w-0'><h3 id='prescription-title' className='flex items-center gap-2 text-base font-bold'><FontAwesomeIcon icon={faCapsules} className='text-clinical-leaf' />Đơn thuốc ngoại trú</h3>
        <p className='mt-1.5 text-xs text-clinical-muted'>Tra cứu thuốc và chỉ định cách sử dụng cho người bệnh.</p></div>
      <span className={issued ? 'shrink-0 rounded-full bg-clinical-leaf-soft px-2.5 py-1 text-xs font-semibold text-clinical-success' : 'shrink-0 rounded-full bg-suncare-100 px-2.5 py-1 text-xs font-semibold text-clinical-warning'}>{issued ? 'Đã phát hành' : 'Chưa phát hành'}</span>
    </header>
    {currentAllergies.length ? <div role='note' aria-label='Cảnh báo dị ứng khi kê đơn' className='flex gap-2 rounded-lg bg-suncare-100 p-3 text-clinical-warning'>
      <FontAwesomeIcon icon={faTriangleExclamation} className='mt-0.5 shrink-0 text-sm' />
      <div className='min-w-0 text-xs'><p className='font-semibold'>Cảnh báo dị ứng</p>{currentAllergies.map((item) =>
        <p key={item.id} className='mt-1 break-words'>{item.substance || item.otherNote || 'Chất chưa đối chiếu'} · {severityLabels[item.severity] || 'Chưa xác định mức độ'}{item.reaction ? ' · ' + item.reaction : ''}</p>)}</div>
    </div> : <p className='text-xs text-clinical-muted'>Chưa ghi nhận thông tin dị ứng; chưa xác nhận người bệnh không có dị ứng.</p>}
    {state.loading && <p role='status' className='text-sm'>Đang tải đơn thuốc...</p>}
    {state.error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{state.error}</p><Button onClick={refresh}>Thử lại đơn thuốc</Button></div>}
    {state.drugError && <p role='alert' className='text-sm text-clinical-danger'>Chưa tải được danh mục thuốc: {state.drugError} Chọn Tải lại đơn thuốc để thử lại.</p>}
    {message && <p role='status' className='text-xs font-semibold text-clinical-success'>{message}</p>}
    {error && <div role='alert' className='space-y-2 text-sm text-clinical-danger'><p>{error.message}</p>
      {error.blocked && <p>Nội dung nhập vẫn được giữ. Kiểm tra và tải lại đơn thuốc trước khi tiếp tục.</p>}
      {error.fields?.items && <p>{error.fields.items}</p>}</div>}
    {pending && <div role='status' className='space-y-2 rounded-lg border border-clinical-border bg-clinical-canvas p-3 text-sm'>
      <p>{busy ? 'Đang xử lý đơn thuốc...' : 'Yêu cầu chưa xác minh được kết quả. Thử lại sẽ dùng đúng yêu cầu đã gửi.'}</p>
      {!busy && <Button onClick={() => run(pending)}>Thử lại yêu cầu đơn thuốc</Button>}
    </div>}
    {!state.loading && !state.error && <>
      {!writable && !issued && <p className='text-sm text-clinical-muted'>Chỉ được kê hoặc thay đổi đơn khi lượt khám đang khám.</p>}
      {!document && !composing && <div className='space-y-3'><p className='text-sm text-clinical-muted'>Chưa có đơn thuốc</p>
        <p className='text-xs text-clinical-muted'>Đơn thuốc không bắt buộc đối với lượt khám.</p>
        {writable && <Button variant='primary' disabled={disabled} onClick={() => { setComposing(true); setMessage(''); setError(null) }}>Tạo đơn thuốc</Button>}</div>}
      {issued && <PrescriptionLines document={document} />}
      {draft && <>
        {writable && <DrugLookup drugs={state.drugs} disabled={disabled || Boolean(state.drugError)} onSelect={chooseDrug} />}
        {editor && writable && <PrescriptionItemEditor key={editor.uiId} item={editor} items={form.items} index={editor.index ?? -1}
          disabled={disabled || Boolean(state.drugError)} onChange={(key, value) => setEditor((previous) => ({ ...previous, [key]: value }))}
          onApply={applyItem} onCancel={() => setEditor(null)} />}
        <PrescriptionDraftList items={form.items} disabled={disabled || Boolean(state.drugError)} errors={error?.fields} onEdit={editItem} onRemove={removeItem} />
        <div><label htmlFor='prescription-instruction' className='mb-2 block text-xs font-semibold text-clinical-muted'>Lời dặn chung của bác sĩ</label>
          <textarea id='prescription-instruction' rows={3} disabled={disabled} value={form.instruction}
            aria-invalid={Boolean(error?.fields?.instruction)} aria-describedby={error?.fields?.instruction ? 'prescription-instruction-error' : undefined}
            onBlur={() => { if (form.instruction.length > 2000) setError((previous) => ({ ...previous, fields: { ...previous?.fields, instruction: 'Lời dặn chung tối đa 2000 ký tự.' } })) }}
            onChange={(event) => { const value = event.target.value; setForm((previous) => ({ ...previous, instruction: value })); if (value.length <= 2000 && error?.fields?.instruction) setError((previous) => ({ ...previous, fields: { ...previous.fields, instruction: undefined } })) }}
            placeholder='Hướng dẫn sử dụng thuốc, lưu ý trong quá trình điều trị…'
            className={'w-full rounded-lg border bg-white px-3 py-2 text-sm disabled:bg-clinical-canvas ' + (error?.fields?.instruction ? 'border-clinical-danger ring-1 ring-clinical-danger' : 'border-clinical-border-strong')} />
          {error?.fields?.instruction && <p id='prescription-instruction-error' className='mt-1 text-xs text-clinical-danger'>{error.fields.instruction}</p>}
        </div>
      </>}
    </>}
    <footer className='flex flex-wrap items-center justify-between gap-3 border-t border-clinical-border pt-4'>
      <div role='status' className='min-w-0 text-xs text-clinical-muted'>{busy ? 'Đang lưu dữ liệu…' : pending ? 'Có yêu cầu cần xác minh' : !document ? 'Chưa lưu' : dirty ? 'Có thay đổi chưa lưu' : 'Đã lưu'}{issued ? ' · Chỉ đọc' : ''}</div>
      <div className='flex flex-wrap items-center gap-2'>
        <button type='button' onClick={refresh} disabled={busy || Boolean(pending) || state.loading} aria-label='Tải lại đơn thuốc' title='Tải lại đơn thuốc'
          className='grid h-10 w-10 place-items-center rounded-lg text-clinical-muted hover:bg-clinical-canvas disabled:opacity-50'><FontAwesomeIcon icon={faRotateRight} /></button>
        {draft && writable && <>
          <Button disabled={disabled} variant='danger' onClick={cancelPrescribing} className='min-h-10 px-3 py-2'>Hủy kê đơn</Button>
          <Button disabled={disabled || Boolean(state.drugError)} onClick={saveDraft} className='min-h-10 px-3 py-2'>Lưu đơn thuốc</Button>
          <Button disabled={disabled || dirty || !document?.items.length || Boolean(state.drugError)} variant='primary' onClick={() => setConfirm('issue')} className='min-h-10 px-3 py-2'>Phát hành đơn thuốc</Button>
        </>}
      </div>
    </footer>
    {editor && <p className='text-xs text-clinical-muted'>Thêm hoặc áp dụng thuốc đang soạn trước khi lưu/phát hành; bỏ chỉnh sửa nếu không sử dụng.</p>}
    {dirty && !editor && <p className='text-xs text-clinical-muted'>Lưu thay đổi và kiểm tra nội dung trước khi phát hành.</p>}
    <PrescriptionConfirmDialog action={confirm} onClose={() => setConfirm(null)} onConfirm={() => {
      if (confirm === 'discard' && !document) { setConfirm(null); clearLocal() }
      else run(confirm)
    }} />
  </section>
}

function toFormWithoutIds(document) {
  return { instruction: document.instruction || '', items: document.items.map((item) => ({ ...item, quantity: String(item.quantity) })) }
}
