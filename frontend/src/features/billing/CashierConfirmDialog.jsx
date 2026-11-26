import { useEffect, useRef } from 'react'
import Button from '../../components/ui/Button'

export default function CashierConfirmDialog({ open, busy, code, amount, onCancel, onConfirm }) {
  const dialog = useRef(null)
  useEffect(() => {
    const element = dialog.current
    if (open) { if (!element.open) element.showModal(); return undefined }
    const timer = window.setTimeout(() => element.close(), window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 160)
    return () => window.clearTimeout(timer)
  }, [open])
  return <dialog ref={dialog} data-visible={open} onCancel={(event) => { event.preventDefault(); if (!busy) onCancel() }} aria-labelledby='cash-confirm-title' aria-describedby='cash-confirm-description' className='fixed inset-0 m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-md translate-y-1 rounded-2xl border border-clinical-border bg-white p-5 text-clinical-ink opacity-0 shadow-xl transition-[opacity,transform] duration-150 backdrop:bg-slate-950/40 data-[visible=true]:translate-y-0 data-[visible=true]:opacity-100 motion-reduce:transition-none sm:p-6'>
    <h2 id='cash-confirm-title' className='text-lg font-bold'>Xác nhận đã nhận tiền mặt</h2>
    <p id='cash-confirm-description' className='mt-3 break-words text-sm'>Bạn xác nhận đã nhận đủ <strong>{amount}</strong> cho hóa đơn <strong className='break-all'>{code}</strong>?</p>
    <div className='mt-5 flex flex-wrap justify-end gap-2'><Button disabled={busy} onClick={onCancel}>Quay lại</Button><Button variant='primary' disabled={busy} onClick={onConfirm}>{busy ? 'Đang xác nhận…' : 'Xác nhận'}</Button></div>
  </dialog>
}
