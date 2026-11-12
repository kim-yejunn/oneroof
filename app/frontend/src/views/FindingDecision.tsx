import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { Finding, RelatedParties, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ProvenanceBadge, ROLE_LABEL, StatusBadge, won } from '../ui.tsx'
import { EvidenceCard, HistoryCard } from './ReviewDetail.tsx'

/**
 * The executive's after-the-fact confirmation of a month the sales support team settled, which a
 * master correction showed was over the limit for a related group. Payments are not reversed.
 */
export function FindingDecision({ findingId, role }: { findingId: number; role: RoleCode }) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const { data: findings = [] } = useQuery({ queryKey: ['findings'], queryFn: () => get<Finding[]>('/findings') })
  const { data: related } = useQuery({ queryKey: ['related-parties'], queryFn: () => get<RelatedParties>('/related-parties') })
  const finding = findings.find((item) => item.id === findingId)
  const decide = useMutation({
    mutationFn: (approved: boolean) =>
      post(`/approvals/${finding?.approvalRequestId}/decision`, role, { approved, comment }),
    onSuccess: () => {
      setComment('')
      void queryClient.invalidateQueries()
    },
  })

  if (!finding) {
    return <Empty>불러오는 중…</Empty>
  }
  const group = related?.groups.find((item) => item.groupKey === finding.unitKey)
  const pending = finding.approvalStatus === 'PENDING'
  const hasAuthority = role === finding.requiredApprover

  return (
    <div className="@container">
      <div className="grid grid-cols-1 gap-4 @5xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <Card>
            <div className="mb-4 flex flex-wrap items-center gap-2">
              <StatusBadge status={finding.status} />
              <Badge tone="gray">{finding.period}</Badge>
              <Badge tone="blue">관련자 그룹 합산</Badge>
              <span className="text-xs text-ink-mute">소급 발견 #{finding.id} · 마스터 정정 #{finding.masterChangeId}</span>
            </div>
            <h2 className="text-xl font-bold leading-snug">
              {finding.unitKey} · {finding.memberAgencyIds.split(',').join(', ')}
            </h2>
            <div className="mt-4 grid grid-cols-1 gap-3 @md:grid-cols-3">
              <Stat label="그룹 합산" value={won(finding.totalKrw)} tone="text-danger" tag={<ProvenanceBadge code="CALCULATED" />} />
              <Stat label="월 한도" value={won(finding.limitKrw)} tag={<ProvenanceBadge code="ORIGINAL" />} />
              <Stat label="당시 승인" value={`${finding.approvedBy} 전결`} tag={<ProvenanceBadge code="ORIGINAL" />} />
            </div>
            <p className="mt-4 whitespace-pre-line rounded-xl bg-fill px-3.5 py-3 text-[13px] leading-relaxed text-ink-soft">
              {finding.rationale}
            </p>
            <div className="mt-5 border-t border-line pt-4">
              {pending ? (
                <div className="flex flex-col gap-3">
                  {!hasAuthority && (
                    <Notice tone="warn">
                      현재 역할은 {ROLE_LABEL[role]}입니다. 사후 확인은 {ROLE_LABEL[finding.requiredApprover]} 권한이라 서버가
                      거부합니다.
                    </Notice>
                  )}
                  <input
                    value={comment}
                    onChange={(event) => setComment(event.target.value)}
                    placeholder="확인 사유 (선택)"
                    className="h-10 rounded-xl border border-line px-3.5 text-sm outline-none focus:border-brand"
                  />
                  <div className="flex gap-2">
                    <Button size="lg" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate(true)}>
                      사후 확인
                    </Button>
                    <Button kind="danger" size="lg" className="flex-1" disabled={decide.isPending} onClick={() => decide.mutate(false)}>
                      확인 거절 · 감사 이관
                    </Button>
                  </div>
                  {decide.error && <Notice tone="error">{decide.error.message}</Notice>}
                </div>
              ) : (
                <p className="text-sm text-ink-soft">
                  결정됨: <StatusBadge status={finding.status} /> {finding.decisionComment && `“${finding.decisionComment}”`}
                </p>
              )}
            </div>
          </Card>
          <Card>
            <CardTitle>전결로 처리된 장려금</CardTitle>
            <p className="text-sm">{finding.rebateIds.split(',').join(', ')}</p>
          </Card>
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          {group && <EvidenceCard group={group} />}
          {group?.history && <HistoryCard history={group.history} currentPeriod={finding.period} />}
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
