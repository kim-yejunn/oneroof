import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { get, post } from '../api.ts'
import type { OffsetComparison, ReceivableAgency, RoleCode } from '../api.ts'
import { Badge, Button, Card, CardTitle, Empty, Notice, ProvenanceBadge, StatusBadge, won } from '../ui.tsx'

/**
 * Finance's view: what agencies owe, what the offset extension changes in the month, and a
 * simulated bank notice for deposits.
 */
export function Receivables({ role }: { role: RoleCode }) {
  const { data: comparison } = useQuery({
    queryKey: ['offset-comparison'],
    queryFn: () => get<OffsetComparison>('/offsets/comparison?period=2026-06'),
  })
  const { data: agencies = [] } = useQuery({ queryKey: ['receivables'], queryFn: () => get<ReceivableAgency[]>('/receivables') })

  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto pb-6">
      {comparison && <ComparisonCard comparison={comparison} />}
      <div className="@container">
        <div className="grid grid-cols-1 gap-4 @6xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
          <AgencyCard agencies={agencies} role={role} days={comparison?.overdueDaysOver ?? 90} />
          {comparison && <LinesCard comparison={comparison} />}
        </div>
      </div>
    </div>
  )
}

function ComparisonCard({ comparison }: { comparison: OffsetComparison }) {
  return (
    <Card>
      <CardTitle
        aside={
          <span className="flex items-center gap-1.5">
            <Badge tone={comparison.enabled ? 'green' : 'gray'}>확장 {comparison.enabled ? '켜짐' : '꺼짐'}</Badge>
            <ProvenanceBadge code="CALCULATED" />
          </span>
        }
      >
        필수 확장 · {comparison.period} 장려금 미수금 상계 켬·끔 비교
      </CardTitle>
      <div className="grid grid-cols-1 gap-3 @container md:grid-cols-3">
        <Tile label="확장 끔 · 현금 지급" value={won(comparison.totalKrw)} note={`장려금 ${comparison.claimCount}건 전액. 연체 미수금은 그대로 남음`} />
        <Tile
          label={`확장 켬 · 상계 (${comparison.originalOffsetAgencyCount}곳, 원본 기준)`}
          value={won(comparison.originalOffsetKrw)}
          note={`현금 지급 ${won(comparison.originalCashKrw)}. 같은 금액의 미수금이 소멸`}
          tone="text-brand-strong"
        />
        <Tile
          label="지금까지 회계·자금팀이 확정한 상계"
          value={won(comparison.settledOffsetKrw)}
          note={
            comparison.proposedOffsetKrw > comparison.settledOffsetKrw
              ? `결정 대기 포함 현재 기준 ${won(comparison.proposedOffsetKrw)}`
              : '입금·전액 지급 결정·이의 인정만큼 원본 기준보다 줄어듭니다'
          }
          tone="text-success"
        />
      </div>
      <div className="mt-3 flex flex-col gap-2">
        <Notice tone="warn">
          교육용 가정: 대리점 계약서에 “이행기 지난 물품대금과 장려금 대등액 상계” 조항이 있고 상계 요건(같은 당사자·같은 종류 채무·이행기
          도래)을 확인했다고 두며, {comparison.overdueDaysOver}일 넘게 연체된 잔액만 대상으로 합니다. 원본에 계약서는 없습니다. 장려금
          몰수가 아니라 상계라 같은 금액의 미수금이 함께 줄어들고, 시스템은 장려금을 스스로 깎지 않습니다.
        </Notice>
        <p className="text-xs leading-relaxed text-ink-mute">
          다른 부서 영향: 회계·자금팀의 채권 회수 목표 때문에 지급 대안이 달라집니다. 영업 승인 뒤에도 연체 대리점의 장려금은 회계·자금팀이
          상계 / 전액 지급 후 별도 회수 중에서 고르고, 대리점 이의가 오면 다시 판단합니다. 제안액은 현재 잔액 기준이라 입금이나 상계가
          반영되면 바뀝니다.
        </p>
      </div>
    </Card>
  )
}

function Tile({ label, value, note, tone = 'text-ink' }: { label: string; value: string; note: string; tone?: string }) {
  return (
    <div className="min-w-0 rounded-xl bg-fill px-4 py-3.5">
      <div className="text-xs font-medium text-ink-soft">{label}</div>
      <div className={`num mt-1.5 truncate text-2xl font-bold ${tone}`}>{value}</div>
      <div className="mt-1 text-xs text-ink-mute">{note}</div>
    </div>
  )
}

