import { useQuery } from '@tanstack/react-query'
import { get } from '../api.ts'
import type { RelatedParties as Data } from '../api.ts'
import { Badge, Card, CardTitle, Empty, ProvenanceBadge, SourceRef } from '../ui.tsx'
import { EvidenceCard, HistoryCard } from './ReviewDetail.tsx'

/** Who counts as one related party today, why, and which pairs merely look alike. */
export function RelatedParties() {
  const { data } = useQuery({ queryKey: ['related-parties'], queryFn: () => get<Data>('/related-parties') })
  if (!data) {
    return <Empty>불러오는 중…</Empty>
  }
  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto pb-6">
      <Card>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
          <Stat label="대리점" value={`${data.agencies.length}곳`} />
          <Stat label="관련자 그룹" value={`${data.groups.length}개`} />
          <Stat label="근접 쌍 (1개 일치)" value={`${data.nearMisses.length}쌍`} />
          <span className="ml-auto text-xs text-ink-mute">
            기준일 {data.asOf} · 마스터 등록 값과 실제 입금 계좌를 함께 대조
          </span>
        </div>
      </Card>

      {data.groups.map((group) => (
        <div key={group.groupKey} className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <div className="flex flex-col gap-4">
            <Card>
              <CardTitle aside={<Badge tone="blue">{group.groupKey}</Badge>}>그룹 구성원</CardTitle>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line text-left text-xs text-ink-mute">
                    <th className="py-2 font-medium">대리점</th>
                    <th className="py-2 font-medium">사업자번호</th>
                    <th className="py-2 font-medium">등록일</th>
                    <th className="py-2 font-medium">원본 위치</th>
                  </tr>
                </thead>
                <tbody>
                  {group.members.map((member) => (
                    <tr key={member.agencyId} className="border-b border-line last:border-0">
                      <td className="py-2.5">
                        <span className="font-semibold">{member.agencyId}</span>{' '}
                        <span className="text-ink-soft">{member.name}</span>
                      </td>
                      <td className="num py-2.5 text-ink-soft">{member.businessRegNo}</td>
                      <td className="num py-2.5 text-ink-soft">{member.registeredDate}</td>
                      <td className="py-2.5">
                        <SourceRef value={member.sourceRef} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            {group.history && <HistoryCard history={group.history} />}
          </div>
          <EvidenceCard group={group} />
        </div>
      ))}

      <Card>
        <CardTitle aside={<ProvenanceBadge code="CALCULATED" />}>근접 쌍 — 1개만 일치, 관련자 아님</CardTitle>
        {data.nearMisses.length === 0 ? (
          <Empty>근접 쌍이 없습니다.</Empty>
        ) : (
          <ul className="flex flex-col gap-2">
            {data.nearMisses.map((link) => (
              <li key={`${link.agencyA}-${link.agencyB}`} className="flex flex-wrap items-center gap-2 rounded-xl border border-line p-3 text-sm">
                <span className="font-bold">
                  {link.agencyA} ↔ {link.agencyB}
                </span>
                {link.matches.map((match) => (
                  <span key={match.kind} className="flex items-center gap-1.5 text-[13px]">
                    <Badge tone="gray">{match.kindLabel}</Badge>
                    <span>{match.left.value}</span>
                  </span>
                ))}
                <span className="ml-auto text-xs text-ink-mute">합산하지 않음 · 식별정보가 하나 더 일치하면 그룹이 됨</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs font-medium text-ink-soft">{label}</div>
      <div className="num text-xl font-bold">{value}</div>
    </div>
  )
}
