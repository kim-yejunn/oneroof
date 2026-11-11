import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { get } from '../api.ts'
import type { AuditEntry } from '../api.ts'
import { ACTION_LABEL, Card, CardTitle, Empty, ProvenanceBadge, ROLE_LABEL } from '../ui.tsx'

/** The append-only record of who did what, newest first. */
export function AuditLog() {
  const { data: entries = [] } = useQuery({ queryKey: ['audit'], queryFn: () => get<AuditEntry[]>('/audit?limit=500') })
  const [filter, setFilter] = useState('')
  const needle = filter.trim().toLowerCase()
  const shown = needle
    ? entries.filter((entry) =>
        [entry.detail, entry.action, ACTION_LABEL[entry.action] ?? '', entry.subjectId, entry.correlationId ?? '', entry.actorRole]
          .join(' ')
          .toLowerCase()
          .includes(needle),
      )
    : entries

  return (
    <Card className="flex h-full min-h-0 flex-col">
      <CardTitle
        aside={
          <input
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="내용·대상·사건 ID 검색"
            className="h-9 w-64 rounded-xl border border-line px-3 text-sm outline-none focus:border-brand"
          />
        }
      >
        감사 로그 <span className="ml-1 text-xs font-medium text-ink-mute">{shown.length}건 · 추가만 가능</span>
      </CardTitle>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {shown.length === 0 ? (
          <Empty>기록이 없습니다.</Empty>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-white">
              <tr className="border-b border-line text-left text-xs text-ink-mute">
                <th className="py-2 font-medium">#</th>
                <th className="py-2 font-medium">기준일</th>
                <th className="py-2 font-medium">행위자</th>
                <th className="py-2 font-medium">행위</th>
                <th className="py-2 font-medium">대상</th>
                <th className="py-2 font-medium">내용</th>
                <th className="py-2 font-medium">출처</th>
                <th className="py-2 font-medium">사건 ID</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((entry) => (
                <tr key={entry.id} className="border-b border-line align-top last:border-0">
                  <td className="num py-2 pr-2 text-xs text-ink-mute">{entry.id}</td>
                  <td className="num whitespace-nowrap py-2 pr-3 text-xs text-ink-soft">{entry.businessDate}</td>
                  <td className="whitespace-nowrap py-2 pr-3 text-[13px] font-medium">{ROLE_LABEL[entry.actorRole] ?? entry.actorRole}</td>
                  <td className="whitespace-nowrap py-2 pr-3 text-[13px] font-bold">{ACTION_LABEL[entry.action] ?? entry.action}</td>
                  <td className="whitespace-nowrap py-2 pr-3 font-mono text-[11px] text-ink-soft">
                    {entry.subjectType} {entry.subjectId.replace(/^.*\//, '')}
                  </td>
                  <td className="py-2 pr-3 text-xs leading-relaxed text-ink-soft">{entry.detail}</td>
                  <td className="py-2 pr-3">
                    <ProvenanceBadge code={entry.provenance} />
                  </td>
                  <td className="whitespace-nowrap py-2 font-mono text-[11px] text-ink-mute">{entry.correlationId ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Card>
  )
}
