import Button from './Button'

export default function ConfirmDialog({ dialogRef, title, description, busy, onCancel, onConfirm }) {
  return (
    <div className='fixed inset-0 z-[70] flex items-center justify-center p-4'>
      <div className='absolute inset-0 bg-slate-950/55' aria-hidden='true' />
      <div ref={dialogRef} role='alertdialog' aria-modal='true' aria-labelledby='confirm-title' aria-describedby='confirm-description' className='relative w-full max-w-md rounded-2xl border border-clinical-border bg-white p-6 shadow-2xl sm:p-7'>
        <h2 id='confirm-title' className='text-xl font-bold text-clinical-ink'>{title}</h2>
        <p id='confirm-description' className='mt-2 text-sm leading-6 text-clinical-muted'>{description}</p>
        <div className='mt-6 flex flex-wrap justify-end gap-2'>
          <Button disabled={busy} onClick={onCancel}>Hủy</Button>
          <Button variant='primary' disabled={busy} onClick={onConfirm}>{busy ? 'Đang xử lý...' : 'Xác nhận'}</Button>
        </div>
      </div>
    </div>
  )
}
