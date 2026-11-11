import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import {
  adminAccessErrorMessage,
  isAdminActorNotFound,
  isAdminPermissionError,
  useAdminAccess,
} from '../features/admin/AdminAccessProvider';
import { adminApi } from '../features/admin/adminApi';
import type { AdminRole, AdminStatus, AdminUserDetail } from '../features/admin/types';
import { ApiRequestError } from '../lib/apiClient';
import { useOptionalAuth } from '../lib/authContext';

type Notice = { message: string; retry?: () => Promise<void> } | null;
type PendingChange =
  | { kind: 'role'; role: AdminRole }
  | { kind: 'status'; status: AdminStatus };
type AdminOperationSnapshot = {
  targetId: number;
  targetGeneration: number;
  operationGeneration: number;
  accessGeneration: number;
  viewerId: number | null;
};

function parseUserId(value: string | undefined): number | null {
  if (!value || !/^\d+$/u.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function formatDate(value: string | null): string {
  if (!value) return '기록 없음';
  const date = value.slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/u.test(date) ? date.replaceAll('-', '.') : '날짜 미정';
}

function formatLastAccess(value: string | null): string {
  return value ? `마지막 접속 ${formatDate(value)}` : '마지막 접속 기록 없음';
}

function actionErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'ADMIN_002') return error.message || '자기 자신의 관리자 권한 회수, 계정 정지 또는 삭제는 할 수 없습니다.';
    if (error.code === 'USER_001') return '대상 사용자를 찾을 수 없습니다.';
    if (error.code === 'USER_003') return '정지된 계정은 관리자 화면을 사용할 수 없습니다.';
    if (error.code === 'ADMIN_001') return '현재 관리자 권한이 없습니다.';
    return error.message || '관리자 요청을 처리하지 못했습니다.';
  }
  return error instanceof Error && error.message ? error.message : '관리자 요청을 처리하지 못했습니다.';
}

function DetailLoading({ label }: { label: string }) {
  return <div className="mt-6 border-[3px] border-ink bg-surface px-6 py-12 text-center text-[13px] text-text-muted shadow-panel">{label}</div>;
}

