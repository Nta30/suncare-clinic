/**
 * FormField — Input dùng chung với label, thông báo lỗi và gợi ý.
 *
 * Tự động gán htmlFor / id / aria-invalid / aria-describedby.
 * Truyền `idPrefix` để tránh xung đột khi dùng nhiều lần trên cùng trang.
 * Hỗ trợ `adornment` (nút Hiện/Ẩn mật khẩu hoặc đơn vị).
 *
 * CSS: dùng token clinical-border-strong, amber-500, amber-100 theo design system.
 * Không dùng raw hex; không tự thêm dependency.
 */

export const fieldCls =
  'mt-1.5 block min-h-11 w-full min-w-0 rounded-xl border border-clinical-border-strong bg-white px-3.5 py-2.5 text-sm text-clinical-ink outline-none transition placeholder:text-slate-400 focus:border-amber-500 focus:ring-4 focus:ring-amber-100 aria-[invalid=true]:border-rose-400'

/**
 * @param {{
 *   name: string,
 *   label: string,
 *   value: string,
 *   onChange: (e: React.ChangeEvent<HTMLInputElement>) => void,
 *   error?: string,
 *   type?: string,
 *   maxLength?: number,
 *   required?: boolean,
 *   placeholder?: string,
 *   hint?: string,
 *   adornment?: import('react').ReactNode,
 *   idPrefix?: string,
 *   disabled?: boolean,
 *   autoComplete?: string,
 * }} props
 */
export default function FormField({
  name,
  label,
  value,
  onChange,
  error,
  type = 'text',
  maxLength,
  required = false,
  placeholder,
  hint,
  adornment,
  idPrefix = 'field',
  disabled = false,
  autoComplete,
}) {
  const id = `${idPrefix}-${name}`
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  const describedBy = error ? errorId : hint ? hintId : undefined

  return (
    <div className='min-w-0'>
      <label htmlFor={id} className='block text-sm font-semibold text-clinical-ink'>
        {label}
        {required && <span className='ml-1 text-rose-600' aria-hidden='true'>*</span>}
        {required && <span className='sr-only'>(bắt buộc)</span>}
      </label>
      <div className='relative'>
        <input
          id={id}
          name={name}
          type={type}
          value={value}
          onChange={onChange}
          maxLength={maxLength}
          placeholder={placeholder}
          disabled={disabled}
          autoComplete={autoComplete ?? (name === 'password' ? 'new-password' : 'off')}
          aria-invalid={Boolean(error)}
          aria-describedby={describedBy}
          className={`${fieldCls} ${adornment ? 'pr-20' : ''}`}
        />
        {adornment && (
          <div className='absolute inset-y-0 right-2 flex items-center pt-1'>
            {adornment}
          </div>
        )}
      </div>
      {error ? (
        <p id={errorId} role='alert' className='mt-1.5 text-xs font-medium text-rose-700'>
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className='mt-1.5 text-xs text-clinical-muted'>
          {hint}
        </p>
      ) : null}
    </div>
  )
}
