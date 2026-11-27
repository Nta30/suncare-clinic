import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCapsules, faPen, faTrashCan } from '@fortawesome/free-solid-svg-icons'
import { duplicateWarnings, formatDose } from './prescriptionUtils'

export default function PrescriptionDraftList({ items, disabled, errors = {}, onEdit, onRemove }) {
  return <div className='min-w-0 space-y-3 border-t border-clinical-border pt-4'>
    <div className='flex items-center justify-between gap-2'><h4 className='text-sm font-bold'>Danh sách thuốc</h4><span className='text-xs text-clinical-muted'>{items.length} thuốc</span></div>
    {items.length === 0 && <p className='rounded-lg border border-dashed border-clinical-border p-4 text-sm text-clinical-muted'>Chưa có thuốc trong đơn. Chọn thuốc từ danh mục để thêm khi cần.</p>}
    <ol className='min-w-0 space-y-3'>{items.map((item, index) => {
      const warnings = duplicateWarnings(items, item, index)
      const fieldErrors = Object.entries(errors).filter(([key]) => key.startsWith('items[' + index + '].')).map(([, message]) => message)
      return <li key={item.uiId} className='min-w-0 space-y-3 rounded-lg border border-clinical-border p-3'>
        <div className='flex items-start gap-2'>
          <span className='grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-clinical-leaf-soft text-clinical-leaf'><FontAwesomeIcon icon={faCapsules} /></span>
          <div className='min-w-0 flex-1'><h5 className='break-words text-sm font-bold'>{index + 1}. {item.drugName} {item.strength || ''}</h5>
            <p className='mt-1 break-words text-xs text-clinical-muted'>{item.dosageForm} · {item.route}</p>
            {item.ingredient && <p className='mt-1 break-words text-xs text-clinical-muted'>Hoạt chất: {item.ingredient}</p>}
          </div>
          <button type='button' disabled={disabled} aria-label={'Sửa thuốc dòng ' + (index + 1)} onClick={() => onEdit(index)}
            className='grid h-10 w-10 shrink-0 place-items-center rounded-lg text-clinical-muted hover:bg-clinical-canvas disabled:opacity-50'><FontAwesomeIcon icon={faPen} /></button>
          <button type='button' disabled={disabled} aria-label={'Xóa thuốc dòng ' + (index + 1)} onClick={() => onRemove(index)}
            className='grid h-10 w-10 shrink-0 place-items-center rounded-lg text-clinical-danger hover:bg-rose-50 disabled:opacity-50'><FontAwesomeIcon icon={faTrashCan} /></button>
        </div>
        <dl className='grid min-w-0 grid-cols-2 gap-3 text-xs sm:grid-cols-4'>
          {[['Liều dùng', item.doseUnit ? formatDose(item.dose, item.doseUnit) : item.dose], ['Tần suất', item.frequency], ['Thời gian', item.durationDays + ' ngày'], ['Số lượng cấp phát', String(item.quantity).replace('.', ',') + ' ' + (item.dispensingUnit || item.unit)]].map(([title, value]) =>
            <div key={title} className='min-w-0'><dt className='text-clinical-muted'>{title}</dt><dd className='mt-1 break-words font-medium'>{value}</dd></div>)}
        </dl>
        {(!item.doseUnit || !item.dispensingUnit) && <p className='text-xs text-clinical-warning'>Đơn cũ chưa tách đơn vị; cần mở sửa và xác nhận lại trước khi lưu/phát hành.</p>}
        {item.instruction && <p className='whitespace-pre-wrap break-words text-xs'><span className='text-clinical-muted'>Hướng dẫn: </span>{item.instruction}</p>}
        {warnings.length > 0 && <div className='space-y-1 text-xs text-clinical-warning'>{warnings.map((text) => <p key={text}>{text}</p>)}</div>}
        {fieldErrors.length > 0 && <div role='alert' className='space-y-1 text-xs text-clinical-danger'>{fieldErrors.map((text) => <p key={text}>{text}</p>)}</div>}
      </li>
    })}</ol>
  </div>
}
