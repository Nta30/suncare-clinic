/**
 * Icon — SVG icon wrapper dùng chung trong toàn bộ dự án.
 * Dùng Lucide-style paths; giữ Font Awesome riêng trong AccountForm/App.
 * Thêm path mới vào `paths` khi cần; không import thư viện icon ngoài.
 */
const paths = {
  plus: <path d='M12 5v14M5 12h14' />,
  search: <><circle cx='10.8' cy='10.8' r='6.3' /><path d='m16 16 4.2 4.2' /></>,
  refresh: <><path d='M20 11a8 8 0 0 0-14-5L4 8' /><path d='M4 4v4h4M4 13a8 8 0 0 0 14 5l2-2' /><path d='M20 20v-4h-4' /></>,
  users: <><path d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2' /><circle cx='9' cy='7' r='4' /><path d='M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8' /></>,
  check: <path d='m5 12 4 4L19 6' />,
  lock: <><rect x='4.5' y='10' width='15' height='11' rx='2' /><path d='M8 10V7a4 4 0 0 1 8 0v3' /></>,
  doctor: <><path d='M6 3v6a5 5 0 0 0 10 0V3M4 3h4M14 3h4' /><path d='M11 14v2a4 4 0 0 0 8 0v-1' /><circle cx='19' cy='13' r='2' /></>,
  chevronLeft: <path d='m15 18-6-6 6-6' />,
  chevronRight: <path d='m9 18 6-6-6-6' />,
  close: <path d='M18 6 6 18M6 6l12 12' />,
  shield: <><path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z' /><path d='m9 12 2 2 4-4' /></>,
  filter: <><path d='M4 7h16M7 12h10M10 17h4' /></>,
  arrow: <path d='M5 12h14m-6-6 6 6-6 6' />,
  info: <><circle cx='12' cy='12' r='9' /><path d='M12 11v5M12 8h.01' /></>,
  edit: <><path d='M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7' /><path d='M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5Z' /></>,
  building: <><path d='M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' /><polyline points='9 22 9 12 15 12 15 22' /></>,
  calendar: <><rect x='3' y='4' width='18' height='18' rx='2' ry='2' /><line x1='16' y1='2' x2='16' y2='6' /><line x1='8' y1='2' x2='8' y2='6' /><line x1='3' y1='10' x2='21' y2='10' /></>,
  clipboard: <><path d='M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2' /><rect x='8' y='2' width='8' height='4' rx='1' ry='1' /></>,
  file: <><path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' /><polyline points='14 2 14 8 20 8' /></>,
  eye: <><path d='M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z' /><circle cx='12' cy='12' r='3' /></>,
  eyeOff: <><path d='M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24' /><line x1='1' y1='1' x2='23' y2='23' /></>,
}

/**
 * @param {{ name: keyof typeof paths, size?: number, className?: string }} props
 */
export default function Icon({ name, size = 19, className = '' }) {
  return (
    <svg
      aria-hidden='true'
      viewBox='0 0 24 24'
      width={size}
      height={size}
      className={className}
      fill='none'
      stroke='currentColor'
      strokeWidth='1.8'
      strokeLinecap='round'
      strokeLinejoin='round'
    >
      {paths[name] || paths.info}
    </svg>
  )
}
