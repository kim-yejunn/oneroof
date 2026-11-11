import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { PendingApproval, Period, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ROLE_LABEL, won } from '../ui.tsx'
import { ReviewDetail } from './ReviewDetail.tsx'

/** The role's to-do list on the left, the selected item's decision screen on the right. */
export function Workbench({ role }: { role: RoleCode }) {
  const queryClient = useQueryClient()
  const { data: periods = [] } = useQuery({ queryKey: ['periods'], queryFn: () => get<Period[]>('/periods') })
  const { data: pending = [] } = useQuery({
    queryKey: ['pending', role],
    queryFn: () => get<PendingApproval[]>(`/approvals/pending?role=${role}`),
  })
  const [picked, setSelected] = useState<number | null>(null)

  // Follow the user's pick while it is still waiting; once it is decided, move on to the next item.
  const selectable = pending.filter((item) => item.subjectType === 'RebateReview').map((item) => Number(item.subjectId))
  const selected = picked !== null && selectable.includes(picked) ? picked : (selectable[0] ?? picked)

  const submit = useMutation({
    mutationFn: (period: string) => post<{ accepted: boolean }>('/settlements', role, { period }),
    onSuccess: () => void queryClient.invalidateQueries(),
  })
  const approveRoutine = useMutation({
    mutationFn: (ids: number[]) => post('/approvals/decisions', role, { ids, approved: true, comment: '한도 이내 전결' }),
    onSuccess: () => void queryClient.invalidateQueries(),
  })

  const unsubmitted = periods.filter((period) => period.pendingCount > 0 && !period.batchOpened)
  const routine = pending.filter((item) => item.exceedsLimit === false)

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
              disabled={submit.isPending || role !== 'SALES_SUPPORT'}
              onClick={() => submit.mutate(period.period)}
            >
              정산 접수
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
              className="mb-3 w-full"
              disabled={approveRoutine.isPending}
              onClick={() => approveRoutine.mutate(routine.map((item) => item.id))}
            >
              한도 이내 {routine.length}건 일괄 승인
            </Button>
          )}
          {approveRoutine.error && <div className="mb-3"><Notice tone="error">{approveRoutine.error.message}</Notice></div>}
          {pending.length === 0 ? (
            <Empty>대기 중인 승인 요청이 없습니다.</Empty>
          ) : (
            <ul className="-mx-2 flex flex-col">
              {pending.map((item) => {
                const reviewId = Number(item.subjectId)
                const active = reviewId === selected
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => setSelected(reviewId)}
                      className={`flex w-full flex-col gap-1 rounded-xl px-3 py-2.5 text-left transition-colors ${active ? 'bg-brand-soft' : 'hover:bg-fill'}`}
                    >
                      <span className="flex items-center gap-1.5">
                        {item.exceedsLimit ? <Badge tone="red">한도 초과</Badge> : <Badge tone="gray">한도 이내</Badge>}
                        {item.relatedGroup && <Badge tone="blue">관련자 그룹</Badge>}
                      </span>
                      <span className="flex items-baseline justify-between gap-2">
                        <span className="truncate text-sm font-semibold">{item.title.replace(/^.*— /, '').replace(/ [\d,]+원$/, '')}</span>
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
        {selected !== null ? (
          <ReviewDetail reviewId={selected} role={role} />
        ) : (
          <Card>
            <Empty>왼쪽에서 처리할 일을 선택하세요.</Empty>
          </Card>
        )}
      </div>
    </div>
  )
}
