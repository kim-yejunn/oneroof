import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { PendingApproval, Period, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ROLE_LABEL, won } from '../ui.tsx'
import { FindingDecision } from './FindingDecision.tsx'
import { OffsetDecision } from './OffsetDecision.tsx'
import { ReviewDetail } from './ReviewDetail.tsx'

/** The role's to-do list on the left, the selected item's decision screen on the right. */
export function Workbench({ role }: { role: RoleCode }) {
  const queryClient = useQueryClient()
  const { data: periods = [] } = useQuery({ queryKey: ['periods'], queryFn: () => get<Period[]>('/periods') })
  const { data: pending = [] } = useQuery({
    queryKey: ['pending', role],
    queryFn: () => get<PendingApproval[]>(`/approvals/pending?role=${role}`),
  })
  const [pickedId, setPickedId] = useState<number | null>(null)
  // Items sent in a bulk decision stay in the queue until the background step removes them; they
  // are locked here meanwhile so nothing is decided twice.
  const [sent, setSent] = useState<ReadonlySet<number>>(new Set())
  const markSent = (ids: number[]) => setSent((previous) => new Set([...previous, ...ids]))
  const [submittedPeriods, setSubmittedPeriods] = useState<ReadonlySet<string>>(new Set())

  // Only this role's waiting items can be selected. When the pick is decided, move to the next one;
  // when nothing waits, show nothing rather than a stale item from before.
  const open = pending.filter((item) => !sent.has(item.id))
  const selected = pending.find((item) => item.id === pickedId) ?? open[0] ?? null

  const submit = useMutation({
    mutationFn: (period: string) => post<{ accepted: boolean }>('/settlements', role, { period }).then(() => period),
    onSuccess: (period) => {
      setSubmittedPeriods((previous) => new Set([...previous, period]))
      return queryClient.invalidateQueries()
    },
  })
  const approveRoutine = useMutation({
    mutationFn: (ids: number[]) =>
      post('/approvals/decisions', role, { ids, approved: true, comment: '한도 이내 전결' }).then(() => ids),
    onSuccess: (ids) => {
      markSent(ids)
      return queryClient.invalidateQueries()
    },
  })

  const unsubmitted = periods.filter((period) => period.pendingCount > 0 && !period.batchOpened)
  const routine = open.filter((item) => item.subjectType === 'RebateReview' && item.exceedsLimit === false)
  const offsets = open.filter((item) => item.subjectType === 'OffsetProposal' && !item.title.startsWith('[이의'))
  const decideOffsets = useMutation({
    mutationFn: (ids: number[]) =>
      post('/approvals/decisions', role, { ids, approved: true, comment: '계약 조항에 따라 상계' }).then(() => ids),
    onSuccess: (ids) => {
      markSent(ids)
      return queryClient.invalidateQueries()
    },
  })
  const bulkBusy = approveRoutine.isPending || decideOffsets.isPending
  const applying = pending.length - open.length

  return (
    <div className="grid h-full grid-cols-[300px_minmax(0,1fr)] gap-4 2xl:grid-cols-[340px_minmax(0,1fr)]">
      <div className="flex min-h-0 flex-col gap-4 overflow-y-auto pb-6">
        {unsubmitted.map((period) => (
          <Card key={period.period}>
            <CardTitle aside={<Badge tone="orange">접수 전</Badge>}>{period.period} 정산</CardTitle>
            <p className="text-[13px] leading-relaxed text-ink-soft">
              미지급 장려금 <b className="text-ink">{period.pendingCount}건</b>이 정산 접수를 기다립니다. 접수하면 관련자
              합산으로 한도를 확인하고 권한자에게 상신합니다.
            </p>
            <Button
              className="mt-3 w-full"
              disabled={submit.isPending || submittedPeriods.has(period.period) || role !== 'SALES_SUPPORT'}
              onClick={() => submit.mutate(period.period)}
            >
              {submittedPeriods.has(period.period) ? '접수 처리 중…' : '정산 접수'}
            </Button>
            {role !== 'SALES_SUPPORT' && <p className="mt-2 text-xs text-ink-mute">정산 접수는 영업지원팀 업무입니다.</p>}
            {submit.error && <div className="mt-2"><Notice tone="error">{submit.error.message}</Notice></div>}
          </Card>
        ))}

        <Card className="flex min-h-0 flex-col">
          <CardTitle aside={<span className="text-xs font-semibold text-brand-strong">{pending.length}건</span>}>
            {ROLE_LABEL[role]} 승인 대기
          </CardTitle>
          {routine.length > 1 && (
            <Button
              kind="secondary"
              className="mb-3 w-full shrink-0"
              disabled={bulkBusy}
              onClick={() => approveRoutine.mutate(routine.map((item) => item.id))}
            >
              한도 이내 {routine.length}건 일괄 승인
            </Button>
          )}
          {offsets.length > 1 && (
            <Button
              kind="secondary"
              className="mb-3 w-full shrink-0"
              disabled={bulkBusy}
              onClick={() => decideOffsets.mutate(offsets.map((item) => item.id))}
            >
              상계 제안 {offsets.length}건 일괄 결정
            </Button>
          )}
          {applying > 0 && <p className="mb-3 text-xs font-semibold text-brand-strong">{applying}건 결정 반영 중…</p>}
          {approveRoutine.error && <div className="mb-3"><Notice tone="error">{approveRoutine.error.message}</Notice></div>}
          {decideOffsets.error && <div className="mb-3"><Notice tone="error">{decideOffsets.error.message}</Notice></div>}
          {pending.length === 0 ? (
            <Empty>대기 중인 승인 요청이 없습니다.</Empty>
          ) : (
            <ul className="-mx-2 flex flex-col">
              {pending.map((item) => {
                const active = item.id === selected?.id
                const locked = sent.has(item.id)
                const finding = item.subjectType === 'RetrospectiveFinding'
                const offset = item.subjectType === 'OffsetProposal'
                const disputed = offset && item.title.startsWith('[이의')
                const resubmitted = item.title.startsWith('[재상신')
                const label = finding
                  ? `${item.period} ${item.title.match(/RPG-D-\d+/)?.[0] ?? ''}`
                  : offset
                    ? item.title.replace(/^\[[^\]]*\] /, '').replace(/ 판매장려금.*$/, ' 미수금 상계')
                    : item.title.replace(/^.*— /, '').replace(/ [\d,]+원$/, '')
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => setPickedId(item.id)}
                      disabled={locked}
                      className={`flex w-full flex-col gap-1 rounded-xl px-3 py-2.5 text-left transition-colors disabled:opacity-40 ${active ? 'bg-brand-soft' : 'hover:bg-fill'}`}
                    >
                      <span className="flex flex-wrap items-center gap-1.5">
                        {offset ? (
                          <Badge tone={disputed ? 'violet' : 'orange'}>{disputed ? '상계 이의' : '상계 검토'}</Badge>
                        ) : finding ? (
                          <Badge tone="orange">사후 확인</Badge>
                        ) : item.exceedsLimit ? (
                          <Badge tone="red">한도 초과</Badge>
                        ) : (
                          <Badge tone="gray">한도 이내</Badge>
                        )}
                        {item.relatedGroup && <Badge tone="blue">관련자 그룹</Badge>}
                        {resubmitted && <Badge tone="violet">소명 첨부 재상신</Badge>}
                      </span>
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="truncate text-sm font-semibold">{label}</span>
                        <span className="num shrink-0 text-sm font-bold">{won(item.amountKrw)}</span>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>
      </div>

      <div className="min-h-0 overflow-y-auto pb-6">
        {selected?.subjectType === 'OffsetProposal' ? (
          <OffsetDecision key={selected.id} request={selected} role={role} />
        ) : selected?.subjectType === 'RetrospectiveFinding' ? (
          <FindingDecision key={selected.id} findingId={Number(selected.subjectId)} role={role} />
        ) : selected ? (
          <ReviewDetail key={selected.id} reviewId={Number(selected.subjectId)} role={role} />
        ) : (
          <Card>
            <Empty>{pending.length === 0 ? "지금 이 역할이 처리할 일이 없습니다." : "왼쪽에서 처리할 일을 선택하세요."}</Empty>
          </Card>
        )}
      </div>
    </div>
  )
}
