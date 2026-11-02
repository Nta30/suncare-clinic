/**
 * Button — Sunflower Clinical button primitives tái sử dụng.
 *
 * Xuất class string constants để dùng trong className trực tiếp,
 * và component Button bao lấy <button> với biến thể chuẩn.
 *
 * Biến thể:
 *   'primary'  — nền sunflower-400, chữ clinical-ink (CTA chính)
 *   'outline'  — viền border-strong, nền trắng (hành động phụ)
 *   'danger'   — viền rose, chữ rose-700 (khóa/xóa/thu hồi)
 *   'ghost'    — không viền, không nền, nền hover nhạt
 *
 * Kích thước:
 *   'md' (mặc định) — min-h-11, padding ngang 5
 *   'sm'           — min-h-9, padding ngang 3, text nhỏ hơn
 *   'icon'         — h-10 w-10, không padding ngang (nút icon vuông)
 */

export const primaryCls =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-suncare-400 px-5 py-2.5 text-sm font-bold text-clinical-ink shadow-sm transition hover:bg-suncare-500 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-200 disabled:cursor-not-allowed disabled:opacity-50'

export const outlineCls =
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-clinical-border-strong bg-white px-4 py-2.5 text-sm font-semibold text-clinical-ink transition hover:border-amber-300 hover:bg-amber-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100 disabled:cursor-not-allowed disabled:opacity-50'

const variantMap = {
  primary: primaryCls,
  outline: outlineCls,
  danger:
    'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-rose-200 bg-white px-4 py-2.5 text-sm font-semibold text-rose-700 transition hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-rose-100 disabled:cursor-not-allowed disabled:opacity-50',
  ghost:
    'inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-clinical-ink transition hover:bg-suncare-100 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-amber-100 disabled:cursor-not-allowed disabled:opacity-50',
}

const sizeMap = {
  md: '',
  sm: 'min-h-9 px-3 py-1.5 text-xs',
  icon: 'h-10 w-10 min-h-10 flex-shrink-0 px-0 py-0',
}

/**
 * @param {{
 *   variant?: 'primary' | 'outline' | 'danger' | 'ghost',
 *   size?: 'md' | 'sm' | 'icon',
 *   className?: string,
 *   children: import('react').ReactNode,
 *   [key: string]: any
 * }} props
 */
export default function Button({ variant = 'primary', size = 'md', className = '', children, ...rest }) {
  const base = variantMap[variant] || variantMap.outline
  const sizeCls = sizeMap[size] || ''
  return (
    <button
      type='button'
      className={[base, sizeCls, className].filter(Boolean).join(' ')}
      {...rest}
    >
      {children}
    </button>
  )
}
