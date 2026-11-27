import { useId, useRef, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faPlus, faCheck } from '@fortawesome/free-solid-svg-icons'
import Button from '../../components/ui/Button'
import RouteLookup from './RouteLookup'
import { suggestQuantity, duplicateWarnings, validatePrescriptionItem } from './prescriptionUtils'

const inputClass = 'mt-1 min-h-10 w-full min-w-0 rounded-xl border border-clinical-border-strong bg-white px-3 py-2 text-sm disabled:bg-clinical-canvas read-only:bg-clinical-canvas read-only:text-clinical-muted'
const definitions = [['dose', 'Liều mỗi lần *', 200], ['route', 'Đường dùng *', 100], ['frequency', 'Tần suất sử dụng *', 200]]

const frequencyOptions = ['1 lần/ngày', '2 lần/ngày', '3 lần/ngày', '4 lần/ngày']

function Field({ label, error, children }) {
  const id = useId()
  return <div className='min-w-0'><label htmlFor={id} className='block text-xs font-semibold'>{label}</label>
    {children(id, error ? `${id}-error` : undefined)}
    {error && <p id={`${id}-error`} className='mt-1 text-xs text-clinical-danger'>{error}</p>}
  </div>
}

export default function PrescriptionItemEditor({ item, items, index, disabled, onChange, onApply, onCancel }) {
  const [errors, setErrors] = useState(item.fieldErrors || {})
  const id = useId()
  const formRef = useRef(null)
  const suggested = suggestQuantity(item)
  const duplicates = duplicateWarnings(items, item, index)
  function blur(key) {
    const message = validatePrescriptionItem(item)[key]
    setErrors((previous) => ({ ...previous, [key]: message }))
  }
  function change(key, value) {
    onChange(key, value)
    if (errors[key]) setErrors((previous) => ({ ...previous, [key]: validatePrescriptionItem({ ...item, [key]: value })[key] }))
  }
  function propsFor(key, fieldId, description) {
    return { id: fieldId, name: key, 'aria-invalid': Boolean(errors[key]), 'aria-describedby': description,
      className: inputClass + (errors[key] ? ' border-clinical-danger ring-1 ring-clinical-danger' : ''),
      onBlur: () => blur(key) }
  }
  function submit(event) {
    event.preventDefault()
    const errors = validatePrescriptionItem(item)
    setErrors(errors)
    if (!Object.keys(errors).length) onApply()
    else requestAnimationFrame(() => formRef.current?.querySelector('[aria-invalid=true]')?.focus())
  }
  return <form ref={formRef} noValidate aria-label='Kê thuốc đã chọn' onSubmit={submit} className='space-y-3 rounded-lg border border-clinical-border bg-clinical-canvas p-3 sm:p-4'>
    <div className='flex flex-wrap items-start justify-between gap-2'><div className='min-w-0'>
      <h4 className='break-words text-sm font-bold'>{item.drugName} {item.strength || ''}</h4>
      <p className='mt-1 break-words text-xs text-clinical-muted'>{item.dosageForm}</p>
    </div><Button disabled={disabled} onClick={onCancel} className='min-h-9 px-3 py-1.5'>Bỏ chỉnh sửa</Button></div>
    {duplicates.length > 0 && <div role='status' className='space-y-1 text-xs text-clinical-warning'>{duplicates.map((text) => <p key={text}>{text} Kiểm tra trước khi kê.</p>)}</div>}
    <fieldset disabled={disabled} className='min-w-0 space-y-3'>
      {errors.drugId && <p role='alert' className='text-xs text-clinical-danger'>{errors.drugId}</p>}
      <div className='grid gap-3 sm:grid-cols-2'>
        {definitions.map(([key, label]) => <Field key={key} label={label} error={errors[key]}>{(fieldId, description) =>
          key === 'route' ? <RouteLookup {...propsFor(key, fieldId, description)} value={item.route}
            placeholder='Chọn hoặc nhập đường dùng' onChange={(value) => change(key, value)} /> :
          key === 'dose' ? <div className={'prescription-dose-field mt-1 flex min-h-10 min-w-0 rounded-xl border bg-white text-sm' + (errors.dose ? ' border-clinical-danger ring-1 ring-clinical-danger' : ' border-clinical-border-strong')}>
            <input {...propsFor(key, fieldId, description)} aria-describedby={[`${fieldId}-unit`, description].filter(Boolean).join(' ')}
              className='min-w-0 flex-1 rounded-l-xl border-0 bg-transparent px-3 py-2 disabled:bg-clinical-canvas'
              inputMode='decimal' placeholder='Ví dụ: 1 hoặc 0,5' value={item.dose} onChange={(event) => change(key, event.target.value)} />
            <span id={`${fieldId}-unit`} className='flex max-w-1/2 shrink-0 items-center break-words rounded-r-xl border-l border-clinical-border bg-clinical-canvas px-3 py-2 text-clinical-muted'>
              {item.doseUnit || 'Chưa xác nhận'}
            </span>
          </div> :
          <input {...propsFor(key, fieldId, description)} list={`${id}-${key}`} placeholder='Số lần hoặc lịch dùng'
            value={item[key]} onChange={(event) => change(key, event.target.value)} />}</Field>)}
        <Field label='Số ngày điều trị *' error={errors.durationDays}>{(fieldId, description) =>
          <input {...propsFor('durationDays', fieldId, description)} inputMode='numeric' placeholder='Số ngày' value={item.durationDays}
            onChange={(event) => change('durationDays', event.target.value)} />}</Field>
        <Field label='Số lượng thuốc *' error={errors.quantity}>{(fieldId, description) =>
          <input {...propsFor('quantity', fieldId, description)} inputMode='decimal' placeholder='Số lượng'
            value={item.quantity} onChange={(event) => change('quantity', event.target.value)} />}</Field>
        <Field label='Đơn vị cấp phát'>{(fieldId) => <input id={fieldId} readOnly value={item.dispensingUnit || ''} className={inputClass} />}</Field>
      </div>
      <datalist id={`${id}-frequency`}>{frequencyOptions.map((text) => <option key={text} value={text} />)}</datalist>
      {suggested != null && <div className='flex flex-wrap items-center gap-2 text-xs text-clinical-leaf'>
        <span>Gợi ý từ cách dùng đã nhập: {suggested.replace('.', ',')} {item.dispensingUnit}.</span>
        <button type='button' onClick={() => change('quantity', suggested)} className='min-h-9 rounded-lg px-2 font-semibold underline underline-offset-4'>Dùng số lượng gợi ý</button>
      </div>}
      {item.doseUnit && item.dispensingUnit && item.doseUnit.toLowerCase() !== item.dispensingUnit.toLowerCase() &&
        <p className='text-xs text-clinical-muted'>Hai đơn vị khác nhau; bác sĩ nhập số lượng cấp phát. Chưa có quy đổi để gợi ý tự động.</p>}
      <Field label='Hướng dẫn sử dụng' error={errors.instruction}>{(fieldId, description) =>
        <textarea {...propsFor('instruction', fieldId, description)} rows={2} placeholder='Hướng dẫn riêng cho thuốc…' value={item.instruction || ''}
          onChange={(event) => change('instruction', event.target.value)} />}</Field>
      <div className='flex justify-end'><Button type='submit' className='min-h-9 py-1.5'>
        <FontAwesomeIcon icon={index < 0 ? faPlus : faCheck} />{index < 0 ? 'Thêm thuốc vào đơn' : 'Áp dụng chỉnh sửa'}
      </Button></div>
    </fieldset>
  </form>
}
