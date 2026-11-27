import { formatDose } from './prescriptionUtils'

const time = (value) => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Saigon', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))

export default function PrescriptionLines({ document }) {
  return <article className='min-w-0 space-y-3' aria-label='Nội dung đơn đã lưu'>
    <div className='flex flex-wrap justify-between gap-2 text-sm'><p className='break-all font-semibold'>{document.code}</p>
      <p className={document.status === 'ISSUED' ? 'font-semibold text-clinical-success' : 'text-clinical-warning'}>{document.status === 'ISSUED' ? 'Đã phát hành' : 'Chưa phát hành'}</p></div>
    {document.issuedAt && <p className='text-sm'>Phát hành lúc {time(document.issuedAt)}</p>}
    {document.items.length === 0 ? <p className='text-sm text-clinical-muted'>Đơn thuốc chưa có thuốc.</p> : <ol className='divide-y divide-clinical-border'>
      {document.items.map((item) => <li key={item.id} className='space-y-1 py-3 text-sm'>
        <p className='break-words font-bold'>{item.lineNo}. {item.drugName} {item.strength || ''}</p>
        <p className='break-words text-clinical-muted'>{[item.ingredient, item.dosageForm].filter(Boolean).join(' · ')}</p>
        <p className='break-words'>Liều: {item.doseUnit ? formatDose(item.dose, item.doseUnit) : item.dose} · Đường dùng: {item.route} · Số lần: {item.frequency}</p>
        <p className='break-words'>Số ngày: {item.durationDays} · Số lượng cấp phát: {item.quantity} {item.dispensingUnit || item.unit}</p>
        {(!item.doseUnit || !item.dispensingUnit) && <p className='text-xs text-clinical-muted'>Đơn cũ chỉ lưu đơn vị chung: {item.unit}. Giữ nguyên nội dung đã phát hành.</p>}
        {item.instruction && <p className='whitespace-pre-wrap break-words'>Cách dùng thêm: {item.instruction}</p>}
      </li>)}
    </ol>}
    {document.instruction && <p className='whitespace-pre-wrap break-words border-t border-clinical-border pt-3 text-sm'><strong>Dặn dò đơn thuốc: </strong>{document.instruction}</p>}
  </article>
}
