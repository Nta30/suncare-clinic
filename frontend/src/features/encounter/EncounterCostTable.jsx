const money = (value) => value == null ? '—' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(BigInt(value))

export function EncounterBillingMetrics({ examinationAmount, invoicedAmount, unbilledAmount, receivedAmount }) {
  return <dl className='grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 xl:grid-cols-4'>
    {[
      ['Phí khám đã chốt', examinationAmount, 'text-clinical-ink'],
      ['Đã lập hóa đơn', invoicedAmount, 'text-clinical-ink'],
      ['Chưa lập hóa đơn', unbilledAmount, 'text-clinical-warning'],
      ['Thực thu', receivedAmount, 'text-clinical-success'],
    ].map(([label, value, color]) => <div key={label} className='min-w-0 rounded-xl bg-clinical-canvas p-3'><dt className='text-xs text-clinical-muted'>{label}</dt><dd className={`mt-2 break-words text-xl font-bold tabular-nums ${color}`}>{money(value)}</dd></div>)}
  </dl>
}

export default function EncounterCostTable({ lines, label = 'Chi tiết chi phí khám bệnh' }) {
  return <div className='overflow-x-auto' role='region' aria-label={label} tabIndex={0}>
    <table className='w-full min-w-[30rem] text-left text-sm'>
      <thead className='text-xs text-clinical-muted'><tr><th className='py-3 pr-3 font-normal'>Dịch vụ</th><th className='px-3 py-3 font-normal'>SL</th><th className='px-3 py-3 text-right font-normal'>Đơn giá</th><th className='py-3 pl-3 text-right font-normal'>Thành tiền</th></tr></thead>
      <tbody>{lines.map((line, index) => <tr key={line.id ?? index} className='border-t border-clinical-border'>
        <td className='py-3 pr-3'><span className='block font-semibold'>{line.serviceName}</span>{line.status === 'CANCELLED' && <span className='text-xs text-clinical-warning'>Đã hủy khoản thu</span>}</td>
        <td className='whitespace-nowrap px-3 py-3'>{line.quantity} {line.unitName}</td>
        <td className='whitespace-nowrap px-3 py-3 text-right tabular-nums'>{money(line.unitPrice)}</td>
        <td className='whitespace-nowrap py-3 pl-3 text-right font-semibold tabular-nums'>{money(line.amount)}</td>
      </tr>)}</tbody>
    </table>
  </div>
}
