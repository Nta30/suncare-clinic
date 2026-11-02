const tones = {
  neutral: 'border-clinical-border bg-white text-clinical-ink',
  success: 'border-clinical-leaf bg-clinical-leaf-soft text-clinical-success',
  danger: 'border-clinical-danger bg-rose-50 text-clinical-danger',
  warning: 'border-suncare-200 bg-suncare-100 text-suncare-800',
}

export default function Badge({ tone = 'neutral', children, className = '' }) {
  return <span className={`inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold ${tones[tone]} ${className}`}>{children}</span>
}