/** `/admin/users/:userId` — canonical 상세를 기준으로 역할·상태·soft delete를 관리한다. */
export function AdminUserPage() {
  const { userId: rawUserId } = useParams();
  const userId = useMemo(() => parseUserId(rawUserId), [rawUserId]);
  const auth = useOptionalAuth();
  const access = useAdminAccess();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const [targetMissing, setTargetMissing] = useState(false);
  const [selectedRole, setSelectedRole] = useState<AdminRole>('USER');
  const [mutation, setMutation] = useState<'role' | 'status' | 'delete' | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [pendingChange, setPendingChange] = useState<PendingChange | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const mountedRef = useRef(false);
  const targetGenerationRef = useRef(0);
  const operationGenerationRef = useRef(0);

  const isSelf = detail !== null && auth?.user?.id === detail.id;
  const canMutate = !isSelf && detail !== null && access.status === 'allowed' && mutation === null;
  const actionAvailable = canMutate && pendingChange === null;

  function isPageCurrent(snapshot: AdminOperationSnapshot) {
    return (
      mountedRef.current &&
      snapshot.targetId === userId &&
      snapshot.targetGeneration === targetGenerationRef.current &&
      access.isCurrent(snapshot.accessGeneration, snapshot.viewerId)
    );
  }

  function isOperationCurrent(snapshot: AdminOperationSnapshot) {
    return isPageCurrent(snapshot) && snapshot.operationGeneration === operationGenerationRef.current;
  }

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      targetGenerationRef.current += 1;
      operationGenerationRef.current += 1;
    };
  }, []);

  useEffect(() => {
    targetGenerationRef.current += 1;
    operationGenerationRef.current += 1;
    setDetail(null);
    setNotice(null);
    setTargetMissing(false);
    setMutation(null);
    setDeleteOpen(false);
    setPendingChange(null);
    setSelectedRole('USER');
  }, [access.generation, access.status, userId]);

  useEffect(() => {
    if (access.status !== 'allowed' || userId === null) return undefined;
    const operationSnapshot: AdminOperationSnapshot = {
      targetId: userId,
      targetGeneration: targetGenerationRef.current,
      operationGeneration: ++operationGenerationRef.current,
      accessGeneration: access.generation,
      viewerId: access.viewerId,
    };
    let cancelled = false;
    setLoading(true);
    setNotice(null);
    setTargetMissing(false);
    void adminApi.get(userId)
      .then((loaded) => {
        if (cancelled || !isOperationCurrent(operationSnapshot)) return;
        setDetail(loaded);
        setSelectedRole(loaded.role);
        setLoading(false);
      })
      .catch(async (caught: unknown) => {
        if (cancelled || !isOperationCurrent(operationSnapshot)) return;
        if (isAdminPermissionError(caught) || isAdminActorNotFound(caught)) {
          setDetail(null);
          setLoading(true);
          setTargetMissing(false);
          const boundary = await access.revalidateAfterError(caught, { preserveAllowed: true });
          if (!isOperationCurrent(operationSnapshot)) return;
          if (boundary === 'target') {
            setDetail(null);
            setLoading(false);
            setTargetMissing(true);
          }
          return;
        }
        setLoading(false);
        setNotice({ message: adminAccessErrorMessage(caught), retry: async () => {
          if (!isOperationCurrent(operationSnapshot)) return;
          setRetryKey((value) => value + 1);
        } });
      });
    return () => {
      cancelled = true;
    };
  }, [access, retryKey, userId]);

  useEffect(() => {
    if (detail) setSelectedRole(detail.role);
  }, [detail]);

  const refreshCanonical = async (snapshot: AdminOperationSnapshot) => {
    if (!isOperationCurrent(snapshot)) return false;
    const canonical = await adminApi.get(snapshot.targetId);
    if (!isOperationCurrent(snapshot)) return false;
    setDetail(canonical);
    setSelectedRole(canonical.role);
    return true;
  };

  const handleMutationError = async (caught: unknown, snapshot: AdminOperationSnapshot, retry: () => Promise<void>) => {
    if (!isOperationCurrent(snapshot)) return;
    if (isAdminPermissionError(caught) || isAdminActorNotFound(caught)) {
      setDetail(null);
      setLoading(true);
      setTargetMissing(false);
      const boundary = await access.revalidateAfterError(caught, { preserveAllowed: true });
      if (!isOperationCurrent(snapshot)) return;
      if (boundary === 'target') {
        setDetail(null);
        setLoading(false);
        setTargetMissing(true);
      }
      return;
    }
    setNotice({ message: actionErrorMessage(caught), retry });
  };

  const changeRole = async (nextRole = selectedRole) => {
    if (!detail || !canMutate || nextRole === detail.role) return;
    const snapshot: AdminOperationSnapshot = {
      targetId: detail.id,
      targetGeneration: targetGenerationRef.current,
      operationGeneration: ++operationGenerationRef.current,
      accessGeneration: access.generation,
      viewerId: access.viewerId,
    };
    const action = async () => {
      if (!isOperationCurrent(snapshot)) return;
      setNotice(null);
      setMutation('role');
      try {
        await adminApi.changeRole(snapshot.targetId, nextRole);
        await refreshCanonical(snapshot);
      } catch (caught) {
        await handleMutationError(caught, snapshot, action);
      } finally {
        if (isOperationCurrent(snapshot)) setMutation(null);
      }
    };
    await action();
  };

  const changeStatus = async (nextStatus: AdminStatus = detail?.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE') => {
    if (!detail || !canMutate) return;
    const snapshot: AdminOperationSnapshot = {
      targetId: detail.id,
      targetGeneration: targetGenerationRef.current,
      operationGeneration: ++operationGenerationRef.current,
      accessGeneration: access.generation,
      viewerId: access.viewerId,
    };
    const action = async () => {
      if (!isOperationCurrent(snapshot)) return;
      setNotice(null);
      setMutation('status');
      try {
        await adminApi.changeStatus(snapshot.targetId, nextStatus);
        await refreshCanonical(snapshot);
      } catch (caught) {
        await handleMutationError(caught, snapshot, action);
      } finally {
        if (isOperationCurrent(snapshot)) setMutation(null);
      }
    };
    await action();
  };

  const confirmChange = async () => {
    const pending = pendingChange;
    if (!pending) return;
    setPendingChange(null);
    if (pending.kind === 'role') await changeRole(pending.role);
    else await changeStatus(pending.status);
  };

  const deleteAccount = async () => {
    if (!detail || !canMutate) return;
    const snapshot: AdminOperationSnapshot = {
      targetId: detail.id,
      targetGeneration: targetGenerationRef.current,
      operationGeneration: ++operationGenerationRef.current,
      accessGeneration: access.generation,
      viewerId: access.viewerId,
    };
    if (!isOperationCurrent(snapshot)) return;
    setMutation('delete');
    setNotice(null);
    try {
      await adminApi.delete(snapshot.targetId);
      if (isOperationCurrent(snapshot)) navigate('/admin');
    } catch (caught) {
      await handleMutationError(caught, snapshot, deleteAccount);
    } finally {
      if (isOperationCurrent(snapshot)) {
        setMutation(null);
        setDeleteOpen(false);
      }
    }
  };

  if (userId === null || targetMissing) {
    return (
      <section aria-label="사용자 상세">
        <p className="mb-3 text-xs text-text-muted"><Link to="/admin" className="text-accent hover:text-accent-strong">ADMIN CONSOLE</Link> › 사용자 상세</p>
        <h1 className="sr-only">사용자 상세</h1>
        <h1 className="text-[23px] font-bold">사용자를 찾을 수 없습니다</h1>
        <p className="mt-2 text-[13px] text-text-body">관리 대상이 없거나 이미 삭제되었습니다.</p>
      </section>
    );
  }

  if (loading && !detail) return <DetailLoading label="사용자 정보를 불러오는 중..." />;
  if (!detail) {
    return (
      <section aria-label="사용자 상세">
        <p className="mb-3 text-xs text-text-muted"><Link to="/admin" className="text-accent hover:text-accent-strong">ADMIN CONSOLE</Link> › 사용자 상세</p>
        <h1 className="sr-only">사용자 상세</h1>
        {notice && <p role="alert" className="border-[3px] border-danger bg-danger-bg px-5 py-4 text-[13px] text-danger shadow-card">{notice.message}{notice.retry && <button type="button" onClick={() => void notice.retry?.()} className="ml-3 border-2 border-danger bg-surface px-3 py-1 text-xs font-bold text-danger">다시 시도</button>}</p>}
      </section>
    );
  }

  return (
    <section aria-label="사용자 상세">
      <p className="mb-3 text-xs text-text-muted"><Link to="/admin" className="text-accent hover:text-accent-strong">ADMIN CONSOLE</Link> › 사용자 상세</p>
      <h1 className="sr-only">사용자 상세</h1>
      {notice && (
        <div role="alert" className="mb-4 border-[3px] border-danger bg-danger-bg px-5 py-4 text-[13px] text-danger shadow-card">
          {notice.message}
          {notice.retry && <button type="button" onClick={() => void notice.retry?.()} className="ml-3 border-2 border-danger bg-surface px-3 py-1 text-xs font-bold text-danger">다시 시도</button>}
        </div>
      )}
      <div className="border-[3px] border-ink bg-surface shadow-panel">
        <header className="flex items-center gap-4 border-b-[3px] border-ink bg-[linear-gradient(180deg,#fff8ec,#fff)] px-[26px] py-[22px]">
          {detail.profileImageUrl ? (
            <img src={detail.profileImageUrl} alt="" className="h-[60px] w-[60px] shrink-0 border-[3px] border-ink object-cover" />
          ) : (
            <span aria-label={`${detail.nickname} 아바타`} role="img" className="grid h-[60px] w-[60px] shrink-0 place-items-center border-[3px] border-ink bg-universe-bg text-[28px]">🛰</span>
          )}
          <div className="min-w-0 flex-1">
            <div className="text-[19px] font-bold">
              {detail.nickname} <Badge kind={detail.status} />
            </div>
            <div className="text-[12.5px] text-text-muted">
              {detail.email} · 가입 {formatDate(detail.createdAt)} · {formatLastAccess(detail.lastAccessAt)}
            </div>
          </div>
          <span className="font-pixel text-[8px] text-universe">UID #{String(detail.id).padStart(4, '0')}</span>
        </header>
        <div className="grid grid-cols-3 border-b-[3px] border-ink">
          <DetailStat label="게시글" value={detail.postStats.total} />
          <DetailStat label="댓글" value={detail.commentCount} />
          <DetailStat label="유니버스 연결" value={detail.universeStats.connectionCount} last />
        </div>
        <div className="flex flex-col gap-3.5 px-[26px] py-5">
          <div className="flex items-center gap-3">
            <span className="w-[90px] text-[13px] font-semibold text-text-muted">역할</span>
            <select
              aria-label="역할"
              value={selectedRole}
              onChange={(event) => setSelectedRole(event.target.value as AdminRole)}
              disabled={!canMutate || (isSelf && detail.role === 'ADMIN')}
              className="border-2 border-ink bg-surface-warm px-2.5 py-1.5 text-[13px] disabled:cursor-not-allowed disabled:border-shadow disabled:bg-surface-inert disabled:text-text-muted"
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
            <Button variant="ink" size="sm" disabled={!actionAvailable || selectedRole === detail.role || (isSelf && detail.role === 'ADMIN')} onClick={() => setPendingChange({ kind: 'role', role: selectedRole })}>역할 변경</Button>
          </div>
          <div className="flex items-center gap-3">
            <span className="w-[90px] text-[13px] font-semibold text-text-muted">계정 상태</span>
            <Button variant="warning" size="sm" disabled={!actionAvailable} onClick={() => setPendingChange({ kind: 'status', status: detail.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' })}>
              {detail.status === 'ACTIVE' ? '일시 정지' : '정지 해제'}
            </Button>
            <span className="text-xs text-text-muted">정지 시 로그인과 글 작성이 차단됩니다.</span>
          </div>
          <div className="flex items-center gap-3 border-t border-line pt-3.5">
            <span className="w-[90px] text-[13px] font-semibold text-danger">위험 구역</span>
            <Button variant="danger" size="sm" disabled={!actionAvailable} onClick={() => setDeleteOpen(true)}>계정 삭제</Button>
            <span className="text-xs text-text-muted">계정 접근이 차단되고 블로그와 게시글이 공개되지 않습니다. 데이터는 보존됩니다.</span>
          </div>
          {isSelf && <p className="ml-[102px] text-xs text-warning">자기 자신의 관리자 권한 회수, 계정 정지 또는 삭제는 할 수 없습니다.</p>}
        </div>
      </div>
      <Modal
        open={pendingChange !== null}
        title={pendingChange?.kind === 'role' ? '역할 변경 확인' : '계정 상태 변경 확인'}
        onClose={() => {
          if (mutation === null) setPendingChange(null);
        }}
        footer={
          <>
            <Button variant="neutral" size="sm" disabled={mutation !== null} onClick={() => setPendingChange(null)}>취소</Button>
            <Button variant="ink" size="sm" disabled={mutation !== null} onClick={() => void confirmChange()}>변경 확인</Button>
          </>
        }
      >
        {pendingChange?.kind === 'role' ? (
          <p><b>{detail.nickname}</b> ({detail.email})의 역할을 <b>{pendingChange.role}</b>로 변경할까요?</p>
        ) : (
          <p><b>{detail.nickname}</b> ({detail.email})의 계정 상태를 <b>{pendingChange?.status}</b>로 변경할까요?</p>
        )}
      </Modal>
      <Modal
        open={deleteOpen}
        title="계정 삭제 확인"
        onClose={() => {
          if (mutation === null) setDeleteOpen(false);
        }}
        footer={
          <>
            <Button variant="neutral" size="sm" disabled={mutation === 'delete'} onClick={() => setDeleteOpen(false)}>취소</Button>
            <Button variant="danger" size="sm" disabled={mutation === 'delete'} onClick={() => void deleteAccount()}>삭제 확인</Button>
          </>
        }
      >
        <p className="font-semibold text-ink">{detail.nickname} ({detail.email}) 계정을 삭제할까요?</p>
        <p className="mt-2 text-text-body">계정 접근이 차단되고 블로그와 게시글이 공개되지 않습니다. 데이터는 보존됩니다.</p>
      </Modal>
    </section>
  );
}

function DetailStat({ label, value, last = false }: { label: string; value: number; last?: boolean }) {
  return (
    <div className={`px-5 py-3.5 ${last ? '' : 'border-r border-line'}`}>
      <div className="text-[11px] font-bold tracking-[2px] text-text-muted">{label}</div>
      <div className="text-[22px] font-bold">{value}</div>
    </div>
  );
}
