import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { ProvenanceCode } from './api.ts'

export const won = (krw: number) => `${krw.toLocaleString('ko-KR')}원`

type Tone = 'gray' | 'blue' | 'red' | 'green' | 'orange' | 'violet' | 'teal'

const TONES: Record<Tone, string> = {
  gray: 'bg-fill text-ink-soft',
  blue: 'bg-brand-soft text-brand-strong',
  red: 'bg-danger-soft text-danger',
  green: 'bg-success-soft text-success',
  orange: 'bg-warn-soft text-[#c26a00]',
  violet: 'bg-violet-soft text-violet',
  teal: 'bg-teal-soft text-teal',
}

export function Badge({ tone = 'gray', children, title }: { tone?: Tone; children: ReactNode; title?: string }) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-md px-1.5 py-0.5 text-[11px] font-semibold ${TONES[tone]}`}
    >
      {children}
    </span>
  )
}

const PROVENANCE: Record<ProvenanceCode, { label: string; tone: Tone; hint: string }> = {
  ORIGINAL: { label: '원본', tone: 'gray', hint: '원본 데이터룸 파일에서 읽은 기록' },
  DEPARTMENT_CLAIM: { label: '부서 주장', tone: 'violet', hint: '부서가 작성한 전망·의견·분석. 사실이 아니라 주장' },
  ASSUMPTION: { label: '가정', tone: 'orange', hint: '원본에 없어 이 시스템이 둔 교육용 가정' },
  MOCK_REPLY: { label: '모의 회신', tone: 'teal', hint: '시연에서 사람·외부 기관 역할로 입력한 모의 처리' },
  CALCULATED: { label: '계산 결과', tone: 'blue', hint: '다른 값에서 이 시스템이 계산한 결과' },
}

/** Marks where a value came from, as the contest rules require for every value shown. */
export function ProvenanceBadge({ code }: { code: ProvenanceCode }) {
  const meta = PROVENANCE[code]
  return (
    <Badge tone={meta.tone} title={meta.hint}>
      {meta.label}
    </Badge>
  )
}

const STATUS: Record<string, { label: string; tone: Tone }> = {
  AWAITING_APPROVAL: { label: '승인 대기', tone: 'orange' },
  APPROVED: { label: '승인 · 지급 진행', tone: 'blue' },
  PAID: { label: '지급 완료', tone: 'green' },
  REJECTED: { label: '반려', tone: 'red' },
  PENDING: { label: '미접수', tone: 'gray' },
  UNDER_APPROVAL: { label: '승인 대기', tone: 'orange' },
  PAYMENT_REQUESTED: { label: '지급 요청', tone: 'blue' },
  SETTLED_IN_SOURCE: { label: '원본상 지급', tone: 'gray' },
  REJECTED_IN_SOURCE: { label: '원본상 반려', tone: 'gray' },
  REQUESTED: { label: '지시 생성', tone: 'gray' },
  SENT: { label: '은행 회신 대기', tone: 'orange' },
  FAILED: { label: '이체 실패', tone: 'red' },
}

export function StatusBadge({ status }: { status: string | null }) {
  if (!status) {
    return <span className="text-xs text-ink-mute">—</span>
  }
  const meta = STATUS[status] ?? { label: status, tone: 'gray' as Tone }
  return <Badge tone={meta.tone}>{meta.label}</Badge>
}

/** A pointer to the original file and line a value was read from. */
export function SourceRef({ value }: { value: string }) {
  const short = value.replace(/^.*\//, '')
  return (
    <span title={value} className="font-mono text-[11px] text-ink-mute">
      {short}
    </span>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-2xl bg-white p-5 ${className}`}>{children}</section>
}

export function CardTitle({ children, aside }: { children: ReactNode; aside?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <h3 className="text-[15px] font-bold text-ink">{children}</h3>
      {aside}
    </div>
  )
}

type ButtonKind = 'primary' | 'secondary' | 'danger' | 'ghost'

const BUTTONS: Record<ButtonKind, string> = {
  primary: 'bg-brand text-white hover:bg-brand-strong',
  secondary: 'bg-brand-soft text-brand-strong hover:bg-[#d6e9ff]',
  danger: 'bg-danger-soft text-danger hover:bg-[#ffdede]',
  ghost: 'bg-fill text-ink-soft hover:bg-line',
}

export function Button({
  kind = 'primary',
  size = 'md',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { kind?: ButtonKind; size?: 'sm' | 'md' | 'lg' }) {
  const sizes = { sm: 'h-8 px-3 text-xs', md: 'h-10 px-4 text-sm', lg: 'h-12 px-5 text-[15px]' }
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex items-center justify-center gap-1.5 rounded-xl font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${sizes[size]} ${BUTTONS[kind]} ${className}`}
    />
  )
}

export function Notice({ tone, children }: { tone: 'info' | 'error' | 'warn'; children: ReactNode }) {
  const tones = {
    info: 'bg-brand-soft text-brand-strong',
    error: 'bg-danger-soft text-danger',
    warn: 'bg-warn-soft text-[#c26a00]',
  }
  return <p className={`rounded-xl px-3.5 py-2.5 text-[13px] font-medium leading-relaxed ${tones[tone]}`}>{children}</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-10 text-center text-sm text-ink-mute">{children}</p>
}

export const ROLE_LABEL: Record<string, string> = {
  SALES_SUPPORT: '영업지원팀',
  SALES_EXECUTIVE: '영업임원',
  SYSTEM: '시스템',
  'EXTERNAL:BANK': '은행(모의)',
}

export const ACTION_LABEL: Record<string, string> = {
  ORIGINAL_DATA_LOADED: '원본 적재',
  EVENT_RECEIVED: '사건 접수',
  DUPLICATE_EVENT_IGNORED: '중복 사건 무시',
  SETTLEMENT_OPENED: '정산 접수',
  SETTLEMENT_ALREADY_OPEN: '이미 접수된 정산',
  NOTHING_TO_SETTLE: '정산할 건 없음',
  REVIEW_OPENED: '검토 생성',
  APPROVAL_REQUESTED: '승인 상신',
  APPROVAL_GRANTED: '승인',
  APPROVAL_REJECTED: '반려',
  REVIEW_REJECTED: '지급 중단',
  PAYMENT_REQUESTED: '지급 요청',
  PAYMENT_INSTRUCTED: '지급 지시 생성',
  PAYMENT_SENT: '은행 전송',
  PAYMENT_CONFIRMED: '이체 완료 회신',
  PAYMENT_FAILED: '이체 실패 회신',
  REVIEW_PAID: '지급 완료',
}
