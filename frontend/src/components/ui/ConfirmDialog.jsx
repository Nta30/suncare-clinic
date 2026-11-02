/**
 * ConfirmDialog — Modal xác nhận thao tác nhạy cảm tái sử dụng.
 *
 * Props:
 *   open        — hiển thị khi true
 *   title       — tiêu đề dialog (VD: "Xác nhận thay đổi")
 *   description — mô tả hành động cần xác nhận
 *   confirmLabel — nhãn nút xác nhận (mặc định: "Xác nhận")
 *   cancelLabel  — nhãn nút hủy (mặc định: "Hủy")
 *   busy        — đang xử lý; disable cả hai nút
 *   variant     — 'warning' (mặc định, amber) | 'danger' (rose)
 *   onConfirm   — callback khi xác nhận
 *   onCancel    — callback khi hủy / nhấn Escape
 *   icon        — tên Icon (mặc định: 'shield')
 *   dialogRef   — ref truyền vào div trong cùng để quản lý focus từ ngoài
 *
 * Trap focus và đóng bằng Escape được quản lý bởi component gọi
 * thông qua useEffect + KeyDown, không tự quản lý ở đây để tránh
 * xung đột khi lồng nhiều layer overlay.
 */
import Icon from './Icon'
import Button, { outlineCls } from './Button'

const variantIcon = {
  warning: { iconBg: 'bg-amber-100 text-amber-900', border: 'border-clinical-border' },
  danger:  { iconBg: 'bg-rose-100 text-rose-700',   border: 'border-rose-200' },
}

/**
 * @param {{
 *   open: boolean,
 *   title: string,
 *   description: string,
 *   confirmLabel?: string,
 *   cancelLabel?: string,
 *   busy?: boolean,
 *   variant?: 'warning' | 'danger',
 *   onConfirm: () => void,
 *   onCancel: () => void,
 *   icon?: string,
 *   dialogRef?: import('react').RefObject<HTMLDivElement>,
 * }} props
 */
export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Xác nhận',
  cancelLabel = 'Hủy',
  busy = false,
  variant = 'warning',
  onConfirm,
  onCancel,
  icon = 'shield',
  dialogRef,
}) {
  if (!open) return null

  const cfg = variantIcon[variant] || variantIcon.warning

  return (
    <div className='fixed inset-0 z-[70] flex items-center justify-center p-4'>
      {/* Overlay */}
      <div className='absolute inset-0 bg-slate-950/55' aria-hidden='true' />

      {/* Dialog box */}
      <div
        ref={dialogRef}
        role='alertdialog'
        aria-modal='true'
        aria-labelledby='confirm-dialog-title'
        aria-describedby='confirm-dialog-description'
        className={`relative w-full max-w-md rounded-2xl border ${cfg.border} bg-white p-6 shadow-2xl sm:p-7`}
      >
        {/* Icon */}
        <div className={`mb-4 flex h-12 w-12 items-center justify-center rounded-xl ${cfg.iconBg}`}>
          <Icon name={icon} size={23} />
        </div>

        <h2 id='confirm-dialog-title' className='text-xl font-extrabold text-clinical-ink'>
          {title}
        </h2>
        <p
          id='confirm-dialog-description'
          className='mt-2 text-sm leading-6 text-clinical-muted'
        >
          {description}
        </p>

        <div className='mt-6 flex flex-wrap justify-end gap-2'>
          <button
            type='button'
            disabled={busy}
            onClick={onCancel}
            className={outlineCls}
          >
            {cancelLabel}
          </button>
          <Button
            variant={variant === 'danger' ? 'danger' : 'primary'}
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? 'Đang xử lý...' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
