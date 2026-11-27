import { useEffect, useRef } from 'react'
import Button from '../../components/ui/Button'

export default function PrescriptionConfirmDialog({ action, onClose, onConfirm }) {
  const dialogRef = useRef(null)
  const timerRef = useRef(null)
  const closingRef = useRef(false)
  useEffect(() => {
    if (action) { closingRef.current = false; dialogRef.current?.showModal() }
    return () => window.clearTimeout(timerRef.current)
  }, [action])
  function close(confirm = false) {
    const dialog = dialogRef.current
    if (!dialog?.open || closingRef.current) return
    closingRef.current = true
    dialog.classList.add('logout-dialog--closing')
    timerRef.current = window.setTimeout(() => {
      dialog.close()
      dialog.classList.remove('logout-dialog--closing')
      if (confirm) onConfirm()
      else onClose()
    }, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 180)
  }
  const issue = action === 'issue'
  return <dialog ref={dialogRef} aria-labelledby='prescription-confirm-title' aria-describedby='prescription-confirm-text'
    onCancel={(event) => { event.preventDefault(); close() }}
    className='logout-dialog m-auto w-[min(28rem,calc(100%-2rem))] rounded-xl border border-clinical-border bg-white p-5 text-clinical-ink shadow-xl backdrop:bg-clinical-ink/60'>
    <h3 id='prescription-confirm-title' className='text-lg font-bold'>{issue ? 'Phát hành đơn thuốc?' : 'Hủy kê đơn?'}</h3>
    <p id='prescription-confirm-text' className='mt-3 text-sm leading-6'>{issue ? 'Kiểm tra tên thuốc, liều, đường dùng, tần suất, số ngày và số lượng. Sau phát hành, đơn thuốc không thể sửa hoặc xóa.' : 'Đơn thuốc chưa phát hành và nội dung đang nhập sẽ được bỏ. Bạn có thể tạo lại đơn thuốc khi cần.'}</p>
    <div className='mt-5 flex flex-wrap justify-end gap-3'><Button autoFocus onClick={() => close()}>Quay lại</Button>
      <Button variant={issue ? 'primary' : 'danger'} onClick={() => close(true)}>{issue ? 'Xác nhận phát hành' : 'Xác nhận hủy kê đơn'}</Button></div>
  </dialog>
}
