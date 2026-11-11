import { Link } from 'react-router-dom';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import type { AdminUserSummary } from './types';

const ADMIN_USER_GRID = 'grid grid-cols-[2fr_1.6fr_1fr_1fr_1fr_90px] gap-[10px]';

function formatDate(value: string): string {
  const date = value.slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/u.test(date) ? date.replaceAll('-', '.') : '날짜 미정';
}

function UserCell({ user }: { user: AdminUserSummary }) {
  return (
    <div className="flex min-w-0 items-center gap-[9px]">
      {user.profileImageUrl ? (
        <img
          src={user.profileImageUrl}
          alt=""
          className="h-7 w-7 shrink-0 border-2 border-ink object-cover"
        />
      ) : (
        <Avatar emoji="🛰" size={28} label={`${user.nickname} 아바타`} />
      )}
      <div className="min-w-0">
        <b className="block truncate">{user.nickname}</b>
        <span className="block truncate text-[11px] text-text-muted">{user.email}</span>
      </div>
    </div>
  );
}

/** Pages 관리자 원본의 grid track/gap/padding을 유지하는 semantic 사용자 표. */
export function AdminUserTable({ users }: { users: AdminUserSummary[] }) {
  return (
    <table
      aria-label="관리자 사용자 목록"
      data-admin-user-table="true"
      data-admin-user-grid="2fr 1.6fr 1fr 1fr 1fr 90px"
      className="w-full border-[3px] border-ink bg-surface shadow-panel"
    >
      <thead>
        <tr className={`${ADMIN_USER_GRID} border-b-[3px] border-ink bg-surface-raise px-5 py-[11px] text-left text-[11px] font-bold tracking-[2px] text-text-body`}>
          <th scope="col" className="min-w-0">사용자</th>
          <th scope="col" className="min-w-0">블로그</th>
          <th scope="col" className="min-w-0">역할</th>
          <th scope="col" className="min-w-0">상태</th>
          <th scope="col" className="min-w-0">가입일</th>
          <th scope="col" aria-label="액션" className="min-w-0" />
        </tr>
      </thead>
      <tbody>
        {users.length === 0 ? (
          <tr className={`${ADMIN_USER_GRID} px-5 py-10`}>
            <td colSpan={6} className="col-span-6 text-center text-[13px] text-text-muted">항목이 없습니다.</td>
          </tr>
        ) : (
          users.map((user) => (
            <tr key={user.id} className={`${ADMIN_USER_GRID} items-center border-b border-line px-5 py-[13px] text-[13px] hover:bg-surface-soft`}>
              <td className="min-w-0"><UserCell user={user} /></td>
              <td className="min-w-0 text-text-body">{user.defaultBlog?.title ?? '블로그 없음'}</td>
              <td className="min-w-0"><Badge kind={user.role} /></td>
              <td className="min-w-0"><Badge kind={user.status} /></td>
              <td className="min-w-0 text-[12px] text-text-muted">{formatDate(user.createdAt)}</td>
              <td className="min-w-0">
                <Link
                  to={`/admin/users/${user.id}`}
                  className="block w-full border-2 border-ink bg-surface px-0 py-[5px] text-center text-[12px] font-semibold text-ink hover:bg-surface-raise"
                >
                  관리
                </Link>
              </td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
