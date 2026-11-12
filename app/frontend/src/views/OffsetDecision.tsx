import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { Payment, PendingApproval, ReceivableItem, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ProvenanceBadge, ROLE_LABEL, SourceRef, StatusBadge, won } from '../ui.tsx'

/**
 * Finance's decision on setting a rebate off against the agency's long-overdue receivables. The
 * proposal is the smaller of the rebate and the overdue balance; nothing is forfeited.
 */
export function OffsetDecision({ request, role }: { request: PendingApproval; role: RoleCode }) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const instructionId = Number(request.subjectId)
  const { data: payments = [] } = useQuery({ queryKey: ['payments'], queryFn: () => get<Payment[]>('/payments') })
  const payment = payments.find((item) => item.id === instructionId)
  const { data: items = [] } = useQuery({
    queryKey: ['receivable-items', payment?.agencyId],
    queryFn: () => get<ReceivableItem[]>(`/agencies/${payment?.agencyId}/receivables`),
    enabled: Boolean(payment),
  })
  const decide = useMutation({
    mutationFn: (approved: boolean) => post(`/approvals/${request.id}/decision`, role, { approved, comment }),
    onSuccess: () => {
      setComment('')
      void queryClient.invalidateQueries()
    },
  })

  if (!payment) {
    return <Empty>불러오는 중…</Empty>
  }
  const disputed = payment.status === 'OFFSET_DISPUTED'
  const dispute = payment.offsetDecision?.match(/대리점 이의: (.*)$/)?.[1]
  const hasAuthority = role === 'FINANCE'
  const overdue = items.filter((item) => item.agingDays > 90)
  const overdueKrw = overdue.reduce((sum, item) => sum + item.outstandingKrw, 0)

  return (
    <div className="@container">
      <div className="grid grid-cols-1 gap-4 @5xl:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <Card>
            <div className="mb-4 flex flex-wrap items-center gap-2">
              <StatusBadge status={payment.status} />
              <Badge tone="gray">{payment.period}</Badge>
              {disputed ? <Badge tone="violet">대리점 이의 · 재검토</Badge> : <Badge tone="orange">회계·자금 결정</Badge>}
              <span className="text-xs text-ink-mute">
                지급 지시 #{payment.id} · {payment.referenceId}
              </span>
            </div>
            <h2 className="text-xl font-bold leading-snug">
              {payment.agencyId} {payment.agencyName} · 판매장려금 미수금 상계
            </h2>
            <div className="mt-4 grid grid-cols-2 gap-3 @xl:grid-cols-4">
              <Stat label="판매장려금" value={won(payment.amountKrw)} tag={<ProvenanceBadge code="ORIGINAL" />} />
              <Stat label="90일 초과 연체" value={won(overdueKrw)} tag={<ProvenanceBadge code="CALCULATED" />} tone="text-danger" />
              <Stat label={disputed ? '상계액' : '상계 제안'} value={won(payment.offsetKrw)} tag={<ProvenanceBadge code="CALCULATED" />} tone="text-brand-strong" />
              <Stat label="현금 지급" value={won(payment.netKrw)} tag={<ProvenanceBadge code="CALCULATED" />} />
            </div>
            <p className="mt-4 rounded-xl bg-fill px-3.5 py-3 text-[13px] leading-relaxed text-ink-soft">
              상계는 장려금 몰수가 아닙니다. 상계한 금액만큼 대리점의 물품대금 채무도 함께 소멸합니다. 상계 제안액은 장려금과
              90일 초과 연체 잔액 중 작은 금액입니다.
            </p>
            {disputed && dispute && (
              <div className="mt-3">
                <Notice tone="warn">
                  대리점 이의 (모의 회신): “{dispute}”
                </Notice>
              </div>
            )}
            <div className="mt-5 border-t border-line pt-4">
              <div className="flex flex-col gap-3">
                {!hasAuthority && (
                  <Notice tone="warn">
                    현재 역할은 {ROLE_LABEL[role]}입니다. 상계 결정은 회계·자금팀 권한이라 서버가 거부합니다.
                  </Notice>
                )}
                <input
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                  placeholder="판단 사유 (선택)"
                  className="h-10 rounded-xl border border-line px-3.5 text-sm outline-none focus:border-brand"
                />
                <div className="flex gap-2">
                  <Button size="lg" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate(true)}>
                    {disputed ? '이의 기각 · 상계 유지' : `${won(payment.offsetKrw)} 상계 결정`}
                  </Button>
                  <Button kind="ghost" size="lg" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate(false)}>
                    {disputed ? '이의 인정 · 상계 취소' : '전액 지급 · 별도 회수'}
                  </Button>
                </div>
                <p className="text-xs leading-relaxed text-ink-mute">
                  {disputed
                    ? '상계를 취소하면 줄였던 미수금이 되살아나고 장려금 전액을 은행으로 보냅니다.'
                    : '상계를 결정하면 미수금이 줄고 대리점에 상계 통지가 나갑니다. 남는 금액이 있으면 지급 계좌 확인을 거쳐 은행으로 보냅니다.'}
                </p>
                {decide.error && <Notice tone="error">{decide.error.message}</Notice>}
              </div>
            </div>
          </Card>
        </div>

        <div className="flex min-w-0 flex-col gap-4">
          <Card>
            <CardTitle aside={<ProvenanceBadge code="ORIGINAL" />}>{payment.agencyId} 미수금 송장</CardTitle>
            {items.length === 0 ? (
              <Empty>남은 미수금이 없습니다.</Empty>
            ) : (
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-line text-left text-xs text-ink-mute">
                    <th className="py-2 font-medium">송장</th>
                    <th className="py-2 text-right font-medium">연체</th>
                    <th className="py-2 text-right font-medium">원본 잔액</th>
                    <th className="py-2 text-right font-medium">현재 잔액</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.arId} className={`border-b border-line last:border-0 ${item.agingDays > 90 ? '' : 'text-ink-mute'}`}>
                      <td className="py-2">
                        <div className="font-semibold">{item.arId}</div>
                        <SourceRef value={item.sourceRef} />
                      </td>
                      <td className="num py-2 text-right">
                        {item.agingDays}일 {item.agingDays > 90 && <Badge tone="red">대상</Badge>}
                      </td>
                      <td className="num py-2 text-right">{won(item.originalOutstandingKrw)}</td>
                      <td className="num py-2 text-right font-semibold">{won(item.outstandingKrw)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
          <Card>
            <CardTitle aside={<ProvenanceBadge code="ASSUMPTION" />}>판단 근거와 제약</CardTitle>
            <ul className="flex list-disc flex-col gap-1.5 pl-4 text-[13px] leading-relaxed text-ink-soft">
              <li>
                <b className="text-ink">교육용 가정</b>: 대리점 계약서에 “이행기 지난 물품대금과 장려금을 대등액으로 상계할 수 있다”는
                조항이 있다고 둡니다. 원본에 계약서가 없습니다. 대상은 90일 초과 연체분만입니다.
              </li>
              <li>
                계약 근거 없이 장려금을 깎거나 주지 않으면 대리점법상 불이익 제공입니다. 공정위는 2024년 4월 연체 비율·일수와 무관하게
                장려금 전액을 주지 않은 가구 3사에 시정명령을 내렸습니다.
              </li>
              <li>상계는 같은 법인 사이 채권끼리만 합니다(민법 제492조). 관련자 그룹 안의 다른 대리점 미수금은 쓰지 않습니다.</li>
              <li>대리점이 상계에 이의를 제기하면 회계·자금팀이 다시 판단합니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </div>
  )
}

function Stat({ label, value, tag, tone = 'text-ink' }: { label: string; value: string; tag: React.ReactNode; tone?: string }) {
  return (
    <div className="min-w-0 rounded-xl bg-fill px-4 py-3.5">
      <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
        <span className="whitespace-nowrap text-xs font-medium text-ink-soft">{label}</span>
        {tag}
      </div>
      <div className={`num mt-1.5 truncate text-lg font-bold ${tone}`}>{value}</div>
    </div>
  )
}