function AgencyCard({ agencies, role, days }: { agencies: ReceivableAgency[]; role: RoleCode; days: number }) {
  const [open, setOpen] = useState<string | null>(null)
  const overdueAgencies = agencies.filter((agency) => agency.overdueBeyondKrw > 0).length
  return (
    <Card className="min-w-0">
      <CardTitle aside={<span className="text-xs text-ink-mute">{days}일 초과 연체 {overdueAgencies}곳</span>}>
        대리점 미수금
      </CardTitle>
      <p className="mb-3 text-[13px] leading-relaxed text-ink-soft">
        원본 연령표 잔액 <ProvenanceBadge code="ORIGINAL" />에 상계 <ProvenanceBadge code="CALCULATED" />와 입금
        <ProvenanceBadge code="MOCK_REPLY" />을 반영한 현재 잔액입니다. 원본 파일은 바꾸지 않습니다.
      </p>
      {agencies.length === 0 ? (
        <Empty>미수금이 없습니다.</Empty>
      ) : (
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-line text-left text-xs text-ink-mute">
              <th className="py-2 font-medium">대리점</th>
              <th className="py-2 text-right font-medium">원본 잔액</th>
              <th className="py-2 text-right font-medium">상계·입금</th>
              <th className="py-2 text-right font-medium">현재 잔액</th>
              <th className="py-2 text-right font-medium">{days}일 초과</th>
              <th className="py-2 text-right font-medium" />
            </tr>
          </thead>
          <tbody>
            {agencies.map((agency) => (
              <AgencyRow
                key={agency.agencyId}
                agency={agency}
                role={role}
                open={open === agency.agencyId}
                toggle={() => setOpen(open === agency.agencyId ? null : agency.agencyId)}
              />
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}

function AgencyRow({ agency, role, open, toggle }: { agency: ReceivableAgency; role: RoleCode; open: boolean; toggle: () => void }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(String(agency.overdueBeyondKrw || agency.outstandingKrw))
  const [account, setAccount] = useState('')
  const deposit = useMutation({
    mutationFn: () =>
      post(`/agencies/${agency.agencyId}/deposits`, role, { amountKrw: Number(amount), bankAccount: account || '모의 입금 계좌' }),
    onSuccess: async () => {
      await queryClient.invalidateQueries()
      toggle()
    },
  })
  const adjusted = agency.offsetKrw + agency.depositKrw
  return (
    <>
      <tr className="border-b border-line last:border-0">
        <td className="py-2">
          <span className="font-semibold">{agency.agencyId}</span> <span className="text-ink-soft">{agency.agencyName}</span>
          <div className="text-[11px] text-ink-mute">
            송장 {agency.itemCount}건 · 최장 {agency.maxAgingDays}일
          </div>
        </td>
        <td className="num py-2 text-right text-ink-soft">{won(agency.originalOutstandingKrw)}</td>
        <td className="num py-2 text-right text-ink-soft">
          {adjusted === 0 ? '—' : `−${won(adjusted)}`}
          {agency.depositKrw > 0 && <div className="text-[11px] text-teal">입금 {won(agency.depositKrw)}</div>}
          {agency.offsetKrw > 0 && <div className="text-[11px] text-brand-strong">상계 {won(agency.offsetKrw)}</div>}
        </td>
        <td className="num py-2 text-right font-semibold">{won(agency.outstandingKrw)}</td>
        <td className={`num py-2 text-right font-semibold ${agency.overdueBeyondKrw > 0 ? 'text-danger' : 'text-ink-mute'}`}>
          {agency.overdueBeyondKrw > 0 ? won(agency.overdueBeyondKrw) : '—'}
        </td>
        <td className="py-2 pl-2 text-right">
          {agency.outstandingKrw > 0 && (
            <Button size="sm" kind="ghost" onClick={toggle}>
              입금
            </Button>
          )}
        </td>
      </tr>
      {open && (
        <tr className="border-b border-line bg-fill/60">
          <td colSpan={6} className="px-2 py-3">
            <div className="flex flex-wrap items-center gap-2">
              <ProvenanceBadge code="MOCK_REPLY" />
              <span className="text-xs text-ink-soft">은행 입금 통지(모의). 가장 오래 연체된 송장부터 줄어듭니다.</span>
              <input
                value={amount}
                onChange={(event) => setAmount(event.target.value.replace(/[^\d]/g, ''))}
                className="num h-9 w-36 rounded-xl border border-line bg-white px-3 text-right text-[13px] outline-none focus:border-brand"
              />
              <input
                value={account}
                onChange={(event) => setAccount(event.target.value)}
                placeholder="입금 계좌"
                className="h-9 w-44 rounded-xl border border-line bg-white px-3 text-[13px] outline-none focus:border-brand"
              />
              <Button size="sm" disabled={deposit.isPending || !amount} onClick={() => deposit.mutate()}>
                {won(Number(amount || 0))} 입금 반영
              </Button>
            </div>
            {deposit.error && <div className="mt-2"><Notice tone="error">{deposit.error.message}</Notice></div>}
          </td>
        </tr>
      )}
    </>
  )
}

function LinesCard({ comparison }: { comparison: OffsetComparison }) {
  const lines = comparison.lines.filter((line) => line.proposedOffsetKrw > 0 || line.settledOffsetKrw > 0)
  return (
    <Card className="min-w-0">
      <CardTitle aside={<span className="text-xs text-ink-mute">{lines.length}건</span>}>{comparison.period} 상계 대상 장려금</CardTitle>
      {lines.length === 0 ? (
        <Empty>상계 대상이 없습니다.</Empty>
      ) : (
        <table className="w-full text-[13px]">
          <thead>
            <tr className="border-b border-line text-left text-xs text-ink-mute">
              <th className="py-2 font-medium">대리점</th>
              <th className="py-2 text-right font-medium">장려금</th>
              <th className="py-2 text-right font-medium">상계</th>
              <th className="py-2 pl-3 font-medium">상태</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line) => (
              <tr key={line.rebateId} className="border-b border-line last:border-0" title={line.basis ?? undefined}>
                <td className="py-2">
                  <span className="font-semibold">{line.agencyId}</span> <span className="text-ink-soft">{line.agencyName}</span>
                  <div className="text-[11px] text-ink-mute">{line.rebateId}</div>
                </td>
                <td className="num py-2 text-right">{won(line.amountKrw)}</td>
                <td className="num py-2 text-right font-semibold text-brand-strong">
                  {won(line.settledOffsetKrw || line.proposedOffsetKrw)}
                </td>
                <td className="py-2 pl-3">
                  {line.paymentStatus ? <StatusBadge status={line.paymentStatus} /> : <Badge tone="gray">승인 전 · 예상</Badge>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}
