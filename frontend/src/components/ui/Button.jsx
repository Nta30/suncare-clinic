const styles = {
  primary: 'border-transparent bg-suncare-400 text-clinical-ink hover:bg-suncare-500',
  outline: 'border-clinical-border-strong bg-white text-clinical-ink hover:bg-suncare-50',
  danger: 'border-clinical-danger bg-white text-clinical-danger hover:bg-rose-50',
}

export default function Button({ variant = 'outline', className = '', children, type = 'button', ...props }) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700 disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
