import { useQuery } from '@tanstack/react-query'
import { Banknote, ClipboardCheck, Inbox, Network, ScrollText } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { get } from './api.ts'
import type { Payment, PendingApproval, RoleCode, Session } from './api.ts'
import { ProvenanceBadge, won } from './ui.tsx'
import { AuditLog } from './views/AuditLog.tsx'
import { Payments } from './views/Payments.tsx'
import { RelatedParties } from './views/RelatedParties.tsx'
import { Settlements } from './views/Settlements.tsx'
import { Workbench } from './views/Workbench.tsx'

type View = 'workbench' | 'settlements' | 'related' | 'payments' | 'audit'

const NAV: { view: View; label: string; icon: LucideIcon }[] = [
  { view: 'workbench', label: '처리할 일', icon: Inbox },
  { view: 'settlements', label: '정산 현황', icon: ClipboardCheck },
  { view: 'related', label: '관련자', icon: Network },
  { view: 'payments', label: '지급', icon: Banknote },
  { view: 'audit', label: '감사 로그', icon: ScrollText },
]

const ROLE_KEY = 'oneroof.role'

function storedRole(): RoleCode {
  try {
    const value = window.localStorage.getItem(ROLE_KEY)
    return value === 'SALES_EXECUTIVE' ? 'SALES_EXECUTIVE' : 'SALES_SUPPORT'
  } catch {
    return 'SALES_SUPPORT'
  }
}

export default function App() {
  const [view, setView] = useState<View>('workbench')
  const [role, setRoleState] = useState<RoleCode>(storedRole)
  const { data: session } = useQuery({ queryKey: ['session'], queryFn: () => get<Session>('/session'), refetchInterval: false })
  const { data: pending = [] } = useQuery({
    queryKey: ['pending', role],
    queryFn: () => get<PendingApproval[]>(`/approvals/pending?role=${role}`),
  })
  const { data: waiting = [] } = useQuery({
    queryKey: ['payments', 'SENT'],
    queryFn: () => get<Payment[]>('/payments?status=SENT'),
  })

  const setRole = (next: RoleCode) => {
    setRoleState(next)
    try {
      window.localStorage.setItem(ROLE_KEY, next)
    } catch {
      // The choice simply is not remembered when storage is unavailable.
    }
  }
  const counts: Partial<Record<View, number>> = { workbench: pending.length, payments: waiting.length }

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-16 shrink-0 items-center gap-4 border-b border-line bg-white px-6">
        <div className="flex items-baseline gap-2.5">
          <span className="text-xl font-extrabold tracking-tight text-brand">OneRoof</span>
          <span className="text-[13px] font-medium text-ink-soft">관련 거래처·판매장려금 지급 통제</span>
        </div>
        <div className="ml-auto flex items-center gap-3">
          {session && (
            <>
              <span className="flex items-center gap-1.5 text-xs text-ink-soft">
                월 한도 <b className="num text-ink">{won(session.limitPolicy.monthlyLimitKrw)}</b>
                <ProvenanceBadge code={session.limitPolicy.provenance} />
              </span>
              <span className="rounded-lg bg-fill px-2.5 py-1.5 text-xs font-semibold text-ink-soft">
                기준일 <span className="num text-ink">{session.businessDate}</span>
              </span>
            </>
          )}
          <div className="flex rounded-xl bg-fill p-1" role="group" aria-label="역할 전환 (시연용)">
            {(session?.roles ?? []).map((item) => (
              <button
                key={item.code}
                type="button"
                onClick={() => setRole(item.code)}
                className={`h-8 rounded-lg px-3 text-[13px] font-semibold transition-colors ${role === item.code ? 'bg-white text-ink shadow-sm' : 'text-ink-mute hover:text-ink-soft'}`}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        <nav className="flex w-52 shrink-0 flex-col gap-1 p-4">
          {NAV.map(({ view: target, label, icon: Icon }) => (
            <button
              key={target}
              type="button"
              onClick={() => setView(target)}
              className={`flex h-11 items-center gap-2.5 rounded-xl px-3 text-sm font-semibold transition-colors ${view === target ? 'bg-white text-brand-strong shadow-sm' : 'text-ink-soft hover:bg-white/60'}`}
            >
              <Icon size={18} strokeWidth={2.2} />
              {label}
              {counts[target] ? (
                <span className="num ml-auto rounded-full bg-brand px-1.5 py-0.5 text-[11px] font-bold leading-none text-white">
                  {counts[target]}
                </span>
              ) : null}
            </button>
          ))}
          <p className="mt-auto px-1 text-[11px] leading-relaxed text-ink-mute">
            역할 전환과 은행 회신은 시연을 위한 모의 처리입니다. 원본 데이터는 수정하지 않습니다.
          </p>
        </nav>
        <main className="min-h-0 min-w-0 flex-1 py-4 pr-6">
          {view === 'workbench' && <Workbench role={role} />}
          {view === 'settlements' && <Settlements role={role} />}
          {view === 'related' && <RelatedParties />}
          {view === 'payments' && <Payments role={role} />}
          {view === 'audit' && <AuditLog />}
        </main>
      </div>
    </div>
  )
}
